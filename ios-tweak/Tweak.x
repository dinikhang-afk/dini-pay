#import <UIKit/UIKit.h>
#import <StoreKit/StoreKit.h>
#import "IAPProductModel.h"
#import "IAPOverlayViewController.h"

static BOOL isTargetApp(void) {
    NSString *bundleId = [[NSBundle mainBundle] bundleIdentifier];
    if (!bundleId) return NO;
    if ([bundleId isEqualToString:@"com.apple.springboard"]) return NO;
    if ([bundleId isEqualToString:@"com.apple.Preferences"]) return NO;
    if ([bundleId hasPrefix:@"com.apple."]) return NO;
    return YES;
}

// -------------------------------------------------------------
// Hook SKProductsRequest (Intercept requested product IDs)
// -------------------------------------------------------------
%hook SKProductsRequest

- (id)initWithProductIdentifiers:(NSSet<NSString *> *)productIdentifiers {
    id orig = %orig;
    if (isTargetApp() && productIdentifiers) {
        NSLog(@"[IAPCheck] Intercepted initWithProductIdentifiers: %@", productIdentifiers);
        [[IAPStoreManager sharedManager] recordRequestedIdentifiers:productIdentifiers];
    }
    return orig;
}

- (void)start {
    if (isTargetApp()) {
        NSLog(@"[IAPCheck] SKProductsRequest started for: %@", [[NSBundle mainBundle] bundleIdentifier]);
    }
    %orig;
}

%end

// -------------------------------------------------------------
// Hook SKProductsResponse (Intercept returned SKProduct details)
// -------------------------------------------------------------
%hook SKProductsResponse

- (NSArray<SKProduct *> *)products {
    NSArray<SKProduct *> *res = %orig;
    if (isTargetApp() && res && res.count > 0) {
        NSLog(@"[IAPCheck] Intercepted %lu SKProducts in response", (unsigned long)res.count);
        for (SKProduct *p in res) {
            [[IAPStoreManager sharedManager] recordSKProduct:p];
        }
    }
    return res;
}

- (NSArray<NSString *> *)invalidProductIdentifiers {
    NSArray<NSString *> *invalid = %orig;
    if (isTargetApp() && invalid.count > 0) {
        NSLog(@"[IAPCheck] Invalid Product Identifiers: %@", invalid);
    }
    return invalid;
}

%end

// -------------------------------------------------------------
// Hook SKPaymentQueue (Track checkout attempts)
// -------------------------------------------------------------
%hook SKPaymentQueue

- (void)addPayment:(SKPayment *)payment {
    if (isTargetApp() && payment) {
        NSLog(@"[IAPCheck] SKPaymentQueue addPayment: %@ (qty: %ld)", payment.productIdentifier, (long)payment.quantity);
        [[IAPStoreManager sharedManager] recordPayment:payment];
    }
    %orig;
}

%end

// -------------------------------------------------------------
// Shake Gesture Hook for triggering HUD on any screen
// -------------------------------------------------------------
%hook UIWindow

- (void)motionEnded:(UIEventSubtype)motion withEvent:(UIEvent *)event {
    if (motion == UIEventSubtypeMotionShake && isTargetApp()) {
        [IAPOverlayViewController toggle];
    }
    %orig;
}

%end

// -------------------------------------------------------------
// Pending Purchase IPC Trigger from Companion App
// -------------------------------------------------------------
static void checkAndTriggerPendingBuy(void) {
    NSString *docPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
    NSString *folder = [docPath stringByAppendingPathComponent:@"IAPCheck"];
    NSString *pendingPath = [folder stringByAppendingPathComponent:@"pending_buy.json"];

    // Also check global /var/mobile/Documents/IAPCheck
    if (![[NSFileManager defaultManager] fileExistsAtPath:pendingPath]) {
        pendingPath = @"/var/mobile/Documents/IAPCheck/pending_buy.json";
    }

    if (![[NSFileManager defaultManager] fileExistsAtPath:pendingPath]) return;

    NSData *data = [NSData dataWithContentsOfFile:pendingPath];
    if (!data) return;

    NSDictionary *dict = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (!dict) return;

    NSString *targetBundle = dict[@"bundleId"];
    NSString *targetProduct = dict[@"productId"];
    NSString *currentBundle = [[NSBundle mainBundle] bundleIdentifier];

    if ([currentBundle isEqualToString:targetBundle] && targetProduct.length > 0) {
        NSLog(@"[IAPCheck] Detected remote pending buy request for product: %@", targetProduct);

        // Remove file immediately to prevent duplicate triggers
        [[NSFileManager defaultManager] removeItemAtPath:pendingPath error:nil];

        dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(1.0 * NSEC_PER_SEC)), dispatch_get_main_queue(), ^{
            for (IAPProduct *p in [IAPStoreManager sharedManager].products) {
                if ([p.productId isEqualToString:targetProduct]) {
                    NSLog(@"[IAPCheck] Auto-launching StoreKit payment for %@", targetProduct);
                    [[IAPStoreManager sharedManager] launchPurchaseFlowForProduct:p];
                    return;
                }
            }

            // Fallback: If not cached yet, request directly
            NSLog(@"[IAPCheck] Product not in cache, fetching directly via SKProductsRequest: %@", targetProduct);
            NSSet *set = [NSSet setWithObject:targetProduct];
            SKProductsRequest *req = [[SKProductsRequest alloc] initWithProductIdentifiers:set];
            req.delegate = (id<SKProductsRequestDelegate>)[IAPStoreManager sharedManager];
            [req start];
        });
    }
}

static void onDarwinTriggerBuy(CFNotificationCenterRef center, void *observer, CFStringRef name, const void *object, CFDictionaryRef userInfo) {
    checkAndTriggerPendingBuy();
}

// -------------------------------------------------------------
// storekitd Daemon Hook: Spoof client bundle identifier for DiniPay
// -------------------------------------------------------------
static NSString *gSpoofBundleId = nil;

static void readPendingSpoofBundle(void) {
    NSString *path = @"/var/mobile/Documents/IAPCheck/pending_buy.json";
    if (![[NSFileManager defaultManager] fileExistsAtPath:path]) {
        path = @"/tmp/IAPCheck/pending_buy.json";
    }
    if (![[NSFileManager defaultManager] fileExistsAtPath:path]) return;

    NSData *data = [NSData dataWithContentsOfFile:path];
    if (!data) return;
    NSDictionary *dict = [NSJSONSerialization JSONObjectWithData:data options:0 error:nil];
    if (dict && dict[@"bundleId"]) {
        gSpoofBundleId = [dict[@"bundleId"] copy];
        NSLog(@"[IAPCheck][storekitd] Loaded spoof bundle ID: %@", gSpoofBundleId);
    }
}

// Hook Client identity in storekitd
%hook SKClient

- (NSString *)bundleIdentifier {
    NSString *orig = %orig;
    if ([orig isEqualToString:@"com.dini.pay"] || [orig isEqualToString:@"com.adr.checkiap"]) {
        readPendingSpoofBundle();
        if (gSpoofBundleId && gSpoofBundleId.length > 0) {
            NSLog(@"[IAPCheck][storekitd] Spoofing SKClient bundleIdentifier from %@ -> %@", orig, gSpoofBundleId);
            return gSpoofBundleId;
        }
    }
    return orig;
}

- (NSString *)clientBundleID {
    NSString *orig = %orig;
    if ([orig isEqualToString:@"com.dini.pay"] || [orig isEqualToString:@"com.adr.checkiap"]) {
        readPendingSpoofBundle();
        if (gSpoofBundleId && gSpoofBundleId.length > 0) {
            NSLog(@"[IAPCheck][storekitd] Spoofing SKClient clientBundleID from %@ -> %@", orig, gSpoofBundleId);
            return gSpoofBundleId;
        }
    }
    return orig;
}

%end

// -------------------------------------------------------------
// Constructor & Initialization
// -------------------------------------------------------------
%ctor {
    @autoreleasepool {
        NSString *procName = [[NSProcessInfo processInfo] processName];
        NSLog(@"[IAPCheck] Initializing in process: %@ (Bundle: %@)", procName, [[NSBundle mainBundle] bundleIdentifier]);

        if ([procName isEqualToString:@"storekitd"] || [procName isEqualToString:@"itunesstored"]) {
            NSLog(@"[IAPCheck] Active inside daemon: %@", procName);
            %init;
            return;
        }

        if (!isTargetApp()) {
            return;
        }

        // Register Darwin notification for cross-app IPC triggers
        CFNotificationCenterAddObserver(
            CFNotificationCenterGetDarwinNotifyCenter(),
            NULL,
            onDarwinTriggerBuy,
            CFSTR("com.adr.checkiap.trigger_buy"),
            NULL,
            CFNotificationSuspensionBehaviorDeliverImmediately
        );

        [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationDidFinishLaunchingNotification
                                                          object:nil
                                                           queue:[NSOperationQueue mainQueue]
                                                      usingBlock:^(NSNotification * _Nonnull note) {
            NSLog(@"[IAPCheck] App finished launching, initializing Floating HUD...");
            [IAPOverlayViewController setupFloatingButton];
            checkAndTriggerPendingBuy();
        }];

        [[NSNotificationCenter defaultCenter] addObserverForName:UIApplicationDidBecomeActiveNotification
                                                          object:nil
                                                           queue:[NSOperationQueue mainQueue]
                                                      usingBlock:^(NSNotification * _Nonnull note) {
            checkAndTriggerPendingBuy();
        }];
    }
}



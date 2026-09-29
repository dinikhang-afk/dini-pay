#import "IAPProductModel.h"

NSString * const kIAPCheckProductDiscoveredNotification = @"kIAPCheckProductDiscoveredNotification";

static NSString *formatPeriod(SKProductSubscriptionPeriod *period) {
    if (!period) return @"";
    NSString *unitLetter = @"D";
    switch (period.unit) {
        case SKProductPeriodUnitDay:   unitLetter = @"D"; break;
        case SKProductPeriodUnitWeek:  unitLetter = @"W"; break;
        case SKProductPeriodUnitMonth: unitLetter = @"M"; break;
        case SKProductPeriodUnitYear:  unitLetter = @"Y"; break;
    }
    return [NSString stringWithFormat:@"P%lu%@", (unsigned long)period.numberOfUnits, unitLetter];
}

static NSString *formatPrice(NSDecimalNumber *price, NSLocale *locale) {
    NSNumberFormatter *formatter = [[NSNumberFormatter alloc] init];
    formatter.numberStyle = NSNumberFormatterCurrencyStyle;
    formatter.locale = locale ?: [NSLocale currentLocale];
    return [formatter stringFromNumber:price] ?: [price stringValue];
}

@implementation IAPPricingPhase
- (NSDictionary *)toDictionary {
    return @{
        @"priceAmountMicros": @(self.priceAmountMicros),
        @"formattedPrice": self.formattedPrice ?: @"",
        @"priceCurrencyCode": self.priceCurrencyCode ?: @"",
        @"billingPeriod": self.billingPeriod ?: @"",
        @"recurrenceMode": @(self.recurrenceMode),
        @"billingCycleCount": @(self.billingCycleCount)
    };
}
@end

@implementation IAPOffer
- (NSDictionary *)toDictionary {
    NSMutableArray *phases = [NSMutableArray array];
    for (IAPPricingPhase *phase in self.pricingPhases) {
        [phases addObject:[phase toDictionary]];
    }
    return @{
        @"offerId": self.offerId ?: [NSNull null],
        @"classification": self.classification ?: @"REGULAR",
        @"pricingPhases": phases,
        @"summaryText": self.summaryText ?: @""
    };
}
@end

@implementation IAPProduct
- (instancetype)init {
    self = [super init];
    if (self) {
        _offers = [NSMutableArray array];
    }
    return self;
}

- (NSDictionary *)toDictionary {
    NSMutableArray *offersArr = [NSMutableArray array];
    for (IAPOffer *offer in self.offers) {
        [offersArr addObject:[offer toDictionary]];
    }
    return @{
        @"productId": self.productId ?: @"",
        @"productType": self.productType ?: @"INAPP",
        @"title": self.title ?: @"",
        @"description": self.productDescription ?: @"",
        @"formattedBasePrice": self.formattedBasePrice ?: @"",
        @"hasFreeTrial": @(self.hasFreeTrial),
        @"hasIntroDiscount": @(self.hasIntroDiscount),
        @"offers": offersArr
    };
}
@end

@interface IAPStoreManager ()
@property (nonatomic, strong) NSMutableArray<IAPProduct *> *products;
@property (nonatomic, strong) NSMutableSet<NSString *> *capturedIds;
@property (nonatomic, copy) NSString *bundleId;
@end

@implementation IAPStoreManager

+ (instancetype)sharedManager {
    static IAPStoreManager *instance = nil;
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        instance = [[IAPStoreManager alloc] init];
    });
    return instance;
}

- (instancetype)init {
    self = [super init];
    if (self) {
        _products = [NSMutableArray array];
        _capturedIds = [NSMutableSet set];
        _bundleId = [[NSBundle mainBundle] bundleIdentifier] ?: @"unknown_app";
    }
    return self;
}

- (void)recordRequestedIdentifiers:(NSSet<NSString *> *)identifiers {
    NSLog(@"[IAPCheck] Target app requested %lu products: %@", (unsigned long)identifiers.count, identifiers);
}

- (void)recordPayment:(SKPayment *)payment {
    NSLog(@"[IAPCheck] Target app initiated payment: %@ (qty: %ld)", payment.productIdentifier, (long)payment.quantity);
}

- (BOOL)launchPurchaseFlowForProduct:(IAPProduct *)product {
    if (!product || !product.rawSKProduct) {
        NSLog(@"[IAPCheck] Cannot launch payment: SKProduct is nil for %@", product.productId);
        return NO;
    }

    dispatch_async(dispatch_get_main_queue(), ^{
        NSLog(@"[IAPCheck] Triggering StoreKit payment flow for: %@", product.productId);
        SKPayment *payment = [SKPayment paymentWithProduct:product.rawSKProduct];
        [[SKPaymentQueue defaultQueue] addPayment:payment];
    });
    return YES;
}

- (void)recordSKProduct:(SKProduct *)skProduct {
    if (!skProduct || !skProduct.productIdentifier) return;

    @synchronized (self) {
        if ([self.capturedIds containsObject:skProduct.productIdentifier]) {
            // Update existing if needed
            return;
        }
        [self.capturedIds addObject:skProduct.productIdentifier];

        IAPProduct *product = [[IAPProduct alloc] init];
        product.productId = skProduct.productIdentifier;
        product.title = skProduct.localizedTitle ?: @"";
        product.productDescription = skProduct.localizedDescription ?: @"";
        product.formattedBasePrice = formatPrice(skProduct.price, skProduct.priceLocale);
        product.rawSKProduct = skProduct;

        BOOL isSubscription = (skProduct.subscriptionPeriod != nil);
        product.productType = isSubscription ? @"SUBS" : @"INAPP";


        // Parse Introductory Price / Free Trial
        if (skProduct.introductoryPrice) {
            SKProductDiscount *intro = skProduct.introductoryPrice;
            IAPOffer *offer = [[IAPOffer alloc] init];
            offer.offerId = intro.identifier;

            IAPPricingPhase *phase = [[IAPPricingPhase alloc] init];
            phase.formattedPrice = formatPrice(intro.price, intro.priceLocale ?: skProduct.priceLocale);
            phase.priceCurrencyCode = [intro.priceLocale objectForKey:NSLocaleCurrencyCode] ?: @"USD";
            phase.billingPeriod = formatPeriod(intro.subscriptionPeriod);
            phase.billingCycleCount = intro.numberOfPeriods;

            double priceVal = [intro.price doubleValue];
            if (intro.paymentMode == SKProductDiscountPaymentModeFreeTrial || priceVal == 0.0) {
                offer.classification = @"FREE_TRIAL";
                phase.priceAmountMicros = 0;
                offer.summaryText = [NSString stringWithFormat:@"FREE TRIAL (%@)", phase.billingPeriod];
                product.hasFreeTrial = YES;
            } else {
                offer.classification = @"INTRO_DISCOUNT";
                phase.priceAmountMicros = (int64_t)(priceVal * 1000000);
                offer.summaryText = [NSString stringWithFormat:@"INTRO: %@ (%@)", phase.formattedPrice, phase.billingPeriod];
                product.hasIntroDiscount = YES;
            }

            offer.pricingPhases = @[phase];
            [product.offers addObject:offer];
        }

        // Parse Promotional Discounts (iOS 12.2+)
        if (@available(iOS 12.2, *)) {
            for (SKProductDiscount *disc in skProduct.discounts) {
                IAPOffer *offer = [[IAPOffer alloc] init];
                offer.offerId = disc.identifier;

                IAPPricingPhase *phase = [[IAPPricingPhase alloc] init];
                phase.formattedPrice = formatPrice(disc.price, disc.priceLocale ?: skProduct.priceLocale);
                phase.priceCurrencyCode = [disc.priceLocale objectForKey:NSLocaleCurrencyCode] ?: @"USD";
                phase.billingPeriod = formatPeriod(disc.subscriptionPeriod);
                phase.billingCycleCount = disc.numberOfPeriods;

                double pVal = [disc.price doubleValue];
                if (disc.paymentMode == SKProductDiscountPaymentModeFreeTrial || pVal == 0.0) {
                    offer.classification = @"FREE_TRIAL";
                    phase.priceAmountMicros = 0;
                    offer.summaryText = [NSString stringWithFormat:@"PROMO TRIAL: %@", disc.identifier ?: @""];
                    product.hasFreeTrial = YES;
                } else {
                    offer.classification = @"INTRO_DISCOUNT";
                    phase.priceAmountMicros = (int64_t)(pVal * 1000000);
                    offer.summaryText = [NSString stringWithFormat:@"PROMO (%@): %@", disc.identifier ?: @"", phase.formattedPrice];
                    product.hasIntroDiscount = YES;
                }

                offer.pricingPhases = @[phase];
                [product.offers addObject:offer];
            }
        }

        [self.products addObject:product];
        NSLog(@"[IAPCheck] Discovered product: %@ (%@) | Trial: %d | Discounts: %lu",
              product.productId, product.formattedBasePrice, product.hasFreeTrial, (unsigned long)product.offers.count);

        [self saveToDisk];

        dispatch_async(dispatch_get_main_queue(), ^{
            [[NSNotificationCenter defaultCenter] postNotificationName:kIAPCheckProductDiscoveredNotification
                                                                object:product];
        });
    }
}

- (NSInteger)totalProducts {
    return self.products.count;
}

- (NSInteger)totalSubscriptions {
    NSInteger count = 0;
    for (IAPProduct *p in self.products) {
        if ([p.productType isEqualToString:@"SUBS"]) count++;
    }
    return count;
}

- (NSInteger)totalFreeTrials {
    NSInteger count = 0;
    for (IAPProduct *p in self.products) {
        if (p.hasFreeTrial) count++;
    }
    return count;
}

- (NSInteger)totalDiscounts {
    NSInteger count = 0;
    for (IAPProduct *p in self.products) {
        if (p.hasIntroDiscount) count++;
    }
    return count;
}

- (void)clearAll {
    @synchronized (self) {
        [self.products removeAllObjects];
        [self.capturedIds removeAllObjects];
    }
}

- (NSString *)exportJSONString {
    @synchronized (self) {
        NSMutableArray *list = [NSMutableArray array];
        for (IAPProduct *p in self.products) {
            [list addObject:[p.toDictionary mutableCopy]];
        }
        NSDictionary *root = @{
            @"bundleId": self.bundleId,
            @"timestamp": @((long)[[NSDate date] timeIntervalSince1970]),
            @"totalProducts": @(self.totalProducts),
            @"totalSubscriptions": @(self.totalSubscriptions),
            @"totalFreeTrials": @(self.totalFreeTrials),
            @"totalDiscounts": @(self.totalDiscounts),
            @"products": list
        };

        NSError *error = nil;
        NSData *data = [NSJSONSerialization dataWithJSONObject:root options:NSJSONWritingPrettyPrinted error:&error];
        if (error || !data) return @"{}";
        return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
    }
}

- (NSString *)saveToDisk {
    NSString *json = [self exportJSONString];
    NSString *docPath = [NSSearchPathForDirectoriesInDomains(NSDocumentDirectory, NSUserDomainMask, YES) firstObject];
    NSString *folder = [docPath stringByAppendingPathComponent:@"IAPCheck"];
    [[NSFileManager defaultManager] createDirectoryAtPath:folder withIntermediateDirectories:YES attributes:nil error:nil];

    NSString *filePath = [folder stringByAppendingPathComponent:[NSString stringWithFormat:@"%@_iap.json", self.bundleId]];
    [json writeToFile:filePath atomically:YES encoding:NSUTF8StringEncoding error:nil];
    return filePath;
}

@end

#import "IAPOverlayViewController.h"

@interface IAPOverlayViewController ()
@property (nonatomic, strong) UITableView *tableView;
@property (nonatomic, strong) NSArray<IAPProduct *> *dataSource;
@property (nonatomic, strong) UILabel *statsLabel;
@property (nonatomic, assign) BOOL showTrialsOnly;
@end

static UIWindow *gFloatingWindow = nil;
static UIButton *gFloatingButton = nil;
static UILabel *gBadgeLabel = nil;
static UIWindow *gModalWindow = nil;

@implementation IAPOverlayViewController

+ (void)setupFloatingButton {
    dispatch_async(dispatch_get_main_queue(), ^{
        if (gFloatingWindow) return;

        CGRect screenBounds = [UIScreen mainScreen].bounds;
        CGRect btnFrame = CGRectMake(screenBounds.size.width - 65, 120, 52, 52);

        if (@available(iOS 13.0, *)) {
            UIWindowScene *scene = nil;
            for (UIScene *s in [UIApplication sharedApplication].connectedScenes) {
                if (s.activationState == UISceneActivationStateForegroundActive && [s isKindOfClass:[UIWindowScene class]]) {
                    scene = (UIWindowScene *)s;
                    break;
                }
            }
            if (scene) {
                gFloatingWindow = [[UIWindow alloc] initWithWindowScene:scene];
            } else {
                gFloatingWindow = [[UIWindow alloc] initWithFrame:btnFrame];
            }
        } else {
            gFloatingWindow = [[UIWindow alloc] initWithFrame:btnFrame];
        }

        gFloatingWindow.frame = btnFrame;
        gFloatingWindow.windowLevel = UIWindowLevelAlert + 50;
        gFloatingWindow.backgroundColor = [UIColor clearColor];
        gFloatingWindow.hidden = NO;

        UIButton *btn = [UIButton buttonWithType:UIButtonTypeCustom];
        btn.frame = gFloatingWindow.bounds;
        btn.backgroundColor = [UIColor colorWithRed:0.08 green:0.08 blue:0.12 alpha:0.9];
        btn.layer.cornerRadius = 26;
        btn.layer.borderWidth = 1.5;
        btn.layer.borderColor = [UIColor colorWithRed:0.2 green:0.8 blue:0.6 alpha:0.9].CGColor;
        btn.layer.shadowColor = [UIColor blackColor].CGColor;
        btn.layer.shadowOpacity = 0.4;
        btn.layer.shadowRadius = 5;
        btn.layer.shadowOffset = CGSizeMake(0, 2);

        [btn setTitle:@"IAP" forState:UIControlStateNormal];
        btn.titleLabel.font = [UIFont boldSystemFontOfSize:14];
        [btn setTitleColor:[UIColor colorWithRed:0.2 green:0.9 blue:0.7 alpha:1.0] forState:UIControlStateNormal];
        [btn addTarget:self action:@selector(toggle) forControlEvents:UIControlEventTouchUpInside];

        UILabel *badge = [[UILabel alloc] initWithFrame:CGRectMake(32, -2, 22, 18)];
        badge.backgroundColor = [UIColor colorWithRed:0.95 green:0.25 blue:0.35 alpha:1.0];
        badge.textColor = [UIColor whiteColor];
        badge.font = [UIFont boldSystemFontOfSize:10];
        badge.textAlignment = NSTextAlignmentCenter;
        badge.layer.cornerRadius = 9;
        badge.clipsToBounds = YES;
        badge.text = @"0";
        badge.hidden = YES;
        [btn addSubview:badge];
        gBadgeLabel = badge;

        UIPanGestureRecognizer *pan = [[UIPanGestureRecognizer alloc] initWithTarget:self action:@selector(handlePan:)];
        [btn addGestureRecognizer:pan];

        [gFloatingWindow addSubview:btn];
        gFloatingButton = btn;

        [[NSNotificationCenter defaultCenter] addObserverForName:kIAPCheckProductDiscoveredNotification
                                                          object:nil
                                                           queue:[NSOperationQueue mainQueue]
                                                      usingBlock:^(NSNotification * _Nonnull note) {
            NSInteger count = [[IAPStoreManager sharedManager] totalProducts];
            gBadgeLabel.text = [NSString stringWithFormat:@"%ld", (long)count];
            gBadgeLabel.hidden = (count == 0);

            // Animate pulse
            [UIView animateWithDuration:0.15 animations:^{
                gFloatingButton.transform = CGAffineTransformMakeScale(1.15, 1.15);
            } completion:^(BOOL finished) {
                [UIView animateWithDuration:0.15 animations:^{
                    gFloatingButton.transform = CGAffineTransformIdentity;
                }];
            }];
        }];
    });
}

+ (void)handlePan:(UIPanGestureRecognizer *)pan {
    CGPoint translation = [pan translationInView:gFloatingWindow];
    CGRect frame = gFloatingWindow.frame;
    frame.origin.x += translation.x;
    frame.origin.y += translation.y;
    gFloatingWindow.frame = frame;
    [pan setTranslation:CGPointZero inView:gFloatingWindow];
}

+ (void)toggle {
    if (gModalWindow && !gModalWindow.hidden) {
        gModalWindow.hidden = YES;
        gModalWindow = nil;
        return;
    }

    CGRect bounds = [UIScreen mainScreen].bounds;
    if (@available(iOS 13.0, *)) {
        UIWindowScene *scene = nil;
        for (UIScene *s in [UIApplication sharedApplication].connectedScenes) {
            if (s.activationState == UISceneActivationStateForegroundActive && [s isKindOfClass:[UIWindowScene class]]) {
                scene = (UIWindowScene *)s;
                break;
            }
        }
        if (scene) {
            gModalWindow = [[UIWindow alloc] initWithWindowScene:scene];
        } else {
            gModalWindow = [[UIWindow alloc] initWithFrame:bounds];
        }
    } else {
        gModalWindow = [[UIWindow alloc] initWithFrame:bounds];
    }

    gModalWindow.windowLevel = UIWindowLevelAlert + 80;
    IAPOverlayViewController *vc = [[IAPOverlayViewController alloc] init];
    gModalWindow.rootViewController = vc;
    gModalWindow.hidden = NO;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = [UIColor colorWithWhite:0 alpha:0.65];

    [self setupUI];
    [self reloadData];
}

- (void)setupUI {
    CGFloat w = self.view.bounds.size.width;
    CGFloat h = self.view.bounds.size.height;

    // Card Container
    UIView *card = [[UIView alloc] initWithFrame:CGRectMake(16, 50, w - 32, h - 80)];
    card.backgroundColor = [UIColor colorWithRed:0.10 green:0.11 blue:0.15 alpha:0.97];
    card.layer.cornerRadius = 18;
    card.layer.borderWidth = 1.0;
    card.layer.borderColor = [UIColor colorWithWhite:0.3 alpha:0.4].CGColor;
    card.clipsToBounds = YES;
    card.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self.view addSubview:card];

    // Header View
    UIView *header = [[UIView alloc] initWithFrame:CGRectMake(0, 0, card.bounds.size.width, 110)];
    header.backgroundColor = [UIColor colorWithRed:0.14 green:0.15 blue:0.20 alpha:1.0];
    header.autoresizingMask = UIViewAutoresizingFlexibleWidth;
    [card addSubview:header];

    UILabel *title = [[UILabel alloc] initWithFrame:CGRectMake(16, 12, card.bounds.size.width - 100, 24)];
    title.text = @"⚡ IAP Check Inspector";
    title.font = [UIFont boldSystemFontOfSize:18];
    title.textColor = [UIColor colorWithRed:0.3 green:0.9 blue:0.7 alpha:1.0];
    [header addSubview:title];

    UIButton *closeBtn = [UIButton buttonWithType:UIButtonTypeSystem];
    closeBtn.frame = CGRectMake(card.bounds.size.width - 45, 12, 32, 28);
    closeBtn.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin;
    [closeBtn setTitle:@"✕" forState:UIControlStateNormal];
    closeBtn.titleLabel.font = [UIFont boldSystemFontOfSize:18];
    [closeBtn setTitleColor:[UIColor colorWithWhite:0.7 alpha:1.0] forState:UIControlStateNormal];
    [closeBtn addTarget:self action:@selector(closeTapped) forControlEvents:UIControlEventTouchUpInside];
    [header addSubview:closeBtn];

    UILabel *bundleLabel = [[UILabel alloc] initWithFrame:CGRectMake(16, 38, card.bounds.size.width - 32, 18)];
    bundleLabel.text = [NSString stringWithFormat:@"App: %@", [IAPStoreManager sharedManager].bundleId];
    bundleLabel.font = [UIFont systemFontOfSize:12 weight:UIFontWeightMedium];
    bundleLabel.textColor = [UIColor colorWithWhite:0.7 alpha:1.0];
    [header addSubview:bundleLabel];

    self.statsLabel = [[UILabel alloc] initWithFrame:CGRectMake(16, 62, card.bounds.size.width - 32, 36)];
    self.statsLabel.font = [UIFont systemFontOfSize:12 weight:UIFontWeightRegular];
    self.statsLabel.textColor = [UIColor whiteColor];
    self.statsLabel.numberOfLines = 2;
    [header addSubview:self.statsLabel];

    // Table View
    CGFloat tableY = 110;
    CGFloat footerH = 50;
    self.tableView = [[UITableView alloc] initWithFrame:CGRectMake(0, tableY, card.bounds.size.width, card.bounds.size.height - tableY - footerH) style:UITableViewStylePlain];
    self.tableView.backgroundColor = [UIColor clearColor];
    self.tableView.separatorColor = [UIColor colorWithWhite:0.2 alpha:0.5];
    self.tableView.dataSource = self;
    self.tableView.delegate = self;
    self.tableView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [card addSubview:self.tableView];

    // Footer with Actions
    UIView *footer = [[UIView alloc] initWithFrame:CGRectMake(0, card.bounds.size.height - footerH, card.bounds.size.width, footerH)];
    footer.backgroundColor = [UIColor colorWithRed:0.13 green:0.14 blue:0.18 alpha:1.0];
    footer.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleTopMargin;
    [card addSubview:footer];

    UIButton *copyBtn = [UIButton buttonWithType:UIButtonTypeSystem];
    copyBtn.frame = CGRectMake(16, 8, 120, 34);
    copyBtn.backgroundColor = [UIColor colorWithRed:0.2 green:0.5 blue:0.9 alpha:0.9];
    copyBtn.layer.cornerRadius = 8;
    [copyBtn setTitle:@"📋 Copy JSON" forState:UIControlStateNormal];
    [copyBtn setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    copyBtn.titleLabel.font = [UIFont boldSystemFontOfSize:13];
    [copyBtn addTarget:self action:@selector(copyJSONTapped) forControlEvents:UIControlEventTouchUpInside];
    [footer addSubview:copyBtn];

    UIButton *filterBtn = [UIButton buttonWithType:UIButtonTypeSystem];
    filterBtn.frame = CGRectMake(146, 8, 110, 34);
    filterBtn.backgroundColor = [UIColor colorWithRed:0.2 green:0.7 blue:0.4 alpha:0.9];
    filterBtn.layer.cornerRadius = 8;
    [filterBtn setTitle:@"★ Free Trials" forState:UIControlStateNormal];
    [filterBtn setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    filterBtn.titleLabel.font = [UIFont boldSystemFontOfSize:13];
    [filterBtn addTarget:self action:@selector(toggleTrialsFilter:) forControlEvents:UIControlEventTouchUpInside];
    [footer addSubview:filterBtn];

    UIButton *clearBtn = [UIButton buttonWithType:UIButtonTypeSystem];
    clearBtn.frame = CGRectMake(card.bounds.size.width - 80, 8, 65, 34);
    clearBtn.autoresizingMask = UIViewAutoresizingFlexibleLeftMargin;
    clearBtn.backgroundColor = [UIColor colorWithRed:0.4 green:0.2 blue:0.2 alpha:0.9];
    clearBtn.layer.cornerRadius = 8;
    [clearBtn setTitle:@"Clear" forState:UIControlStateNormal];
    [clearBtn setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    clearBtn.titleLabel.font = [UIFont systemFontOfSize:13];
    [clearBtn addTarget:self action:@selector(clearTapped) forControlEvents:UIControlEventTouchUpInside];
    [footer addSubview:clearBtn];
}

- (void)reloadData {
    NSArray *all = [[IAPStoreManager sharedManager].products copy];
    if (self.showTrialsOnly) {
        NSPredicate *pred = [NSPredicate predicateWithFormat:@"hasFreeTrial == YES"];
        self.dataSource = [all filteredArrayUsingPredicate:pred];
    } else {
        self.dataSource = all;
    }

    IAPStoreManager *mgr = [IAPStoreManager sharedManager];
    self.statsLabel.text = [NSString stringWithFormat:@"Total: %ld  |  Subs: %ld  |  ⚡ Free Trials: %ld  |  🏷 Discounts: %ld",
                            (long)mgr.totalProducts, (long)mgr.totalSubscriptions, (long)mgr.totalFreeTrials, (long)mgr.totalDiscounts];
    [self.tableView reloadData];
}

- (void)closeTapped {
    [IAPOverlayViewController toggle];
}

- (void)copyJSONTapped {
    NSString *json = [[IAPStoreManager sharedManager] exportJSONString];
    [UIPasteboard generalPasteboard].string = json;

    UIAlertController *alert = [UIAlertController alertControllerWithTitle:@"Export Thành Công"
                                                                   message:@"Đã copy toàn bộ dữ liệu JSON vào Clipboard!"
                                                            preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:nil]];
    [self presentViewController:alert animated:YES completion:nil];
}

- (void)toggleTrialsFilter:(UIButton *)sender {
    self.showTrialsOnly = !self.showTrialsOnly;
    sender.backgroundColor = self.showTrialsOnly ? [UIColor colorWithRed:0.9 green:0.6 blue:0.1 alpha:0.9] : [UIColor colorWithRed:0.2 green:0.7 blue:0.4 alpha:0.9];
    [self reloadData];
}

- (void)clearTapped {
    [[IAPStoreManager sharedManager] clearAll];
    [self reloadData];
}

#pragma mark - Table View Data Source

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
    return self.dataSource.count;
}

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
    IAPProduct *p = self.dataSource[indexPath.row];
    CGFloat baseH = 80;
    if (p.offers.count > 0) baseH += (p.offers.count * 22);
    return baseH;
}

- (UITableViewCell *)tableView:(UITableView *)tableView cellForRowAtIndexPath:(NSIndexPath *)indexPath {
    static NSString *cellId = @"IAPCell";
    UITableViewCell *cell = [tableView dequeueReusableCellWithIdentifier:cellId];
    if (!cell) {
        cell = [[UITableViewCell alloc] initWithStyle:UITableViewCellStyleSubtitle reuseIdentifier:cellId];
        cell.backgroundColor = [UIColor clearColor];
        cell.selectionStyle = UITableViewCellSelectionStyleNone;
        cell.textLabel.textColor = [UIColor whiteColor];
        cell.textLabel.font = [UIFont boldSystemFontOfSize:14];
        cell.detailTextLabel.textColor = [UIColor colorWithWhite:0.6 alpha:1.0];
        cell.detailTextLabel.numberOfLines = 0;
    }

    IAPProduct *p = self.dataSource[indexPath.row];

    NSString *badge = @"";
    if (p.hasFreeTrial) {
        badge = @" [★ FREE TRIAL DETECTED]";
    } else if (p.hasIntroDiscount) {
        badge = @" [INTRO DISCOUNT]";
    }

    cell.textLabel.text = [NSString stringWithFormat:@"%@ — %@ (%@)%@", p.title.length > 0 ? p.title : p.productId, p.formattedBasePrice, p.productType, badge];
    if (p.hasFreeTrial) {
        cell.textLabel.textColor = [UIColor colorWithRed:0.25 green:0.95 blue:0.55 alpha:1.0];
    } else {
        cell.textLabel.textColor = [UIColor whiteColor];
    }

    NSMutableString *details = [NSMutableString stringWithFormat:@"ID: %@\n", p.productId];
    if (p.productDescription.length > 0) {
        [details appendFormat:@"Desc: %@\n", p.productDescription];
    }
    for (IAPOffer *offer in p.offers) {
        [details appendFormat:@"  ▶ %@\n", offer.summaryText];
    }
    cell.detailTextLabel.text = details;

    UIButton *buyBtn = [UIButton buttonWithType:UIButtonTypeSystem];
    buyBtn.frame = CGRectMake(0, 0, 72, 30);
    buyBtn.tag = indexPath.row;
    [buyBtn setTitle:@"⚡ Mua" forState:UIControlStateNormal];
    [buyBtn setTitleColor:[UIColor whiteColor] forState:UIControlStateNormal];
    buyBtn.titleLabel.font = [UIFont boldSystemFontOfSize:12];
    buyBtn.backgroundColor = [UIColor colorWithRed:0.2 green:0.7 blue:0.4 alpha:0.95];
    buyBtn.layer.cornerRadius = 6;
    [buyBtn addTarget:self action:@selector(buyTapped:) forControlEvents:UIControlEventTouchUpInside];
    cell.accessoryView = buyBtn;

    return cell;
}

- (void)buyTapped:(UIButton *)sender {
    NSInteger idx = sender.tag;
    if (idx >= self.dataSource.count) return;
    IAPProduct *p = self.dataSource[idx];

    UIAlertController *confirm = [UIAlertController alertControllerWithTitle:@"⚡ Kích Hoạt Thanh Toán"
                                                                     message:[NSString stringWithFormat:@"Khởi chạy Apple StoreKit cho gói:\n%@\nGiá: %@", p.productId, p.formattedBasePrice]
                                                              preferredStyle:UIAlertControllerStyleAlert];

    [confirm addAction:[UIAlertAction actionWithTitle:@"Huỷ" style:UIAlertActionStyleCancel handler:nil]];
    [confirm addAction:[UIAlertAction actionWithTitle:@"Bật Popup Mua" style:UIAlertActionStyleDefault handler:^(UIAlertAction * _Nonnull action) {
        BOOL ok = [[IAPStoreManager sharedManager] launchPurchaseFlowForProduct:p];
        if (ok) {
            // Dismiss overlay so user can confirm StoreKit Apple Pay/FaceID sheet
            [IAPOverlayViewController toggle];
        }
    }]];

    [self presentViewController:confirm animated:YES completion:nil];
}

@end


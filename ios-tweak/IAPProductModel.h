#import <Foundation/Foundation.h>
#import <StoreKit/StoreKit.h>

NS_ASSUME_NONNULL_BEGIN

extern NSString * const kIAPCheckProductDiscoveredNotification;

@interface IAPPricingPhase : NSObject
@property (nonatomic, assign) int64_t priceAmountMicros;
@property (nonatomic, copy) NSString *formattedPrice;
@property (nonatomic, copy) NSString *priceCurrencyCode;
@property (nonatomic, copy) NSString *billingPeriod;
@property (nonatomic, assign) NSInteger recurrenceMode;
@property (nonatomic, assign) NSInteger billingCycleCount;
- (NSDictionary *)toDictionary;
@end

@interface IAPOffer : NSObject
@property (nonatomic, copy, nullable) NSString *offerId;
@property (nonatomic, copy) NSString *classification; // FREE_TRIAL, INTRO_DISCOUNT, REGULAR
@property (nonatomic, strong) NSArray<IAPPricingPhase *> *pricingPhases;
@property (nonatomic, copy) NSString *summaryText;
- (NSDictionary *)toDictionary;
@end

@interface IAPProduct : NSObject
@property (nonatomic, copy) NSString *productId;
@property (nonatomic, copy) NSString *productType; // INAPP, SUBS
@property (nonatomic, copy) NSString *title;
@property (nonatomic, copy) NSString *productDescription;
@property (nonatomic, copy) NSString *formattedBasePrice;
@property (nonatomic, strong) NSMutableArray<IAPOffer *> *offers;
@property (nonatomic, assign) BOOL hasFreeTrial;
@property (nonatomic, assign) BOOL hasIntroDiscount;
@property (nonatomic, strong, nullable) SKProduct *rawSKProduct;

- (NSDictionary *)toDictionary;
@end

@interface IAPStoreManager : NSObject
@property (nonatomic, strong, readonly) NSMutableArray<IAPProduct *> *products;
@property (nonatomic, copy, readonly) NSString *bundleId;

+ (instancetype)sharedManager;
- (void)recordSKProduct:(SKProduct *)skProduct;
- (void)recordRequestedIdentifiers:(NSSet<NSString *> *)identifiers;
- (BOOL)launchPurchaseFlowForProduct:(IAPProduct *)product;

- (void)recordPayment:(SKPayment *)payment;

- (NSInteger)totalProducts;
- (NSInteger)totalSubscriptions;
- (NSInteger)totalFreeTrials;
- (NSInteger)totalDiscounts;

- (void)clearAll;
- (NSString *)exportJSONString;
- (NSString *)saveToDisk;
@end

NS_ASSUME_NONNULL_END

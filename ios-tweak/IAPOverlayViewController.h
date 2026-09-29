#import <UIKit/UIKit.h>
#import "IAPProductModel.h"

NS_ASSUME_NONNULL_BEGIN

@interface IAPOverlayViewController : UIViewController <UITableViewDataSource, UITableViewDelegate>
+ (void)setupFloatingButton;
+ (void)toggle;
+ (void)showInWindow:(nullable UIWindow *)window;
@end

NS_ASSUME_NONNULL_END

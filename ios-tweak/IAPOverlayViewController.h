#import <UIKit/UIKit.h>
#import "IAPProductModel.h"

NS_ASSUME_NONNULL_BEGIN

@interface IAPOverlayViewController : UIViewController <UITableViewDataSource, UITableViewDelegate>
+ (void)showInWindow:(UIWindow *)window;
+ (void)toggle;
+ (void)setupFloatingButton;
@end

NS_ASSUME_NONNULL_END

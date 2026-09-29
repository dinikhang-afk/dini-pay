import Foundation
import StoreKit

/// Handles direct in-app StoreKit payment without leaving DiniPay.
/// Relies on storekitd hook to spoof the client bundle identifier to the target app's bundle ID.
public final class DirectPaymentHandler: NSObject, SKProductsRequestDelegate, SKPaymentTransactionObserver {
    public static let shared = DirectPaymentHandler()

    private var currentCompletion: ((Swift.Result<Void, IAPError>) -> Void)?
    private var activeRequest: SKProductsRequest?
    private var pendingProductId: String?
    private var targetBundleId: String?

    private override init() {
        super.init()
        SKPaymentQueue.default().add(self)
    }

    public func startPayment(
        productId: String,
        targetBundleId: String,
        completion: @escaping (Swift.Result<Void, IAPError>) -> Void
    ) {
        self.currentCompletion = completion
        self.pendingProductId = productId
        self.targetBundleId = targetBundleId

        // Request product details directly from StoreKit
        let productSet = Set([productId])
        let request = SKProductsRequest(productIdentifiers: productSet)
        self.activeRequest = request
        request.delegate = self
        request.start()
    }

    // MARK: - SKProductsRequestDelegate

    public func productsRequest(_ request: SKProductsRequest, didReceive response: SKProductsResponse) {
        DispatchQueue.main.async {
            guard let product = response.products.first(where: { $0.productIdentifier == self.pendingProductId }) ?? response.products.first else {
                let invalidList = response.invalidProductIdentifiers.joined(separator: ", ")
                let msg = invalidList.isEmpty ? "Không tìm thấy thông tin gói từ Apple StoreKit" : "Gói không hợp lệ: \(invalidList)"
                self.currentCompletion?(.failure(.failed(msg)))
                self.cleanup()
                return
            }

            // Product fetched successfully. Trigger official Apple payment sheet directly inside DiniPay
            let payment = SKPayment(product: product)
            SKPaymentQueue.default().add(payment)
        }
    }

    public func request(_ request: SKRequest, didFailWithError error: Error) {
        DispatchQueue.main.async {
            self.currentCompletion?(.failure(.failed("StoreKit request thất bại: \(error.localizedDescription)")))
            self.cleanup()
        }
    }

    // MARK: - SKPaymentTransactionObserver

    public func paymentQueue(_ queue: SKPaymentQueue, updatedTransactions transactions: [SKPaymentTransaction]) {
        for transaction in transactions {
            guard transaction.payment.productIdentifier == pendingProductId else { continue }

            switch transaction.transactionState {
            case .purchased:
                SKPaymentQueue.default().finishTransaction(transaction)
                DispatchQueue.main.async {
                    self.currentCompletion?(.success(()))
                    self.cleanup()
                }

            case .restored:
                SKPaymentQueue.default().finishTransaction(transaction)
                DispatchQueue.main.async {
                    self.currentCompletion?(.success(()))
                    self.cleanup()
                }

            case .failed:
                SKPaymentQueue.default().finishTransaction(transaction)
                let errorMsg = transaction.error?.localizedDescription ?? "Giao dịch bị huỷ hoặc từ chối"
                DispatchQueue.main.async {
                    self.currentCompletion?(.failure(.failed(errorMsg)))
                    self.cleanup()
                }

            case .purchasing, .deferred:
                break

            @unknown default:
                break
            }
        }
    }

    private func cleanup() {
        self.currentCompletion = nil
        self.activeRequest = nil
        self.pendingProductId = nil
        self.targetBundleId = nil
    }
}

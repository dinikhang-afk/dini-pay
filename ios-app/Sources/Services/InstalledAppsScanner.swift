import Foundation
import UIKit

public final class InstalledAppsScanner: ObservableObject {
    public static let shared = InstalledAppsScanner()

    @Published public var installedApps: [InstalledAppInfo] = []
    @Published public var isScanning: Bool = false

    public init() {}

    public func scanApps(includeSystem: Bool = false) {
        isScanning = true

        DispatchQueue.global(qos: .userInitiated).async {
            var results: [InstalledAppInfo] = []

            // Use Objective-C Runtime to access LSApplicationWorkspace dynamically
            if let workspaceClass = NSClassFromString("LSApplicationWorkspace") as? NSObject.Type,
               let defaultWorkspace = workspaceClass.perform(NSSelectorFromString("defaultWorkspace"))?.takeUnretainedValue() {

                if let apps = defaultWorkspace.perform(NSSelectorFromString("allInstalledApplications"))?.takeUnretainedValue() as? [NSObject] {
                    for app in apps {
                        let bundleId = (app.value(forKey: "applicationIdentifier") as? String) ?? ""
                        guard !bundleId.isEmpty else { continue }

                        let appName = (app.value(forKey: "localizedName") as? String) ?? bundleId
                        let version = (app.value(forKey: "shortVersionString") as? String) ?? "1.0"
                        let appType = (app.value(forKey: "applicationType") as? String) ?? "User"
                        let isSystem = (appType == "System")

                        if !includeSystem && isSystem {
                            continue
                        }

                        let containerURL = app.value(forKey: "bundleURL") as? URL

                        results.append(
                            InstalledAppInfo(
                                bundleId: bundleId,
                                appName: appName,
                                version: version,
                                isSystemApp: isSystem,
                                hasIAPSupport: true,
                                containerPath: containerURL?.path
                            )
                        )
                    }
                }
            }

            // Fallback for non-jailbreak test environment
            if results.isEmpty {
                results = [
                    InstalledAppInfo(bundleId: "com.spotify.client", appName: "Spotify", version: "8.9.20", isSystemApp: false, hasIAPSupport: true, containerPath: nil),
                    InstalledAppInfo(bundleId: "com.duolingo.DuolingoMobile", appName: "Duolingo", version: "7.15.0", isSystemApp: false, hasIAPSupport: true, containerPath: nil),
                    InstalledAppInfo(bundleId: "com.apple.mobilesafari", appName: "Safari", version: "18.0", isSystemApp: true, hasIAPSupport: false, containerPath: nil),
                    InstalledAppInfo(bundleId: "com.tinder.Tinder", appName: "Tinder", version: "15.4.1", isSystemApp: false, hasIAPSupport: true, containerPath: nil),
                    InstalledAppInfo(bundleId: "com.netflix.Netflix", appName: "Netflix", version: "16.8.0", isSystemApp: false, hasIAPSupport: true, containerPath: nil)
                ].filter { includeSystem || !$0.isSystemApp }
            }

            results.sort { $0.appName.localizedCaseInsensitiveCompare($1.appName) == .orderedAscending }

            DispatchQueue.main.async {
                self.installedApps = results
                self.isScanning = false
            }
        }
    }

    public func launchApp(bundleId: String) {
        if let workspaceClass = NSClassFromString("LSApplicationWorkspace") as? NSObject.Type,
           let defaultWorkspace = workspaceClass.perform(NSSelectorFromString("defaultWorkspace"))?.takeUnretainedValue() {
            let selector = NSSelectorFromString("openApplicationWithBundleID:")
            _ = defaultWorkspace.perform(selector, with: bundleId)
        } else if let url = URL(string: "\(bundleId)://") {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }
}

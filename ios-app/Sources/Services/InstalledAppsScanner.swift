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

            // Direct filesystem scan for TrollStore / Jailbreak rootless if runtime returned empty
            if results.isEmpty {
                let fileManager = FileManager.default
                let bundleContainer = "/var/containers/Bundle/Application"
                if fileManager.fileExists(atPath: bundleContainer),
                   let folders = try? fileManager.contentsOfDirectory(atPath: bundleContainer) {
                    for folder in folders {
                        let folderPath = (bundleContainer as NSString).appendingPathComponent(folder)
                        if let subItems = try? fileManager.contentsOfDirectory(atPath: folderPath) {
                            for subItem in subItems where subItem.hasSuffix(".app") {
                                let appPath = (folderPath as NSString).appendingPathComponent(subItem)
                                let plistPath = (appPath as NSString).appendingPathComponent("Info.plist")
                                if let dict = NSDictionary(contentsOfFile: plistPath) {
                                    let bundleId = (dict["CFBundleIdentifier"] as? String) ?? ""
                                    guard !bundleId.isEmpty else { continue }
                                    let appName = (dict["CFBundleDisplayName"] as? String)
                                        ?? (dict["CFBundleName"] as? String)
                                        ?? subItem.replacingOccurrences(of: ".app", with: "")
                                    let version = (dict["CFBundleShortVersionString"] as? String) ?? "1.0"

                                    results.append(
                                        InstalledAppInfo(
                                            bundleId: bundleId,
                                            appName: appName,
                                            version: version,
                                            isSystemApp: false,
                                            hasIAPSupport: true,
                                            containerPath: appPath
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if includeSystem {
                    let systemPaths = ["/Applications", "/var/jb/Applications"]
                    for sysDir in systemPaths {
                        guard fileManager.fileExists(atPath: sysDir),
                              let subItems = try? fileManager.contentsOfDirectory(atPath: sysDir) else { continue }
                        for subItem in subItems where subItem.hasSuffix(".app") {
                            let appPath = (sysDir as NSString).appendingPathComponent(subItem)
                            let plistPath = (appPath as NSString).appendingPathComponent("Info.plist")
                            if let dict = NSDictionary(contentsOfFile: plistPath) {
                                let bundleId = (dict["CFBundleIdentifier"] as? String) ?? ""
                                guard !bundleId.isEmpty else { continue }
                                let appName = (dict["CFBundleDisplayName"] as? String)
                                    ?? (dict["CFBundleName"] as? String)
                                    ?? subItem.replacingOccurrences(of: ".app", with: "")
                                let version = (dict["CFBundleShortVersionString"] as? String) ?? "1.0"

                                results.append(
                                    InstalledAppInfo(
                                        bundleId: bundleId,
                                        appName: appName,
                                        version: version,
                                        isSystemApp: true,
                                        hasIAPSupport: false,
                                        containerPath: appPath
                                    )
                                )
                            }
                        }
                    }
                }
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

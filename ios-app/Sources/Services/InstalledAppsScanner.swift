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
            var seenBundleIds = Set<String>()

            // 1. Explicitly load CoreServices / MobileCoreServices into memory for LSApplicationWorkspace
            let frameworks = [
                "/System/Library/Frameworks/CoreServices.framework/CoreServices",
                "/System/Library/PrivateFrameworks/MobileCoreServices.framework/MobileCoreServices"
            ]
            for fw in frameworks {
                _ = dlopen(fw, RTLD_NOW)
            }

            // 2. Access LSApplicationWorkspace via Objective-C Runtime
            if let workspaceClass = NSClassFromString("LSApplicationWorkspace") as AnyObject as? NSObjectProtocol {
                let defaultSel = NSSelectorFromString("defaultWorkspace")
                if workspaceClass.responds(to: defaultSel),
                   let defaultWorkspace = (workspaceClass as AnyObject).perform(defaultSel)?.takeUnretainedValue() {

                    var appProxies: [AnyObject] = []
                    let selInstalled = NSSelectorFromString("allInstalledApplications")
                    let selAll = NSSelectorFromString("allApplications")

                    if defaultWorkspace.responds(to: selInstalled),
                       let unmanaged = defaultWorkspace.perform(selInstalled) {
                        appProxies = unmanaged.takeUnretainedValue() as? [AnyObject] ?? []
                    } else if defaultWorkspace.responds(to: selAll),
                              let unmanaged = defaultWorkspace.perform(selAll) {
                        appProxies = unmanaged.takeUnretainedValue() as? [AnyObject] ?? []
                    }

                    for proxy in appProxies {
                        let bundleId = (proxy.value(forKey: "bundleIdentifier") as? String)
                            ?? (proxy.value(forKey: "applicationIdentifier") as? String)
                            ?? ""
                        guard !bundleId.isEmpty, !seenBundleIds.contains(bundleId) else { continue }

                        let appName = (proxy.value(forKey: "localizedName") as? String)
                            ?? (proxy.value(forKey: "itemName") as? String)
                            ?? bundleId
                        let version = (proxy.value(forKey: "shortVersionString") as? String) ?? "1.0"
                        let appType = (proxy.value(forKey: "applicationType") as? String) ?? "User"
                        let isSystem = (appType.lowercased() == "system")

                        if !includeSystem && isSystem {
                            continue
                        }

                        let containerURL = (proxy.value(forKey: "bundleURL") as? URL)
                            ?? (proxy.value(forKey: "containerURL") as? URL)

                        seenBundleIds.insert(bundleId)
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

            // 3. Direct filesystem scan for TrollStore / Rootless Jailbreak if runtime missed any
            let fileManager = FileManager.default
            let scanDirs: [(path: String, isSys: Bool)] = [
                ("/var/containers/Bundle/Application", false),
                ("/var/jb/Applications", false),
                ("/var/jb/var/mobile/Applications", false),
                ("/Applications", true)
            ]

            for (dirPath, isSysDir) in scanDirs {
                if isSysDir && !includeSystem { continue }
                guard fileManager.fileExists(atPath: dirPath),
                      let entries = try? fileManager.contentsOfDirectory(atPath: dirPath) else { continue }

                for entry in entries {
                    let entryPath = (dirPath as NSString).appendingPathComponent(entry)
                    if entry.hasSuffix(".app") {
                        if let info = self.extractAppInfo(fromAppFolder: entryPath, isSystem: isSysDir) {
                            if !seenBundleIds.contains(info.bundleId) {
                                seenBundleIds.insert(info.bundleId)
                                results.append(info)
                            }
                        }
                    } else {
                        // Directory of UUIDs (e.g. /var/containers/Bundle/Application/<UUID>/<Name>.app)
                        if let subEntries = try? fileManager.contentsOfDirectory(atPath: entryPath) {
                            for sub in subEntries where sub.hasSuffix(".app") {
                                let subAppPath = (entryPath as NSString).appendingPathComponent(sub)
                                if let info = self.extractAppInfo(fromAppFolder: subAppPath, isSystem: false) {
                                    if !seenBundleIds.contains(info.bundleId) {
                                        seenBundleIds.insert(info.bundleId)
                                        results.append(info)
                                    }
                                }
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

    private func extractAppInfo(fromAppFolder appPath: String, isSystem: Bool) -> InstalledAppInfo? {
        let plistPath = (appPath as NSString).appendingPathComponent("Info.plist")
        guard let dict = NSDictionary(contentsOfFile: plistPath) else { return nil }
        let bundleId = (dict["CFBundleIdentifier"] as? String) ?? ""
        guard !bundleId.isEmpty, !bundleId.hasPrefix("com.apple.") else { return nil }

        let appName = (dict["CFBundleDisplayName"] as? String)
            ?? (dict["CFBundleName"] as? String)
            ?? (appPath as NSString).lastPathComponent.replacingOccurrences(of: ".app", with: "")
        let version = (dict["CFBundleShortVersionString"] as? String) ?? "1.0"

        return InstalledAppInfo(
            bundleId: bundleId,
            appName: appName,
            version: version,
            isSystemApp: isSystem,
            hasIAPSupport: true,
            containerPath: appPath
        )
    }

    public func addCustomApp(bundleId: String, appName: String) {
        let trimmedId = bundleId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedId.isEmpty else { return }
        let name = appName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? trimmedId : appName
        let newApp = InstalledAppInfo(
            bundleId: trimmedId,
            appName: name,
            version: "Custom",
            isSystemApp: false,
            hasIAPSupport: true,
            containerPath: nil
        )
        if !installedApps.contains(where: { $0.bundleId == trimmedId }) {
            installedApps.insert(newApp, at: 0)
        }
    }

    public func launchApp(bundleId: String) {
        _ = dlopen("/System/Library/Frameworks/CoreServices.framework/CoreServices", RTLD_NOW)
        _ = dlopen("/System/Library/PrivateFrameworks/MobileCoreServices.framework/MobileCoreServices", RTLD_NOW)
        if let workspaceClass = NSClassFromString("LSApplicationWorkspace") as AnyObject as? NSObjectProtocol {
            let defaultSel = NSSelectorFromString("defaultWorkspace")
            if workspaceClass.responds(to: defaultSel),
               let defaultWorkspace = (workspaceClass as AnyObject).perform(defaultSel)?.takeUnretainedValue() {
                let openSel = NSSelectorFromString("openApplicationWithBundleID:")
                if defaultWorkspace.responds(to: openSel) {
                    _ = defaultWorkspace.perform(openSel, with: bundleId)
                    return
                }
            }
        }
        if let url = URL(string: "\(bundleId)://") {
            UIApplication.shared.open(url, options: [:], completionHandler: nil)
        }
    }
}

import SwiftUI

public struct MainTabView: View {
    // Default to "Đã cài đặt" (tag 1) matching video 00:00
    @State private var selectedTab: Int = 1

    public init() {
        let navAppearance = UINavigationBarAppearance()
        navAppearance.configureWithOpaqueBackground()
        navAppearance.backgroundColor = UIColor(Color.iappaySurface)
        navAppearance.titleTextAttributes = [.foregroundColor: UIColor(Color.iappayTextPrimary)]
        UINavigationBar.appearance().standardAppearance = navAppearance
        UINavigationBar.appearance().scrollEdgeAppearance = navAppearance

        let tabAppearance = UITabBarAppearance()
        tabAppearance.configureWithOpaqueBackground()
        tabAppearance.backgroundColor = UIColor(Color.iappaySurface)
        UITabBar.appearance().standardAppearance = tabAppearance
        UITabBar.appearance().scrollEdgeAppearance = tabAppearance
    }

    public var body: some View {
        TabView(selection: $selectedTab) {
            ExploreStoreView()
                .tabItem {
                    Label("Khám phá", systemImage: "sparkles")
                }
                .tag(0)

            InstalledView()
                .tabItem {
                    Label("Đã cài đặt", systemImage: "square.grid.2x2.fill")
                }
                .tag(1)

            LibraryView()
                .tabItem {
                    Label("Thư viện", systemImage: "folder.fill")
                }
                .tag(2)

            SettingsView()
                .tabItem {
                    Label("Cài đặt", systemImage: "gearshape.fill")
                }
                .tag(3)
        }
        .accentColor(.iappayPurple)
    }
}

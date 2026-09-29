import Foundation

public struct OnlineAppSearchResult: Identifiable, Hashable {
    public var id: String { bundleId }
    public let trackId: Int
    public let appName: String
    public let bundleId: String
    public let iconUrl: String?
    public let artistName: String
    public let priceFormatted: String
    public let genres: [String]
}

public final class AppStoreSearchService: ObservableObject {
    public static let shared = AppStoreSearchService()

    @Published public var searchResults: [OnlineAppSearchResult] = []
    @Published public var isSearching: Bool = false

    private init() {}

    public func searchAppStore(query: String, country: String = "vn") {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            searchResults = []
            return
        }

        isSearching = true

        // Check if query is an App Store link with ID (e.g. id1500855883)
        var term = trimmed
        if let idRange = trimmed.range(of: "id[0-9]+", options: .regularExpression) {
            let idStr = String(trimmed[idRange]).replacingOccurrences(of: "id", with: "")
            term = idStr
        }

        let encoded = term.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? term
        let urlString = "https://itunes.apple.com/search?term=\(encoded)&entity=software&country=\(country)&limit=25"

        guard let url = URL(string: urlString) else {
            isSearching = false
            return
        }

        URLSession.shared.dataTask(with: url) { data, _, error in
            defer {
                DispatchQueue.main.async {
                    self.isSearching = false
                }
            }

            guard let data = data, error == nil else { return }
            guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let results = json["results"] as? [[String: Any]] else { return }

            var list: [OnlineAppSearchResult] = []
            for item in results {
                let trackId = item["trackId"] as? Int ?? 0
                let bundleId = item["bundleId"] as? String ?? ""
                guard !bundleId.isEmpty else { continue }
                let name = item["trackName"] as? String ?? bundleId
                let icon = item["artworkUrl100"] as? String ?? item["artworkUrl60"] as? String
                let artist = item["artistName"] as? String ?? ""
                let price = item["formattedPrice"] as? String ?? "Miễn phí"
                let genres = item["genres"] as? [String] ?? []

                list.append(OnlineAppSearchResult(
                    trackId: trackId,
                    appName: name,
                    bundleId: bundleId,
                    iconUrl: icon,
                    artistName: artist,
                    priceFormatted: price,
                    genres: genres
                ))
            }

            DispatchQueue.main.async {
                self.searchResults = list
            }
        }.resume()
    }
}

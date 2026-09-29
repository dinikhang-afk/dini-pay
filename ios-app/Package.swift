// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "DiniPay",
    platforms: [
        .iOS(.v16)
    ],
    products: [
        .library(
            name: "DiniPay",
            targets: ["DiniPay"]
        ),
    ],
    targets: [
        .target(
            name: "DiniPay",
            path: "Sources"
        )
    ]
)

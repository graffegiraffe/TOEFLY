// swift-tools-version: 6.2
// TOEFLY iOS core: generated API clients, authentication, networking and the view models of the app
// (docs/12-implementation-plan.md phase 12). The macOS platform lets `swift test` run the unit tests without a
// simulator.
import PackageDescription

let package = Package(
    name: "ToeflyKit",
    platforms: [.iOS(.v26), .macOS(.v26)],
    dependencies: [
        .package(url: "https://github.com/apple/swift-openapi-generator", exact: "1.14.4"),
        .package(url: "https://github.com/apple/swift-openapi-runtime", exact: "1.13.4"),
        .package(url: "https://github.com/apple/swift-openapi-urlsession", exact: "1.4.0"),
        .package(url: "https://github.com/apple/swift-http-types", exact: "1.8.0"),
    ],
    targets: [
        .target(
            name: "ProfileAPI",
            dependencies: [.product(name: "OpenAPIRuntime", package: "swift-openapi-runtime")],
            plugins: [.plugin(name: "OpenAPIGenerator", package: "swift-openapi-generator")],
        ),
        .target(
            name: "PlanningAPI",
            dependencies: [.product(name: "OpenAPIRuntime", package: "swift-openapi-runtime")],
            plugins: [.plugin(name: "OpenAPIGenerator", package: "swift-openapi-generator")],
        ),
    ],
    swiftLanguageModes: [.v6],
)

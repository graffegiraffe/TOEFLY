# TOEFLY iOS

Native SwiftUI client for iOS 26 (docs/12-implementation-plan.md, phase 12). Swift 6, `@Observable` MVVM, Liquid
Glass only on the chrome, the simulator only (no paid Apple developer account).

## Layout

| Path | What |
|---|---|
| `Toefly/` | App target: composition root, screens, design system (`DesignSystem/Tokens.swift`, `Components/`), assets |
| `ToeflyUITests/` | XCUITest per story (`test_AC_…`), accessibility audits and light/dark/AX3 screenshots |
| `Packages/ToeflyKit/` | Swift package: generated API clients, authentication, networking, view models and their tests |
| `Packages/ToeflyKit/Sources/ProfileAPI`, `PlanningAPI` | `swift-openapi-generator` clients; `openapi.yaml` links to `services/<svc>/src/main/openapi/` |
| `project.yml` | XcodeGen spec; `Toefly.xcodeproj` is generated from it and committed |
| `scripts/generate-color-assets.py` | Writes the color sets (light and dark) from the design tokens of docs/07 §2.1–2.3 |

## Run

```bash
open clients/ios/Toefly.xcodeproj
```

Run the `Toefly` scheme on an iPhone 17 Pro simulator. By default the app signs in at the local Keycloak realm
`toefly` (`http://localhost:8180`) and calls api-gateway (`http://localhost:8080`) from the Compose stack. Without the
stack, add launch arguments in the scheme:

| Argument | Effect |
|---|---|
| `-fixtures new-student` · `mid-onboarding` · `onboarded` · `with-plan` | In-memory backend and identity provider |
| `-signedIn` | Start with a stored session |
| `-authOutcome cancel` | The sign-in sheet is cancelled |
| `-expireSession` | The refresh token is rejected right after launch |
| `-appleSignInEnabled` | Treat the Keycloak `apple` provider as configured |

## Test

```bash
cd clients/ios/Packages/ToeflyKit && swift test --enable-code-coverage
```

```bash
cd clients/ios && xcodebuild test -project Toefly.xcodeproj -scheme Toefly -destination 'platform=iOS Simulator,name=iPhone 17 Pro' -skipPackagePluginValidation
```

After changing `project.yml`, run `xcodegen generate`; after changing a color token, run
`python3 scripts/generate-color-assets.py`. `swiftformat .` and `swiftlint --strict` run in the pre-commit hook and CI.

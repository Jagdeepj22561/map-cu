# iOS Build Notes

This repository now includes a minimal Xcode project at `iosApp/iosApp.xcodeproj`.

The app target now integrates the Kotlin Multiplatform `shared` module through CocoaPods.

Before opening the iOS app in Xcode, run:

- `./gradlew :shared:generateDummyFramework :shared:podInstall`

For CI and local simulator builds, use:

- `cd iosApp`
- `xcodebuild -workspace iosApp.xcworkspace -scheme iosApp -sdk iphonesimulator -configuration Debug CODE_SIGNING_ALLOWED=NO build`

You can still export the reusable Apple artifact with:

- `./gradlew :shared:assembleXCFramework`

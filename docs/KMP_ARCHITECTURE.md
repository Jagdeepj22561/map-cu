# Campus Map KMP architecture

## Required dependency direction

```text
Android app / iOS app (composition roots)
                 |
                 v
       common repository interfaces
                 |
                 v
 common use cases, models, Supabase data sources
                 |
                 v
 small injected platform services (cache, push token, maps, files)
```

`commonMain` owns models, validation, use cases, backend DTOs, repository
interfaces, deterministic identifiers, and Supabase operations that behave the
same on every target. A feature should be implemented here once.

Android and iOS source sets own only platform integrations: Room/CoreData (or
another local cache), notification registration, lifecycle hooks, maps, image
selection, and secure credential persistence. These implementations are passed
to shared code through interfaces from each platform's composition root.

## Rules enforced by the build

- Repository classes are not `expect`/`actual`. Use a common interface and
  inject a real implementation.
- `expect`/`actual` is limited to small platform primitives such as disk cache
  and configuration.
- `commonMain` cannot import Android, JVM, Apple, CocoaPods, or Firebase APIs.
- Shared business rules have host-side tests (`testAndroidHostTest`).
- Android, iOS, and Render use the same `directChatId` implementation contract.
- The Supabase service-role key remains server-only. Mobile clients use the
  publishable key plus the authenticated user's session and rely on RLS.

## Current migration boundary

Announcements, events, teams, notification dispatch, models, and shared UI are
already in `commonMain`. Android legitimately owns Room and the native socket,
but its current repository still mixes platform storage with social metadata
queries; those shared rules should continue moving behind common interfaces.

The remaining legacy boundary is the iOS Firebase profile/chat/group store in
`iosMain`. It must be migrated feature-by-feature behind common repository
interfaces before Firebase can be removed. Do not add new behavior to that
store. New data and business rules belong in `commonMain` and use Supabase; only
the local persistence and Apple framework calls belong in `iosMain`.

## Verification

```powershell
./gradlew :shared:verifyKmpArchitecture
./gradlew :shared:testAndroidHostTest
./gradlew :app:compileDebugKotlin --no-daemon
```

The iOS framework must additionally be compiled on macOS because Google Maps
CocoaPods cinterop cannot be cross-compiled on Windows.

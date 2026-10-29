# Working on the VisionForgeX SDK for Android

The SDK apps add to be monetized with VisionForgeX (apps.visionforgex.com). This repository is
**public**: it holds only what runs inside an app. Every decision (what to request, refresh, pacing,
floors, consent tags, experiments) is made by the platform, in the private repository
`VisionForgeX/visionforgex-apps`, and arrives as settings. Never put a decision, a threshold chosen for
revenue, a key, an account or a customer's name here.

## Read before writing code

1. `docs/PROTOCOL.md` — everything the SDK and the platform say to each other. A change here is a change
   there too (`site/public_html/admin/sdk-lib.php` in visionforgex-apps), in the same piece of work.
2. `CHANGELOG.md` — what each version did.
3. The platform's `docs/BLUEPRINT.md` (section on the SDK and its gotchas).

## Standing rules

- **Push after every change**, with a CHANGELOG line; tag releases (`1.0.1`), which JitPack builds.
- **Never break an app in production.** Keep the public API (`VisionForgeX`, `VfxFullScreen`, the views,
  the listeners, `VfxOptions`) compatible within a major version; Java callers too (`@JvmStatic`, overloads).
- **Google's limits are kept in the SDK whatever the settings say**: banners never refresh faster than
  30 s; ads are thrown away before Google's expiry (1 h, app open 4 h); never two full-screen ads at once.
- **Test what you change**: `./gradlew :vfx-core:testDebugUnitTest :vfx-ads:testDebugUnitTest`, lint, and
  the sample app (`:sample:assembleDebug :sample:assembleRelease`, the release one minified). The
  platform's tests/slice6.php runs `PlatformContractTest` against its test server: run it after any
  change to the protocol. Never report a pass you did not watch.
- `vfx-core` has no ad SDK in it; the Google engine is `vfx-ads`. A new engine (Google's Next-Gen SDK)
  is a new module implementing `com.visionforgex.ads.engine.AdEngine`.
- Publisher-facing wording says VisionForgeX and support@visionforgex.com.

## Building in Claude's workspace

Maven Central rate-limits the workspace's shared address and the Gradle plugin portal's artifact host is
blocked: builds there go through a local retrying mirror (the platform's BLUEPRINT, gotcha 16), with
`ANDROID_HOME=/opt/android-sdk` and `VFX_MAVEN_MIRROR` for Robolectric. The repository itself names the
normal repositories.

# Changelog

## 1.3.0 (15 Nov 2026)

- **Ad revenue to AppsFlyer and Adjust** (off unless the platform's settings carry an `mmp` block): each impression's
  revenue from Google's paid event goes to AppsFlyer (6.15+, `logAdRevenue`, mediation network Google AdMob, with the
  serving network, ad unit, format and placement) and/or Adjust (`trackAdRevenue`, source `admob_sdk`, with the ad
  unit and placement). Found in the app by name: the SDK depends on neither, an app without them is unchanged, and
  one switched on but missing is said once in Logcat. Never in test mode, never a zero value. R8 rules keep the
  few methods it calls when the app has them.

## 1.2.0 (15 Nov 2026)

Proven on a real phone (15 Nov 2026, the Awlyaa demo app on Next-Gen): the platform's settings, an interstitial and an
app open ad filled from Ad Manager; in Google's demo mode every format filled and showed; the banner ladder asked its three
sizes in order. README: a WebView app on Next-Gen registers its WebView once the SDK is ready.

- **Google's GMA Next-Gen SDK**: a second engine, `vfx-ads-nextgen`, on Google's Next-Gen SDK 1.6.0 (Google's
  preferred Android SDK since July 2026; Google Mobile Ads 25.x is deprecated on 30 Jun 2027 and retired on
  30 Jun 2028). An app adds `vfx-ads-nextgen` instead of `vfx-ads`; its code does not change. Every format
  (banner with the same size ladder, interstitial, rewarded, rewarded interstitial, app open, native in the same
  two layouts), the same key-values, collapsible banners, request agent, age treatment, test devices, content rating,
  consent form, paid events and ad inspector. Google's error numbers are reported as 25.x numbered them. Google
  calls back on its own thread: the banner handles each answer on the main thread and ignores an answer to a load
  that was replaced.
- Events carry `eng`, the Google SDK under the app (`ngsdk-1.6.0` or `gma-25`), so the platform can show which
  apps still run a Google SDK that is being retired.
- The demo app builds on either engine (flavours `gma` and `nextgen`), each with its own banner lab.
- Includes everything below under 1.1.0 and 1.0.3.

## 1.1.0 (not released yet)

- Prebid for apps (off unless the platform's settings carry a `prebid` block): banners and interstitials first ask
  VisionForgeX's own Prebid Server for bids, waiting at most the settings' time, and give the winner's `hb_*` keys to
  Google's request; no bid or a slow server means Google alone, as before. Pauses itself after repeated failures,
  does not ask when GDPR applies without a consent string, sends no advertising ID or location. New event `pb`.
- Includes everything below under 1.0.3.

## 1.0.3 (not released yet)

- A banner's `fill` and `nofill` events say which sizes it asked for and how each answered (`sizes`), and a
  `nofill` carries Google's own message (`msg`), so a banner that never fills says why in the app's logs.
- The demo app has a banner lab: the same banner asked for eight ways straight through Google's SDK
  (Google's own sample, each size, the fixed-size unit, with VisionForgeX's key-value or request agent, all
  three sizes together, AdMob's test banner), each with its answer, to find what a banner needs to fill.

## 1.0.2 (30 Oct 2026)

- Banners now ask for sizes from the largest down: Google's large anchored adaptive banner, the standard
  anchored adaptive one and 320x50 in one request; when that finds no ad (or the unit takes none of those
  sizes), the standard adaptive size alone, then 320x50 alone, all within the same load. The size that
  filled is kept for the banner's refreshes. In 1.0.1 a unit that does not take the large size, such as
  Google's demo adaptive banner unit, never filled.

## 1.0.1 (30 Oct 2026)

- `VfxOptions.Builder().googleDemoAds()`: every format on Google's own demo ad units, which always fill
  with test ads, so an app (or the demo app) can be seen working with no account; nothing is sent.
  `settingsForTesting(json)` for tests with settings of your own. Neither is for a release build.
- `VisionForgeX.setRevenueListener { … }`: each impression's value (Google's paid event) for the app's own
  analytics; `setEventListener { … }`: every ad event; `settingsSource()`.
- The sample app is now the VisionForgeX demo: every format with its state, both native layouts, a
  collapsible banner, a live event log, and the choice between Google's demo ads and an app key.

## 1.0.0 (30 Oct 2026)

The first release, on Google Mobile Ads 25.5 and Google's User Messaging Platform 4.0.

- Settings from the platform (cached, refreshed with ETag), every decision there.
- Banner (large anchored adaptive, collapsible, refresh only while seen), interstitial, rewarded,
  rewarded interstitial, app open (on return and at a cold start), native (small and medium layouts).
- Preloading with expiry, retries with backoff, floor tiers with timeouts, pacing and caps, a gap
  between full-screen ads, never two at once.
- Consent through Google's UMP; child-directed and under-age settings (Google's age-restricted treatment).
- Events: sessions, requests, fills, shows, clicks, paid (per-impression revenue), kept on disk until sent.
- Experiments reported by variant; a debug panel; Google's ad inspector.

# Changelog

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

# The VisionForgeX SDK protocol (version 1)

What an app running the VisionForgeX SDK says to `apps.visionforgex.com`, and what it is told back.
The SDK carries out what the platform decides; it holds no decision of its own. Every value here
comes from the platform, per app, and can change without an app update.

All bodies are JSON in UTF-8. Every time is milliseconds since 1970 (UTC) unless the name says
otherwise. Money is in **micros** (1,000,000 = one unit of the currency).

## 1. The settings: `GET /sdk/v1/config.php`

| Query | What |
|---|---|
| `k` | The app's key (shown on the app's page on the platform). Public: it is in the app. |
| `i` | The install ID: a random UUID the SDK makes once per install. Not an advertising ID. |
| `sv` | SDK version, e.g. `1.0.0` |
| `p` | `android` or `ios` |
| `b` | The app's package name (Android) or bundle ID (iOS), as installed |
| `av` | The app's version name |

Headers: `If-None-Match` with the last `ETag`. Answer `304` when nothing changed.

`200`:

```json
{
  "v": 1,
  "ttl": 3600,
  "app": { "enabled": true, "test": false, "reason": "" },
  "global": { "fullGapSec": 30, "sessionTimeoutSec": 1800, "log": "warn" },
  "consent": { "ump": true, "child": false, "underAge": false, "rating": "" },
  "events": { "on": true, "url": "https://apps.visionforgex.com/sdk/v1/events.php", "sample": 1.0, "batch": 20, "flushSec": 30 },
  "kv": { "vfx_app": "com.example.game" },
  "ab": "e1:b",
  "placements": {
    "level_complete": {
      "format": "interstitial", "on": true,
      "unit": "/21000000001,22000000002/vfxa-and-com-example-game-interstitial",
      "preload": true, "expiryMin": 55, "refreshSec": 0,
      "cap": { "minIntervalSec": 60, "maxPerSession": 0, "maxPerHour": 0, "maxPerDay": 0, "graceSec": 30, "graceSessions": 0, "everyNth": 1 },
      "retry": { "maxAttempts": 6, "baseSec": 2, "maxSec": 64 },
      "floors": { "key": "vfx_fl", "tiers": ["300", "150"], "timeoutMs": 2500 },
      "collapsible": "", "template": "", "autoShow": false
    }
  }
}
```

- `app.enabled` false: show no ads at all (the app is paused, not approved, or the SDK is off for it).
  `reason` says why, for the log.
- `app.test`: use Google's demo ad units instead of `unit` (the platform already put them in `unit`).
- `global.fullGapSec`: the least time between any two full-screen ads that are not rewarded.
- `global.sessionTimeoutSec`: a session ends after this long in the background.
- `consent.ump`: ask Google's User Messaging Platform for consent where the law needs it, before any
  ad request. `child` / `underAge`: Google's child-directed and under-age-of-consent tags. `rating`:
  the highest ad content rating (`G`, `PG`, `T`, `MA`, or empty for none).
- `events`: where to send events (section 2). `sample` (0–1) applies to every event but `ses` and
  `paid`, which are always sent (users and revenue are never estimated). The install is in or out of
  the sample once per install (FNV-1a of the install ID), not per event; the platform scales the
  sampled counts back up.
- `kv`: key-values added to every ad request.
- `ab`: the experiment and variant this install is in (`experiment:variant`), or empty. The platform
  has already applied the variant's settings to `placements`; the SDK only reports the tag.
- A placement:
  - `format`: `banner`, `interstitial`, `rewarded`, `rewarded_interstitial`, `app_open`, `native`.
  - `unit`: the Google Ad Manager ad unit path to request.
  - `preload`: keep one ad loaded and waiting; load the next as soon as one is shown.
  - `expiryMin`: a loaded ad older than this is thrown away and loaded again.
  - `refreshSec`: banners only; 0 = never; otherwise at least 30. Paused while not on screen.
  - `cap`: full-screen pacing. `minIntervalSec` between two shows of this placement; `maxPerSession`,
    `maxPerHour`, `maxPerDay` (0 = no limit); `graceSec`: none in the first seconds of a session;
    `graceSessions`: none in the first sessions after install; `everyNth`: show on every Nth call of
    `show()` (1 = every call).
  - `retry`: after a failed load, wait `baseSec × 2^(attempt-1)` seconds (at most `maxSec`), up to
    `maxAttempts`; after that, the next `show()` or a return to the app starts again.
  - `floors`: price tiers, highest first. Each tier is one request with the key-value
    `key=tier`; the first to fill wins; a tier that has not answered within `timeoutMs` counts as
    no fill. After the last tier, one request without the key. Empty `tiers`: one request.
  - `collapsible`: banners only, `top`, `bottom` or empty.
  - `template`: native only, `small` or `medium`.
  - `autoShow`: app open only; show it when the app comes back to the front (and at a cold start
    when an ad is ready).

The SDK keeps the last settings it got and uses them while offline. Before it has ever had any, it
shows nothing.

## 2. Events: `POST /sdk/v1/events.php`

Body (may be sent with `Content-Encoding: gzip`), at most 512 KB, at most 500 events:

```json
{
  "k": "<app key>", "i": "<install ID>", "s": "<session ID>", "sv": "1.0.0", "p": "android",
  "av": "2.3.1", "os": "14", "dev": "Pixel 8", "c": "PK", "ab": "e1:b", "sr": 1.0,
  "e": [
    { "t": "ses", "ts": 1730000000000, "n": 3, "first": false },
    { "t": "req", "pl": "level_complete", "f": "interstitial", "ts": 1730000000123, "tier": 1 },
    { "t": "fill", "pl": "level_complete", "f": "interstitial", "ts": 1730000000923, "ms": 800, "tier": 1, "src": "Google AdMob" },
    { "t": "show", "pl": "level_complete", "f": "interstitial", "ts": 1730000060000 },
    { "t": "paid", "pl": "level_complete", "f": "interstitial", "ts": 1730000060100, "v": 4210, "cur": "USD", "prec": "PRECISE" }
  ]
}
```

| `t` | When |
|---|---|
| `ses` | A session starts. `n`: the session's number on this install; `first`: the first ever. |
| `req` | An ad request (one per floor tier). |
| `fill` / `nofill` | Its answer; `ms` the time it took; `err` Google's error code on `nofill`, `msg` Google's message (up to 100 characters); for banners `sizes`, the sizes asked for and how each answered (`411x64+411x60+320x50=3 → 320x50=filled 320x50`). `msg` and `sizes` are for the app's own logs: the platform ignores them. |
| `show` / `click` / `close` | The ad was shown, clicked, closed. |
| `fail` | A loaded ad could not be shown (`err`). |
| `reward` | Rewarded formats: the user earned the reward. |
| `paid` | Google's paid event: what this one impression earned (`v` micros, `cur`, `prec`). |
| `capped` | `show()` was called but pacing said no (`why`: interval, session, hour, day, grace, nth, gap). |

`c` is the country of the device's locale (the platform uses the request's own country when its CDN
gives one). `ab` counts only when it names one of the app's own experiments. Events more than 3 days
old or more than 10 minutes in the future are dropped; one impression's `paid` above 100 units is dropped.
Answer: `200 {"ok": true, "n": <events kept>}`. `4xx` means do not send this batch again; `5xx` or
no answer means keep it and try later.

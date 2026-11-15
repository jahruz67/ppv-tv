# PPV.st API Verification Report

**Verified Date:** 2026-11-14 (UTC 2026-11-15T05:36Z)  
**Target Website:** `https://ppv.st/`  
**Historical Reference:** `https://old.ppv.to/api` (returned OpenSSL TLS unrecognized name from execution container)

## 1. API Endpoint Probe

**Request:**
```http
GET https://api.ppv.st/api/streams
```

**Result:** `HTTP/2 200 OK` (`content-type: application/json`, `cache-control: max-age=10`, `access-control-allow-origin: *`)

**Verified Top-Level Response Structure:**
- `success`: `boolean` (`true`)
- `timestamp`: `integer` (Unix epoch in **seconds**, e.g. `1794720970`)
- `READ_ME`: `string` (`"Interested in using our API? Contact us for more information."`)
- `performance`: `number` (`0.0336`)
- `streams`: `array` of category objects (10 categories, 70 total stream entries observed at verification time)

**Verified Category Object Fields:**
- `id`: `int`
- `category`: `string` (e.g., `"American Football"`, `"Australian Football"`, `"Basketball"`, `"Combat Sports"`, `"Cricket"`, `"Golf"`, `"Ice Hockey"`, `"Rugby"`, `"Wrestling"`, `"24/7 Streams"`)
- `always_live`: `boolean`
- `streams`: `array` of stream objects

**Verified Stream Object Fields:**
- `id`: `int`
- `name`: `string`
- `tag`: `string` (e.g., `"NFL"`, `"NBA"`, `"MMA"`, `"24/7 channel"`)
- `source_tag`: `string` (e.g., `"HD | ESPN"`, `"HD | Paramount+"`)
- `poster`: `string` (HTTPS thumbnail URL)
- `blurhash`: `string`
- `colors`: `array<string>`
- `uri_name`: `string`
- `starts_at`: `int` (Unix seconds; `0`, `-60`, or `-3600` observed on some 24/7 channels)
- `ends_at`: `int` (Unix seconds; `0` or `10740` observed on some 24/7 channels)
- `always_live`: `int` (`0` or `1` on stream objects, differing from `boolean` on category objects)
- `locale`: `string` (`"en"`, `"es"`, `"pt"`)
- `category_name`: `string`
- `iframe`: `string` (HTML embed URL)
- `viewers`: `string` (numeric viewer count serialized as string, e.g., `"402"`)
- `substreams`: `array` of alternate source objects (`id`, `name`, `tag`, `uri_name`, `source_tag`, `locale`, `iframe`)

## 2. Playback & Embed Investigation

- All 70 stream entries and their `substreams` returned HTML embed URLs hosted on `https://taifood-blog.asia/embed/...` rather than direct `.m3u8` / `.mpd` playlist URLs.
- Probing `https://taifood-blog.asia/embed/nfl-network` (with `Referer: https://ppv.st/`) returns `HTTP/2 200 OK` HTML loading `/js/bundle-jw.js` (or `/js/bundle.js` for `#player=clappr`).
- **Root-Cause Investigation for Android WebView `"Remove sandbox attributes on the iframe tag"`:**
  - `/js/bundle-jw.js` defines `window.sandDetect`, which unconditionally replaces `document.body.innerHTML` with `"Remove sandbox attributes on the iframe tag"`, and injects `<object id="sandDetect" data="data:application/pdf;base64,aG1t" ... onerror="window.sandDetect()">` via `document.body.insertAdjacentHTML('beforeend', ...)`.
  - Desktop Chrome renders `data:application/pdf` via its built-in PDF plugin unless blocked by an `<iframe sandbox>`; Android `WebView` has no built-in PDF viewer plugin, causing `<object data="data:application/pdf">` to always fail and fire `onerror="window.sandDetect()"`.
- **App Handling:**
  - `PlaybackStrategyResolver` routes `.m3u8`/`.mpd`/`.mp4` direct streams to `NativeMediaPlayer` (Media3 ExoPlayer) and HTTP/HTTPS embed URLs to `EmbeddedWebPlayer` (WebView + `WebChromeClient` fullscreen video).
  - `EmbeddedWebPlayer` injects a document-start compatibility guard (`WebViewCompat.addDocumentStartJavaScript` when `WebViewFeature.DOCUMENT_START_SCRIPT` is supported, plus `onPageStarted`/`onLoadResource`/`onPageFinished` fallback) that defines `window.sandDetect` as a non-overwritable no-op, neutralizes the `<object data="data:application/pdf">` probe, strips `sandbox` attributes from any nested `<iframe>`, and blocks popup/ad window hijacking via `WebChromeClient.onCreateWindow`.
  - `EmbeddedWebPlayer` intercepts main-frame HTTP 4xx/5xx responses (`onReceivedHttpError`) and network errors (`onReceivedError`) and displays the TV-remote recovery overlay (**Retry**, **Change Source**, **Back to Event**).
  - No DRM circumvention, stream scraping, or unauthorized proxy extraction is performed.

## 3. Verification Matrix

| Component | Status | Notes |
|---|---|---|
| Live API HTTP 200 & JSON schema verification | Verified | Tested directly against `https://api.ppv.st/api/streams` |
| Embed HTML/JS `sandDetect` root-cause & guard verification | Verified | Verified against live `https://taifood-blog.asia/embed/nfl-network` and `/js/bundle-jw.js` in Node VM |
| DTO parsing, status logic, sorting, search | Verified | Unit tests in `PpvDomainUnitTest` |
| DataStore persistence, D-pad/UI flows, error recovery | Verified | Robolectric Compose tests in `ExampleRobolectricTest` |
| Debug APK compilation & packaging | Verified | Built via `gradle :app:assembleDebug` |
| Live video frame rendering on Android TV WebView | Unverified in container | No Android TV emulator/ADB in execution environment |
| Physical Android TV remote hardware testing | Unverified in container | Remote key handling implemented & tested via Compose key events in Robolectric |

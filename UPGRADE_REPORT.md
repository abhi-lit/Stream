# Stream Window 1.4 — upgrade and verification report

Date: 29 October 2026

## Result and scope

This is an incremental update of the saved Stream Window 1.3 Android app. It retains the application ID, signing certificate, WebView architecture, MultiMovies/MultiNime destinations, cookies, website search/navigation, ad filtering, popup blocking, browser/download handoff and text-size settings. No database, authentication service, backend endpoint or deployment configuration exists in this source project. The APK is not a desktop website, Samsung Tizen app or LG webOS app.

**The original HTTP 503 root cause remains unverified. This release improves recovery and diagnosis; it does not claim to repair the external service. The reported pause-button overlap also remains unverified and is not labeled fixed.**

## 503 investigation

Inspected the recovered v1.3 source before editing. Its visible HTTP error banner was triggered only by a main-frame HTTP error callback. Therefore the earlier banner indicated a top-level page failure, not merely an advertisement or iframe failure. That version did not retain the failed host, response headers or a diagnostic history, so the earlier exact URL and server-side cause cannot be reconstructed.

Checked these public endpoints:

- https://multimovies.wtf/ — web retrieval returned the landing page.
- https://m1.multimovies.courses/ — web retrieval returned the movie catalog.
- https://multinime.site/ — web retrieval and the live cloud browser loaded the anime catalog.
- A live anime watch page loaded and embedded a player from flixcloud.cc. No 503 was reproduced during that inspection.

Direct Python GET requests from the build environment returned HTTP 403 for all three home/catalog URLs. Those results are not evidence of a 503, server overload, or the user's device behavior. The source of those 403 responses was not established. Web retrieval is also not proof that every API, playback host or user network is healthy.

No origin logs, deployment dashboard, resource metrics, API credentials, database configuration or hosting access were available. There is no evidence establishing overload, a failed deployment, database exhaustion or a proxy timeout; these must not be represented as verified causes.

Recovery if the failure returns: open Menu → Diagnostics and record the host, page/resource classification and status. Record the title/page and time separately. The website/provider operator needs to correlate these with origin and reverse-proxy logs, check backend health/capacity/deployment status, and repair the failing upstream service. If a provider rejects Android WebView/TV clients, the operator must enable supported embedded playback or supply an authorized player/API. Changing UI or hiding the error cannot repair that condition.

## Implemented changes

### Reliability

- Clear error panel with explicit Retry and Browser buttons; TV focus moves to Retry when a new error appears.
- At most two automatic retries for main-frame GET responses with HTTP 429, 502, 503 or 504.
- Exponential delays of 2 then 4 seconds, increased to honor Retry-After seconds or HTTP-date values.
- Delays over two minutes require a later manual attempt; the app does not shorten the server's wait. Manual retry also checks the recorded wait.
- POST requests and arbitrary subresource requests are never automatically replayed. Network failures require explicit retry.
- Navigation, Back, backgrounding and destruction cancel pending retries. Backgrounded retries do not silently restart.
- Bounded, session-only diagnostics record up to 20 host/status/type entries. Query strings, paths, headers, cookies, credentials and response bodies are not collected. A throttled resource-error notice directs users to Diagnostics without reloading the whole page.
- HTTP errors remain visible; no synthetic success is displayed.

### UI and TV navigation

- Multi Movie and Multi Anime retain their original destinations and gain locally drawn film/play and sparkle icons, rounded surfaces, selected/hover/focus styling and lightweight native ripple feedback.
- Compact phone navigation, wider side rail on tablets, and larger 72dp navigation controls with 18sp labels in TV mode.
- TV layout can be selected from Menu, including on devices that do not identify themselves as televisions. Android TV/Google TV launcher support remains present.
- TV safe-area spacing and high-contrast page focus outlines.
- A small page enhancement supplies directional spatial-focus fallback for accessible links, cards and controls, scrolling the selected item into view. It respects editable fields and events already handled by the website. It scopes candidates to a visible modal when available.
- Native Menu remains available through the remote Menu key. Back/Escape exits fullscreen or theater view first; the app asks before exiting at the root.
- No catalog data or poster grids were replaced. Offscreen images receive browser-native lazy-loading hints only when supported and not already configured by the site. This is a limited optimization, not catalog virtualization.

### Playback and fullscreen

- Fullscreen uses the existing Android WebChromeClient custom-view lifecycle, explicit system-bar handling, layout/inset refresh and focus restoration.
- Rotation and fullscreen transitions notify accessible page content to recompute its layout.
- Direct HTML5 video fullscreen uses object-fit: contain; provider frame internals remain untouched.
- Menu → Theater view hides app navigation and system bars as an app-level fallback. It does not force an inaccessible embedded player's video to fullscreen.
- Remote play/pause and 10-second seek commands are added for accessible top-document HTML5 video, with seekable-range bounds. Hardware volume stays with Android. Fullscreen provider views retain their own media-key handling.
- No duplicate play/pause overlay or second timeline is added. Existing subtitle, audio and provider controls are preserved.

## Playback investigation and remaining gaps

The inspected MultiNime watch page used a cross-origin flixcloud.cc iframe. At the inspected desktop viewport, its central play control and bottom controls were separated. Click attempts did not establish advancing playback or successful fullscreen activation. This was an observation of the unmodified provider website, not a runtime test of the new APK.

The app cannot use ordinary top-page JavaScript to restyle or drive controls inside that cross-origin iframe. It also cannot override DRM, unsupported codecs, provider restrictions or browser security. No such bypass was attempted. A precise overlap fix requires the affected player/page, device/viewport and provider-supported integration or source ownership. No blanket player CSS was added because enlarging unknown controls could itself cause overlap.

Remote-only operation of every provider frame, subtitles/audio menus, control auto-hide, unsupported-format feedback and playback on all TVs are not verified or guaranteed. Some embedded controls in the inspected page are generic containers rather than semantic buttons, which limits ordinary keyboard accessibility. Fixing those controls requires the player provider's support.

## Changed files

Paths are relative to the source ZIP's android-app directory.

| File | Change |
| --- | --- |
| app/src/main/java/com/example/streamwindow/MainActivity.java | Retry/error lifecycle, diagnostics, navigation styling, TV mode, fullscreen/focus restoration, media-key fallback, service-worker callback cleanup |
| app/src/main/java/com/example/streamwindow/RetryPolicy.java | New bounded retry and Retry-After policy |
| app/src/main/java/com/example/streamwindow/NavIcon.java | New local vector navigation icons |
| app/src/main/assets/accessibility.js | New scoped TV focus, direct-video media handling, fullscreen resize and lazy-load hints |
| app/build.gradle | Version code 5 / version name 1.4; minimum API remains 23 and target remains 35 |
| README.md | Installation and capability notes updated |
| verification/ | Retry, accessibility logic and existing filter/link regression tests |
| UPGRADE_REPORT.md | This report |

The manifest, Internet-only permission set, hostname blocklist and hostname matching implementation are unchanged. No private signing key is included in the source package.

## Verification

Passed:

- Java compilation, Android resource packaging and D8 bytecode generation.
- APK signature verification, v1/v2/v3, and certificate comparison with the saved 1.3 APK.
- APK alignment and manifest inspection: unchanged app ID, version 1.4, minimum Android 6/API 23, target API 35, optional touchscreen, Android TV launcher, Internet permission only.
- 20 retry-policy checks: eligible statuses/methods, delays, dates, invalid/overflow headers, retry count and maximum automatic wait.
- 19 accessibility logic checks using a mocked DOM: four-way movement, boundaries, disabled elements, editable fields, handled-event preservation, mobile noninterference, idempotent installation, video commands/seek bounds, missing-video behavior and resize notification.
- 12 hostname-filter regression checks against the bundled 3,568-domain list.
- 7 site-routing regression checks and syntax checks for both retained injected scripts.

Not performed / not established:

- No Android emulator or physical phone/TV was available. Installation, native layout screenshots, remote behavior inside WebView, full lifecycle recovery, fullscreen restoration and real-device playback are unverified.
- Local Chromium installation failed because its download was not a usable archive. Mocked DOM tests are not browser rendering tests.
- No actual 503-to-success recovery was observed against an external server. Pure retry policy was tested; native WebView callback/timer integration still needs device testing.
- No 720p/1080p/4K/ultrawide device matrix, long-session profiling, memory benchmarks or external API/database connectivity tests were possible.
- Android Studio/Gradle build was not run here; APK was built with the Android SDK tools and ECJ, as in earlier versions.

## Installation and acceptance check

Install Stream_Window_1.4.apk over the existing supplied app; its signing certificate matches 1.3. Android 6 or newer and a functioning Android System WebView are required. Old or TV WebViews may still lack the provider's required codecs/APIs. This is a development-signed APK.

On a real device: test both site buttons and search, focus a poster using arrows, activate it with OK, test the provider's pause/seek/fullscreen controls, rotate where applicable, press Back to restore the page, and check Menu → Diagnostics if an error occurs. Test Android TV with only the remote before treating remote-only support as complete. For controlled recovery testing, use a staging HTTPS page that returns 503 twice then 200 and separately test Retry-After and navigation/background cancellation; do not overload the live service.

Technical references: Android WebChromeClient custom-view fullscreen documentation; MDN HTTP 503 and Retry-After documentation.

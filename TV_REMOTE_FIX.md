# Stream Window 1.5 — TV fullscreen remote fix

The supplied recording shows the player focus moving across controls while playback time changes from about 2:03 to 2:23 and seek feedback appears. The recording does not show raw remote key codes. Source inspection found that fullscreen directional key events were forwarded directly to the provider player; this can allow a focus movement and a provider seek shortcut to react to the same press.

Changed MainActivity.dispatchKeyEvent to intercept TV fullscreen DPAD Up/Down/Left/Right before child views receive them. Down/Right now send Tab; Up/Left send Shift+Tab. Both original keydown and keyup are consumed. Repeats are throttled to one focus move per 180ms. These events never synthesize a click, OK press or seek command.

OK/Enter activates the selected provider control. Dedicated media rewind/fast-forward, Back, volume and non-fullscreen navigation retain their prior behavior. This uses sequential focus order, not geometric four-way navigation, within fullscreen. It cannot make an inaccessible provider control keyboard-focusable.

Install over version 1.4. On the TV, use Menu > TV layout if TV mode is not enabled. Enter video fullscreen, press Down/Right to move to the next control, Up/Left to move back, and OK to activate. Selecting rewind/forward and pressing OK still seeks intentionally.

Validation: APK compiled, signature verified, same signing certificate as 1.4, and 32 regression checks passed against the actual extracted dispatch method with a stub Android event sink. Tests cover all four arrows, both event phases, Shift direction, repeated events, unchanged OK/Back/media commands, mobile behavior and non-fullscreen behavior. Physical TV/player testing was unavailable. The fix is implemented; confirmation on the affected TV is still needed. Original external 503 limitations remain unchanged.

Changed: MainActivity.java, new TvRemotePolicy.java, app/build.gradle (versionCode 6, versionName 1.5), README.md and verification/remote tests. No provider scripts, destinations, ad filters, cookies or permissions were removed. The user's recording is not included in the source archive.

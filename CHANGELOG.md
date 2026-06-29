# Changelog

These notes are written in plain English and focus on what changed for real use.

# MpvRxN v1.5.0

> **MpvRxN** is a custom fork of MpvRx.

### ✨ New Features

- 🎵 **Audio file support with thumbnails** — new toggle in *Settings → Audio → "Audio Player"*. Shows MP3, FLAC, AAC, OGG, M4A, OPUS, WAV, WMA and more alongside videos in every folder, with embedded album art. Off by default.
- 🔊 **Background audio on back press** — pressing back (gesture, button, or the in-player arrow) while playing audio now keeps it playing in the background instead of stopping.
- 📂 **Background play doesn't exist app ** — Now when we press background play button the player doesn't go back to launcher instead it goes back where the file is being play.
- 🔔 **Folder navigation via notification** — opening the app from a folder-specific notification jumps straight to that folder.
- 👆 **Swipe between tabs** — works even when the nav bar is hidden, with wrap-around.
- ⏫ **Long-press folder name or title** to scroll back to the top instantly.
- ▶️ **FAB quick play** — tap plays your most recently played video; long-press opens the original menu.
- ⏱️ **Long-press the speed button** to reset playback speed to your default.
- 🔍 **Search auto-opens the keyboard** when tapped.
- 🔓 **Easy Unlock** — lock icons on both sides for one-tap unlocking.
- 🎞️ **Smooth Back Animation toggle** — choose a smooth scale exit or an instant cut.

### 🎨 Improvements

- Transparent volume/brightness/speed/seek overlays with white text — no more dark boxes.
- Cleaner speed pill (no arrow icon), no full slider panel during swipe.
- Chnaged swipe-speed always jumping to 2x — now starts from your actual hold-speed preset.
- Thinner, more consistent seekbar with a smaller thumb.
- **Much faster folder loading** — disk-cached after first open, instant on every load after (even post-reboot).

### 🎬 Picture-in-Picture

- Play/Pause, Rewind (−10s), and Forward (+10s) buttons inside PiP now work correctly.

### 🐛 Bug Fixes

The **rotation/black-screen flash on exit** has haunted this fork for a while — this release fixes the large majority of cases, with several genuinely separate causes resolved:
- System back (gesture/button) was sometimes silently disabled, which could stop background audio and skip folder-return logic.
- The exit orientation lock could get overridden mid-transition by an unrelated background process.
- The "instant exit" setting wasn't actually taking effect due to a timing bug.
- Android 14's own predictive-back preview is now suppressed specifically for the player.
- The manual background-play button had its own separate flash — fixed by locking orientation earlier in the process.

Also fixed:
- Audio-only folders not showing up when Audio Player is enabled.
- Video pausing/restarting briefly when switching system day/night theme.
- A UI blink when returning to the home screen after playback.
- Single-tap on the FAB sometimes doing nothing.

### 💡 Known Limitations

- With the **system-wide Android "Predictive Back Animations"** developer setting on, a very brief black-screen flash can still occasionally occur on exit. Turning that system setting off avoids it entirely.
- A few 10-bit/AVI files may still show inconsistent thumbnails on some devices.

✨ Default Settings Changes
Smooth Back Animation: now on by default
Easy Unlock: now on by default
Hide Player Buttons Background: now on by default
Seekbar Style: now defaults to Standard (was Thick)
Use Online Skip Markers: now off by default
Bottom navigation tabs (Home, Recents, Playlists, Network): now off by default
---


## 1.4.1-final

### Player, Playback & Stability
- Fixed stuttering and massive frame drops by optimizing video and screen synchronization.
- Fixed overlapping video frames/glitches when skipping rapidly through videos.
- Fixed picture-in-picture mode progress sync so your playback progress is correctly saved on exit.
- Prevented screen locking when opening the video's chapter list if the video has no chapters.
- Fixed visible screen rotation animation glitches when using the swipe-back gesture to exit the player.
- Adjusted player bottom controls bar padding to a more comfortable size.
- Cleaned up redundant HDR colorspace and tone mapping settings to avoid visual confusion.
- Fixed sudden crashes when loading or playing certain media files.
- Added outlined text with a black border to the Stats Page 6 overlay so text stays readable over any video content.

### Library, File Browser & Sizing
- Fixed playback, titles showing up as raw IDs (like `msf:1000`), missing thumbnails, and progress tracking for local M3U/M3U8 playlists.
- Added support for folder thumbnails in folder grid view (disabled by default, can be turned on in settings).
- Centralized file sorting options and improved title alignment in grid layouts.
- Fixed selection bar background in library list and grid layouts.
- Added Copy, Move, and Compress/Downscale options to the library selection bar.
- Fixed top toolbar delete button showing during folder selection to prevent accidental folder deletions.
- Fixed thumbnail taps so they toggle item selection instead of selecting ranges of items.
- Fixed library Media Info button to open details page instead of playing the file.

### Gestures & Dialogs
- Fixed subtitle swipe and pinch gesture detection zones so they are more responsive and natural to use.
- Added custom skip keywords for video openings and endings to automatically skip intros/outros.
- Redesigned the Sort and View Options Dialog to be more compact, with a collapsible "Fields" toggle section.
- Fixed bottom control bar icon scaling for smaller/larger screens.
- Fixed a visual glitch where the seekbar layout preview looked wavy.

### Network & Connections
- Added support for sending your playback progress to Jellyfin when playing videos externally.

- Many More Small QOL Fixes and Optimizations 

## 1.4.0

### Player And Seek Preview

- added the ThumbFast-style seek preview UI and thumbnail cache improvements.
- restored the legacy live-video seek preview path and added a Player setting to switch between ThumbFast thumbnail preview and legacy live seeking.
- tightened ThumbFast preview accuracy by using smaller preview time buckets and preventing stale thumbnail requests from replacing the newest preview frame.
- fixed glitched player vector icons and tightened subtitle/notch safe-area behavior.
-  improved launch smoothness and predictive back behavior.

### Subtitles

-  added subtitle font management in Settings > Subtitles: choose a fonts directory, see the selected source folder, reload fonts, clear the font cache, and select the default subtitle font.
-  added custom subtitle border styles and a shadow offset slider.
- **Arnab Sadhukhan** added subtitle zoom gestures.
- **Arnab Sadhukhan** optimized the subtitle pinch hit-zone for multi-line subtitles.
- **Arnab Sadhukhan** added horizontal swipe on subtitles to seek dialog lines.

### Browser, Library, And Storage

- redesigned the Media Info page to use a premium, tabbed Material 3 interface with beautiful overview stats, container metadata detail, track summaries, and customizable sharing.
- implemented the unified Media Library view mode.
- added multi-select range handling, folder copy/move/rename, and SMB mutex/reconnection guards.
- redesigned settings sections and moved progress-related options into cleaner places.
- fixed segmented button unchecked color handling.
- fixed Lua script copy behavior.

### Gestures And Quality Of Life

- **Arnab Sadhukhan** added the playlist swipe-up gesture in the player when swiped up from middle of screen now playlists open.
- **Arnab Sadhukhan** added auto-scroll to the currently selected theme.
- Removed Avif / Jpeg-Xl type images from the settings selection
- Added Font selection in the Subtitle Settings section.
- Fixed issue of Lua script when copying specific part Copied whole Lua script.
- added Expressive Scrollbar like in Pixel player 

## 1.3.9

> # 🚀 **CURL IS NOW SUPPORTED!** 
> ### ⚠️ **EXPERIMENTAL** — This is brand new and may or may not work properly on your device. ⚠️
> Lua and JavaScript scripts can now make HTTP requests through the new native libcurl bridge via JNI.
> Use it, break it, and report issues so we can make it stable!, see `MPVRX_CUSTOM_COMMANDS.d` for tutorial on how to use in Lua and JS.
>
> **What this means:** You can now write scripts that fetch data from the internet — APIs, subtitles, metadata, you name it — all through libcurl compiled directly into the app.

- AI support has been updated. Gemini is removed and OpenCode Zen AI is now available for AI rename, subtitle formatting, and subtitle translation.
- AI model lists now come from the provider APIs instead of a saved model list in the app. OpenRouter also marks free models using the pricing data returned by OpenRouter itself.
- Background playback is fixed so repeat keeps working after using the headphone button, and returning to the player no longer restarts the current stream from the beginning.
- Subtitle search has been updated for the latest Wyzie source changes.
- Added Hybrid Skip Markers. The player can now check IntroDB, TIDB, and AniSkip together and use whichever result is found first.
- Anime4K settings are now easier to use with a collapsible section in Decoder Preferences and also Added Optimization by Sunny Vishnu .
- Added a setting to show or hide Media Info from Android's share/open-with screen.
- Added documentation for custom Lua/JS player commands in `MPVRX_CUSTOM_COMMANDS.md`.

## 1.3.8

- **Integrated yt-dlp by [**SunnyVishnu3**](https://github.com/SunnyVishnu3)** — Added full yt-dlp integration for video watching audio/video from YouTube and other supported sites directly within the app (_Dont expect from me to add Download Functionality_). **Note: You need to download yt-dlp first (Settings > Advanced > yt-dlp Manager) before playing YouTube links — don't be clueless.**
- Fixed Issue of USer defined Colors Filters were not getting Saved and not getting applied through Mpv conf by [**SunnyVishnu3**](https://github.com/SunnyVishnu3)
- Fixed Gemini AI Error Generating / Translating Subs.
- Fixed Crashing issue of MpvRx , in a nutshell Ambient mode and Custom lua were not initialized in Sync causing to crash player sometimes
- That's all for Today Adiosss!!

## 1.3.7

- **Updated Wyzie subtitle API integration** — Synced with latest Wyzie API changes: added `ai` field for AI-translated subtitle detection, updated provider sources list (removed `subdl`, `podnapisi`, `ajatttools`; added `tvsubtitles`), and fixed TMDB endpoints to include API key authentication.
- **Material 3 Expressive Design** — Complete visual overhaul using Material 3 Expressive design system for a more modern, fluid, and engaging experience
- **Smoother Animations** — Replaced rigid linear transitions with spring-based physics animations throughout the app (navigation, controls, browser, dialogs)
- Added Voltage Battery Temperature And improved the style of Page 6 
- Added Optimized Ambient Mode with Eco Battery Saver Mode who want to take feel of Ambient without much Battery Impact
- Removed Dead Code and also Optimized some File Handling / Ui Rendering Operations
- **Settings export now stores app version**  exported XML files include the app version, so import dialogs show the correct version instead of "unknown".
- **HEVC 10bit thumbnails**  added a software-decoder fallback using Android's MediaCodec API. When the system can't decode a video frame (e.g. HEVC 10bit on devices without hardware support), the app now tries Google's software decoder before giving up. This means more thumbnails will show up on devices with limited codec support ( To be tested Propelry on unsupported device).
- **Fixed app icon on Android 16**  changed the adaptive icon background from transparent to opaque black so the icon doesn't disappear on launchers that don't handle transparency well.
- **User mpv.conf now has highest priority**  during player startup, your mpv.conf settings are re-applied after all app defaults so they always take precedence. but some of the Hardcoded things doesnt change like for example `sid, aid`.
- 

## 1.3.6

- **Six AI providers, one gorgeous settings page**  OpenAI, Anthropic, OpenRouter, and Together joined Groq and Gemini in a completely redesigned UI. Every provider gets its own API key, every single model is visible (free ones get a bold green badge), and the new searchable model picker sorts free models to the top. The offline model experience got a premium card-based overhaul too  tiers, speed/translation badges, device recommendations, DeepSeek-R1 support, reasoning toggles, and a benchmark button for downloaded models. One-tap download, delete, and switch between models without ever leaving the screen.

- **Subtitle translation**  SUPPORTS ASS Subs Translation tooooooooo..... , you can now configure your target languages once in settings. One language means one tap to translate. Two or more means a clean picker showing only the languages you chose. Translation progress appears right on the video screen (even with the sheet closed), partially translated subs survive restarts, and a red X lets you cancel mid-translation instantly. When using local models, the system automatically picks the best downloaded model for each language, keeps it warm between chunks, and never runs two local AI jobs at once.

- **Generate subtitles from video audio**  **_(EXPERIMENTAL)_** This is work in progress might not work Don't baby Cry that this shit aint working ,i ain't getting paid enough to implement this whole heartedly , so what it does is -> one tap generates subtitles using the audio you're already playing. Media3 extraction feeds Groq, Gemini, or offline Whisper, and the resulting SRT/VTT saves automatically.

- **Smarter AI across the board**  reasoning tags are automatically stripped from final results, token limits prevent stalls in heavy tasks, and every AI feature (rename, formatting, translation) comes with customizable prompts that fall back gracefully to built-in instructions.

- **Real-time subtitle toggle**  new on/off switch in AI settings to control real-time subtitle generation from audio. When off, the indicator and generate button are hidden from the player.

- **AI features respect the master switch**  turning off AI Integration now hides all AI indicators (translation, real-time subs) and buttons (generate, translate, format) from the player view. Renamed "AI Subtitle Search Formatting" to "AI Search" for clarity.


## 1.3.5

- **Removed Play Store and F-Droid build variants**  streamlined to a single `standard` flavor with full update support and all features enabled.
- **Revamped README**  comprehensive feature documentation organized by category, UPI QR code and Buy Me a Coffee links in the Support section.
- **SMB Network Thumbnail Generation**  fixed thumbnail generation for SMB shares through Codex AI (Beta).
- **Bulk AI Rename**  rename multiple files at once using Gemini or Groq with concurrency limiting and edge case handling.
- **AI Subtitle Translation**  translate subtitles using AI providers with custom prompts, progress indication, and user preference management.
- **AI Subtitle Translation Enhancements**  in-house developed translation pipeline with fully customizable prompts and per-user preference overrides.

## 1.3.4

- Capped generated thumbnails to safer preview sizes so large videos do not waste memory while browsing.
- Improved MKV/WebM thumbnail handling, including embedded artwork and smarter fallback frames.
- Cleaned old thumbnail cache paths when clearing thumbnail cache.
- Fixed the About and crash info screen showing `UNKNOWN` in the bundled mpv version.
- Updated Gradle, Kotlin, Compose, Koin, Navigation 3, AndroidX, and related dependency versions through the version catalog.
- Added SUbHub MpvRx specific Subtitle Fetching nd Downloading featured developed by me
- Added Video COmpresser Overlay in Tree Mode also
- Cleaned up codebase and Improved Playback bottlenecks
- Added Window Offset to prevent Camera notch overlap issues


## 1.3.3

- Fixed Background Playback and Pip issues 
- Anime4K should feel much smoother now. The player now uses the clean six-preset Anime4K flow from the reference app and avoids piling old shader work on top of the new preset when you switch modes.
- Anime4K is still off by default, but when you turn it on the picker is simpler: Off, A, B, C, A+, B+, and C+.
- Moved the Fast / Balanced / High Anime4K choice into Decoder settings, with Balanced as the default.
- Removed frame interpolation because it added a lot of GPU load and did not add enough real value.
- Removed the old OneThird and Halfway thumbnail choices.
- Removed the unused old player screen path.
- Cleaned up the track sheets so audio, subtitle, chapter, decoder, and online subtitle lists no longer depend on the old generic sheet.
- Removed SubDL from subtitle search sources.
- Network streaming is now opt-in instead of being enabled on a fresh install.
- HDR and Ambient controls are no longer placed on the default player buttons, so heavy visual extras stay out of the way unless you add them yourself.
- Turning HDR on now starts with Linear HDR by default.
- The app now does less background media scanning and cache cleanup on startup, which should help large libraries open with less churn.
- Added new MpvLib File with Some Optimization and Removing Deprecated Andorid Versions
- Thumbnails are now Loaded Faster and more Precisly

## 1.3.2

### HDR hdr-toys Pipeline

- Replaced the old 3-mode HDR system (Off / SDR with HDR / Normal HDR) with a proper shader-based pipeline powered by [hdr-toys](https://github.com/natural-harmonia-gropius/hdr-toys).
- Four HDR modes are now available: **BT.2100 PQ** (HDR10), **BT.2100 HLG**, **BT.2020**, and **Linear HDR** (mpv-native, no shaders).
- 77 GLSL shaders are bundled in the app and copied to the mpv config directory on first use  no manual setup required.
- The HDR panel no longer shows an "Off" option. Off is the default and is toggled by the HDR button; the panel only presents the four active modes.
- Selecting a mode while GPU Next + Vulkan is unavailable shows a clear error pill and falls back to Off safely.
- Added `boostSdrToHdr` preference (used by the Linear HDR path).
- `HdrToysManager` cleanly removes all hdr-toys shaders when switching to Off or when the pipeline is not ready, so no stale shaders leak between sessions.

### Thermal & Battery Improvements

- Added `ThermalMonitor`  samples `PowerManager.getThermalHeadroom()` (Android 11+) every 10 seconds during playback.
- Ambient shader sample budget is automatically capped based on thermal headroom: 8 samples (severe), 12 (moderate), 18 (mild), uncapped (cool).
- Anime4K is proactively downgraded to C/Fast when thermal headroom drops below 40%, before frame drops even start.
- Ambient shader recompilation is now skipped when all parameters are identical to the last compiled version  reduces unnecessary GPU stutter on orientation changes and no-op callbacks.
- Removed redundant dual position polling: the event-driven `time-pos` observer and the polling loop were both updating the same StateFlow, causing double seek-bar recompositions on every MPV event.
- Background playback position poll interval halved from 250 ms to 500 ms when controls are not visible, cutting idle JNI wake-ups by 50%.

### Stats Page 6  Fixes

- **GPU estimate bar fixed**: was using cumulative drop + delay totals that drifted to 100% after long sessions and added a fixed FPS-proportional baseline (120fps with zero drops showed 70% GPU load). Now uses per-second delta counts relative to the current frame rate  0 drops = 0%, all frames dropped = 100%.
- **CPU label corrected**: relabelled from "CPU Usage" to "App CPU (this process)" to accurately reflect that `getElapsedCpuTime()` measures only MpvRx's own process, not the whole device.
- **Frame drop text now shows per-second deltas** alongside the all-time totals, so you can tell current rendering pressure at a glance.
- **Pause-aware poll backoff**: the stats loop backs off from 1 s to 2 s intervals when playback is paused, cutting pointless JNI calls when metrics are static.

### Gesture & Action Overlay Toggles

- Added a new **"Gesture & Action Overlays"** section in Player Settings with seven independent on/off switches:
  - **Volume slider overlay**  vertical pill shown during volume swipe
  - **Brightness slider overlay**  vertical pill shown during brightness swipe
  - **Hold speed overlay**  speed badge and slider shown during long-press speed boost
  - **Aspect ratio feedback**  pill shown when cycling aspect ratio
  - **Zoom level feedback**  pill shown when pinching to zoom
  - **Repeat & shuffle feedback**  pill shown when toggling repeat or shuffle
  - **Action feedback pills**  brief text pills from custom buttons, ambient toggle, subtitle drag, and Lua/JS scripts
- All overlays default to **on**, so existing behaviour is unchanged until the user opts out.
- Disabling an overlay suppresses only the visual pill  the underlying gesture action (volume change, speed change, etc.) still happens normally.

## 1.3.1

- Update FFmpeg to n8.1 (latest stable)
- Update Android SDK to 36, build tools 36.0.0
- Update Kotlin to 2.1.21, Gradle to 8.11.1
- Update dependencies: unibreak 6.2, harfbuzz 11.5.0, fribidi 1.0.17, freetype 2.13.4, mbedtls 3.6.5
- Add mujs 1.3.5 support for JavaScript scripting inside mpv
- JavaScript (.js) scripts are now supported alongside Lua scripts, with "Scripts (Lua / JS)" kept to the main section titles.
- Script editor now uses the native Sora editor with TextMate syntax highlighting for Lua and JavaScript.
- Script editor includes a chip toggle to choose between `.lua` and `.js` file extensions when creating or editing scripts.
- Custom player buttons can now run either Lua or JavaScript, with language selection per button and import/export support.
- Long-pressing the HDR button now opens an HDR Output panel with Off, SDR with HDR, and Normal HDR modes.
- Media title resolution improved: MPV's resolved title is preferred for non-direct-media URLs and when the current filename looks like a generic route (e.g., `/watch`, `/stream`).
- Updated mpv library dependency from `mpv-android-lib-v0.0.1.aar` to `mpvlib.aar` and removed the old AAR.
- Added Multiple new provider to Wyzie subtitle sources.
- PiP and background playback now save the latest watched position instead of returning to the timestamp from before PiP started.
- Video lists refresh playback progress as soon as the saved position changes, so returning from the player shows the current progress.
- Folder thumbnails now begin rendering immediately when a folder opens, while still using cached thumbnail data first.

## 1.3.0

- The project now carries the `MpvRx` name across the app, docs, and release files.
- Tree View `NEW` labels now work properly and update as you watch.
- Single-child folders now flatten automatically so you reach files faster.
- Subtitle matching is smarter and better at finding subtitles that line up.
- Cached library data shows up first, then refreshes quietly in the background.
- Browser updates now react to changes instead of constantly polling.
- The player now remembers your chosen aspect ratio.
- Seeking feels steadier and cleanup after playback is smoother.
- Ambient mode and Lua scripting were reverted.
- The settings page was revamped.
- New tab and video animations were added.
- Icons were refreshed across the app.
- Network and playlist behavior was cleaned up.
- Folder pinning was added.
- A video size downgrade option was added in the video editing section.
- Page 6 was added to More Sheet for battery usage and extra system info.
- A new status icon row can show network speed, battery percentage, and time.

## 1.2.9

- Library scanning became faster and more dependable.
- Subtitle search got a noticeable improvement.
- Theme picking now jumps to the active theme more cleanly.
- Ambient mode got another round of polish and fixes.

## 1.2.8-hotfix

- A rough ambient mode change was rolled back to keep playback stable.
- The zoom sheet layout was cleaned up.
- Playback profiles became easier to manage.

## 1.2.8

- Background playback became more dependable.
- File rename and delete flows became safer and clearer.
- Custom buttons load more reliably.
- Play Store and F-Droid releases were cleaned up.
- The update and media tools were reorganized.

## 1.2.7

- The seekbar was cleaned up and accidental swipe behavior was reduced.
- F-Droid builds were added.
- Release packaging and signing became more reliable.

## 1.2.6

- Background playback and notifications became steadier.
- Filter presets and video quality controls were improved.
- External subtitle scaling and positioning were fixed.

## 1.2.5

- Video scaling and smooth motion options were added.
- Thumbnail generation became faster and more consistent.
- Browser spacing and player gestures were cleaned up.

## 1.2.4

- New videos now show a `NEW` label more reliably.
- Rotated videos and aspect handling were improved.
- Subtitle styling controls were expanded.
- Playlist order and storage permission handling were cleaned up.

## 1.2.3

- Network thumbnails became optional.
- Recently Played works better with network items.
- Thumbnail loading became faster.
- Browser navigation and floating actions became more consistent.

## 1.2.2

- Repeat and shuffle now stay the way you left them.
- Subtitle preferences now carry across playback more reliably.
- Hardware decoding falls back more safely on tricky devices.
- Player rotation and status bar behavior were improved.
- SMB playback became more dependable.

## 1.2.1

- Grid mode arrived for folders and videos.
- Scroll position is remembered when you come back.
- Thumbnail visibility can be toggled.
- A background playback edge case was fixed.

## 1.2.0

- The app got a major Material 3 refresh.
- Settings were reorganized into a cleaner card layout.
- Local M3U playlists were added.
- Recently Played got pull-to-refresh.
- Track and subtitle handling became smarter.

## 1.1.0

- Network browsing arrived for SMB, FTP, and WebDAV.
- File manager mode and breadcrumb navigation were added.
- Playlist mode became more useful.
- Recently Played learned how to handle playlists too.
- The project website and screenshots were refreshed.

## 1.0.0

- First public release.
- Media info viewing and sharing were added.
- F-Droid release work was prepared.

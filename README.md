# The Incredible Machine (tablet remake)

A from-scratch remake of Sierra/Dynamix's 1993 contraption puzzle game for Android tablets and the
browser (installable as a web app on iPad, iPhone, Android and desktops), with 44 puzzles and a free-play mode, designed so a six-year-old can play it on a tablet: big parts, fine-grained snapping placement, forgiving drops,
one-sentence goals, a giant Play button, a tutorial nudge on the first puzzles, and a celebration
when the machine works.

* **Play:** 44 puzzles plus free play with every part. Ropes, belts and wires are tied by tapping the tool, then the two things to join (pulleys in between for ropes); free play sorts the parts into category tabs.
* **Play now:** [gangeli.github.io/the-incredible-machine/play/](https://gangeli.github.io/the-incredible-machine/play/) runs the same game in the browser; "Add to Home Screen" (Safari on iOS) or "Install app" (Chrome) turns it into a full-screen, offline app with its own icon.
* **Install the APK:** see the [GitHub Pages site](https://gangeli.github.io/the-incredible-machine/) for the APK and step-by-step sideloading instructions. (The site lives in `docs/`; a copy of the signed APK is committed there too, so it also works with "Deploy from a branch: main, /docs". Pages has to be switched on once under *Settings -> Pages*; after that every push to `main` redeploys it.)
* **Size:** the signed release APK is about 100 KB. All art is vector, all sounds are synthesised.

## Layout

| Module | What it is |
| --- | --- |
| `core/` | Pure Kotlin (multiplatform: JVM and JS): physics engine, parts, levels, goals, screens and input. No platform dependencies, so everything is unit-testable and can be rendered with Java2D. |
| `app/` | Thin Android layer: a `View` that renders the game through the Android `Canvas`, touch forwarding, `SharedPreferences` progress, and an `AudioTrack` mixer for the synthesised sounds. |
| `web/` | Thin browser layer (Kotlin/JS): an HTML canvas painter, pointer events, `localStorage` progress, WebAudio for the synthesised sounds, and a web-app manifest plus service worker so the game installs on a home screen and works offline. |
| `docs/` | The GitHub Pages site (install instructions and screenshots). |
| `docs/research/` | Notes on the original game's mechanics gathered while building this (part tables, physics constants, puzzle list). |

### Engine notes

* Fixed 60 Hz steps with four sub-steps; every run of a machine is deterministic, so the stored solution of each level is verified by a test.
* Bodies never rotate dynamically (like the original sprites); the seesaw plank is kinematic and tips as a short animation, launching whatever sits on the rising end with a mass-dependent speed copied from the original engine.
* Restitution uses the smaller of the two bodies' values (walls "defer" to whatever hits them), buckets are soft, trampolines add speed, conveyors drag with their surface, fans push with a force that falls off with distance, balloons are buoyant against the air-pressure setting.
* Ropes are length constraints that can pass over pulleys; scissors and candle flames cut them. A heavy enough load on a rope tied to a seesaw's low end tips it, launching whatever sits there.
* Mort and Pokey are animated creatures: distance-driven walk cycles, acceleration and smooth turns, blinking, sitting, grooming, startle jumps with an arched back, and landing squash. Mort scurries in bursts and stops to sniff; Pokey sits until he sees a mouse.

## Building

Requires JDK 17+ and the Android SDK (platform 35, build-tools 35).

```sh
./gradlew :core:test                 # physics, parts, levels and screen tests; writes PNGs to core/build/snaps
./gradlew :app:testDebugUnitTest     # launches the real Activity under Robolectric and screenshots it
./gradlew :app:assembleRelease       # signed, shrunk APK in app/build/outputs/apk/release/
./gradlew :web:webDist               # browser build in web/build/webdist/ (one script + the PWA shell; serve that folder)
NODE_PATH=$(npm root -g) node web/e2e/smoke.cjs   # plays a puzzle in headless Chromium against a local server
```

The release keystore in `app/release.keystore` is intentionally committed: it only exists so that
new builds install over old ones on a sideloaded tablet.

## Tests

* `WorldTest` - the physics engine (gravity, bounces, rolling, conveyors, ropes, pulleys, determinism).
* `PartsBehaviourTest` - one test per part (seesaw launches, trampoline height, fan vs. bowling ball, candle pops balloon, cannon fires, bucket catches, mouse finds cheese, cage traps cat, switch powers fan, ...).
* `LevelsTest` - every level's stored solution solves it, the empty board never solves itself, solutions only use tray parts and sit on the grid.
* `ScreensTest` / `MainActivityTest` - drive the UI with synthetic touches (title -> level select -> drag a part -> play -> win) and write screenshots.
* `ArtSheetTest` / `GalleryTest` - render every part in every state at high zoom for visual review.

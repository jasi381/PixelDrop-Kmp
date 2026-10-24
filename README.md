```
█▀█ █ ▀▄▀ █▀▀ █░░   █▀▄ █▀█ █▀█ █▀█
█▀▀ █ █░█ ██▄ █▄▄   █▄▀ █▀▄ █▄█ █▀▀
        C A N V A S   C L A S S I C S
```

> A pocket-sized retro handheld, drawn entirely on a canvas. One console, six arcade classics, Android + iOS.

## ▶ GAMES

| # | Game | Vibe |
|---|------|------|
| 1 | **PIXEL DROP** | falling blocks, clear lines |
| 2 | **PIXEL TRAIL** | grow the trail, don't bite it |
| 3 | **BRICK PULSE** | bounce, smash, repeat |
| 4 | **ORBIT BLASTER** | slide, shoot, survive |
| 5 | **METEOR DODGE** | sidestep the sky |
| 6 | **PIXEL RACER** | weave through traffic |
| 7-10 | ??? | `COMING SOON` |

## ⚙ CONSOLE

- **SETTINGS** deck: sound, haptics, game theme, shell paint
- **GAME THEME**: the shell repaints itself for each game
- Animated menu previews, CRT/LCD look, synthwave backdrop
- Honors reduced-motion

## ⌨ BUILD

```bash
./gradlew :app:installDebug            # Android
open iosApp/iosApp.xcodeproj           # iOS (run from Xcode)
./gradlew :shared:testAndroidHostTest  # tests
```

**Stack:** Kotlin Multiplatform · Compose Multiplatform · Canvas

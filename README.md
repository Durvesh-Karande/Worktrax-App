# Worktrax

A minimalist workout logger — native Android app (Kotlin + Jetpack Compose).

## Quickstart

Requires **Android Studio Hedgehog (2023.1) or newer** and JDK 17 (bundled with Android Studio).

1. Open Android Studio.
2. `File → Open…` and select the `android/` folder in this repo.
3. Let Gradle sync finish (downloads the Android Gradle Plugin and Compose BOM).
4. Start an emulator or plug in a device with USB debugging enabled.
5. Press **Run** (▶) to install the debug APK.

To produce an uploadable bundle: **Build → Generate Signed Bundle / APK → Android App Bundle**.

## Architecture

```
android/app/src/main/java/com/worktrax/app/
  MainActivity.kt              Entry point, theme wrapper
  data/                        Enums + data classes (Exercise, Workout, Set, etc.)
  lib/                         Formatters, storage, id gen, PDF report builder
  store/                       ViewModels (Session, History, Settings) backed by SharedPreferences
  nav/                         Navigation Compose routes + NavHost graph
  ui/
    theme/                     Colors (paper/ink/accent), typography
    components/                Button, Chip, Stepper, TabBar, TopBar, StickyFooter,
                               RestTimer, BodyMap, StatTile, Kicker, HairlineDivider
    screens/                   Home, TypePicker, MusclePicker, ExercisePicker, Log,
                               Summary, History, WorkoutDetail, Profile, Settings
```

State is persisted to `SharedPreferences`:

- `worktrax.history.v1` — saved workouts (JSON array)
- `worktrax.settings.v1` — name, unit, theme (JSON object)

There is no backend.

## Design tokens

- Colors (paper, ink, accent) and typography live in `ui/theme/Theme.kt` and `ui/theme/Type.kt`.
- Light/dark mode follows the **Appearance** setting (Light / Dark / Auto) in Settings.

## Reset stored data

System Settings → Apps → Worktrax → Storage → Clear storage.

## Non-goals

No auth, no backend sync, no social features, no AI, no charts, no onboarding tour.

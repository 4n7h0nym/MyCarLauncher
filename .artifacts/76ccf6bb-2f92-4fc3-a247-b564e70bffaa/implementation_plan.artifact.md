# Implementation Plan - Steering Wheel Controls Configuration UI

Add a dedicated settings section in OpenLauncher to view and configure physical steering wheel buttons (SWC) for media and navigation actions.

## User Review Required

> [!IMPORTANT]
> - A new section **"Commandes au volant (SWC)"** will be added to `SettingsScreen.kt`.
> - Each action (`PLAY_PAUSE`, `NEXT_TRACK`, `PREVIOUS_TRACK`, `OPEN_PHONE`, `OPEN_NAVIGATION`) will display its currently bound key code.
> - Clicking "Configurer" enters capture mode: a dialog appears asking the user to press the steering wheel button they want to bind.
> - `MainActivity` will be updated to route all key events (`onKeyDown`) through `LauncherViewModel.handleSteeringKey(keyCode)`.

## Proposed Changes

### 1. ViewModel & Activity Integration
#### [MODIFY] [MainActivity.kt](file:///C:/Users/lafor/AndroidStudioProjects/MyCarLauncher/app/src/main/java/com/openlauncher/app/MainActivity.kt)
- Update `onKeyDown` to call `vm.handleSteeringKey(keyCode)`. If the launcher is in key capture mode, it captures the pressed key code and maps it to the target action. Otherwise, it executes the bound action.

### 2. UI Settings Component
#### [MODIFY] [SettingsScreen.kt](file:///C:/Users/lafor/AndroidStudioProjects/MyCarLauncher/app/src/main/java/com/openlauncher/app/ui/screen/SettingsScreen.kt)
- Add `SettingsSection("Commandes au volant")`.
- List each steering wheel action with its label, description, and assigned key code.
- Provide a button to trigger `vm.requestSteeringKeyCapture(action)`.
- Show an active capture dialog when `vm.steeringCaptureAction` is non-null, listening for key inputs.

## Verification Plan

### Automated Tests
- Run Gradle `:app:assembleDebug` to verify compilation and syntax correctness.

### Manual Verification
- Navigate to Settings -> Commandes au volant.
- Test capturing a steering wheel or keyboard key for an action.
- Verify that pressing the bound key executes the assigned action.

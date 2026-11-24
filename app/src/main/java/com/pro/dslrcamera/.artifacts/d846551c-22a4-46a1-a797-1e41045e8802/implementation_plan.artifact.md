# Sin City Rework Implementation Plan

This plan outlines the enhancements to the Sin City color-isolation filter based on user requirements: supporting both presets and a toggleable custom hue slider, refined color-family isolation with sharp/natural falloff, and three distinct range options.

## Proposed Changes

### [FilterCatalog.kt](file:///C:/Users/Achu/Downloads/ProDSLRCamera/app/src/main/java/com/pro/dslrcamera/FilterCatalog.kt)
- Define 3 clear Sin City Range options (e.g., **NARROW**, **STANDARD**, **WIDE**).

### [CameraShaderRenderer.kt](file:///C:/Users/Achu/Downloads/ProDSLRCamera/app/src/main/java/com/pro/dslrcamera/CameraShaderRenderer.kt)
- Refine the fragment shader and bitmap color isolation logic so that hues within the selected color family retain vivid original colors, while all other hues are strictly desaturated to high-contrast black and white.

### [DslrCameraScreen.kt](file:///C:/Users/Achu/Downloads/ProDSLRCamera/app/src/main/java/com/pro/dslrcamera/DslrCameraScreen.kt)
- Add a slider icon toggle in the Sin City UI to show/hide a custom hue slider (`0°` to `360°`).
- Show the 3 range options (Narrow, Standard, Wide).
- Keep preset color chips and ensure smooth interaction.

## Verification Plan

### Automated Tests
- Run Gradle build (`app:assembleDebug`) to verify compilation.

### Manual Verification
- Deploy to device/emulator.
- Test Sin City mode with preset colors (Red umbrella style).
- Toggle the custom hue slider icon and adjust custom hue.
- Switch between the 3 range options (Narrow, Standard, Wide).

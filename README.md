# Pro DSLR Camera App (Android Studio Project)

Complete, compilable Android Studio Kotlin + Jetpack Compose + CameraX/Camera2 Interop + OpenGL ES 3.0 camera application.

## Included Files
- `settings.gradle.kts` & `build.gradle.kts`
- `app/build.gradle.kts` (Compile/Target SDK 35, Min SDK 26, CameraX 1.4.1, Compose BOM 2025.01.01)
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pro/dslrcamera/FilterCatalog.kt` (All 34 film simulations, Leica looks, Ricoh GR IV, Sin City & Double Exposure presets)
- `app/src/main/java/com/pro/dslrcamera/CameraShaderRenderer.kt` (OpenGL ES 3.0 real-time shader pipeline + full-resolution Bitmap processor)
- `app/src/main/java/com/pro/dslrcamera/DslrControls.kt` (Slider-free Mode Dial, Thumb Jog Wheel, Rotary Value Carousel, Lens-Barrel Zoom Dial, Vertical Rotary Film Wheel)
- `app/src/main/java/com/pro/dslrcamera/DslrCameraScreen.kt` (Camera2 Interop manual ISO/Shutter/Focus, Bulb timer, Portrait/Landscape reflow, MediaStore 98% JPEG export)
- `app/src/main/java/com/pro/dslrcamera/MainActivity.kt`

## How to Open in Android Studio
1. Unzip `ProDslrCamera.zip`.
2. Open Android Studio and select **File > Open...** pointing to the unzipped `ProDslrCamera` folder.
3. Wait for Gradle sync to complete and run on a physical Android device (API 26+).

# Source integration notes

- `native/disflux-source/`: supplied Disflux source retained for reference; the mobile DSP implementation is built into the app.
- `native/Lemon-Studio-main/`: supplied Lemon Studio source archive retained for reference. The Android application does not load or host CLAP plugins.
- Disflux and Press are exposed as built-in FX modules. StereoProcessor is excluded.
- The mobile app uses Kotlin/Jetpack Compose and its own offline DSP processing chain; no CLAP runtime is required.

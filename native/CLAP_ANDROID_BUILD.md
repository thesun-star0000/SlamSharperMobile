# Android ARM64 CLAP runtime

SlamSharperMobile now contains a native CLAP bridge based on Lemon Studio's `os-clap` host implementation. The bridge scans Android ELF CLAP modules, instantiates effect plugins, activates them at the current sample rate, exposes parameter metadata/setters, and processes stereo buffers offline. The Kotlin UI can import `.clap` files and apply compatible effects to the current rendered buffer.

## Why the six bundled binaries do not run on Android

The remaining files in `app/src/main/assets/clap` are PE32+ Windows x86-64 DLLs. Android needs ELF shared objects for `aarch64-linux-android` (ARM64). The bridge deliberately reports these files as incompatible instead of trying to load them or crashing the app. Renaming a Windows DLL to `.so`/`.clap` cannot convert its machine code or ABI.

## Build the Android host and source-available plugins (Windows)

Requirements:
- Android Studio with Android NDK installed;
- set `ANDROID_NDK_HOME` to the NDK directory (for example `C:\\Users\\<you>\\AppData\\Local\\Android\\Sdk\\ndk\\<version>`);
- Rust stable 1.85+ and `rustup`;
- `rustup target add aarch64-linux-android`;
- `cargo install cargo-ndk`;
- network access for Cargo dependencies (including the NIH-plug Git dependency).

From the SlamSharperMobile project root, run:

```powershell
.\native\build-android-clap.ps1
```

The script builds `libslam_clap_bridge.so` into `app/src/main/jniLibs/arm64-v8a` and builds four source-available Lemon effects (Fast Distortion, Parametric EQ, Transient Processor and Stereo Shaper) into `app/src/main/assets/clap-android`. Press and Disflux are provided as built-in FX modules instead of standalone CLAP binaries. Rebuild/reinstall the Android app after running the script. The app copies bundled plugin modules to its private `files/clap` folder and scans them on launch.

The source archive contains matching source for Lemon Fast Distortion, Lemon Parametric EQ, Lemon Press, Lemon Transient Processor, and Lemon Stereo Shaper. Press is ported as the built-in `Press` three-band maximiser module; its desktop CLAP binary is not built by this script. The supplied `StereoProcessor.clap` is excluded and is not treated as equivalent to Lemon Stereo Shaper. The supplied Disflux 1.2.0 source is preserved in `native/disflux-source/`; its all-pass diffusion algorithm is integrated into the SlamSharperMobile FX chain as a mobile-native `Disflux` module.

## Runtime limits

- The current bridge hosts CLAP **audio effects** for offline application to the rendered sample. It does not yet embed desktop plugin editor windows or host CLAP instruments.
- Parameter metadata and setter functions are exposed by the native bridge; the current compact UI applies effects with their plugin defaults. A full per-plugin parameter editor is a separate UI step.
- Build and test on a physical ARM64 Android device. The current execution environment has no Android NDK/Rust toolchain and cannot produce or validate the `.so` binaries here.

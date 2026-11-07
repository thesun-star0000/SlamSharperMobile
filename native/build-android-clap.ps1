$ErrorActionPreference = 'Stop'
$Project = Split-Path -Parent $PSScriptRoot
$Lemon = Join-Path $PSScriptRoot 'Lemon-Studio-main'
$Target = 'aarch64-linux-android'
$App = Join-Path $Project 'app'
$Jni = Join-Path $App 'src/main/jniLibs/arm64-v8a'
$ClapOut = Join-Path $App 'src/main/assets/clap-android'

if (-not (Get-Command cargo -ErrorAction SilentlyContinue)) { throw 'Install Rust stable first: https://rustup.rs/' }
if (-not (Get-Command cargo-ndk -ErrorAction SilentlyContinue)) { throw 'Install cargo-ndk: cargo install cargo-ndk' }
if (-not $env:ANDROID_NDK_HOME -and -not $env:ANDROID_NDK_ROOT) { throw 'Set ANDROID_NDK_HOME to your Android NDK directory.' }
rustup target add $Target
New-Item -ItemType Directory -Force $Jni,$ClapOut | Out-Null

Push-Location $Lemon
try {
    cargo ndk -t arm64-v8a build -p slam-clap-bridge --release
    Copy-Item (Join-Path $Lemon "target/$Target/release/libslam_clap_bridge.so") (Join-Path $Jni 'libslam_clap_bridge.so') -Force

    $plugins = @(
        @{ Package='lemon-fast-distortion'; File='Lemon Fast Distortion.clap'; Binary='liblemon_fast_distortion.so' },
        @{ Package='lemon-parametric-eq-2'; File='Lemon Parametric EQ.clap'; Binary='liblemon_parametric_eq_2.so' },
        @{ Package='lemon-transient-processor'; File='Lemon Transient Processor.clap'; Binary='liblemon_transient_processor.so' },
        @{ Package='lemon-stereo-shaper'; File='Lemon Stereo Shaper.clap'; Binary='liblemon_stereo_shaper.so' }
    )
    foreach ($plugin in $plugins) {
        cargo ndk -t arm64-v8a build -p $plugin.Package --release
        $source = Join-Path $Lemon "target/$Target/release/$($plugin.Binary)"
        if (-not (Test-Path $source)) { throw "Expected plugin output not found: $source" }
        Copy-Item $source (Join-Path $ClapOut $plugin.File) -Force
    }
} finally { Pop-Location }
Write-Host 'Android ARM64 CLAP host and 4 source-available Lemon plugins built.' -ForegroundColor Green
Write-Host 'Disflux and Press are built-in Kotlin FX modules; their standalone CLAP binaries are not bundled.' -ForegroundColor Green

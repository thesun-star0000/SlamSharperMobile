# Disflux source integration notes

The supplied Disflux 1.2.0 source is preserved under `native/disflux-source/`, including its original license and project metadata.

SlamSharperMobile exposes a mobile-native `Disflux` effect in the FX chain. Its all-pass diffusion DSP uses the source's parameter model and logarithmic frequency distribution (Amount, Frequency, Spread, Pinch, Mix) in a Kotlin implementation so it can run in the Android audio pipeline without requiring a desktop JUCE plugin binary.

License note: the supplied archive's `LICENSE.txt` is AGPL-3.0, while some individual source headers refer to GPL-3.0. Review the original license files and comply with applicable copyleft/source-disclosure obligations before distributing builds.

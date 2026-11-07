package com.slamsharper.mobile.model

import java.util.UUID

data class SamplerSettings(
    val attackMs: Float = 0f,
    val holdMs: Float = 0f,
    val decayMs: Float = 80f,
    val sustain: Float = 1f,
    val releaseMs: Float = 40f,
    val envelopeEnabled: Boolean = false,
    val pitchEnvStart: Float = 0f,
    val pitchEnvEnd: Float = 0f,
    val pitchEnvAmount: Float = 0f,
    val filterEnabled: Boolean = false,
    val filterCutoff: Float = 18000f,
    val filterResonance: Float = 0f,
    val filterMode: String = "Low-pass",
    val lfoEnabled: Boolean = false,
    val lfoRateHz: Float = 4f,
    val lfoDepth: Float = 0f,
    val lfoTarget: String = "Volume",
    val fadeInMs: Float = 0f,
    val fadeOutMs: Float = 0f,
    val normalize: Boolean = false,
    val removeDc: Boolean = false,
    val ringModHz: Float = 0f,
    val ringModMix: Float = 0f,
    val softClip: Boolean = false,
    val stereoSwap: Boolean = false,
    val pan: Float = 0f,
    val rootNote: Int = 60,
    val loopStart: Float = 0f,
    val loopEnd: Float = 1f,
    val pingPong: Boolean = false,
    val panEnvEnabled: Boolean = false,
    val panAttackMs: Float = 0f,
    val panDecayMs: Float = 100f,
    val panSustain: Float = .5f,
    val panReleaseMs: Float = 40f,
    val modAEnvEnabled: Boolean = false,
    val modAAttackMs: Float = 0f,
    val modADecayMs: Float = 100f,
    val modASustain: Float = 1f,
    val modAReleaseMs: Float = 40f,
    val modBEnvEnabled: Boolean = false,
    val modBAttackMs: Float = 0f,
    val modBDecayMs: Float = 100f,
    val modBSustain: Float = 0f,
    val modBReleaseMs: Float = 40f,
    val stretchEnabled: Boolean = false,
    val stretchRatio: Float = 1f,
    val echoEnabled: Boolean = false,
    val echoFeed: Float = .35f,
    val echoPitch: Float = 7f,
    val echoTimeMs: Float = 180f,
    val echoCount: Int = 4,
    val echoPingPong: Boolean = false,
    val echoFatMode: Boolean = false
)

data class AudioBuffer(
    val samples: FloatArray,
    val sampleRate: Int,
    val channels: Int = 1
) {
    val frames: Int get() = samples.size / channels
    val duration: Float get() = if (sampleRate == 0) 0f else frames.toFloat() / sampleRate
    fun copyOfSamples() = samples.copyOf()
}

enum class ModuleType(val title: String) {
    ENVELOPE("Envelope"),
    EQ("EQ"),
    COMPRESSOR("Compressor"),
    SATURATION("Saturation"),
    BLUR("Blur"),
    RESONANCE_SUPPRESSOR("Resonance Suppressor"),
    FREQ_SHIFT("Frequency Shift"),
    PITCH_ENVELOPE("Pitch Envelope"),
    GAIN("Gain"),
    STACK("Stack"),
    DISFLUX("Disflux"),
    PRESS("Press")
}

data class AudioModule(
    val id: String = UUID.randomUUID().toString(),
    val type: ModuleType,
    var bypassed: Boolean = false,
    val params: MutableMap<String, Any> = mutableMapOf()
)

data class Layer(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    var source: AudioBuffer,
    var gainDb: Float = 0f,
    var mute: Boolean = false,
    var solo: Boolean = false,
    var offsetMs: Float = 0f,
    val chain: MutableList<AudioModule> = mutableListOf(),
    var trimStart: Float = 0f,
    var trimEnd: Float = source.duration
)

data class KitSlot(
    val id: String = UUID.randomUUID().toString(),
    var label: String,
    val layers: MutableList<Layer> = mutableListOf(),
    var mute: Boolean = false,
    var solo: Boolean = false
)

fun defaults(type: ModuleType): MutableMap<String, Any> = when (type) {
    ModuleType.GAIN -> mutableMapOf("gain_db" to 0f, "polarity_flip" to false, "dc_remove" to false, "normalize" to false, "normalize_target_db" to 0f)
    ModuleType.SATURATION -> mutableMapOf("mode" to "soft", "drive" to .3f, "mix" to 1f, "output_trim_db" to 0f, "oversample_factor" to 2)
    ModuleType.COMPRESSOR -> mutableMapOf("threshold_db" to -12f, "ratio" to 4f, "attack_ms" to 5f, "release_ms" to 50f, "knee_db" to 6f, "makeup_gain_db" to 0f, "mix" to 1f, "lookahead_ms" to 5f)
    ModuleType.ENVELOPE -> mutableMapOf("pre_gain_db" to 0f, "points" to mutableListOf(listOf(0f,0f,0f), listOf(1f,0f,0f)))
    ModuleType.PITCH_ENVELOPE -> mutableMapOf("pre_shift_cents" to 0f, "snap_semitone" to false, "points" to mutableListOf(listOf(0f,0f,0f), listOf(1f,0f,0f)))
    ModuleType.EQ -> mutableMapOf("global_gain_db" to 0f, "bands" to mutableListOf(mutableMapOf("type" to "peaking","freq" to 1000f,"gain" to 0f,"q" to 1f)))
    ModuleType.FREQ_SHIFT -> mutableMapOf("mode" to "resample", "shift_cents" to 0f, "shift_hz" to 0f, "detect_fundamental" to true)
    ModuleType.RESONANCE_SUPPRESSOR -> mutableMapOf()
    ModuleType.BLUR -> mutableMapOf("noise_type" to "white","blend" to .2f,"tail_length_ms" to 0f,"tail_decay_db" to -48f,"stereo_width" to .3f,"smoothness" to .3f,"seed" to 0)
    ModuleType.STACK -> mutableMapOf("copy_count" to 0,"pitch_interval_cents" to 700f,"spacing_mode" to "linear","fat_mode" to false,"taper_mode" to "none","taper_amount" to .5f,"master_gain_db" to -6f)
    ModuleType.DISFLUX -> mutableMapOf("amount" to 1f,"spread" to 200f,"frequency" to 50f,"pinch" to 1f,"mix" to 1f)
    ModuleType.PRESS -> mutableMapOf(
        "low_crossover_hz" to 200f, "high_crossover_hz" to 3000f,
        "low_threshold_db" to -8f, "mid_threshold_db" to -6f, "high_threshold_db" to -5f,
        "low_ratio" to 4f, "mid_ratio" to 4f, "high_ratio" to 4f,
        "attack_ms" to 2f, "release_ms" to 86f, "master_ceiling_db" to -0.2f,
        "mix" to 1f, "output_gain_db" to 0f
    )
}

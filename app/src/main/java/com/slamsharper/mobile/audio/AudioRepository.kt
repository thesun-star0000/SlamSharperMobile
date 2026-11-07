package com.slamsharper.mobile.audio

import android.content.ContentResolver
import android.net.Uri
import com.slamsharper.mobile.model.AudioBuffer
import java.io.InputStream

class AudioRepository(private val resolver: ContentResolver) {
    fun load(uri: Uri): AudioBuffer {
        val stream: InputStream = resolver.openInputStream(uri) ?: error("Cannot open selected audio file.")
        stream.use { return WavCodec.decode(it) }
    }
    fun saveWav(uri: Uri, audio: AudioBuffer, bitDepth: Int = 32) {
        val out = resolver.openOutputStream(uri) ?: error("Cannot create output file.")
        out.use { WavCodec.encode(audio,it,bitDepth) }
    }
}

package com.slamsharper.mobile.audio

import com.slamsharper.mobile.model.AudioBuffer
import java.io.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object WavCodec {
    fun decode(input: InputStream): AudioBuffer {
        val data = input.readBytes()
        require(data.size >= 44 && String(data, 0, 4) == "RIFF" && String(data, 8, 4) == "WAVE") { "Unsupported audio file. WAV is required." }
        var pos = 12
        var channels = 1
        var sampleRate = 44100
        var bits = 16
        var format = 1
        var pcmStart = -1
        var pcmSize = 0
        while (pos + 8 <= data.size) {
            val id = String(data, pos, 4)
            val size = leInt(data, pos + 4)
            val start = pos + 8
            if (id == "fmt ") {
                format = leShort(data, start)
                channels = leShort(data, start + 2)
                sampleRate = leInt(data, start + 4)
                bits = leShort(data, start + 14)
            } else if (id == "data") {
                pcmStart = start
                pcmSize = minOf(size, data.size - start)
                break
            }
            pos = start + size + (size and 1)
        }
        require(pcmStart >= 0) { "WAV data chunk not found." }
        require(channels in 1..2 && sampleRate in 8000..384000) { "Unsupported WAV channel count or sample rate." }
        require(format == 1 || format == 3) { "Unsupported WAV encoding. PCM and IEEE float are supported." }
        require(format != 3 || bits == 32) { "IEEE float WAV must use 32-bit samples." }
        val bytesPerSample = when(bits) { 16 -> 2; 24 -> 3; 32 -> 4; else -> error("Only 16/24/32-bit WAV is supported.") }
        require(pcmSize % (bytesPerSample * channels) == 0) { "WAV data chunk is incomplete." }
        val count = pcmSize / bytesPerSample
        val out = FloatArray(count)
        var p = pcmStart
        for (i in out.indices) {
            out[i] = when(bits) {
                16 -> (leShort(data,p).toFloat()/32768f).also { p += 2 }
                24 -> {
                    val x=(data[p].toInt() and 255) or ((data[p+1].toInt() and 255) shl 8) or ((data[p+2].toInt()) shl 16)
                    p += 3
                    (if (x and 0x800000 != 0) x or -0x1000000 else x).toFloat()/8388608f
                }
                else -> {
                    val bb=ByteBuffer.wrap(data,p,4).order(ByteOrder.LITTLE_ENDIAN)
                    p+=4
                    if(format==3) bb.float else bb.int.toFloat()/2147483648f
                }
            }
        }
        return AudioBuffer(out,sampleRate,channels)
    }

    fun encode(buffer: AudioBuffer, output: OutputStream, bitDepth: Int = 32) {
        val bits=if(bitDepth==16)16 else 32
        val bytesPerSample=bits/8
        val dataSize=buffer.samples.size*bytesPerSample
        val bb=ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        bb.put("RIFF".toByteArray()); bb.putInt(36+dataSize); bb.put("WAVE".toByteArray())
        bb.put("fmt ".toByteArray()); bb.putInt(16); bb.putShort(if(bits==32) 3 else 1); bb.putShort(buffer.channels.toShort())
        bb.putInt(buffer.sampleRate); bb.putInt(buffer.sampleRate*buffer.channels*bytesPerSample)
        bb.putShort((buffer.channels*bytesPerSample).toShort()); bb.putShort(bits.toShort()); bb.put("data".toByteArray()); bb.putInt(dataSize)
        output.write(bb.array())
        val out=ByteArray(8192)
        var op=0
        if(bits==16) {
            for(x in buffer.samples) {
                val v=(x.coerceIn(-1f,1f)*32767f).roundToInt()
                out[op++]=(v and 255).toByte(); out[op++]=(v shr 8).toByte()
                if(op>=out.size-2){output.write(out);op=0}
            }
        } else {
            val fb=ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            for(x in buffer.samples) {
                fb.clear(); fb.putFloat(if(x.isFinite()) x else 0f); System.arraycopy(fb.array(),0,out,op,4); op+=4
                if(op>=out.size-4){output.write(out);op=0}
            }
        }
        if(op>0) output.write(out,0,op)
    }
    private fun leInt(b:ByteArray,p:Int)=ByteBuffer.wrap(b,p,4).order(ByteOrder.LITTLE_ENDIAN).int
    private fun leShort(b:ByteArray,p:Int)=ByteBuffer.wrap(b,p,2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()
}

package com.slamsharper.mobile.clap

import org.json.JSONArray
import java.io.File

data class ClapPluginEntry(
    val path: String,
    val id: String = "",
    val name: String = "",
    val vendor: String = "",
    val kind: String = "",
    val error: String? = null
)

data class ClapParameter(
    val id: Int,
    val name: String,
    val group: String,
    val min: Double,
    val max: Double,
    val value: Double,
    val defaultValue: Double,
    val steps: Int,
    val readOnly: Boolean,
    val bypass: Boolean
)

object NativeClapHost {
    val available: Boolean = try { System.loadLibrary("slam_clap_bridge"); true } catch (_: Throwable) { false }
    private external fun nativeScan(folder: String): String
    private external fun nativeOpen(path: String, pluginId: String, sampleRate: Int): Long
    private external fun nativeParams(handle: Long): String
    private external fun nativeSetParam(handle: Long, paramId: Int, value: Double): Int
    private external fun nativeProcessCopy(handle: Long, samples: FloatArray): FloatArray?
    private external fun nativeClose(handle: Long)

    fun scan(folder: File): List<ClapPluginEntry> {
        val files=folder.listFiles()?.filter { it.isFile && it.extension.equals("clap",true) }?.sortedBy { it.name }.orEmpty()
        if (!available) return files.map { file ->
            ClapPluginEntry(file.absolutePath,error="Файл найден, но Android CLAP host/ABI недоступен. Нужен бинарный плагин Android ARM64; Windows .clap здесь не запускается.")
        }.ifEmpty { listOf(ClapPluginEntry(folder.path,error="Android CLAP host library is not installed. Run native/build-android-clap.ps1.")) }
        return try {
            val raw=nativeScan(folder.absolutePath)
            val arr=JSONArray(raw)
            val scanned=(0 until arr.length()).map { i ->
                val o=arr.getJSONObject(i)
                ClapPluginEntry(o.optString("path"),o.optString("id"),o.optString("name"),o.optString("vendor"),o.optString("kind"),o.optString("error").ifBlank{null})
            }
            val known=scanned.map{File(it.path).canonicalPath}.toSet()
            scanned + files.filter{it.canonicalPath !in known}.map { file ->
                ClapPluginEntry(file.absolutePath,error="Файл обнаружен, но плагин не прошёл сканирование: проверь ABI Android ARM64 и корректность CLAP-бинарника.")
            }
        } catch (e: Throwable) {
            files.map { ClapPluginEntry(it.absolutePath,error=e.message ?: "CLAP scan failed") }
                .ifEmpty { listOf(ClapPluginEntry(folder.path,error=e.message ?: rawError())) }
        }
    }

    private fun rawError() = "CLAP scan failed"

    fun open(path: String, id: String, sampleRate: Int): Long {
        check(available) { "Android CLAP host is not installed. Build it with native/build-android-clap.ps1." }
        return nativeOpen(path,id,sampleRate).also { check(it>0) { "Could not open CLAP plugin: $id" } }
    }

    fun parameters(handle: Long): List<ClapParameter> {
        val arr=JSONArray(nativeParams(handle))
        return (0 until arr.length()).map { i -> val o=arr.getJSONObject(i);ClapParameter(o.optInt("id"),o.optString("name"),o.optString("group"),o.optDouble("min"),o.optDouble("max"),o.optDouble("value"),o.optDouble("default"),o.optInt("steps"),o.optBoolean("readOnly"),o.optBoolean("bypass")) }
    }

    fun setParameter(handle: Long, id: Int, normalizedValue: Double): Boolean = nativeSetParam(handle,id,normalizedValue.coerceIn(0.0,1.0)) != 0
    fun processOpened(handle: Long, interleavedStereo: FloatArray): FloatArray = nativeProcessCopy(handle,interleavedStereo) ?: error("CLAP processing failed")
    fun close(handle: Long) = nativeClose(handle)

    fun process(path: String, id: String, sampleRate: Int, audio: FloatArray): FloatArray {
        val handle=open(path,id,sampleRate)
        return try { processOpened(handle,audio) } finally { close(handle) }
    }
}

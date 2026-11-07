package com.slamsharper.mobile.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.slamsharper.mobile.audio.AudioRepository
import com.slamsharper.mobile.dsp.AudioEngine
import com.slamsharper.mobile.dsp.Dsp
import com.slamsharper.mobile.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class UiState(
    val source:AudioBuffer?=null,
    val rendered:AudioBuffer?=null,
    val chain:List<AudioModule> = emptyList(),
    val selectedId:String?=null,
    val kit:List<KitSlot> = emptyList(),
    val selectedSlotId:String?=null,
    val message:String?=null,
    val isBusy:Boolean=false,
    val isPlaying:Boolean=false,
    val samplerPitch:Float=0f,
    val samplerGainDb:Float=0f,
    val samplerReverse:Boolean=false,
    val samplerLoop:Boolean=false,
    val sampler: SamplerSettings = SamplerSettings(),
    val trimStart:Float=0f,
    val trimEnd:Float=0f,
    val undo:List<String> = emptyList(),
    val redo:List<String> = emptyList()
)

class MainViewModel(app:Application):AndroidViewModel(app){
    private val repo=AudioRepository(app.contentResolver)
    private val _state=MutableStateFlow(UiState())
    val state=_state.asStateFlow()
    private val undo=ArrayDeque<String>()
    private val redo=ArrayDeque<String>()
    private var playbackJob: Job? = null
    private var renderJob: Job? = null
    private var keyboardJob: Job? = null
    private var keyboardVersion: Long = 0L
    private var renderVersion: Long = 0L
    private val renderMutex = Mutex()
    @Volatile private var activeTrack: AudioTrack? = null

    fun togglePlayback() {
        if (_state.value.isPlaying) { stopPlayback(); return }
        val audio = _state.value.rendered ?: return
        if (audio.samples.isEmpty()) return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch(Dispatchers.IO) {
            val mask = if (audio.channels == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO
            val minBytes = AudioTrack.getMinBufferSize(audio.sampleRate, mask, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(audio.sampleRate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(mask).build())
                .setBufferSizeInBytes(maxOf(minBytes, 8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            activeTrack = track
            _state.value = _state.value.copy(isPlaying = true)
            try {
                val chunkFrames = 2048
                val totalFrames = audio.frames
                var frame = 0
                var firstChunk = true
                while (isActive && frame < totalFrames) {
                    val countFrames = minOf(chunkFrames, totalFrames-frame)
                    val pcm = ShortArray(countFrames * audio.channels)
                    for (i in 0 until countFrames) for (ch in 0 until audio.channels) {
                        val srcFrame = frame + i
                        val value = audio.samples[srcFrame*audio.channels+ch].let { if(it.isFinite())it else 0f }.coerceIn(-1f,1f)
                        pcm[i*audio.channels+ch] = (value*32767f).toInt().toShort()
                    }
                    var written = 0
                    while (written < pcm.size && isActive) {
                        val n = track.write(pcm, written, pcm.size-written, AudioTrack.WRITE_BLOCKING)
                        if (n < 0) error("Audio playback failed: $n")
                        written += n
                    }
                    if (firstChunk) { track.play(); firstChunk = false }
                    frame += countFrames
                }
            } finally {
                try { if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop() } catch (_: Exception) {}
                track.release()
                if (activeTrack === track) activeTrack = null
                _state.value = _state.value.copy(isPlaying = false)
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        playbackJob = null
        try { activeTrack?.pause(); activeTrack?.flush(); activeTrack?.stop() } catch (_: Exception) {}
        _state.value = _state.value.copy(isPlaying = false)
    }

    override fun onCleared() { stopPlayback(); super.onCleared() }

    fun load(uri:android.net.Uri){run("Loading"){val a=repo.load(uri);_state.value=UiState(source=a,rendered=a,trimEnd=a.duration)}}
    fun export(uri:android.net.Uri,bitDepth:Int){val a=_state.value.rendered?:return;run("Exporting"){repo.saveWav(uri,a,bitDepth)}}
    fun add(type:ModuleType){snapshot();val list=_state.value.chain.toMutableList();val m=AudioModule(type=type,params=defaults(type));list+=m;updateChain(list,m.id)}
    fun remove(){val id=_state.value.selectedId?:return;snapshot();val list=_state.value.chain.filterNot{it.id==id};updateChain(list,list.lastOrNull()?.id)}
    fun bypass(){val id=_state.value.selectedId?:return;snapshot();val list=_state.value.chain.map{if(it.id==id)it.copy(bypassed=!it.bypassed) else it};updateChain(list,id)}
    fun move(delta:Int){val id=_state.value.selectedId?:return;snapshot();val list=_state.value.chain.toMutableList();val i=list.indexOfFirst{it.id==id};val j=i+delta;if(i>=0&&j in list.indices){val t=list[i];list[i]=list[j];list[j]=t;updateChain(list,id)}}
    fun select(id:String){_state.value=_state.value.copy(selectedId=id)}
    fun setParam(key:String,value:Any){val id=_state.value.selectedId?:return;val list=_state.value.chain.map{if(it.id==id)it.copy(params=it.params.toMutableMap().also{p->p[key]=value}) else it};_state.value=_state.value.copy(chain=list);render()}
    fun setTrim(start:Float,end:Float){_state.value=_state.value.copy(trimStart=start.coerceAtLeast(0f),trimEnd=end.coerceAtLeast(start+0.001f));render()}
    fun setSamplerPitch(value:Float){_state.value=_state.value.copy(samplerPitch=value);render()}
    fun setSamplerGain(value:Float){_state.value=_state.value.copy(samplerGainDb=value);render()}
    fun setSamplerReverse(value:Boolean){_state.value=_state.value.copy(samplerReverse=value);render()}
    fun setSamplerLoop(value:Boolean){_state.value=_state.value.copy(samplerLoop=false)}
    fun updateSampler(transform: (SamplerSettings) -> SamplerSettings) {
        _state.value = _state.value.copy(sampler = transform(_state.value.sampler))
        render()
    }
    fun playKeyboardNote(note: Int) = playKeyboardChord(listOf(note))

    /** Render selected keyboard notes as one polyphonic chord, with every pitch starting together. */
    fun playKeyboardChord(notes: List<Int>) {
        val snapshot = _state.value
        val src = snapshot.source ?: return
        val selected = notes.distinct().take(16)
        keyboardVersion += 1
        val version = keyboardVersion
        keyboardJob?.cancel()
        if (selected.isEmpty()) { stopPlayback(); return }
        stopPlayback()
        keyboardJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                renderMutex.withLock {
                delay(35)
                if (version != keyboardVersion) return@withLock
                val start = (snapshot.trimStart * src.sampleRate).toInt().coerceIn(0, src.frames)
                val end = (snapshot.trimEnd * src.sampleRate).toInt().coerceIn(start, src.frames)
                val source = AudioBuffer(src.samples.copyOfRange(start * src.channels, end * src.channels), src.sampleRate, src.channels)
                val voices = selected.map { AudioEngine.pitch(source, (it - snapshot.sampler.rootNote).toFloat()) }
                val frames = voices.maxOfOrNull { it.frames } ?: 0
                val mixed = FloatArray(frames * source.channels)
                val gain = 1f / kotlin.math.sqrt(voices.size.toFloat())
                for (voice in voices) for (i in voice.samples.indices) mixed[i] += voice.samples[i] * gain
                var buffer = AudioBuffer(mixed, source.sampleRate, source.channels)
                if (snapshot.samplerReverse) buffer = AudioEngine.reverse(buffer)
                if (snapshot.sampler.stretchEnabled) buffer = AudioEngine.timeStretch(buffer, snapshot.sampler.stretchRatio)
                if (snapshot.samplerGainDb != 0f) {
                    val g = Dsp.dbToLin(snapshot.samplerGainDb)
                    for (i in buffer.samples.indices) buffer.samples[i] *= g
                }
                buffer = AudioEngine.processSampler(buffer, snapshot.sampler)
                buffer = AudioEngine.render(buffer, snapshot.chain)
                withContext(Dispatchers.Main) {
                    if (version == keyboardVersion) {
                        _state.value = _state.value.copy(rendered = buffer)
                        togglePlayback()
                    }
                }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Throwable) {
                withContext(Dispatchers.Main) { _state.value = _state.value.copy(message = e.message ?: "Keyboard render failed") }
            }
        }
    }
    fun loadProject(uri:android.net.Uri){getApplication<Application>().contentResolver.openInputStream(uri)?.use{val json=it.readBytes().toString(Charsets.UTF_8);val d=ProjectStore.decode(json);_state.value=_state.value.copy(chain=d.first,trimStart=d.second.first,trimEnd=d.second.second,sampler=ProjectStore.decodeSampler(json));render()}}
    fun saveProject(uri: android.net.Uri) {
        val s = _state.value
        val json = ProjectStore.encode(s.chain, s.trimStart, s.trimEnd, s.sampler)
        getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
            it.write(json.toByteArray(Charsets.UTF_8))
        } ?: error("Cannot create project file.")
    }

    fun undo() {
        if (undo.isEmpty()) return
        val current = snapshotString()
        val previous = undo.removeLast()
        redo.addLast(current)
        publishHistory()
        restore(previous)
    }

    fun redo() {
        if (redo.isEmpty()) return
        val current = snapshotString()
        val next = redo.removeLast()
        undo.addLast(current)
        publishHistory()
        restore(next)
    }

    private fun publishHistory() {
        _state.value = _state.value.copy(undo = undo.toList(), redo = redo.toList())
    }

    fun autoTrim() {
        val src = _state.value.source ?: return
        if (src.frames == 0) return
        // Detect the active region by peak amplitude per frame, retaining a short safety margin.
        var first = src.frames
        var last = -1
        val threshold = 0.0015f
        for (frame in 0 until src.frames) {
            var peak = 0f
            for (ch in 0 until src.channels) {
                peak = maxOf(peak, kotlin.math.abs(src.samples[frame * src.channels + ch]))
            }
            if (peak >= threshold) {
                if (first == src.frames) first = frame
                last = frame
            }
        }
        if (last < first) {
            _state.value = _state.value.copy(message = "Auto Trim: no signal above threshold.")
            return
        }
        val margin = (src.sampleRate * 0.008f).toInt()
        val start = (first - margin).coerceAtLeast(0).toFloat() / src.sampleRate
        val end = (last + margin + 1).coerceAtMost(src.frames).toFloat() / src.sampleRate
        setTrim(start, end.coerceAtLeast(start + 0.001f))
    }

    fun addKitSlot() {
        val current = _state.value
        if (current.kit.size >= 16) return
        val slot = KitSlot(label = "Slot ${current.kit.size + 1}")
        val updated = current.kit + slot
        _state.value = current.copy(kit = updated, selectedSlotId = slot.id)
    }

    fun selectKitSlot(id: String) {
        _state.value = _state.value.copy(selectedSlotId = id)
    }

    fun addLayerToSelected() {
        val current = _state.value
        val src = current.source ?: return
        val slotId = current.selectedSlotId ?: return
        val slot = current.kit.firstOrNull { it.id == slotId } ?: return
        val startFrame = (current.trimStart * src.sampleRate).toInt().coerceIn(0, src.frames)
        val endFrame = (current.trimEnd * src.sampleRate).toInt().coerceIn(startFrame, src.frames)
        val trimmed = AudioBuffer(
            src.samples.copyOfRange(startFrame * src.channels, endFrame * src.channels),
            src.sampleRate,
            src.channels
        )
        val layer = Layer(
            name = "Layer ${slot.layers.size + 1}",
            source = trimmed,
            chain = current.chain.map { it.copy(params = it.params.toMutableMap()) }.toMutableList(),
            trimEnd = trimmed.duration
        )
        val updatedKit = current.kit.map { existing ->
            if (existing.id == slotId) existing.copy(layers = (existing.layers + layer).toMutableList()) else existing
        }
        _state.value = current.copy(kit = updatedKit)
    }

    private fun snapshot(){undo.addLast(snapshotString());while(undo.size>100)undo.removeFirst();redo.clear();publishHistory()}
    private fun snapshotString()=ProjectStore.encode(_state.value.chain,_state.value.trimStart,_state.value.trimEnd,_state.value.sampler)
    private fun restore(s:String){val d=ProjectStore.decode(s);_state.value=_state.value.copy(chain=d.first,trimStart=d.second.first,trimEnd=d.second.second,sampler=ProjectStore.decodeSampler(s));render()}
    private fun updateChain(list:List<AudioModule>,selected:String?){_state.value=_state.value.copy(chain=list,selectedId=selected);render()}
    private fun render(){
        val snapshot = _state.value
        val src = snapshot.source ?: return
        val version = ++renderVersion
        renderJob?.cancel()
        renderJob = viewModelScope.launch(Dispatchers.Default) {
            try {
                renderMutex.withLock {
                    // Coalesce rapid slider updates and serialize expensive offline renders.
                    delay(70)
                    if (version != renderVersion) return@withLock
                    val end = if (snapshot.trimEnd <= 0f) src.duration else snapshot.trimEnd
                    val startFrame = (snapshot.trimStart * src.sampleRate).toInt().coerceIn(0, src.frames)
                    val endFrame = (end * src.sampleRate).toInt().coerceIn(startFrame, src.frames)
                    var buffer = AudioBuffer(src.samples.copyOfRange(startFrame * src.channels, endFrame * src.channels), src.sampleRate, src.channels)
                    if (snapshot.samplerReverse) buffer = AudioEngine.reverse(buffer)
                    if (snapshot.samplerPitch != 0f) buffer = AudioEngine.pitch(buffer, snapshot.samplerPitch)
                    if (snapshot.sampler.stretchEnabled) buffer = AudioEngine.timeStretch(buffer, snapshot.sampler.stretchRatio)
                    if (snapshot.samplerGainDb != 0f) {
                        val gain = Dsp.dbToLin(snapshot.samplerGainDb)
                        for (i in buffer.samples.indices) buffer.samples[i] *= gain
                    }
                    buffer = AudioEngine.processSampler(buffer, snapshot.sampler)
                    val rendered = AudioEngine.render(buffer, snapshot.chain)
                    withContext(Dispatchers.Main) {
                        if (version == renderVersion) _state.value = _state.value.copy(rendered = rendered)
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Throwable) {
                withContext(Dispatchers.Main) {
                    if (version == renderVersion) _state.value = _state.value.copy(message = e.message ?: "Audio render failed")
                }
            }
        }
    }
    private inline fun run(msg:String,crossinline block:()->Unit){_state.value=_state.value.copy(isBusy=true,message=msg);try{block();_state.value=_state.value.copy(isBusy=false,message=null)}catch(e:Exception){_state.value=_state.value.copy(isBusy=false,message=e.message?:"Operation failed")}}
}

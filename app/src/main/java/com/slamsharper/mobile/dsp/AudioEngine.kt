package com.slamsharper.mobile.dsp

import com.slamsharper.mobile.model.*
import kotlin.math.*

object AudioEngine {
    fun render(source:AudioBuffer, chain:List<AudioModule>):AudioBuffer{
        var x=source
        for(m in chain)x=Modules.process(x,m)
        return x
    }

    fun renderLayer(layer:Layer):AudioBuffer{
        val start=(layer.trimStart*layer.source.sampleRate).toInt().coerceIn(0,layer.source.frames)
        val end=(layer.trimEnd*layer.source.sampleRate).toInt().coerceIn(start,layer.source.frames)
        val startS=start*layer.source.channels;val endS=end*layer.source.channels
        var x=AudioBuffer(layer.source.samples.copyOfRange(startS,endS),layer.source.sampleRate,layer.source.channels)
        x=render(x,layer.chain)
        val gain=Dsp.dbToLin(layer.gainDb);for(i in x.samples.indices)x.samples[i]*=gain
        if(layer.offsetMs>0){
            val pad=(layer.offsetMs*layer.source.sampleRate/1000f).roundToInt()*layer.source.channels
            x=AudioBuffer(FloatArray(pad)+x.samples, x.sampleRate,x.channels)
        }
        return x
    }

    fun mixLayers(layers:List<Layer>, sampleRate:Int):AudioBuffer{
        val active=layers.filter{!it.mute}
        if(active.isEmpty())return AudioBuffer(FloatArray(0),sampleRate,1)
        val solo=active.filter{it.solo}
        val use=if(solo.isNotEmpty())solo else active
        val targetChannels=if(use.any{it.source.channels==2})2 else 1
        val rendered=use.map { layer ->
            val raw=renderLayer(layer)
            convertFormat(raw,sampleRate,targetChannels)
        }
        val n=rendered.maxOfOrNull{it.frames}?:0
        val out=FloatArray(n*targetChannels)
        for(a in rendered) for(i in a.samples.indices) out[i]+=a.samples[i]
        return AudioBuffer(out,sampleRate,targetChannels)
    }

    private fun convertFormat(input:AudioBuffer, rate:Int, channels:Int):AudioBuffer {
        if(input.frames==0)return AudioBuffer(FloatArray(0),rate,channels)
        val frames=max(1,(input.frames.toDouble()*rate/input.sampleRate).roundToInt())
        val out=FloatArray(frames*channels)
        for(f in 0 until frames) {
            val srcPos=f.toDouble()*input.sampleRate/rate
            val a=srcPos.toInt().coerceIn(0,input.frames-1)
            val b=(a+1).coerceAtMost(input.frames-1)
            val t=(srcPos-a).toFloat()
            for(c in 0 until channels) {
                fun sample(frame:Int):Float = when {
                    input.channels==channels -> input.samples[frame*input.channels+c]
                    input.channels==1 -> input.samples[frame]
                    channels==1 -> (input.samples[frame*2]+input.samples[frame*2+1])*0.5f
                    else -> 0f
                }
                out[f*channels+c]=sample(a)*(1f-t)+sample(b)*t
            }
        }
        return AudioBuffer(out,rate,channels)
    }

    fun reverse(input:AudioBuffer):AudioBuffer {
        val out=FloatArray(input.samples.size)
        for(frame in 0 until input.frames) for(ch in 0 until input.channels)
            out[frame*input.channels+ch]=input.samples[(input.frames-1-frame)*input.channels+ch]
        return AudioBuffer(out,input.sampleRate,input.channels)
    }

    /** Pitch shift by resampling, matching classic sampler playback behavior. */
    fun pitch(input:AudioBuffer,semitones:Float):AudioBuffer {
        if(input.frames<2)return input
        val ratio=2.0.pow(semitones/12.0)
        val outFrames=max(1,(input.frames/ratio).roundToInt())
        val out=FloatArray(outFrames*input.channels)
        for(frame in 0 until outFrames) {
            val pos=frame*ratio
            val a=pos.toInt().coerceIn(0,input.frames-1)
            val b=(a+1).coerceAtMost(input.frames-1)
            val t=(pos-a).toFloat()
            for(ch in 0 until input.channels) {
                val x=input.samples[a*input.channels+ch]
                val y=input.samples[b*input.channels+ch]
                out[frame*input.channels+ch]=x*(1f-t)+y*t
            }
        }
        return AudioBuffer(out,input.sampleRate,input.channels)
    }

    /** Pitch-preserving WSOLA time stretch for short sampler material. Ratio is output duration / input duration. */
    fun timeStretch(input: AudioBuffer, ratio: Float): AudioBuffer {
        if (input.frames < 128 || !ratio.isFinite() || kotlin.math.abs(ratio-1f) < .005f) return input
        val r=ratio.coerceIn(.5f,2f)
        val channels=input.channels
        val window=(input.sampleRate*.020f).toInt().coerceIn(128,2048).coerceAtMost(input.frames)
        val overlap=window/2
        val hop=(window-overlap).coerceAtLeast(1)
        val analysisHop=(hop/r).roundToInt().coerceAtLeast(1)
        val search=(input.sampleRate*.005f).toInt().coerceAtLeast(8)
        val outFrames=(input.frames*r).roundToInt().coerceAtLeast(window)
        val out=FloatArray(outFrames*channels)
        val first=minOf(window,input.frames,outFrames)
        System.arraycopy(input.samples,0,out,0,first*channels)
        var outPos=hop
        var previousAnalysis=0
        while(outPos<outFrames){
            val maxStart=(input.frames-window).coerceAtLeast(0)
            val predicted=(previousAnalysis+analysisHop).coerceIn(0,maxStart)
            val minCandidate=(predicted-search).coerceAtLeast(0)
            val maxCandidate=(predicted+search).coerceAtMost(maxStart)
            var best=predicted.coerceIn(minCandidate,maxCandidate)
            var bestScore=Double.NEGATIVE_INFINITY
            val compare=minOf(overlap,outFrames-outPos)
            for(candidate in minCandidate..maxCandidate){
                var dot=0.0;var aa=1e-12;var bb=1e-12
                for(i in 0 until compare step 2) for(ch in 0 until channels){
                    val a=out[(outPos+i)*channels+ch].toDouble()
                    val b=input.samples[(candidate+i)*channels+ch].toDouble()
                    dot+=a*b;aa+=a*a;bb+=b*b
                }
                val score=dot/kotlin.math.sqrt(aa*bb)
                if(score>bestScore){bestScore=score;best=candidate}
            }
            val count=minOf(window,outFrames-outPos,input.frames-best)
            for(i in 0 until count) for(ch in 0 until channels){
                val src=input.samples[(best+i)*channels+ch]
                val idx=(outPos+i)*channels+ch
                if(i<overlap && idx<out.size){
                    val mix=(i.toFloat()/overlap).coerceIn(0f,1f)
                    val fade=.5f-.5f*cos(mix*PI.toFloat())
                    out[idx]=out[idx]*(1f-fade)+src*fade
                } else out[idx]=src
            }
            previousAnalysis=best
            outPos+=hop
        }
        return AudioBuffer(out,input.sampleRate,channels)
    }

    /** Applies SlamSampler's instrument envelope, modulation, filter and sample FX to the actual audio buffer. */
    fun processSampler(input: AudioBuffer, settings: SamplerSettings): AudioBuffer {
        if (input.frames == 0) return input
        val out = input.samples.copyOf()
        if (settings.pitchEnvAmount > 0f && input.frames > 2) {
            val phase = DoubleArray(input.frames)
            var acc = 0.0
            for (f in 0 until input.frames) {
                val t = f.toFloat() / (input.frames - 1).coerceAtLeast(1)
                val semi = (settings.pitchEnvStart + (settings.pitchEnvEnd-settings.pitchEnvStart)*t) * settings.pitchEnvAmount
                acc += 2.0.pow(semi / 12.0)
                phase[f] = acc
            }
            val total = phase.last().coerceAtLeast(1.0)
            for (f in 0 until input.frames) {
                val pos = (phase[f] / total * (input.frames - 1)).coerceIn(0.0, (input.frames - 1).toDouble())
                val a = pos.toInt(); val b = (a + 1).coerceAtMost(input.frames - 1); val frac = (pos - a).toFloat()
                for (ch in 0 until input.channels) out[f*input.channels+ch] = input.samples[a*input.channels+ch]*(1f-frac)+input.samples[b*input.channels+ch]*frac
            }
        }
        val rate = input.sampleRate.toFloat()
        val frames = input.frames
        val attack = (settings.attackMs * rate / 1000f).toInt().coerceAtLeast(0)
        val hold = (settings.holdMs * rate / 1000f).toInt().coerceAtLeast(0)
        val decay = (settings.decayMs * rate / 1000f).toInt().coerceAtLeast(0)
        val release = (settings.releaseMs * rate / 1000f).toInt().coerceAtLeast(0)
        val fadeIn = (settings.fadeInMs * rate / 1000f).toInt().coerceAtLeast(0)
        val fadeOut = (settings.fadeOutMs * rate / 1000f).toInt().coerceAtLeast(0)
        var peak = 0f
        var dc = 0f
        for (v in out) { if (abs(v) > peak) peak = abs(v); dc += v }
        if (settings.removeDc && out.isNotEmpty()) {
            dc /= out.size
            for (i in out.indices) out[i] -= dc
        }
        var lpL = 0f; var lpR = 0f
        val cutoff = settings.filterCutoff.coerceIn(20f, rate * .49f)
        fun envAt(frame:Int, attackMs:Float, decayMs:Float, sustain:Float, releaseMs:Float):Float {
            val a=(attackMs*rate/1000f).toInt().coerceAtLeast(0)
            val d=(decayMs*rate/1000f).toInt().coerceAtLeast(0)
            val r=(releaseMs*rate/1000f).toInt().coerceAtLeast(0)
            var v=when { a>0 && frame<a -> frame.toFloat()/a; d>0 && frame<a+d -> 1f-(1f-sustain.coerceIn(0f,1f))*((frame-a).toFloat()/d); else -> sustain.coerceIn(0f,1f) }
            if(r>0) { val releaseStart=(frames-r).coerceAtLeast(0); if(frame>=releaseStart) v *= ((frames-frame).toFloat()/r.coerceAtLeast(1)).coerceIn(0f,1f) }
            return v
        }
        val lfoPhaseStep = (2.0 * Math.PI * settings.lfoRateHz.coerceIn(.01f, 40f) / rate).toFloat()
        for (f in 0 until frames) {
            var env = 1f
            if (settings.envelopeEnabled) {
                env = when {
                    attack > 0 && f < attack -> f.toFloat() / attack
                    f < attack + hold -> 1f
                    decay > 0 && f < attack + hold + decay -> 1f - (1f - settings.sustain.coerceIn(0f,1f)) * ((f-attack-hold).toFloat()/decay)
                    else -> settings.sustain.coerceIn(0f,1f)
                }
                if (release > 0) { val releaseStart=(frames-release).coerceAtLeast(0); if (f >= releaseStart) env *= ((frames-f).toFloat()/release.coerceAtLeast(1)).coerceIn(0f,1f) }
            }
            if (fadeIn > 0 && f < fadeIn) env *= f.toFloat()/fadeIn
            if (fadeOut > 0 && f >= frames-fadeOut) env *= ((frames-f).toFloat()/fadeOut).coerceIn(0f,1f)
            val lfo = if (settings.lfoEnabled) (sin(f*lfoPhaseStep)*settings.lfoDepth.coerceIn(0f,1f)) else 0f
            val panEnv = if(settings.panEnvEnabled) envAt(f,settings.panAttackMs,settings.panDecayMs,settings.panSustain,settings.panReleaseMs) else .5f
            val modA = if(settings.modAEnvEnabled) envAt(f,settings.modAAttackMs,settings.modADecayMs,settings.modASustain,settings.modAReleaseMs) else .5f
            val modB = if(settings.modBEnvEnabled) envAt(f,settings.modBAttackMs,settings.modBDecayMs,settings.modBSustain,settings.modBReleaseMs) else .5f
            for (ch in 0 until input.channels) {
                val idx=f*input.channels+ch
                var x=out[idx]
                if (settings.filterEnabled) {
                    val prev=if(ch==0)lpL else lpR
                    val modCut = if(settings.modAEnvEnabled) (2.0.pow((modA-.5f)*3.0)).toFloat() else 1f
                    val alphaNow=(1f-exp((-2.0*Math.PI*(cutoff*modCut).coerceIn(20f,rate*.49f)/rate).toFloat())).coerceIn(.0001f,.9999f)
                    val low=prev+alphaNow*(x-prev)
                    if(ch==0)lpL=low else lpR=low
                    val resonance=(settings.filterResonance + if(settings.modBEnvEnabled)(modB-.5f)*.8f else 0f).coerceIn(0f,1f)
                    x=when(settings.filterMode){"High-pass"->x-low;"Band-pass"->(x-low)*(1f+resonance*2f)+low*.15f;else->low}
                }
                if(settings.ringModMix>0f && settings.ringModHz>0f){
                    val carrier=sin(2.0*Math.PI*settings.ringModHz.coerceIn(1f,rate*.45f)*f/rate).toFloat()
                    x=x*(1f-settings.ringModMix)+x*carrier*settings.ringModMix
                }
                if(settings.lfoEnabled && settings.lfoTarget=="Filter" && settings.filterEnabled) x *= 1f + lfo*.7f
                else if(settings.lfoEnabled && settings.lfoTarget=="Pan" && input.channels==2) x *= if(ch==0) 1f-lfo*.5f else 1f+lfo*.5f
                else if(settings.lfoEnabled && settings.lfoTarget=="Volume") x *= 1f-lfo
                x *= env
                if(settings.softClip) x=tanh(x*1.8f)/tanh(1.8f)
                if(input.channels==2) {
                    val pan=(settings.pan + if(settings.panEnvEnabled)(panEnv-.5f)*2f else 0f).coerceIn(-1f,1f)
                    x *= if(ch==0) (1f-pan).coerceAtLeast(0f) else (1f+pan).coerceAtLeast(0f)
                }
                out[idx]=if(x.isFinite())x else 0f
            }
        }
        if(settings.stereoSwap && input.channels==2) for(f in 0 until frames){val i=f*2;val a=out[i];out[i]=out[i+1];out[i+1]=a}
        if(settings.normalize && peak>1e-8f){var p=0f;for(v in out)p=max(p,abs(v));if(p>1e-8f)for(i in out.indices)out[i]=(out[i]/p).coerceIn(-1f,1f)}
        val processed=AudioBuffer(out,input.sampleRate,input.channels)
        return if(settings.echoEnabled) applyEcho(processed,settings) else processed
    }


    /** Multi-tap echo with safe bounded tail allocation and simultaneous Fat Mode voices. */
    private fun applyEcho(input:AudioBuffer, s:SamplerSettings):AudioBuffer {
        if (input.frames == 0) return input
        val channels=input.channels.coerceIn(1,2)
        val taps=s.echoCount.coerceIn(1,16)
        val delayFrames=(s.echoTimeMs.coerceIn(1f,2000f)*input.sampleRate/1000f).roundToInt().coerceAtLeast(1)
        val feedback=s.echoFeed.coerceIn(0f,.95f)
        val spread=abs(s.echoPitch).coerceIn(1f,24f)
        val offsets=if(s.echoFatMode) floatArrayOf(-spread,0f,spread) else floatArrayOf(0f)
        val ratios=DoubleArray(offsets.size){2.0.pow(offsets[it]/12.0)}
        val longest=ratios.minOf{ceil(input.frames.toDouble()/it).toLong()}
        val tail=(delayFrames.toLong()*taps+longest).coerceAtMost(input.sampleRate.toLong()*30L)
        val totalFrames=input.frames.toLong()+tail
        require(totalFrames<=Int.MAX_VALUE/channels) { "Echo output is too long; shorten the sample or reduce Echoes/Delay." }
        val out=FloatArray(totalFrames.toInt()*channels)
        val voiceGain=1f/sqrt(offsets.size.toFloat())
        for(v in ratios.indices){
            val ratio=ratios[v]; val frames=minOf(ceil(input.frames.toDouble()/ratio).toLong(),totalFrames).toInt()
            for(f in 0 until frames){
                val pos=f*ratio; if(pos>=input.frames-1) continue
                val lo=pos.toInt().coerceIn(0,input.frames-1); val hi=(lo+1).coerceAtMost(input.frames-1); val frac=(pos-lo).toFloat()
                for(ch in 0 until channels){val a=input.samples[lo*channels+ch];val b=input.samples[hi*channels+ch];out[f*channels+ch]+=(a+(b-a)*frac)*voiceGain}
            }
        }
        for(tap in 1..taps){
            val delay=delayFrames.toLong()*tap; if(delay>=totalFrames) break
            val tapGain=feedback.pow(tap.toFloat())*voiceGain
            for(v in ratios.indices){
                val ratio=ratios[v]; val frames=minOf(ceil(input.frames.toDouble()/ratio).toLong(),totalFrames-delay).toInt()
                for(f in 0 until frames){
                    val pos=f*ratio; if(pos>=input.frames-1) continue
                    val lo=pos.toInt().coerceIn(0,input.frames-1);val hi=(lo+1).coerceAtMost(input.frames-1);val frac=(pos-lo).toFloat();val dst=(delay+f).toInt()
                    for(ch in 0 until channels){
                        val sample=input.samples[lo*channels+ch]*(1f-frac)+input.samples[hi*channels+ch]*frac
                        val dc=if(s.echoPingPong&&channels==2&&tap%2==1)1-ch else ch
                        out[dst*channels+dc]+=sample*tapGain
                    }
                }
            }
        }
        return AudioBuffer(out,input.sampleRate,channels)
    }

    fun autoTrim(a:AudioBuffer):Pair<Float,Float>{
        val threshold=.001f;var first=0;var last=a.frames-1
        while(first<a.frames && peakFrame(a,first)<threshold)first++
        while(last>=first && peakFrame(a,last)<threshold)last--
        return Pair(first.toFloat()/a.sampleRate,(last+1).toFloat()/a.sampleRate)
    }
    private fun peakFrame(a:AudioBuffer,f:Int):Float{var p=0f;for(c in 0 until a.channels)p=max(p,abs(a.samples[f*a.channels+c]));return p}
}

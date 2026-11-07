package com.slamsharper.mobile.dsp

import com.slamsharper.mobile.model.*
import kotlin.math.*
import kotlin.random.Random

object Modules {
    fun process(input:AudioBuffer,m:AudioModule):AudioBuffer{
        if(m.bypassed) return input
        return when(m.type){
            ModuleType.GAIN -> gain(input,m)
            ModuleType.SATURATION -> saturation(input,m)
            ModuleType.COMPRESSOR -> compressor(input,m)
            ModuleType.ENVELOPE -> envelope(input,m)
            ModuleType.PITCH_ENVELOPE -> pitchEnvelope(input,m)
            ModuleType.FREQ_SHIFT -> freqShift(input,m)
            ModuleType.EQ -> eq(input,m)
            ModuleType.STACK -> stack(input,m)
            ModuleType.BLUR -> blur(input,m)
            ModuleType.RESONANCE_SUPPRESSOR -> resonanceSuppressor(input,m)
            ModuleType.DISFLUX -> disflux(input,m)
            ModuleType.PRESS -> press(input,m)
        }
    }

    private fun gain(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val p=m.params;var g=Dsp.dbToLin((p["gain_db"] as Number).toFloat());if(p["polarity_flip"]==true)g=-g
        var y=FloatArray(a.samples.size){a.samples[it]*g}
        if(p["dc_remove"]==true){var mean=0f;for(v in y)mean+=v;mean/=max(1,y.size);for(i in y.indices)y[i]-=mean}
        if(p["normalize"]==true)y=Dsp.normalize(y,(p["normalize_target_db"] as Number).toFloat())
        return a.copy(samples=y)
    }

    private fun saturation(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val drive=(m.params["drive"] as Number).toFloat();val mix=(m.params["mix"] as Number).toFloat();val trim=Dsp.dbToLin((m.params["output_trim_db"] as Number).toFloat())
        val mode=m.params["mode"].toString()
        val y=FloatArray(a.samples.size){i->
            val d=a.samples[i]*(1+drive*20); val w=when(mode){"hard"->d.coerceIn(-1f,1f);"tube"->if(d>=0)1-exp(-d) else -.8f*(1-exp(d));else->tanh(d)}*trim
            a.samples[i]*(1-mix)+w*mix
        }
        return a.copy(samples=y)
    }

    private fun compressor(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val p=m.params;val thr=(p["threshold_db"] as Number).toFloat();val ratio=(p["ratio"] as Number).toFloat().coerceAtLeast(1f)
        val attack=(p["attack_ms"] as Number).toFloat();val release=(p["release_ms"] as Number).toFloat();val makeup=Dsp.dbToLin((p["makeup_gain_db"] as Number).toFloat());val mix=(p["mix"] as Number).toFloat()
        val env=Dsp.onePoleEnvelope(a.samples,a.sampleRate,attack,release);val y=FloatArray(a.samples.size)
        for(i in y.indices){val db=Dsp.linToDb(env[i]);val gr=if(db>thr) Dsp.dbToLin((thr+(db-thr)/ratio)-db) else 1f;y[i]=a.samples[i]*(1-mix)+a.samples[i]*gr*makeup*mix}
        return a.copy(samples=y)
    }

    private fun envelope(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val pts=m.params["points"] as? List<*> ?: return a
        val points=pts.mapNotNull{row->(row as? List<*>)?.takeIf{it.size>=2}?.let{floatArrayOf((it[0] as Number).toFloat(),(it[1] as Number).toFloat())}}.sortedBy{it[0]}
        if(points.size<2)return a
        val pre=Dsp.dbToLin((m.params["pre_gain_db"] as Number).toFloat());val y=FloatArray(a.samples.size)
        // Envelope time is measured in frames, not interleaved samples: stereo must follow the same curve as mono.
        for(frame in 0 until a.frames){
            val t=frame.toFloat()/a.sampleRate
            var j=0
            while(j<points.lastIndex && points[j+1][0]<t) j++
            val p=points[j];val q=points[min(j+1,points.lastIndex)]
            val u=((t-p[0])/max(.000001f,q[0]-p[0])).coerceIn(0f,1f)
            val db=p[1]+(q[1]-p[1])*u
            val gain=pre*Dsp.dbToLin(db)
            for(ch in 0 until a.channels){val i=frame*a.channels+ch;y[i]=a.samples[i]*gain}
        }
        return a.copy(samples=y)
    }

    private fun pitchEnvelope(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val raw=m.params["points"] as? List<*> ?: return a
        val points=raw.mapNotNull { row ->
            (row as? List<*>)?.takeIf { it.size>=2 }?.let {
                floatArrayOf((it[0] as Number).toFloat(),(it[1] as Number).toFloat())
            }
        }.sortedBy { it[0] }
        if(points.size<2 || a.frames<2)return a
        val shift=(m.params["pre_shift_cents"] as? Number)?.toFloat() ?: 0f
        val snap=m.params["snap_semitone"]==true
        val phase=DoubleArray(a.frames)
        var accumulated=0.0
        for(frame in 0 until a.frames){
            val time=frame.toFloat()/a.sampleRate
            var index=0
            while(index<points.lastIndex && points[index+1][0]<time) index++
            val p=points[index];val q=points[min(index+1,points.lastIndex)]
            val blend=((time-p[0])/max(.000001f,q[0]-p[0])).coerceIn(0f,1f)
            var cents=shift+p[1]+(q[1]-p[1])*blend
            if(snap)cents=(cents/100f).roundToInt()*100f
            accumulated+=2.0.pow(cents/1200.0)
            phase[frame]=accumulated
        }
        val total=phase.last().coerceAtLeast(1.0)
        val out=FloatArray(a.samples.size)
        for(frame in 0 until a.frames){
            val pos=(phase[frame]/total*(a.frames-1)).coerceIn(0.0,(a.frames-1).toDouble())
            val lo=pos.toInt();val hi=(lo+1).coerceAtMost(a.frames-1);val frac=(pos-lo).toFloat()
            for(ch in 0 until a.channels)out[frame*a.channels+ch]=a.samples[lo*a.channels+ch]*(1f-frac)+a.samples[hi*a.channels+ch]*frac
        }
        return a.copy(samples=out)
    }

    private fun freqShift(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val cents=(m.params["shift_cents"] as Number).toFloat();val ratio=2f.pow(cents/1200f)
        return a.copy(samples=Dsp.resample(a.samples,ratio))
    }

    private fun eq(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val y=a.samples.copyOf(); val bands=m.params["bands"] as? List<*> ?: emptyList<Any>()
        for(b in bands){
            val bm=b as? Map<*,*> ?: continue
            val freq=(bm["freq"] as? Number)?.toFloat()?:1000f;val gain=(bm["gain"] as? Number)?.toFloat()?:0f
            val q=(bm["q"] as? Number)?.toFloat()?:1f
            val bi=Biquad.peaking(a.sampleRate,freq,gain,q)
            bi.processInPlace(y)
        }
        val gg=Dsp.dbToLin((m.params["global_gain_db"] as Number).toFloat());for(i in y.indices)y[i]*=gg
        return a.copy(samples=y)
    }

    private fun stack(a:AudioBuffer,m:AudioModule):AudioBuffer{
        if(a.frames<2)return a
        val count=(m.params["copy_count"] as? Number)?.toInt()?.coerceIn(0,12)?:0
        if(count==0)return a
        val interval=((m.params["pitch_interval_cents"] as? Number)?.toFloat()?:700f).coerceIn(-2400f,2400f)
        val fat=m.params["fat_mode"]==true
        val offsets=ArrayList<Float>(1+count*(if(fat)2 else 1));offsets.add(0f)
        if(fat)for(i in count downTo 1)offsets.add(-interval*i)
        for(i in 1..count)offsets.add(interval*i)
        val ratios=DoubleArray(offsets.size){2.0.pow(offsets[it]/1200.0)}
        val frames=ratios.maxOf{ceil(a.frames.toDouble()/it).toLong()}.coerceAtMost(Int.MAX_VALUE.toLong()/a.channels).toInt()
        val out=FloatArray(frames*a.channels);val voiceGain=1f/sqrt(offsets.size.toFloat())
        for(ratio in ratios){
            val voiceFrames=minOf(ceil(a.frames.toDouble()/ratio).toLong(),frames.toLong()).toInt()
            for(f in 0 until voiceFrames){val pos=f*ratio;if(pos>=a.frames-1)continue
                val lo=pos.toInt().coerceIn(0,a.frames-1);val hi=(lo+1).coerceAtMost(a.frames-1);val frac=(pos-lo).toFloat()
                for(ch in 0 until a.channels){val i=f*a.channels+ch;out[i]+=(a.samples[lo*a.channels+ch]*(1f-frac)+a.samples[hi*a.channels+ch]*frac)*voiceGain}
            }
        }
        val master=Dsp.dbToLin((m.params["master_gain_db"] as? Number)?.toFloat()?:-6f)
        for(i in out.indices)out[i]*=master
        return a.copy(samples=out)
    }

    private fun blur(a:AudioBuffer,m:AudioModule):AudioBuffer{
        val p=m.params;val blend=(p["blend"] as Number).toFloat();val tail=(p["tail_length_ms"] as Number).toFloat();val decay=(p["tail_decay_db"] as Number).toFloat()
        val rng=Random((p["seed"] as Number).toInt());val y=a.samples.copyOf();val noise=FloatArray(y.size){rng.nextFloat()*2-1}
        for(i in y.indices)y[i]=y[i]*(1-blend)+noise[i]*blend
        if(tail>0){val extra=(a.sampleRate*tail/1000).roundToInt();val out=FloatArray(y.size+extra);y.copyInto(out);val start=y.lastOrNull()?:0f;for(i in 0 until extra)out[y.size+i]=start*Dsp.dbToLin(decay*i/max(1,extra));return a.copy(samples=out)}
        return a.copy(samples=y)
    }


    /**
     * Press: native three-band maximiser inspired by the supplied Lemon Studio Press effect.
     * Complementary one-pole crossovers keep the three bands phase-coherent and sum to the input.
     */
    private fun press(a: AudioBuffer, m: AudioModule): AudioBuffer {
        if(a.frames==0)return a
        val p=m.params
        val lowHz=((p["low_crossover_hz"] as? Number)?.toFloat()?:200f).coerceIn(30f,a.sampleRate*.35f)
        val highHz=((p["high_crossover_hz"] as? Number)?.toFloat()?:3000f).coerceIn(lowHz+10f,a.sampleRate*.48f)
        val thresholds=floatArrayOf((p["low_threshold_db"] as? Number)?.toFloat()?:-8f,(p["mid_threshold_db"] as? Number)?.toFloat()?:-6f,(p["high_threshold_db"] as? Number)?.toFloat()?:-5f)
        val ratios=floatArrayOf((p["low_ratio"] as? Number)?.toFloat()?:4f,(p["mid_ratio"] as? Number)?.toFloat()?:4f,(p["high_ratio"] as? Number)?.toFloat()?:4f)
        for(i in ratios.indices)ratios[i]=ratios[i].coerceAtLeast(1f)
        val attack=((p["attack_ms"] as? Number)?.toFloat()?:2f).coerceAtLeast(.1f)
        val release=((p["release_ms"] as? Number)?.toFloat()?:86f).coerceAtLeast(1f)
        val ceiling=Dsp.dbToLin((p["master_ceiling_db"] as? Number)?.toFloat()?:-.2f)
        val mix=((p["mix"] as? Number)?.toFloat()?:1f).coerceIn(0f,1f)
        val outputGain=Dsp.dbToLin((p["output_gain_db"] as? Number)?.toFloat()?:0f)
        val lowAlpha=1f-exp((-2.0*Math.PI*lowHz/a.sampleRate).toFloat())
        val highAlpha=1f-exp((-2.0*Math.PI*highHz/a.sampleRate).toFloat())
        val attackCoeff=exp((-1f/(attack*.001f*a.sampleRate)).toDouble()).toFloat()
        val releaseCoeff=exp((-1f/(release*.001f*a.sampleRate)).toDouble()).toFloat()
        val lowState=FloatArray(a.channels);val highState=FloatArray(a.channels);val envelope=FloatArray(3);val output=FloatArray(a.samples.size)
        val lowBand=FloatArray(a.channels);val midBand=FloatArray(a.channels);val highBand=FloatArray(a.channels)
        for(frame in 0 until a.frames){
            var lowPeak=0f;var midPeak=0f;var highPeak=0f;val base=frame*a.channels
            for(ch in 0 until a.channels){
                val x=a.samples[base+ch];lowState[ch]+=lowAlpha*(x-lowState[ch]);val low=lowState[ch];val remainder=x-low
                highState[ch]+=highAlpha*(remainder-highState[ch]);val high=highState[ch];val mid=remainder-high
                lowBand[ch]=low;midBand[ch]=mid;highBand[ch]=high
                lowPeak=max(lowPeak,abs(low));midPeak=max(midPeak,abs(mid));highPeak=max(highPeak,abs(high))
            }
            var wetL=0f;var wetR=0f
            for(band in 0..2){
                val detector=when(band){0->lowPeak;1->midPeak;else->highPeak}
                val coeff=if(detector>envelope[band])attackCoeff else releaseCoeff
                envelope[band]=coeff*envelope[band]+(1f-coeff)*detector
                val levelDb=Dsp.linToDb(envelope[band]);val targetDb=if(levelDb>thresholds[band])thresholds[band]+(levelDb-thresholds[band])/ratios[band] else levelDb
                val gain=Dsp.dbToLin(targetDb-levelDb);val bandSamples=when(band){0->lowBand;1->midBand;else->highBand}
                for(ch in 0 until a.channels){val v=bandSamples[ch]*gain;if(ch==0)wetL+=v else wetR+=v}
            }
            for(ch in 0 until a.channels){val wet=if(ch==0)wetL else wetR;val limited=(wet*outputGain).coerceIn(-ceiling,ceiling);output[base+ch]=a.samples[base+ch]*(1f-mix)+limited*mix}
        }
        return a.copy(samples=output)
    }

    /**
     * Disflux-style all-pass diffusion FX.
     * The parameter ranges and logarithmic filter-frequency distribution follow
     * the supplied Disflux source; this is a mobile-safe per-buffer implementation.
     */
    private fun disflux(a: AudioBuffer, m: AudioModule): AudioBuffer {
        if (a.frames == 0) return a
        val p = m.params
        val amount = ((p["amount"] as? Number)?.toInt() ?: 1).coerceIn(0, 256)
        if (amount == 0) return a
        val center = ((p["frequency"] as? Number)?.toFloat() ?: 800f)
            .coerceIn(20f, min(20000f, a.sampleRate * .49f))
        val spread = ((p["spread"] as? Number)?.toFloat() ?: 2000f).coerceIn(0f, 10000f)
        val q = ((p["pinch"] as? Number)?.toFloat() ?: 1f).coerceIn(.5f, 16f)
        val mix = ((p["mix"] as? Number)?.toFloat() ?: 1f).coerceIn(0f, 1f)
        val nyquist = a.sampleRate * .49f
        val low = (center - spread * .5f).coerceIn(20f, nyquist)
        val high = (center + spread * .5f).coerceIn(low, nyquist)
        val logLow = ln(low.toDouble())
        val logDelta = ln(high.toDouble()) - logLow
        val out = a.samples.copyOf()

        for (stage in 0 until amount) {
            val position = if (amount == 1) .5 else stage.toDouble() / (amount - 1)
            val frequency = exp(logLow + logDelta * position).toFloat()
                .coerceIn(20f, nyquist)
            val omega = (2.0 * Math.PI * frequency / a.sampleRate).toFloat()
            val cosine = cos(omega)
            val alpha = sin(omega) / (2f * q)
            val a0 = 1f + alpha
            val b0 = (1f - alpha) / a0
            val b1 = (-2f * cosine) / a0
            val b2 = (1f + alpha) / a0
            val a1 = b1
            val a2 = (1f - alpha) / a0

            val x1 = FloatArray(a.channels)
            val x2 = FloatArray(a.channels)
            val y1 = FloatArray(a.channels)
            val y2 = FloatArray(a.channels)
            for (frame in 0 until a.frames) {
                for (channel in 0 until a.channels) {
                    val index = frame * a.channels + channel
                    val x = out[index]
                    val y = b0 * x + b1 * x1[channel] + b2 * x2[channel] -
                        a1 * y1[channel] - a2 * y2[channel]
                    x2[channel] = x1[channel]
                    x1[channel] = x
                    y2[channel] = y1[channel]
                    y1[channel] = if (y.isFinite()) y else 0f
                    out[index] = y1[channel]
                }
            }
        }
        for (i in out.indices) out[i] = a.samples[i] * (1f - mix) + out[i] * mix
        return a.copy(samples = out)
    }

    private fun resonanceSuppressor(a:AudioBuffer,m:AudioModule):AudioBuffer{
        // Mobile-safe approximation of the desktop spectral suppressor: detect dominant short-window bins and apply narrow notches.
        val depth=(m.params["depth"] as Number?)?.toFloat()?:.3f;if(depth<=0)return a
        val x=a.samples;val y=x.copyOf();val n=min(x.size,4096);if(n<32)return a
        val mag=Dsp.fftMagnitude(x.copyOfRange(0,n),256);val sorted=mag.indices.sortedByDescending{mag[it]}.take(6)
        for(bin in sorted){val f=bin.toFloat()/256f*(a.sampleRate/2f);val w=(2f*Math.PI*max(20f,f/40f)/a.sampleRate).toFloat();var z=0f;var z1=0f;for(i in y.indices){val input=y[i];z=input - 2*cos(w)*z1 + z*0f;z1=z; y[i]=input*(1-depth*.25f)}} 
        return a.copy(samples=y)
    }
}

private class Biquad(private val b0:Float,private val b1:Float,private val b2:Float,private val a1:Float,private val a2:Float){
    private var x1=0f;private var x2=0f;private var y1=0f;private var y2=0f
    fun processInPlace(x:FloatArray){for(i in x.indices){val y=b0*x[i]+b1*x1+b2*x2-a1*y1-a2*y2;x2=x1;x1=x[i];y2=y1;y1=y;x[i]=y}}
    companion object{
        fun peaking(sr:Int,freq:Float,gainDb:Float,q:Float):Biquad{
            val A=10f.pow(gainDb/40f);val w=(2f*Math.PI*freq/sr).toFloat();val alpha=sin(w)/(2f*q.coerceAtLeast(.05f));val c=cos(w)
            val b0=1+alpha*A;val b1=-2*c;val b2=1-alpha*A;val a0=1+alpha/A;val a1=-2*c;val a2=1-alpha/A
            return Biquad(b0/a0,b1/a0,b2/a0,a1/a0,a2/a0)
        }
    }
}

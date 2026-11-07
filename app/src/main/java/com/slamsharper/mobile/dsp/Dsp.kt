package com.slamsharper.mobile.dsp

import kotlin.math.*

object Dsp {
    fun dbToLin(db:Float)=10f.pow(db/20f)
    fun linToDb(x:Float)=20f*log10(max(1e-9f,abs(x)))
    fun resample(x:FloatArray, ratio:Float):FloatArray {
        if(x.isEmpty() || ratio<=0f) return x.copyOf()
        val n=max(1,(x.size/ratio).roundToInt())
        val y=FloatArray(n)
        for(i in y.indices){
            val pos=i*ratio; val a=floor(pos).toInt().coerceAtMost(x.lastIndex); val b=(a+1).coerceAtMost(x.lastIndex); val t=pos-a
            y[i]=x[a]*(1-t)+x[b]*t
        }
        return y
    }
    fun mix(a:FloatArray,b:FloatArray,wet:Float):FloatArray{
        val n=min(a.size,b.size); val y=a.copyOf()
        for(i in 0 until n)y[i]=a[i]*(1-wet)+b[i]*wet
        return y
    }
    fun mono(x:FloatArray,channels:Int):FloatArray{
        if(channels==1)return x.copyOf()
        val n=x.size/channels; val y=FloatArray(n)
        for(i in 0 until n){var s=0f;for(c in 0 until channels)s+=x[i*channels+c];y[i]=s/channels}
        return y
    }
    fun interleave(left:FloatArray,right:FloatArray):FloatArray{
        val n=max(left.size,right.size);val y=FloatArray(n*2)
        for(i in 0 until n){y[2*i]=if(i<left.size)left[i] else 0f;y[2*i+1]=if(i<right.size)right[i] else 0f};return y
    }
    fun onePoleEnvelope(x:FloatArray,sr:Int,attackMs:Float,releaseMs:Float):FloatArray{
        val y=FloatArray(x.size); var env=0f
        val a=exp(-1f/(max(1f,attackMs)*.001f*sr)); val r=exp(-1f/(max(1f,releaseMs)*.001f*sr))
        for(i in x.indices){val v=abs(x[i]); env=if(v>env)a*env+(1-a)*v else r*env+(1-r)*v;y[i]=env};return y
    }
    fun normalize(x:FloatArray,targetDb:Float):FloatArray{
        val peak=x.maxOfOrNull{abs(it)}?:0f;if(peak<1e-9f)return x.copyOf();val k=dbToLin(targetDb)/peak;return FloatArray(x.size){x[it]*k}
    }
    fun fftMagnitude(x:FloatArray, bins:Int):FloatArray{
        val n=min(x.size,2048); if(n<4)return FloatArray(bins)
        val out=FloatArray(bins)
        for(k in 0 until bins){
            val f=k.toDouble()/bins
            var re=0.0;var im=0.0
            for(i in 0 until n){val a=2*Math.PI*f*i/n;re+=x[i]*cos(a);im-=x[i]*sin(a)}
            out[k]=sqrt(re*re+im*im).toFloat()/n
        }
        return out
    }
}

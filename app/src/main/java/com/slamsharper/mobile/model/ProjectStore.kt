package com.slamsharper.mobile.model

import org.json.JSONArray
import org.json.JSONObject

object ProjectStore {
    fun encode(chain:List<AudioModule>, trimStart:Float, trimEnd:Float, sampler: SamplerSettings = SamplerSettings()):String{
        val o=JSONObject();o.put("version",2);o.put("trimStart",trimStart);o.put("trimEnd",trimEnd)
        val sm=JSONObject()
        sm.put("attackMs",sampler.attackMs);sm.put("holdMs",sampler.holdMs);sm.put("decayMs",sampler.decayMs);sm.put("sustain",sampler.sustain);sm.put("releaseMs",sampler.releaseMs);sm.put("envelopeEnabled",sampler.envelopeEnabled)
        sm.put("pitchEnvStart",sampler.pitchEnvStart);sm.put("pitchEnvEnd",sampler.pitchEnvEnd);sm.put("pitchEnvAmount",sampler.pitchEnvAmount)
        sm.put("filterEnabled",sampler.filterEnabled);sm.put("filterCutoff",sampler.filterCutoff);sm.put("filterResonance",sampler.filterResonance);sm.put("filterMode",sampler.filterMode)
        sm.put("lfoEnabled",sampler.lfoEnabled);sm.put("lfoRateHz",sampler.lfoRateHz);sm.put("lfoDepth",sampler.lfoDepth);sm.put("lfoTarget",sampler.lfoTarget)
        sm.put("fadeInMs",sampler.fadeInMs);sm.put("fadeOutMs",sampler.fadeOutMs);sm.put("normalize",sampler.normalize);sm.put("removeDc",sampler.removeDc);sm.put("ringModHz",sampler.ringModHz);sm.put("ringModMix",sampler.ringModMix);sm.put("softClip",sampler.softClip);sm.put("stereoSwap",sampler.stereoSwap);sm.put("pan",sampler.pan);sm.put("rootNote",sampler.rootNote);sm.put("loopStart",sampler.loopStart);sm.put("loopEnd",sampler.loopEnd);sm.put("pingPong",sampler.pingPong)
        sm.put("panEnvEnabled",sampler.panEnvEnabled);sm.put("panAttackMs",sampler.panAttackMs);sm.put("panDecayMs",sampler.panDecayMs);sm.put("panSustain",sampler.panSustain);sm.put("panReleaseMs",sampler.panReleaseMs)
        sm.put("modAEnvEnabled",sampler.modAEnvEnabled);sm.put("modAAttackMs",sampler.modAAttackMs);sm.put("modADecayMs",sampler.modADecayMs);sm.put("modASustain",sampler.modASustain);sm.put("modAReleaseMs",sampler.modAReleaseMs)
        sm.put("modBEnvEnabled",sampler.modBEnvEnabled);sm.put("modBAttackMs",sampler.modBAttackMs);sm.put("modBDecayMs",sampler.modBDecayMs);sm.put("modBSustain",sampler.modBSustain);sm.put("modBReleaseMs",sampler.modBReleaseMs)
        sm.put("stretchEnabled",sampler.stretchEnabled);sm.put("stretchRatio",sampler.stretchRatio)
        sm.put("echoEnabled",sampler.echoEnabled);sm.put("echoFeed",sampler.echoFeed);sm.put("echoPitch",sampler.echoPitch);sm.put("echoTimeMs",sampler.echoTimeMs);sm.put("echoCount",sampler.echoCount);sm.put("echoPingPong",sampler.echoPingPong);sm.put("echoFatMode",sampler.echoFatMode)
        o.put("sampler",sm)
        val a=JSONArray();for(m in chain){val x=JSONObject();x.put("id",m.id);x.put("type",m.type.name);x.put("bypassed",m.bypassed);x.put("params",JSONObject(m.params as Map<*,*>));a.put(x)}
        o.put("chain",a);return o.toString(2)
    }
    fun decodeSampler(s:String):SamplerSettings {
        val sm=JSONObject(s).optJSONObject("sampler") ?: return SamplerSettings()
        return SamplerSettings(
            attackMs=sm.optDouble("attackMs",0.0).toFloat(),holdMs=sm.optDouble("holdMs",0.0).toFloat(),decayMs=sm.optDouble("decayMs",80.0).toFloat(),sustain=sm.optDouble("sustain",1.0).toFloat(),releaseMs=sm.optDouble("releaseMs",40.0).toFloat(),envelopeEnabled=sm.optBoolean("envelopeEnabled",false),
            pitchEnvStart=sm.optDouble("pitchEnvStart",0.0).toFloat(),pitchEnvEnd=sm.optDouble("pitchEnvEnd",0.0).toFloat(),pitchEnvAmount=sm.optDouble("pitchEnvAmount",0.0).toFloat(),
            filterEnabled=sm.optBoolean("filterEnabled",false),filterCutoff=sm.optDouble("filterCutoff",18000.0).toFloat(),filterResonance=sm.optDouble("filterResonance",0.0).toFloat(),filterMode=sm.optString("filterMode","Low-pass"),
            lfoEnabled=sm.optBoolean("lfoEnabled",false),lfoRateHz=sm.optDouble("lfoRateHz",4.0).toFloat(),lfoDepth=sm.optDouble("lfoDepth",0.0).toFloat(),lfoTarget=sm.optString("lfoTarget","Volume"),
            fadeInMs=sm.optDouble("fadeInMs",0.0).toFloat(),fadeOutMs=sm.optDouble("fadeOutMs",0.0).toFloat(),normalize=sm.optBoolean("normalize",false),removeDc=sm.optBoolean("removeDc",false),ringModHz=sm.optDouble("ringModHz",0.0).toFloat(),ringModMix=sm.optDouble("ringModMix",0.0).toFloat(),softClip=sm.optBoolean("softClip",false),stereoSwap=sm.optBoolean("stereoSwap",false),pan=sm.optDouble("pan",0.0).toFloat(),rootNote=sm.optInt("rootNote",60),loopStart=sm.optDouble("loopStart",0.0).toFloat(),loopEnd=sm.optDouble("loopEnd",1.0).toFloat(),pingPong=sm.optBoolean("pingPong",false),
            panEnvEnabled=sm.optBoolean("panEnvEnabled",false),panAttackMs=sm.optDouble("panAttackMs",0.0).toFloat(),panDecayMs=sm.optDouble("panDecayMs",100.0).toFloat(),panSustain=sm.optDouble("panSustain",.5).toFloat(),panReleaseMs=sm.optDouble("panReleaseMs",40.0).toFloat(),
            modAEnvEnabled=sm.optBoolean("modAEnvEnabled",false),modAAttackMs=sm.optDouble("modAAttackMs",0.0).toFloat(),modADecayMs=sm.optDouble("modADecayMs",100.0).toFloat(),modASustain=sm.optDouble("modASustain",1.0).toFloat(),modAReleaseMs=sm.optDouble("modAReleaseMs",40.0).toFloat(),
            modBEnvEnabled=sm.optBoolean("modBEnvEnabled",false),modBAttackMs=sm.optDouble("modBAttackMs",0.0).toFloat(),modBDecayMs=sm.optDouble("modBDecayMs",100.0).toFloat(),modBSustain=sm.optDouble("modBSustain",0.0).toFloat(),modBReleaseMs=sm.optDouble("modBReleaseMs",40.0).toFloat(),stretchEnabled=sm.optBoolean("stretchEnabled",false),stretchRatio=sm.optDouble("stretchRatio",1.0).toFloat(),
            echoEnabled=sm.optBoolean("echoEnabled",false),echoFeed=sm.optDouble("echoFeed",.35).toFloat(),echoPitch=sm.optDouble("echoPitch",7.0).toFloat(),echoTimeMs=sm.optDouble("echoTimeMs",180.0).toFloat(),echoCount=sm.optInt("echoCount",4),echoPingPong=sm.optBoolean("echoPingPong",false),echoFatMode=sm.optBoolean("echoFatMode",false)
        )
    }
    private fun decodeJsonValue(value:Any?):Any? = when(value) {
        is JSONArray -> (0 until value.length()).map { decodeJsonValue(value.get(it)) }
        is JSONObject -> value.keys().asSequence().associateWith { decodeJsonValue(value.get(it)) }.toMutableMap()
        JSONObject.NULL -> null
        else -> value
    }

    fun decode(s:String):Pair<MutableList<AudioModule>,Pair<Float,Float>>{
        val o=JSONObject(s);val a=o.getJSONArray("chain");val list=mutableListOf<AudioModule>()
        for(i in 0 until a.length()){val x=a.getJSONObject(i);val type=ModuleType.valueOf(x.getString("type"));val p=defaults(type);val po=x.optJSONObject("params");if(po!=null)for(k in po.keys()){val decoded=decodeJsonValue(po.get(k));if(decoded!=null)p[k]=decoded};list+=AudioModule(x.optString("id"),type,x.optBoolean("bypassed"),p)}
        return Pair(list,Pair(o.optDouble("trimStart",0.0).toFloat(),o.optDouble("trimEnd",0.0).toFloat()))
    }
}

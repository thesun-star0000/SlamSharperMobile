package com.slamsharper.mobile

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.slamsharper.mobile.model.*
import com.slamsharper.mobile.ui.MainViewModel
import com.slamsharper.mobile.ui.UiState
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.exp

private val Bg=Color(0xFF14262C)
private val Panel=Color(0xFF1B3037)
private val TextHi=Color(0xFFD6F7FC)
private val TextLo=Color(0xFF9BCBD4)
private val DefaultAccent=Color(0xFF8DEBF4)

class MainActivity:ComponentActivity(){
    private val vm by viewModels<MainViewModel>()
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);window.statusBarColor=android.graphics.Color.rgb(20,38,44);window.navigationBarColor=android.graphics.Color.rgb(14,27,32);setContent{SlamApp(vm)}}
}

@Composable fun SlamTheme(accent:Color,content:@Composable ()->Unit){
    MaterialTheme(colorScheme=darkColorScheme(primary=accent,secondary=TextLo,background=Bg,surface=Panel,onSurface=TextHi),content=content)
}

private fun tr(ru:Boolean,ruText:String,enText:String)=if(ru)ruText else enText

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun SlamApp(vm:MainViewModel){
    val s by vm.state.collectAsState()
    val context= LocalContext.current
    val prefs= remember { context.getSharedPreferences("slamsharper_prefs",0) }
    var ru by remember { mutableStateOf(prefs.getBoolean("ru",false)) }
    var accentArgb by remember { mutableIntStateOf(prefs.getInt("accent",DefaultAccent.toArgb())) }
    var savedPalette by remember { mutableStateOf(prefs.getString("palette","")!!.split(",").mapNotNull{it.toIntOrNull()}) }
    var showTheme by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showAdd by remember{mutableStateOf(false)}
    val accent=Color(accentArgb)
    val open=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->u?.let(vm::load)}
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/wav")){u:Uri?->u?.let{vm.export(it,32)}}
    val save=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){u:Uri?->u?.let(vm::saveProject)}
    val loadProject=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){u:Uri?->u?.let(vm::loadProject)}
    SlamTheme(accent){
        Scaffold(containerColor=Bg,topBar={
            TopAppBar(
                title={Text("SlamSharperMobile",fontSize=15.sp,maxLines=1,softWrap=false,fontWeight=FontWeight.Black,letterSpacing=.35.sp,color=TextHi)},
                actions={
                    TextButton(contentPadding=PaddingValues(horizontal=4.dp,vertical=0.dp),onClick={ru=!ru;prefs.edit().putBoolean("ru",ru).apply()}){Text(if(ru)"RU" else "EN",fontSize=11.sp,color=accent)}
                    TextButton(contentPadding=PaddingValues(horizontal=5.dp,vertical=0.dp),enabled=s.rendered!=null,onClick=vm::togglePlayback){Text(if(s.isPlaying)"Ⅱ" else "▶",fontSize=13.sp,color=accent)}
                    IconButton(onClick={showTheme=true},modifier=Modifier.size(40.dp)){Text("◉",fontSize=18.sp,color=accent)}
                    Box {
                        IconButton(onClick={showTopMenu=true},modifier=Modifier.size(40.dp)){Text("☰",fontSize=19.sp,color=accent)}
                        DropdownMenu(expanded=showTopMenu,onDismissRequest={showTopMenu=false}) {
                            DropdownMenuItem(text={Text(tr(ru,"Загрузить сэмпл","Load sample"))},onClick={showTopMenu=false;open.launch(arrayOf("audio/wav","audio/x-wav","audio/*"))})
                            DropdownMenuItem(text={Text(tr(ru,"Экспорт WAV","Export WAV"))},enabled=s.source!=null,onClick={showTopMenu=false;export.launch("slamsharper.wav")})
                            DropdownMenuItem(text={Text(tr(ru,"Сохранить проект","Save project"))},onClick={showTopMenu=false;save.launch("slamsharper.json")})
                        }
                    }
                },
                colors=TopAppBarDefaults.topAppBarColors(containerColor=Bg)
            )
        }){pad->
            Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal=12.dp, vertical=8.dp)){
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                    Text(tr(ru,"АУДИО ДВИЖОК","AUDIO ENGINE"),fontSize=11.sp,letterSpacing=2.sp,color=TextLo,fontWeight=FontWeight.Bold)
                    Row { TextButton(onClick={vm.undo()},enabled=s.undo.isNotEmpty()){Text("↶")};TextButton(onClick={vm.redo()},enabled=s.redo.isNotEmpty()){Text("↷")};TextButton(onClick={loadProject.launch(arrayOf("application/json","text/*"))}){Text(tr(ru,"ПРОЕКТ","PROJECT"))} }
                }
                if(s.message!=null)Text(s.message!!,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.secondary)
                if(s.source==null) EmptyState(ru){open.launch(arrayOf("audio/wav","audio/x-wav","audio/*"))}
                else {
                    SpectrumCard(accent,ru)
                    Spacer(Modifier.height(10.dp))
                    SamplerCard(s,vm,accent,ru)
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        Text(tr(ru,"ЦЕПОЧКА ОБРАБОТКИ","EFFECT CHAIN"),fontWeight=FontWeight.Black,letterSpacing=1.5.sp,color=TextLo)
                        Row { TextButton(onClick=vm::autoTrim){Text(tr(ru,"АВТО-ТРИМ","AUTO TRIM"))};TextButton(onClick={showAdd=true}){Text("+ FX",color=accent)} }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                        s.chain.forEach{m->FilterChip(selected=m.id==s.selectedId,onClick={vm.select(m.id)},label={Text(moduleTitle(m.type,ru))},leadingIcon={Text(if(m.bypassed)"Ⅱ" else "●",color=accent)})}
                    }
                    val selected=s.chain.firstOrNull{it.id==s.selectedId}
                    if(selected!=null) {
                        Card(Modifier.fillMaxWidth().padding(top=8.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Panel)){
                            Column(Modifier.padding(14.dp)){
                                Row(verticalAlignment=Alignment.CenterVertically){
                                    Text(moduleTitle(selected.type,ru),fontSize=19.sp,fontWeight=FontWeight.Bold,color=TextHi)
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick={vm.move(-1)}){Text("←")}
                                    TextButton(onClick={vm.move(1)}){Text("→")}
                                    TextButton(onClick=vm::bypass){Text(if(selected.bypassed)"ON" else "BYPASS")}
                                    TextButton(onClick=vm::remove){Text("×",color=Color(0xFFFF8F8F))}
                                }
                                ModuleControls(selected,vm,ru)
                            }
                        }
                    } else {
                        Card(Modifier.fillMaxWidth().padding(top=8.dp),colors=CardDefaults.cardColors(containerColor=Panel)){Box(Modifier.fillMaxWidth().padding(24.dp),contentAlignment=Alignment.Center){Text(tr(ru,"Добавь эффект в цепочку обработки","Add an effect to the processing chain"),color=TextLo)}}
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
            if(showAdd)AddModuleDialog(ru,onDismiss={showAdd=false},onAdd={vm.add(it);showAdd=false})
            if(showTheme)ThemeDialog(accent,savedPalette,onDismiss={showTheme=false},onSelect={accentArgb=it;prefs.edit().putInt("accent",it).apply()},onSave={color->savedPalette=(savedPalette+color).distinct().takeLast(16);prefs.edit().putString("palette",savedPalette.joinToString(",")).apply()})
        }
    }
}

@Composable fun EmptyState(ru:Boolean,onLoad:()->Unit){
    Card(Modifier.fillMaxWidth().padding(top=24.dp),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(22.dp)){
        Column(Modifier.fillMaxWidth().padding(28.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Text("SlamSharper",fontSize=27.sp,fontWeight=FontWeight.Black,letterSpacing=2.sp,color=TextHi)
            Text(tr(ru,"Сэмплер и процессор ударных","Sampler & transient sound workstation"),color=TextLo)
            Spacer(Modifier.height(20.dp));Button(onClick=onLoad){Text(tr(ru,"ЗАГРУЗИТЬ СЭМПЛ","LOAD SAMPLE"))}
            Spacer(Modifier.height(8.dp));Text("WAV • 16 / 24 / 32-bit • Offline",fontSize=11.sp,color=TextLo)
        }
    }
}

@Composable fun SpectrumCard(accent:Color,ru:Boolean){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(12.dp)){
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(tr(ru,"ЧАСТОТНЫЙ ОТКЛИК","FREQUENCY RESPONSE"),fontSize=11.sp,letterSpacing=1.5.sp,color=TextLo,fontWeight=FontWeight.Bold);Text("0 dB",color=TextLo,fontSize=12.sp)}
            Canvas(Modifier.fillMaxWidth().height(190.dp)){
                val mid=size.height*.52f
                for(i in 0..10){val x=size.width*i/10f;drawLine(Color(0xFF35515A),Offset(x,0f),Offset(x,size.height),1f)}
                for(i in 0..4){val y=size.height*i/4f;drawLine(Color(0xFF35515A),Offset(0f,y),Offset(size.width,y),1f)}
                drawLine(Color(0xFF8ABBC4),Offset(0f,mid),Offset(size.width,mid),1.2f)
                val centers=listOf(.04f,.11f,.19f,.29f,.43f,.56f,.68f,.79f,.89f,.97f)
                centers.forEachIndexed{idx,c->
                    val width=.025f+idx*.002f
                    val height=size.height*(.16f+(.1f* sin(idx*.8f).let{abs(it)}))
                    val path=androidx.compose.ui.graphics.Path().apply{
                        moveTo((c-width*2)*size.width,mid)
                        cubicTo((c-width)*size.width,mid-height,(c+width*.5f)*size.width,mid-height*.75f,(c+width*2)*size.width,mid)
                        cubicTo((c+width*.5f)*size.width,mid+height*.3f,(c-width)*size.width,mid+height*.2f,(c-width*2)*size.width,mid)
                        close()
                    }
                    drawPath(path,accent.copy(alpha=.09f+idx*.012f))
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){listOf("30","100","300","1K","3K","10K").forEach{Text(it,fontSize=10.sp,color=TextLo)}}
        }
    }
}

@Composable fun SamplerCard(s:UiState,vm:MainViewModel,accent:Color,ru:Boolean){
    var tab by remember { mutableIntStateOf(0) }
    val names=if(ru) listOf("Сэмпл","Огибающая","Фильтр","FX","Клавиши") else listOf("Sample","Envelope","Filter","FX","Keys")
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=Panel),shape=RoundedCornerShape(18.dp)){
        Column(Modifier.padding(12.dp)){
            Row(verticalAlignment=Alignment.CenterVertically){
                Text("SLAM",fontSize=10.sp,letterSpacing=1.5.sp,color=accent,fontWeight=FontWeight.Black)
                Spacer(Modifier.width(6.dp));Text("Sampler",fontSize=17.sp,fontWeight=FontWeight.Black,color=TextHi)
                Spacer(Modifier.weight(1f));Text("${s.source?.sampleRate} Hz",fontSize=10.sp,color=TextLo)
            }
            ScrollableTabRow(selectedTabIndex=tab,containerColor=Panel,contentColor=accent,edgePadding=0.dp){
                names.forEachIndexed{i,n->Tab(selected=tab==i,onClick={tab=i},text={Text(n,fontSize=11.sp)})}
            }
            when(tab){
                0 -> {
                    Spacer(Modifier.height(8.dp));Waveform(s.rendered?.samples?:FloatArray(0),accent,Modifier.fillMaxWidth().height(92.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                        Text(s.source?.let{"${it.channels} CH • ${"%.2f".format(it.duration)} s"}?:"",fontSize=10.sp,color=TextLo)
                        Text(if(ru)"Прослушивание — сверху" else "Audition control is in the top bar",fontSize=9.sp,color=TextLo)
                    }
                    val duration=(s.source?.duration?:1f).coerceAtLeast(.002f)
                    Text(tr(ru,"НАЧАЛО","START")+"  ${"%.3f".format(s.trimStart)} s",fontSize=10.sp,color=TextLo)
                    Slider(s.trimStart.coerceIn(0f,duration-.001f),{vm.setTrim(it,s.trimEnd.coerceAtLeast(it+.001f))},valueRange=0f..(duration-.001f))
                    Text(tr(ru,"КОНЕЦ","END")+"  ${"%.3f".format(s.trimEnd)} s",fontSize=10.sp,color=TextLo)
                    Slider(s.trimEnd.coerceIn(.001f,duration),{vm.setTrim(s.trimStart.coerceAtMost(it-.001f),it)},valueRange=.001f..duration)
                    Text(tr(ru,"ВЫСОТА","PITCH")+": ${"%.1f".format(s.samplerPitch)} st",fontSize=10.sp,color=TextLo)
                    Slider(s.samplerPitch,vm::setSamplerPitch,valueRange=-24f..24f)
                    Text(tr(ru,"УСИЛЕНИЕ","GAIN")+": ${"%.1f".format(s.samplerGainDb)} dB",fontSize=10.sp,color=TextLo)
                    Slider(s.samplerGainDb,vm::setSamplerGain,valueRange=-24f..24f)
                    Row(verticalAlignment=Alignment.CenterVertically){Text(tr(ru,"TIME STRETCH","TIME STRETCH"),color=TextHi,fontSize=11.sp);Spacer(Modifier.weight(1f));Switch(s.sampler.stretchEnabled,{v->vm.updateSampler{it.copy(stretchEnabled=v)}})}
                    if(s.sampler.stretchEnabled)SamplerSlider(tr(ru,"Длительность","Duration"),s.sampler.stretchRatio,.5f,2f,"x"){v->vm.updateSampler{it.copy(stretchRatio=v)}}
                    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(s.samplerReverse,vm::setSamplerReverse);Text(tr(ru,"Реверс","Reverse"),color=TextHi,fontSize=12.sp);Spacer(Modifier.weight(1f));TextButton(onClick=vm::autoTrim){Text("AUTO TRIM",fontSize=10.sp)}}
                    Text(tr(ru,"Воспроизведение однократное — LOOP отключён","One-shot playback — LOOP is disabled"),fontSize=9.sp,color=TextLo)
                }
                1 -> SamplerEnvelopeTab(s,vm,accent,ru)
                2 -> SamplerFilterTab(s,vm,ru)
                3 -> SamplerFxTab(s,vm,ru)
                else -> SamplerKeyboardTab(s,vm,accent,ru)
            }
        }
    }
}

@Composable private fun SamplerEnvelopeTab(s:UiState,vm:MainViewModel,accent:Color,ru:Boolean){
    val e=s.sampler
    var target by remember { mutableStateOf("Volume") }
    Row(horizontalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.horizontalScroll(rememberScrollState())){listOf("Volume","Pan","Mod A","Mod B","Pitch").forEach{FilterChip(selected=target==it,onClick={target=it},label={Text(if(ru)when(it){"Volume"->"Громк.";"Pan"->"Панорама";"Mod A"->"Мод A";"Mod B"->"Мод B";else->"Высота"}else it,fontSize=9.sp)})}}
    val targetEnabled=when(target){"Volume"->e.envelopeEnabled;"Pan"->e.panEnvEnabled;"Mod A"->e.modAEnvEnabled;"Mod B"->e.modBEnvEnabled;else->e.pitchEnvAmount>0f}
    Row(verticalAlignment=Alignment.CenterVertically){Text("${target.uppercase()} ENVELOPE",fontSize=10.sp,color=TextLo,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Text(if(targetEnabled)"ON" else "OFF",fontSize=10.sp,color=accent);if(target!="Pitch")Switch(targetEnabled,{v->vm.updateSampler{p->when(target){"Volume"->p.copy(envelopeEnabled=v);"Pan"->p.copy(panEnvEnabled=v);"Mod A"->p.copy(modAEnvEnabled=v);else->p.copy(modBEnvEnabled=v)}}})}
    val envAttack=when(target){"Pan"->e.panAttackMs;"Mod A"->e.modAAttackMs;"Mod B"->e.modBAttackMs;else->e.attackMs}
    val envHold=if(target=="Volume")e.holdMs else 0f
    val envDecay=when(target){"Pan"->e.panDecayMs;"Mod A"->e.modADecayMs;"Mod B"->e.modBDecayMs;else->e.decayMs}
    val envSustain=when(target){"Pan"->e.panSustain;"Mod A"->e.modASustain;"Mod B"->e.modBSustain;else->e.sustain}.coerceIn(0f,1f)
    val envRelease=when(target){"Pan"->e.panReleaseMs;"Mod A"->e.modAReleaseMs;"Mod B"->e.modBReleaseMs;else->e.releaseMs}
    Canvas(Modifier.fillMaxWidth().height(92.dp).background(Color(0xFF14272E),RoundedCornerShape(10.dp))){
        val w=size.width;val h=size.height;val base=h*.86f;val top=h*.12f
        drawLine(Color(0xFF38565F),Offset(0f,base),Offset(w,base),1f)
        val total=(envAttack+envHold+envDecay+envRelease).coerceAtLeast(1f)
        val a=w*envAttack/total;val hold=w*envHold/total;val decay=w*envDecay/total;val release=w*envRelease/total
        val sustainY=base-(base-top)*envSustain
        val path=androidx.compose.ui.graphics.Path().apply{
            moveTo(0f,base);lineTo(a,top);lineTo(a+hold,top)
            lineTo(a+hold+decay,sustainY);lineTo((w-release).coerceAtLeast(a+hold+decay),sustainY);lineTo(w,base)
        }
        drawPath(path,accent,style=androidx.compose.ui.graphics.drawscope.Stroke(width=3f,cap=StrokeCap.Round))
    }
    when(target){
        "Volume"->{SamplerSlider("Attack",e.attackMs,0f,500f,"ms"){v->vm.updateSampler{it.copy(attackMs=v)}};SamplerSlider("Hold",e.holdMs,0f,500f,"ms"){v->vm.updateSampler{it.copy(holdMs=v)}};SamplerSlider("Decay",e.decayMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(decayMs=v)}};SamplerSlider("Sustain",e.sustain,0f,1f,"%"){v->vm.updateSampler{it.copy(sustain=v)}};SamplerSlider("Release",e.releaseMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(releaseMs=v)}}}
        "Pan"->{SamplerSlider("Attack",e.panAttackMs,0f,500f,"ms"){v->vm.updateSampler{it.copy(panAttackMs=v)}};SamplerSlider("Decay",e.panDecayMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(panDecayMs=v)}};SamplerSlider("Sustain",e.panSustain,0f,1f,"%"){v->vm.updateSampler{it.copy(panSustain=v)}};SamplerSlider("Release",e.panReleaseMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(panReleaseMs=v)}}}
        "Mod A"->{SamplerSlider("Attack",e.modAAttackMs,0f,500f,"ms"){v->vm.updateSampler{it.copy(modAAttackMs=v)}};SamplerSlider("Decay",e.modADecayMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(modADecayMs=v)}};SamplerSlider("Sustain",e.modASustain,0f,1f,"%"){v->vm.updateSampler{it.copy(modASustain=v)}};SamplerSlider("Release",e.modAReleaseMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(modAReleaseMs=v)}}}
        "Mod B"->{SamplerSlider("Attack",e.modBAttackMs,0f,500f,"ms"){v->vm.updateSampler{it.copy(modBAttackMs=v)}};SamplerSlider("Decay",e.modBDecayMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(modBDecayMs=v)}};SamplerSlider("Sustain",e.modBSustain,0f,1f,"%"){v->vm.updateSampler{it.copy(modBSustain=v)}};SamplerSlider("Release",e.modBReleaseMs,0f,2000f,"ms"){v->vm.updateSampler{it.copy(modBReleaseMs=v)}}}
        else->{SamplerSlider("Pitch start",e.pitchEnvStart,-24f,24f,"st"){v->vm.updateSampler{it.copy(pitchEnvStart=v)}};SamplerSlider("Pitch end",e.pitchEnvEnd,-24f,24f,"st"){v->vm.updateSampler{it.copy(pitchEnvEnd=v)}};SamplerSlider("Amount",e.pitchEnvAmount,0f,1f,"%"){v->vm.updateSampler{it.copy(pitchEnvAmount=v)}}}
    }
    Divider(color=Color(0xFF35515A));Text(if(ru)"LFO / MODULATION" else "LFO / MODULATION",fontSize=10.sp,color=TextLo,fontWeight=FontWeight.Bold)
    Row(verticalAlignment=Alignment.CenterVertically){Text("LFO",color=TextHi,fontSize=12.sp);Spacer(Modifier.weight(1f));Switch(e.lfoEnabled,{vm.updateSampler{it.copy(lfoEnabled=!it.lfoEnabled)}})}
    SamplerSlider("Rate",e.lfoRateHz,.1f,20f,"Hz"){v->vm.updateSampler{it.copy(lfoRateHz=v)}}
    SamplerSlider("Depth",e.lfoDepth,0f,1f,"%"){v->vm.updateSampler{it.copy(lfoDepth=v)}}
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Volume","Filter","Pan").forEach{FilterChip(selected=e.lfoTarget==it,onClick={vm.updateSampler{x->x.copy(lfoTarget=it)}},label={Text(if(ru)when(it){"Volume"->"Громкость";"Filter"->"Фильтр";else->"Панорама"}else it,fontSize=10.sp)})}}

}
@Composable private fun SamplerFilterTab(s:UiState,vm:MainViewModel,ru:Boolean){
    val f=s.sampler
    Row(verticalAlignment=Alignment.CenterVertically){Text(if(ru)"ФИЛЬТР" else "FILTER",fontSize=10.sp,color=TextLo,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Switch(f.filterEnabled,{vm.updateSampler{it.copy(filterEnabled=!it.filterEnabled)}})}
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){listOf("Low-pass","High-pass","Band-pass").forEach{FilterChip(selected=f.filterMode==it,onClick={vm.updateSampler{x->x.copy(filterMode=it)}},label={Text(if(ru)when(it){"Low-pass"->"НЧ";"High-pass"->"ВЧ";else->"Полосовой"}else it,fontSize=10.sp)})}}
    SamplerSlider(tr(ru,"Частота среза","Cutoff"),f.filterCutoff,20f,20000f,"Hz"){v->vm.updateSampler{it.copy(filterCutoff=v)}}
    SamplerSlider(tr(ru,"Резонанс","Resonance"),f.filterResonance,0f,1f,"%"){v->vm.updateSampler{it.copy(filterResonance=v)}}
    Text(if(ru)"LFO может модулировать фильтр на вкладке Огибающая." else "LFO can modulate the filter from the Envelope tab.",fontSize=10.sp,color=TextLo)
}

@Composable private fun SamplerFxTab(s:UiState,vm:MainViewModel,ru:Boolean){
    val f=s.sampler
    SamplerSlider(tr(ru,"Fade In","Fade In"),f.fadeInMs,0f,500f,"ms"){v->vm.updateSampler{it.copy(fadeInMs=v)}}
    SamplerSlider(tr(ru,"Fade Out","Fade Out"),f.fadeOutMs,0f,1000f,"ms"){v->vm.updateSampler{it.copy(fadeOutMs=v)}}
    SamplerSlider(tr(ru,"Ring Mod частота","Ring Mod Freq"),f.ringModHz,0f,2000f,"Hz"){v->vm.updateSampler{it.copy(ringModHz=v)}}
    SamplerSlider(tr(ru,"Ring Mod mix","Ring Mod Mix"),f.ringModMix,0f,1f,"%"){v->vm.updateSampler{it.copy(ringModMix=v)}}
    SamplerSlider(tr(ru,"Панорама","Pan"),f.pan,-1f,1f,"L/R"){v->vm.updateSampler{it.copy(pan=v)}}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(f.normalize,{v->vm.updateSampler{it.copy(normalize=v)}});Text(tr(ru,"Нормализация","Normalize"),color=TextHi,fontSize=12.sp);Checkbox(f.removeDc,{v->vm.updateSampler{it.copy(removeDc=v)}});Text("DC",color=TextHi,fontSize=12.sp)}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(f.softClip,{v->vm.updateSampler{it.copy(softClip=v)}});Text(tr(ru,"Soft clip","Soft clip"),color=TextHi,fontSize=12.sp);Checkbox(f.stereoSwap,{v->vm.updateSampler{it.copy(stereoSwap=v)}});Text(tr(ru,"Swap L/R","Swap L/R"),color=TextHi,fontSize=12.sp)}
    Divider(color=Color(0xFF35515A))
    Row(verticalAlignment=Alignment.CenterVertically){Text("ECHO DELAY / FAT MODE",fontSize=11.sp,color=TextLo,fontWeight=FontWeight.Bold);Spacer(Modifier.weight(1f));Switch(f.echoEnabled,{v->vm.updateSampler{it.copy(echoEnabled=v)}})}
    SamplerSlider("Feed",f.echoFeed,0f,.95f,"%"){v->vm.updateSampler{it.copy(echoFeed=v)}}
    SamplerSlider("Pitch spread",f.echoPitch,1f,24f,"st"){v->vm.updateSampler{it.copy(echoPitch=v)}}
    SamplerSlider("Time",f.echoTimeMs,1f,2000f,"ms"){v->vm.updateSampler{it.copy(echoTimeMs=v)}}
    SamplerSlider("Echoes",f.echoCount.toFloat(),1f,16f,"taps"){v->vm.updateSampler{it.copy(echoCount=v.toInt().coerceIn(1,16))}}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(f.echoPingPong,{v->vm.updateSampler{it.copy(echoPingPong=v)}});Text("Ping pong",fontSize=11.sp,color=TextHi);Spacer(Modifier.width(12.dp));Checkbox(f.echoFatMode,{v->vm.updateSampler{it.copy(echoFatMode=v)}});Text("Fat mode • 3 pitches together",fontSize=11.sp,color=TextHi)}
}

@Composable private fun SamplerKeyboardTab(s:UiState,vm:MainViewModel,accent:Color,ru:Boolean){
    val notes=listOf("C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B")
    var chord by remember { mutableStateOf(setOf<Int>()) }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically){
            Text(if(ru)"Клавиатура предпрослушивания • MIDI 36–83" else "Audition keyboard • MIDI 36–83",fontSize=10.sp,color=TextLo)
            Spacer(Modifier.weight(1f));Text("${chord.size}",fontSize=10.sp,color=accent)
            TextButton(onClick={chord=emptySet();vm.playKeyboardChord(emptyList())}){Text(if(ru)"СБРОСИТЬ" else "CLEAR",fontSize=9.sp)}
        }
        Text(if(ru)"Выбирай несколько клавиш — ноты звучат одновременно. Повторное нажатие снимает ноту." else "Select multiple keys to play a chord. Tap a selected key again to remove it.",fontSize=9.sp,color=TextLo)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(3.dp)){
            (36..83).forEach{note->
                val name=notes[note%12];val black=name.contains('♯');val selected=note in chord
                Button(onClick={chord=if(selected)chord-note else chord+note;vm.playKeyboardChord(chord.sorted())},modifier=Modifier.width(37.dp).height(if(black)54.dp else 70.dp),contentPadding=PaddingValues(0.dp),colors=ButtonDefaults.buttonColors(containerColor=if(selected)accent else if(black)Color(0xFF0C171B) else Color(0xFFD8EDF0),contentColor=if(selected)Color(0xFF173039) else if(black)TextHi else Color(0xFF173039)),shape=RoundedCornerShape(5.dp)){Text("$name${note/12-1}",fontSize=9.sp)}
            }
        }
    }
    SamplerSlider(if(ru)"Корневая нота" else "Root note",s.sampler.rootNote.toFloat(),24f,96f,"MIDI"){v->vm.updateSampler{it.copy(rootNote=v.toInt())}}
}

@Composable private fun SamplerSlider(label:String,value:Float,min:Float,max:Float,unit:String,onChange:(Float)->Unit){
    val shown=if(unit=="%")value*100f else value
    Text("$label: ${"%.2f".format(shown)} $unit",fontSize=11.sp,color=TextHi)
    Slider(value.coerceIn(min,max),onChange,valueRange=min..max)
}

@Composable fun Waveform(samples:FloatArray,accent:Color,modifier:Modifier=Modifier){
    Canvas(modifier){
        drawRect(Color(0xFF14272E))
        if(samples.isEmpty())return@Canvas
        val frames=samples.size
        val step=maxOf(1,frames/(size.width.toInt().coerceAtLeast(1)))
        var x=0f
        while(x<size.width){
            val i=(x/size.width*frames).toInt().coerceIn(0,frames-1)
            val amp=abs(samples[i]).coerceAtMost(1f)
            drawLine(accent.copy(alpha=.9f),Offset(x,size.height/2-amp*size.height*.48f),Offset(x,size.height/2+amp*size.height*.48f),1.2f,cap=StrokeCap.Round)
            x+=1f
        }
    }
}

@Composable fun ThemeDialog(current:Color,saved:List<Int>,onDismiss:()->Unit,onSelect:(Int)->Unit,onSave:(Int)->Unit){
    val palettes=listOf(
        listOf(0xFF8DEBF4,0xFF00B4D8,0xFF6BCB9B,0xFFB5A0FF),
        listOf(0xFFFFB86B,0xFFFF7B72,0xFFFFD166,0xFFE98BAA),
        listOf(0xFFB6C2D9,0xFF94A3B8,0xFFCBD5E1,0xFF8CA6A6),
        listOf(0xFFC5F467,0xFF9BE564,0xFF43AA8B,0xFF4D908E)
    )
    var hue by remember{mutableFloatStateOf(185f)}
    var sat by remember{mutableFloatStateOf(.42f)}
    var value by remember{mutableFloatStateOf(.95f)}
    val custom=Color.hsv(hue,sat,value)
    AlertDialog(onDismissRequest=onDismiss,title={Text("THEME / ПАЛИТРА")},text={
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
            Text("BUILT-IN THEMES",fontSize=11.sp,color=TextLo)
            palettes.forEach{palette->Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){palette.forEach{argb->
                Box(Modifier.size(38.dp).background(Color(argb),CircleShape).clickable{onSelect(argb.toInt())})
            }}}
            Divider(color=TextLo.copy(alpha=.25f))
            Text("MY PALETTE",fontSize=11.sp,color=TextLo)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                saved.forEach{color->Box(Modifier.size(34.dp).background(Color(color),CircleShape).clickable{onSelect(color);onDismiss()})}
            }
            Text("CUSTOM COLOR",fontSize=11.sp,color=TextLo)
            Box(Modifier.fillMaxWidth().height(34.dp).background(custom,RoundedCornerShape(10.dp)))
            Text("Hue",fontSize=11.sp);Slider(hue,{hue=it},valueRange=0f..360f)
            Text("Saturation",fontSize=11.sp);Slider(sat,{sat=it},valueRange=0f..1f)
            Text("Brightness",fontSize=11.sp);Slider(value,{value=it},valueRange=0f..1f)
            Text("Choose a palette swatch or adjust the color visually. No hex codes.",fontSize=10.sp,color=TextLo)
        }
    },confirmButton={Row{TextButton(onClick={onSave(custom.toArgb())}){Text("SAVE COLOR")};TextButton(onClick={onSelect(custom.toArgb());onDismiss()}){Text("APPLY")}}},dismissButton={TextButton(onClick=onDismiss){Text("CLOSE")}})
}

private fun moduleTitle(type:ModuleType,ru:Boolean):String = if(!ru) type.title else when(type){
    ModuleType.ENVELOPE->"Огибающая";ModuleType.EQ->"Эквалайзер";ModuleType.COMPRESSOR->"Компрессор"
    ModuleType.SATURATION->"Сатурация";ModuleType.BLUR->"Размытие";ModuleType.RESONANCE_SUPPRESSOR->"Подавитель резонансов"
    ModuleType.FREQ_SHIFT->"Частотный сдвиг";ModuleType.PITCH_ENVELOPE->"Огибающая высоты"
    ModuleType.GAIN->"Усиление";ModuleType.STACK->"Слои";ModuleType.DISFLUX->"Disflux";ModuleType.PRESS->"Press"
}

private fun paramTitle(key:String,ru:Boolean):String = if(!ru) key.replace('_',' ').replaceFirstChar{it.uppercase()} else when(key){
    "gain_db"->"Усиление (дБ)";"drive"->"Драйв";"mix"->"Смешивание";"output_trim_db"->"Выходной уровень"
    "threshold_db"->"Порог (дБ)";"ratio"->"Соотношение";"attack_ms"->"Атака (мс)";"release_ms"->"Спад (мс)"
    "knee_db"->"Колено (дБ)";"makeup_gain_db"->"Компенсация усиления";"lookahead_ms"->"Предпросмотр (мс)"
    "pre_gain_db"->"Входное усиление";"pre_shift_cents"->"Сдвиг высоты (центы)";"global_gain_db"->"Общее усиление"
    "shift_cents"->"Сдвиг (центы)";"shift_hz"->"Сдвиг (Гц)";"copy_count"->"Количество копий"
    "pitch_interval_cents"->"Интервал высоты";"master_gain_db"->"Общее усиление";"blend"->"Смешивание"
    "tail_length_ms"->"Длина хвоста";"tail_decay_db"->"Затухание хвоста";"stereo_width"->"Ширина стерео"
    "smoothness"->"Сглаживание";"depth"->"Глубина";"sharpness"->"Резкость";"selectivity"->"Избирательность"
    "polarity_flip"->"Инверсия фазы";"dc_remove"->"Удаление DC";"normalize"->"Нормализация"
    "snap_semitone"->"Привязка к полутонам";"detect_fundamental"->"Определять основной тон"
    "amount"->"Количество фильтров";"frequency"->"Частота";"spread"->"Разброс";"pinch"->"Резонанс";"mix"->"Смешивание"
    "low_crossover_hz"->"Нижний кроссовер (Гц)";"high_crossover_hz"->"Верхний кроссовер (Гц)"
    "low_threshold_db"->"Порог НЧ (дБ)";"mid_threshold_db"->"Порог СЧ (дБ)";"high_threshold_db"->"Порог ВЧ (дБ)"
    "low_ratio"->"Сжатие НЧ";"mid_ratio"->"Сжатие СЧ";"high_ratio"->"Сжатие ВЧ"
    "master_ceiling_db"->"Потолок мастера (дБ)";"output_gain_db"->"Выходное усиление (дБ)"
    else->key.replace('_',' ')
}

@Composable fun ModuleControls(m:AudioModule,vm:MainViewModel,ru:Boolean=false){
    when(m.type){
        ModuleType.GAIN -> {SliderParam(m,"gain_db",-24f,24f,vm,ru);CheckParam(m,"polarity_flip",vm,ru);CheckParam(m,"dc_remove",vm,ru);CheckParam(m,"normalize",vm,ru)}
        ModuleType.SATURATION -> {ChoiceParam(m,"mode",listOf("soft","hard","tube"),vm,ru);SliderParam(m,"drive",0f,1f,vm,ru);SliderParam(m,"mix",0f,1f,vm,ru);SliderParam(m,"output_trim_db",-24f,6f,vm,ru)}
        ModuleType.COMPRESSOR -> {SliderParam(m,"threshold_db",-60f,0f,vm,ru);SliderParam(m,"ratio",1f,20f,vm,ru);SliderParam(m,"attack_ms",0.1f,100f,vm,ru);SliderParam(m,"release_ms",1f,500f,vm,ru);SliderParam(m,"knee_db",0f,24f,vm,ru);SliderParam(m,"makeup_gain_db",-24f,24f,vm,ru);SliderParam(m,"mix",0f,1f,vm,ru);SliderParam(m,"lookahead_ms",0f,20f,vm,ru)}
        ModuleType.ENVELOPE -> {SliderParam(m,"pre_gain_db",-24f,24f,vm,ru);Text("Envelope points: edit in project JSON for exact multi-point curves.",fontSize=12.sp,color=Color.Gray)}
        ModuleType.PITCH_ENVELOPE -> {SliderParam(m,"pre_shift_cents",-2400f,2400f,vm,ru);CheckParam(m,"snap_semitone",vm,ru);Text("Block pitch envelope is supported; fine point editing is persisted.",fontSize=12.sp,color=Color.Gray)}
        ModuleType.EQ -> {SliderParam(m,"global_gain_db",-24f,24f,vm,ru);Text("Parametric peaking bands are supported. Add/edit bands in the project JSON.",fontSize=12.sp,color=Color.Gray)}
        ModuleType.FREQ_SHIFT -> {SliderParam(m,"shift_cents",-2400f,2400f,vm,ru);SliderParam(m,"shift_hz",-5000f,5000f,vm,ru);CheckParam(m,"detect_fundamental",vm,ru)}
        ModuleType.STACK -> {SliderParam(m,"copy_count",0f,12f,vm,ru);SliderParam(m,"pitch_interval_cents",-2400f,2400f,vm,ru);CheckParam(m,"fat_mode",vm,ru);SliderParam(m,"master_gain_db",-96f,24f,vm,ru)}
        ModuleType.DISFLUX -> {SliderParam(m,"amount",0f,256f,vm,ru);SliderParam(m,"frequency",20f,20000f,vm,ru);SliderParam(m,"spread",0f,10000f,vm,ru);SliderParam(m,"pinch",.5f,16f,vm,ru);SliderParam(m,"mix",0f,1f,vm,ru)}
        ModuleType.PRESS -> {
            Text(tr(ru,"КРОССОВЕРЫ","CROSSOVERS"),fontSize=10.sp,color=TextLo,fontWeight=FontWeight.Bold)
            SliderParam(m,"low_crossover_hz",30f,2000f,vm,ru)
            SliderParam(m,"high_crossover_hz",500f,18000f,vm,ru)
            Text(tr(ru,"ПОРОГ ПО ДИАПАЗОНАМ","BAND THRESHOLDS"),fontSize=10.sp,color=TextLo,fontWeight=FontWeight.Bold)
            SliderParam(m,"low_threshold_db",-36f,0f,vm,ru);SliderParam(m,"mid_threshold_db",-36f,0f,vm,ru);SliderParam(m,"high_threshold_db",-36f,0f,vm,ru)
            Text(tr(ru,"СТЕПЕНЬ СЖАТИЯ","COMPRESSION RATIO"),fontSize=10.sp,color=TextLo,fontWeight=FontWeight.Bold)
            SliderParam(m,"low_ratio",1f,20f,vm,ru);SliderParam(m,"mid_ratio",1f,20f,vm,ru);SliderParam(m,"high_ratio",1f,20f,vm,ru)
            SliderParam(m,"attack_ms",.1f,100f,vm,ru);SliderParam(m,"release_ms",1f,1000f,vm,ru)
            SliderParam(m,"master_ceiling_db",-12f,0f,vm,ru);SliderParam(m,"output_gain_db",-12f,12f,vm,ru);SliderParam(m,"mix",0f,1f,vm,ru)
        }
        ModuleType.BLUR -> {SliderParam(m,"blend",0f,1f,vm,ru);SliderParam(m,"tail_length_ms",0f,1000f,vm,ru);SliderParam(m,"tail_decay_db",-96f,0f,vm,ru);SliderParam(m,"stereo_width",0f,1f,vm,ru);SliderParam(m,"smoothness",0f,1f,vm,ru)}
        ModuleType.RESONANCE_SUPPRESSOR -> {SliderParam(m,"depth",0f,1f,vm,ru);SliderParam(m,"sharpness",0f,1f,vm,ru);SliderParam(m,"selectivity",0f,10f,vm,ru);SliderParam(m,"attack_ms",1f,100f,vm,ru);SliderParam(m,"release_ms",1f,500f,vm,ru);SliderParam(m,"mix",0f,1f,vm,ru)}
    }
}

@Composable fun SliderParam(m:AudioModule,key:String,min:Float,max:Float,vm:MainViewModel,ru:Boolean=false){
    val initial=(m.params[key] as? Number)?.toFloat() ?: min
    var v by remember(m.id,key,initial){mutableFloatStateOf(initial)}
    Text("${paramTitle(key,ru)}: ${"%.2f".format(v)}",fontSize=12.sp,color=TextHi)
    Slider(v,{v=it;vm.setParam(key,it)},valueRange=min..max)
}
@Composable fun CheckParam(m:AudioModule,key:String,vm:MainViewModel,ru:Boolean=false){
    val initial=m.params[key]==true;var v by remember(m.id,key){mutableStateOf(initial)}
    Row(verticalAlignment=Alignment.CenterVertically){Checkbox(v,{v=it;vm.setParam(key,it)});Text(paramTitle(key,ru),color=TextHi)}
}
@Composable fun ChoiceParam(m:AudioModule,key:String,values:List<String>,vm:MainViewModel,ru:Boolean=false){
    var v by remember(m.id,key){mutableStateOf(m.params[key]?.toString()?:values.first())}
    Row(verticalAlignment=Alignment.CenterVertically){Text("${paramTitle(key,ru)}: ",color=TextHi);values.forEach{FilterChip(selected=v==it,onClick={v=it;vm.setParam(key,it)},label={Text(it)})}}
}

@Composable fun AddModuleDialog(ru:Boolean,onDismiss:()->Unit,onAdd:(ModuleType)->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text(tr(ru,"Добавить эффект","Add effect"))},text={Column{ModuleType.values().forEach{t->Text(moduleTitle(t,ru),Modifier.fillMaxWidth().clickable{onAdd(t)}.padding(12.dp),color=TextHi)}}},confirmButton={TextButton(onClick=onDismiss){Text(tr(ru,"Закрыть","Close"))}})
}

@Composable fun KitDialog(s:UiState,vm:MainViewModel,onDismiss:()->Unit){
    AlertDialog(onDismissRequest=onDismiss,title={Text("Kit • ${s.kit.size}/16")},text={
        Column{Button(onClick=vm::addKitSlot){Text("+ Slot")};Button(onClick=vm::addLayerToSelected,enabled=s.source!=null&&s.selectedSlotId!=null){Text("Add current sample as layer")};s.kit.forEach{slot->Row(Modifier.fillMaxWidth().clickable{vm.selectKitSlot(slot.id)}.padding(8.dp)){Text(if(slot.id==s.selectedSlotId)"▶ " else "");Text("${slot.label} (${slot.layers.size} layers)")}}}
    },confirmButton={TextButton(onClick=onDismiss){Text("Done")}})
}

package br.com.copilotoia99

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.net.Uri
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
    private val captureRequest=1201
    private lateinit var captureStatus: TextView
    private lateinit var store: OfferStore
    private lateinit var result: TextView
    private lateinit var history: TextView
    private lateinit var region: EditText
    private lateinit var observed: EditText
    private lateinit var limits: DriverLimits
    private val prefs by lazy { getSharedPreferences("driver_limits",Context.MODE_PRIVATE) }
    private val pt = Locale("pt","BR")
    private val sections = mutableListOf<LinearLayout>()
    private lateinit var scroll: ScrollView
    private lateinit var homePanel: LinearLayout
    private fun dp(n: Int): Int = (resources.displayMetrics.density*n).toInt()
    private val navy = Color.rgb(18, 38, 58)
    private val orange = Color.rgb(255, 153, 0)
    private val ink = Color.rgb(27, 39, 51)
    private val muted = Color.rgb(91, 105, 120)
    private fun rounded(color: Int, stroke: Int = Color.TRANSPARENT): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply {
            setColor(color); cornerRadius=dp(18).toFloat()
            if(stroke != Color.TRANSPARENT) setStroke(dp(1),stroke)
        }
    private fun text(value: String, size: Float=16f, bold: Boolean=false): TextView = TextView(this).apply {
        text=value; textSize=size; setTextColor(if(size<=13f) muted else ink)
        if(bold) setTypeface(null,Typeface.BOLD)
        setPadding(0,dp(7),0,dp(7)); setLineSpacing(dp(2).toFloat(),1.0f)
    }
    private fun input(root: LinearLayout, label: String, initial: String, numeric: Boolean=true): EditText {
        root.addView(text(label,14f,true))
        return EditText(this).apply {
            setText(initial); setSingleLine(true); textSize=18f; minHeight=dp(54)
            setPadding(dp(14),dp(8),dp(14),dp(8))
            setTextColor(ink); setHintTextColor(muted); background=rounded(Color.WHITE,Color.rgb(205,215,225))
            if(numeric) inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            else inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            contentDescription=label
            root.addView(this)
        }
    }
    private fun parse(e: EditText): Double = e.text.toString().trim().replace(',','.').toDouble()
    private fun button(root: LinearLayout, title: String, action: ()->Unit) {
        root.addView(Button(this).apply {
            text=title; isAllCaps=false; textSize=15f; setTextColor(Color.WHITE)
            typeface=Typeface.DEFAULT_BOLD; minHeight=dp(56); setPadding(dp(16),dp(10),dp(16),dp(10))
            background=rounded(navy); contentDescription=title; stateListAnimator=null
            setOnClickListener { action() }
        }, LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(8) })
    }
    private fun card(root: LinearLayout, title: String): LinearLayout {
        val group=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(16),dp(16),dp(16))
            background=rounded(Color.WHITE,Color.rgb(224,231,238)); elevation=dp(2).toFloat()
        }
        val p=LinearLayout.LayoutParams(-1,-2).apply { topMargin=dp(14) }
        root.addView(group,p); group.addView(text(title,18f,true).apply { setTextColor(navy) })
        sections.add(group)
        return group
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=navy
        window.navigationBarColor=navy
        store=OfferStore(this)
        limits=DriverLimits(prefs.getFloat("km",1.8f).toDouble(),prefs.getFloat("hour",35f).toDouble(),prefs.getFloat("fare",10f).toDouble(),prefs.getFloat("rating",4.7f).toDouble(),prefs.getFloat("pickupKm",8f).toDouble(),prefs.getFloat("pickupMin",15f).toDouble())
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(36)); setBackgroundColor(Color.rgb(244,247,250)) }
        scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false; addView(root) }
        setContentView(scroll)
        val hero=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),dp(22),dp(20),dp(22)); background=rounded(navy); elevation=dp(3).toFloat() }
        root.addView(hero)
        hero.addView(text("🚘  COPILOTO IA 99",24f,true).apply { setTextColor(Color.WHITE) })
        hero.addView(text("Decida melhor. Rode com mais consciência.",15f).apply { setTextColor(Color.rgb(226,235,244)) })
        hero.addView(text("Análise de corridas • Privacidade no aparelho • Você decide se aceita",12f).apply { setTextColor(Color.rgb(205,218,230)) })
        root.addView(text("SEU PAINEL",12f,true).apply { setTextColor(orange); setPadding(dp(2),dp(16),0,0) })
        root.addView(text("Escolha uma ação para começar.",16f,true))
        val evolution=EvolutionEngine(this)
        val evoCard=card(root,"COPILOTO EVOLUTION • v1.1")
        val evoStatus=text(if(evolution.enabled()) "Compartilhamento de diagnósticos: autorizado" else "Compartilhamento de diagnósticos: desligado",13f)
        evoCard.addView(evoStatus)
        button(evoCard,"ATIVAR / DESATIVAR CONSENTIMENTO") {
            evolution.setEnabled(!evolution.enabled())
            evoStatus.text=if(evolution.enabled()) "Compartilhamento de diagnósticos: autorizado" else "Compartilhamento de diagnósticos: desligado"
        }
        button(evoCard,"VISUALIZAR RELATÓRIO ANÔNIMO DE EXEMPLO") {
            android.app.AlertDialog.Builder(this).setTitle("Prévia de diagnóstico")
                .setMessage(evolution.previewReport(0,0,0).toString(2)+"\n\nExemplo sem dados reais. Nenhum envio foi realizado.")
                .setPositiveButton("OK",null).show()
        }
        button(evoCard,"RESTAURAR CONFIGURAÇÃO ANTERIOR") { evolution.rollback(); evoStatus.text="Configuração anterior restaurada (se disponível)" }
        evoCard.addView(text("A conexão com servidor e a aplicação de pacotes assinados ainda não estão ativadas. A análise de ofertas permanece local.",12f))
        val recorderCard=card(root,"EVOLUTION RECORDER • v1.2")
        recorderCard.addView(text("Registros locais compactos, retenção de 30 dias e limite de 3.000 eventos detalhados. Estatísticas agregadas preservadas por até 365 dias.",13f))
        button(recorderCard,"VISUALIZAR DIAGNÓSTICO REAL") {
            try { EvolutionRecorder(this).use { recorder ->
                android.app.AlertDialog.Builder(this).setTitle("Diagnóstico local")
                    .setMessage(recorder.report().toString(2)).setPositiveButton("OK",null).show()
            } } catch(e:Exception) { android.app.AlertDialog.Builder(this).setMessage("Falha ao gerar relatório: ${e.message}").setPositiveButton("OK",null).show() }
        }
        button(recorderCard,"COMPARTILHAR RELATÓRIO (AÇÃO MANUAL)") {
            try { EvolutionRecorder(this).use { recorder ->
                val payload=recorder.report().toString(2)
                val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,payload) }
                startActivity(Intent.createChooser(intent,"Compartilhar diagnóstico Evolution"))
            } } catch(e:Exception) { android.widget.Toast.makeText(this,"Não foi possível preparar o relatório",android.widget.Toast.LENGTH_LONG).show() }
        }
        recorderCard.addView(text("Nenhum dado é enviado automaticamente. A integração com servidor/API será uma etapa posterior, com consentimento específico.",12f))
        val monitor=card(root,"MONITOR DE OFERTAS • A54")
        monitor.addView(text("Inicie a captura apenas com autorização do Android. A leitura de ofertas é experimental. Não interage com os botões da 99.",13f))
        captureStatus=text("Captura desativada",15f,true)
        button(monitor,"1. PERMITIR JANELA FLUTUANTE") {
            if(!Settings.canDrawOverlays(this))startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
            else captureStatus.text="Janela flutuante já permitida"
        }
        button(monitor,"2. INICIAR CAPTURA AUTORIZADA") {
            if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),100)
            val mgr=getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(mgr.createScreenCaptureIntent(),captureRequest)
        }
        button(monitor,"PARAR MONITORAMENTO") { startService(Intent(this,CaptureService::class.java).setAction(CaptureService.ACTION_STOP));captureStatus.text="Monitoramento encerrado" }
        monitor.addView(captureStatus)
        val accessibilityCard=card(root,"🤖  ASSISTENTE AUTOMÁTICO")
        accessibilityCard.addView(text("Recomendado • Sem gravação contínua. O Android permite que o Copiloto leia o texto visível de ofertas compatíveis no app 99. Você continua no controle: ele não aceita nem recusa corridas.",15f))
        val accessibilityStatus=text(if(isAccessibilityServiceEnabled()) "Acessibilidade: ativada" else "Acessibilidade: desativada",14f,true)
        accessibilityCard.addView(accessibilityStatus)
        button(accessibilityCard,"ATIVAR AUTOMAÇÃO NO ANDROID") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button(accessibilityCard,"VERIFICAR STATUS") {
            accessibilityStatus.text=if(isAccessibilityServiceEnabled()) "Acessibilidade: ativada" else "Acessibilidade: desativada"
        }

        val offer=card(root,"✍️  ANÁLISE MANUAL")
        offer.addView(text("Preencha os dados da oferta para receber uma avaliação rápida.",14f))
        val price=input(offer,"Valor da corrida (R$)","30")
        val pickupKm=input(offer,"Embarque — distância (km)","2")
        val tripKm=input(offer,"Viagem — distância (km)","10")
        val pickupMin=input(offer,"Embarque — tempo (min)","6")
        val tripMin=input(offer,"Viagem — tempo (min)","24")
        val rating=input(offer,"Nota do passageiro","4,95")
        region=input(offer,"Região do destino (apelido, sem endereço)","Centro",false)
        val estimatedWait=input(offer,"Espera estimada (min; opcional)","")
        result=text("Aguardando análise",16f,true)
        button(offer,"ANALISAR E SALVAR NO CELULAR") {
            try {
                val name=region.text.toString().trim().take(60).ifBlank { "Desconhecida" }
                val input=OfferInput(parse(price),parse(pickupKm),parse(tripKm),parse(pickupMin),parse(tripMin),parse(rating),name,estimatedWait.text.toString().takeIf { it.isNotBlank() }?.replace(',','.')?.toDouble())
                val prediction=OfferEvaluator.evaluate(input,limits,store.getLearning(name))
                store.add(input,prediction)
                EvolutionRecorder.recordAsync(this,"manual_offer")
                result.text=String.format(pt,"%s • %d/100\nR$ %.2f/km • R$ %.2f/h\n%s\nConfiança: %s\nDestino: %s\nProjeção com espera: %s\nSalvo somente no aparelho.",prediction.verdict,prediction.score,prediction.perKm,prediction.perHour,prediction.explanation,prediction.confidence,prediction.destinationScore?.toString()?:"indisponível",prediction.projectedPerHourWithWait?.let { "R$ %.2f/h".format(pt,it) }?:"indisponível")
                refresh()
            } catch(e:Exception) { result.text="Revise os valores. Distâncias e tempos não podem ser negativos." }
        }
        offer.addView(result)
        val learn=card(root,"📍  APRENDIZADO POR REGIÃO")
        learn.addView(text("Após uma corrida, informe a espera real por outra chamada naquela região. Isso atualiza o modelo local gradualmente.",13f))
        observed=input(learn,"Espera real observada (0 a 240 min)","12")
        val learningResult=text("",13f)
        button(learn,"REGISTRAR OBSERVAÇÃO REAL") {
            try {
                val name=region.text.toString().trim().take(60)
                store.observeWait(name,parse(observed))
                val model=store.getLearning(name)!!
                learningResult.text="Região: $name • ${model.samples} observações • espera aprendida: ${"%.1f".format(pt,model.meanWaitMinutes)} min"
            } catch(e:Exception) { learningResult.text="Informe região e espera real válida." }
        }
        learn.addView(learningResult)
        val settings=card(root,"⚙️  SEUS LIMITES DE GANHO")
        val minKm=input(settings,"Mínimo R$/km",limits.minPerKm.toString())
        val minHour=input(settings,"Mínimo R$/hora",limits.minPerHour.toString())
        val minFare=input(settings,"Valor mínimo R$",limits.minFare.toString())
        val minRating=input(settings,"Nota mínima do passageiro",limits.minRating.toString())
        val maxPickupKm=input(settings,"Embarque máximo (km)",limits.maxPickupKm.toString())
        val maxPickupMin=input(settings,"Embarque máximo (min)",limits.maxPickupMinutes.toString())
        val settingsResult=text("",13f)
        button(settings,"SALVAR LIMITES LOCALMENTE") {
            try {
                val new=DriverLimits(parse(minKm),parse(minHour),parse(minFare),parse(minRating),parse(maxPickupKm),parse(maxPickupMin))
                require(new.minPerKm>0 && new.minPerHour>0 && new.minFare>0 && new.minRating in 0.0..5.0 && new.maxPickupKm>=0 && new.maxPickupMinutes>=0)
                limits=new
                prefs.edit().putFloat("km",new.minPerKm.toFloat()).putFloat("hour",new.minPerHour.toFloat()).putFloat("fare",new.minFare.toFloat()).putFloat("rating",new.minRating.toFloat()).putFloat("pickupKm",new.maxPickupKm.toFloat()).putFloat("pickupMin",new.maxPickupMinutes.toFloat()).apply()
                settingsResult.text="Limites salvos no aparelho. A IA não os altera."
            } catch(e:Exception) { settingsResult.text="Limites inválidos; nenhuma alteração foi salva." }
        }
        settings.addView(settingsResult)
        val saved=card(root,"📊  HISTÓRICO DE CORRIDAS")
        history=text("",13f)
        button(saved,"RECARREGAR HISTÓRICO") { refresh() }
        saved.addView(history)
        val privacy=card(root,"🔒  PRIVACIDADE E CONTROLE")
        privacy.addView(text("A leitura é experimental e pode não reconhecer todos os campos. A nota do passageiro não é extraída automaticamente. O Copiloto não aceita nem recusa corridas. Seus registros ficam no aparelho e podem ser apagados ao desinstalar.",14f))

        // Home-first navigation: common actions first, advanced tools in Settings.
        homePanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(16),dp(16),dp(16))
            background=rounded(Color.WHITE,Color.rgb(224,231,238))
            elevation=dp(2).toFloat()
        }
        homePanel.addView(text("O QUE VOCÊ DESEJA FAZER?",17f,true).apply { setTextColor(navy) })
        homePanel.addView(text("Acesse rapidamente as funções principais. As ferramentas avançadas ficam em Configurações.",14f))
        button(homePanel,"🤖  ASSISTENTE AUTOMÁTICO") { showSections(2,3) }
        button(homePanel,"🧮  ANALISAR UMA CORRIDA") { showSections(4) }
        button(homePanel,"📊  MEU HISTÓRICO") { showSections(7) }
        button(homePanel,"⚙️  CONFIGURAÇÕES E FERRAMENTAS") { showSections(0,1,5,6,8) }
        root.addView(homePanel,3)
        sections.forEach { section ->
            section.visibility=View.GONE
            button(section,"←  VOLTAR AO INÍCIO") { goHome() }
        }
        refresh()
    }
    private fun showSections(vararg indexes: Int) {
        homePanel.visibility=View.GONE
        sections.forEachIndexed { index, section ->
            section.visibility=if(indexes.contains(index)) View.VISIBLE else View.GONE
        }
        scroll.post { scroll.smoothScrollTo(0,0) }
    }
    private fun goHome() {
        sections.forEach { it.visibility=View.GONE }
        homePanel.visibility=View.VISIBLE
        scroll.post { scroll.smoothScrollTo(0,0) }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${OfferAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    @Deprecated("Activity result API is retained for minimal dependency prototype")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){
        super.onActivityResult(requestCode,resultCode,data)
        if(requestCode==captureRequest&&resultCode==RESULT_OK&&data!=null){
            val intent=Intent(this,CaptureService::class.java).putExtra(CaptureService.EXTRA_RESULT_CODE,resultCode).putExtra(CaptureService.EXTRA_DATA,data)
            try{startForegroundService(intent);captureStatus.text="Captura iniciada com consentimento"}catch(e:Exception){captureStatus.text="Erro ao iniciar: ${e.message}"}
        }
    }
    private fun refresh() {
        if(!::history.isInitialized)return
        val all=store.all()
        history.text="${all.size} ofertas armazenadas localmente\n"+all.take(20).joinToString("\n") {
            String.format(pt,"#%d • R$ %.2f • %.1f km • %.0f min • %s (%d)",it.id,it.price,it.km,it.minutes,it.verdict,it.score)
        }
    }
    override fun onDestroy(){ store.close();super.onDestroy() }
}

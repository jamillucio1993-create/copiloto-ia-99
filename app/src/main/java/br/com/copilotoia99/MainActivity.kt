package br.com.copilotoia99

import android.Manifest
import android.location.LocationManager
import android.location.Location
import android.os.CancellationSignal
import android.content.pm.PackageManager
import android.os.Build
import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.provider.Settings
import android.net.Uri
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import java.util.Locale

class MainActivity : Activity() {
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
        val field=EditText(this).apply {
            setText(initial); setSingleLine(true); textSize=20f; minHeight=dp(54)
            setPadding(dp(10),dp(8),dp(10),dp(8))
            setTextColor(ink); setHintTextColor(muted); background=rounded(Color.WHITE,Color.rgb(205,215,225))
            inputType=if(numeric) InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                      else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            contentDescription=label
        }
        if(!numeric) {
            root.addView(field)
            return field
        }
        val step=when {
            label.contains("Nota",true) -> 0.05
            label.contains("tempo",true) || label.contains("min)",true) -> 1.0
            label.contains("distância",true) || label.contains("(km)",true) -> 0.5
            label.contains("R$/km",true) -> 0.1
            label.contains("R$/hora",true) -> 1.0
            else -> 0.5
        }
        val maximum=if(label.contains("Nota",true)) 5.0 else 10000.0
        val decimals=if(step==0.05 || step==0.1) 2 else if(step==0.5) 1 else 0
        val line=LinearLayout(this).apply { orientation=LinearLayout.HORIZONTAL; gravity=Gravity.CENTER_VERTICAL }
        field.apply {
            isFocusable=false; isFocusableInTouchMode=false; isCursorVisible=false
            gravity=Gravity.CENTER; setTextIsSelectable(false)
        }
        fun adjust(direction: Int) {
            val current=field.text.toString().replace(',','.').toDoubleOrNull() ?: 0.0
            val next=((current+direction*step).coerceIn(0.0,maximum)/step).let {
                kotlin.math.round(it)*step
            }.coerceIn(0.0,maximum)
            field.setText(String.format(pt,"%.${decimals}f",next))
        }
        fun control(symbol: String, direction: Int): Button = Button(this).apply {
            text=symbol; textSize=26f; setTextColor(Color.WHITE); typeface=Typeface.DEFAULT_BOLD
            background=rounded(navy); minHeight=dp(54); minWidth=dp(64)
            contentDescription=(if(direction>0) "Aumentar " else "Diminuir ")+label
            setOnClickListener { adjust(direction) }
        }
        line.addView(control("−",-1),LinearLayout.LayoutParams(dp(64),dp(56)))
        line.addView(field,LinearLayout.LayoutParams(0,dp(56),1f).apply {
            leftMargin=dp(8); rightMargin=dp(8)
        })
        line.addView(control("+",1),LinearLayout.LayoutParams(dp(64),dp(56)))
        root.addView(line)
        return field
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
    private fun locationPermissionStatus(): String = when {
        checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED -> "precisa autorizada"
        checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED -> "aproximada autorizada"
        else -> "não autorizada"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,grantResults)
        if(requestCode==1002) {
            Toast.makeText(this,"Localização: "+locationPermissionStatus(),Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeviceDiagnostics() {
        val checks = DeviceHealthCheck.run(this)
        val requiredOk = checks.filter { it.required }.all { it.passed }
        val report = checks.joinToString("\n\n") { check ->
            (if (check.passed) "✓ " else if (check.required) "✗ " else "• ") + check.label + ": " + check.detail
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(if (requiredOk) "Copiloto • configuração básica pronta" else "Copiloto • configuração pendente")
            .setMessage(report + "\n\nO reconhecimento real das ofertas só pode ser validado durante o uso da 99.")
            .setPositiveButton("Entendi", null)
            .setNeutralButton("Verificar novamente") { _, _ -> showDeviceDiagnostics() }
            .setNegativeButton("Abrir acessibilidade") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) {
            // Recheck after returning from Android permissions settings.
            val checks = DeviceHealthCheck.run(this)
            getSharedPreferences("device_health", MODE_PRIVATE).edit()
                .putLong("last_check_at", System.currentTimeMillis())
                .putBoolean("required_ready", checks.filter { it.required }.all { it.passed }).apply()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor=navy
        window.navigationBarColor=navy
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
        store=OfferStore(this)
        limits=DriverLimits(prefs.getFloat("km",1.8f).toDouble(),prefs.getFloat("hour",35f).toDouble(),prefs.getFloat("fare",10f).toDouble(),prefs.getFloat("rating",4.7f).toDouble(),prefs.getFloat("pickupKm",8f).toDouble(),prefs.getFloat("pickupMin",15f).toDouble())
        val root=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(36)); setBackgroundColor(Color.rgb(244,247,250)) }
        scroll=ScrollView(this).apply { isFillViewport=true; clipToPadding=false; addView(root) }
        setContentView(scroll)
        button(root, "VERIFICAR CONFIGURAÇÃO DO DISPOSITIVO") { showDeviceDiagnostics() }
        if (savedInstanceState == null) scroll.post { showDeviceDiagnostics() }
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
        button(recorderCard,"REVISAR E ENVIAR DIAGNÓSTICO AO CHATGPT") {
            try { EvolutionRecorder(this).use { recorder ->
                val payload=recorder.report().toString(2)
                val message="Diagnóstico do Copiloto IA 99 (dados técnicos):\\n"+payload+
                    "\\nAnalise falhas de leitura de ofertas e sugira correções. Não presuma acesso remoto ao aplicativo."
                android.app.AlertDialog.Builder(this)
                    .setTitle("Revisar antes de compartilhar")
                    .setMessage(message)
                    .setNegativeButton("Cancelar",null)
                    .setPositiveButton("Escolher aplicativo") { _, _ ->
                        val intent=Intent(Intent.ACTION_SEND).apply {
                            type="text/plain"
                            putExtra(Intent.EXTRA_TEXT,message)
                        }
                        startActivity(Intent.createChooser(intent,"Escolha ChatGPT para enviar o diagnóstico"))
                    }.show()
            } } catch(e:Exception) {
                android.widget.Toast.makeText(this,"Não foi possível preparar o diagnóstico",android.widget.Toast.LENGTH_LONG).show()
            }
        }
        recorderCard.addView(text("Revise o texto antes de compartilhar. O envio só ocorre após você escolher o destino e confirmar no aplicativo escolhido. Nenhuma API ou servidor externo está conectado.",12f))
        val accessibilityCard=card(root,"🤖  ASSISTENTE AUTOMÁTICO")
        accessibilityCard.addView(text("Recomendado • Sem gravação contínua. O Android permite que o Copiloto leia o texto visível de ofertas compatíveis no app 99. Você continua no controle: ele não aceita nem recusa corridas.",15f))
        val accessibilityStatus=text(if(isAccessibilityServiceEnabled()) "Acessibilidade: ativada" else "Acessibilidade: desativada",14f,true)
        accessibilityCard.addView(accessibilityStatus)
        button(accessibilityCard,"PERMITIR JANELA FLUTUANTE") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
        }
        button(accessibilityCard,"ATIVAR AUTOMAÇÃO NO ANDROID") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        button(accessibilityCard,"TESTAR JANELA FLUTUANTE") {
            if (!Settings.canDrawOverlays(this)) {
                android.app.AlertDialog.Builder(this).setTitle("Permissão bloqueada")
                    .setMessage("Permita Aparecer sobre outros apps para o Copiloto IA 99. Depois repita o teste.")
                    .setPositiveButton("Abrir permissão") { _, _ ->
                        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName")))
                    }.setNegativeButton("Cancelar",null).show()
            } else {
                try {
                    val wm=getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
                    val view=TextView(this).apply {
                        text="COPILOTO IA 99 • Janela de teste funcionando"
                        textSize=17f; setTextColor(Color.WHITE); gravity=Gravity.CENTER
                        setPadding(dp(18),dp(18),dp(18),dp(18)); background=rounded(navy)
                    }
                    val params=android.view.WindowManager.LayoutParams(
                        -2,-2,android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        android.view.WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        android.view.WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        android.graphics.PixelFormat.TRANSLUCENT
                    ).apply { gravity=Gravity.TOP or Gravity.CENTER_HORIZONTAL; y=dp(90) }
                    wm.addView(view,params)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        try { wm.removeView(view) } catch (_: Exception) {}
                    },5000L)
                } catch(e:Exception) {
                    android.app.AlertDialog.Builder(this).setTitle("Falha na janela flutuante")
                        .setMessage("Não foi possível exibir a janela: ${e.javaClass.simpleName}")
                        .setPositiveButton("OK",null).show()
                }
            }
        }
        val locationStatus=text("Localização: "+locationPermissionStatus(),14f,true)
        accessibilityCard.addView(locationStatus)
        accessibilityCard.addView(text("Localização opcional, somente enquanto o Copiloto estiver aberto. Não há rastreamento em segundo plano nem envio de coordenadas.",12f))
        button(accessibilityCard,"AUTORIZAR LOCALIZAÇÃO") {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION), 1002)
            } else locationStatus.text="Localização: "+locationPermissionStatus()
        }
        button(accessibilityCard,"TESTAR LOCALIZAÇÃO ATUAL") {
            if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                locationStatus.text="Localização: autorize primeiro o acesso"
            } else {
                try {
                    val lm=getSystemService(Context.LOCATION_SERVICE) as LocationManager
                    val provider=when {
                        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED -> LocationManager.GPS_PROVIDER
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> null
                    }
                    if(provider==null) locationStatus.text="Localização: ative o GPS nas configurações do Android"
                    else {
                        locationStatus.text="Localização: buscando posição..."
                        lm.getCurrentLocation(provider, CancellationSignal(), mainExecutor) { loc: Location? ->
                            locationStatus.text=if(loc==null) "Localização indisponível. Verifique GPS e sinal."
                            else "Localização obtida • precisão aproximada: "+loc.accuracy.toInt()+" m. Nenhuma coordenada foi armazenada."
                        }
                    }
                } catch(e: SecurityException) { locationStatus.text="Localização: permissão revogada" }
                catch(e: Exception) { locationStatus.text="Localização: erro "+e.javaClass.simpleName }
            }
        }
        button(accessibilityCard,"VERIFICAR CONEXÃO COM A 99") {
            val state=getSharedPreferences("accessibility_status",Context.MODE_PRIVATE)
            val seen=state.getLong("last_99_seen_at",0L)
            val age=if(seen>0L) System.currentTimeMillis()-seen else Long.MAX_VALUE
            val connected=state.getBoolean("service_connected",false) && isAccessibilityServiceEnabled()
            val label=when {
                !connected -> "ACESSIBILIDADE DESCONECTADA"
                age in 0L..15000L -> "99 DETECTADA RECENTEMENTE"
                else -> "99 NÃO DETECTADA RECENTEMENTE"
            }
            val last=if(seen>0L) java.text.SimpleDateFormat("dd/MM HH:mm:ss",pt).format(java.util.Date(seen)) else "Nunca"
            val pkg=state.getString("last_99_package","Não identificado")
            android.app.AlertDialog.Builder(this).setTitle(label)
                .setMessage("Última detecção: "+last+"\nPacote: "+pkg+"\nServiço: "+(if(connected) "conectado" else "desconectado")+
                    "\n\nAbra a 99, volte ao Copiloto e repita esta verificação.")
                .setPositiveButton("OK",null).show()
        }
        button(accessibilityCard,"VERIFICAR STATUS") {
            accessibilityStatus.text=if(isAccessibilityServiceEnabled()) "Acessibilidade: ativada" else "Acessibilidade: desativada"
            val status=getSharedPreferences("accessibility_status",Context.MODE_PRIVATE)
            val notifications = if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) "permitidas" else "bloqueadas"
            } else "permissão não exigida"
            val details="Localização: "+locationPermissionStatus()+"\\nNotificações: "+notifications+"\nAcessibilidade: "+(if(isAccessibilityServiceEnabled()) "ativa" else "inativa")+"\nJanela flutuante: "+(if(Settings.canDrawOverlays(this)) "permitida" else "bloqueada")+
                "\nÚltimo estado: "+status.getString("last_status","Nenhuma leitura")+
                "\nOrigem: "+status.getString("last_source","Não identificada")+
                "\nLatência: "+status.getLong("last_latency_ms",-1L)+" ms"
            android.app.AlertDialog.Builder(this).setTitle("Autodiagnóstico")
                .setMessage(details).setPositiveButton("OK",null).show()
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
        privacy.addView(text("A leitura é experimental e pode não reconhecer todos os campos. A nota do passageiro só é usada quando a 99 disponibiliza esse dado na acessibilidade. O Copiloto não aceita nem recusa corridas. Seus registros ficam no aparelho e podem ser apagados ao desinstalar.",14f))

        // Home-first navigation: common actions first, advanced tools in Settings.
        homePanel=LinearLayout(this).apply {
            orientation=LinearLayout.VERTICAL
            setPadding(dp(16),dp(16),dp(16),dp(16))
            background=rounded(Color.WHITE,Color.rgb(224,231,238))
            elevation=dp(2).toFloat()
        }
        homePanel.addView(text("O QUE VOCÊ DESEJA FAZER?",17f,true).apply { setTextColor(navy) })
        homePanel.addView(text("Acesse rapidamente as funções principais. As ferramentas avançadas ficam em Configurações.",14f))
        val dailyPrefs=getSharedPreferences("daily_copilot",Context.MODE_PRIVATE)
        val dailyStatus=text(dailyGoalStatus(),14f,true)
        homePanel.addView(dailyStatus)
        button(homePanel,"🚗  LIGAR COPILOTO-A54") {
            val goalInput=EditText(this).apply {
                inputType=InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                setSingleLine(true)
                hint="Ex.: 300,00"
                setText(dailyPrefs.getFloat("target_reais",0f).takeIf { it>0f }?.let { String.format(pt,"%.2f",it) } ?: "")
                setPadding(dp(20),dp(18),dp(20),dp(18))
            }
            val dialog=android.app.AlertDialog.Builder(this)
                .setTitle("Quanto pretende faturar hoje?")
                .setMessage("Informe sua meta de faturamento bruto do dia em reais. A meta será salva somente no celular. A leitura automática depende da acessibilidade ativa.")
                .setView(goalInput)
                .setNegativeButton("Cancelar",null)
                .setPositiveButton("LIGAR",null)
                .create()
            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value=goalInput.text.toString().trim().replace(',','.').toDoubleOrNull()
                    if(value==null || !value.isFinite() || value<=0.0 || value>100000.0) {
                        goalInput.error="Informe uma meta válida entre R$ 0,01 e R$ 100.000"
                    } else {
                        val today=java.text.SimpleDateFormat("yyyy-MM-dd",pt).format(java.util.Date())
                        dailyPrefs.edit().putString("day",today).putFloat("target_reais",value.toFloat()).putBoolean("enabled",true).apply()
                        dailyStatus.text=dailyGoalStatus()
                        dialog.dismiss()
                        if(!isAccessibilityServiceEnabled()) {
                            android.app.AlertDialog.Builder(this)
                                .setTitle("Meta salva • ativar leitura")
                                .setMessage("Sua meta foi salva. Para analisar as ofertas automaticamente, ative o serviço de acessibilidade Copiloto IA 99 nas configurações do Android.")
                                .setPositiveButton("ABRIR ACESSIBILIDADE") { _, _ -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
                                .setNegativeButton("Depois",null).show()
                        } else if(!Settings.canDrawOverlays(this)) {
                            android.app.AlertDialog.Builder(this)
                                .setTitle("Meta salva • liberar janela")
                                .setMessage("Autorize a janela flutuante para exibir avaliações das ofertas sobre a 99.")
                                .setPositiveButton("ABRIR PERMISSÃO") { _, _ -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) }
                                .setNegativeButton("Depois",null).show()
                        } else Toast.makeText(this,"Meta diária salva. Leitura automática disponível quando a 99 estiver aberta.",Toast.LENGTH_LONG).show()
                    }
                }
            }
            dialog.show()
        }
        button(homePanel,"⏹  DESLIGAR COPILOTO-A54") {
            dailyPrefs.edit().putBoolean("enabled",false).apply()
            dailyStatus.text=dailyGoalStatus()
            Toast.makeText(this,"Meta diária pausada. O serviço de acessibilidade deve ser desligado separadamente nas configurações do Android.",Toast.LENGTH_LONG).show()
        }
        button(homePanel,"🤖  ASSISTENTE AUTOMÁTICO") { showSections(2) }
        button(homePanel,"🧮  ANALISAR UMA CORRIDA") { showSections(3) }
        button(homePanel,"📊  MEU HISTÓRICO") { showSections(6) }
        button(homePanel,"⚙️  CONFIGURAÇÕES E FERRAMENTAS") { showSections(0,1,4,5,7) }
        button(homePanel,"🔄  VERIFICAR ATUALIZAÇÕES") { checkForUpdates() }
        root.addView(homePanel,3)
        sections.forEach { section ->
            section.visibility=View.GONE
            button(section,"←  VOLTAR AO INÍCIO") { goHome() }
        }
        refresh()
    }
    private fun dailyGoalStatus(): String {
        val p=getSharedPreferences("daily_copilot",Context.MODE_PRIVATE)
        val today=java.text.SimpleDateFormat("yyyy-MM-dd",pt).format(java.util.Date())
        if(p.getString("day","")!=today) return "Copiloto-A54: informe a meta de faturamento de hoje"
        val target=p.getFloat("target_reais",0f)
        return if(p.getBoolean("enabled",false)) String.format(pt,"Meta de hoje: R$ %.2f • acompanhamento solicitado",target)
        else String.format(pt,"Meta de hoje: R$ %.2f • pausada",target)
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

    private fun checkForUpdates() {
        Toast.makeText(this, "Verificando atualizações...", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val connection = (java.net.URL("https://api.github.com/repos/jamillucio1993-create/copiloto-ia-99/releases/latest").openConnection() as java.net.HttpURLConnection)
                connection.connectTimeout = 8000
                connection.readTimeout = 8000
                connection.setRequestProperty("Accept", "application/vnd.github+json")
                connection.setRequestProperty("User-Agent", "Copiloto-IA-99")
                val response = try {
                    if (connection.responseCode == 200) org.json.JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    else null
                } finally { connection.disconnect() }
                runOnUiThread {
                    if (response == null) {
                        android.app.AlertDialog.Builder(this)
                            .setTitle("Atualizações")
                            .setMessage("Nenhuma versão publicada disponível agora. Verifique a página oficial de Releases.")
                            .setNegativeButton("Fechar", null)
                            .setPositiveButton("Ver Releases") { _, _ -> openUpdateUrl("https://github.com/jamillucio1993-create/copiloto-ia-99/releases") }
                            .show()
                    } else {
                        val tag = response.optString("tag_name")
                        val current = packageManager.getPackageInfo(packageName, 0).versionName ?: "0"
                        fun versionParts(s: String) = Regex("""\d+""").findAll(s).take(3).map { it.value.toIntOrNull() ?: 0 }.toList()
                        val installed = versionParts(current)
                        val published = versionParts(tag)
                        val newer = published.isNotEmpty() && (0..2).firstOrNull {
                            published.getOrElse(it) { 0 } != installed.getOrElse(it) { 0 }
                        }?.let { published.getOrElse(it) { 0 } > installed.getOrElse(it) { 0 } } == true
                        val assets = response.optJSONArray("assets")
                        var apkUrl = ""
                        if (assets != null) for (i in 0 until assets.length()) {
                            val asset = assets.optJSONObject(i) ?: continue
                            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                                apkUrl = asset.optString("browser_download_url")
                                break
                            }
                        }
                        val releaseUrl = response.optString("html_url").takeIf { it.startsWith("https://github.com/") }
                            ?: "https://github.com/jamillucio1993-create/copiloto-ia-99/releases"
                        val message = if (newer) "Nova versão disponível: $tag\nInstalada: $current\n\nO Android solicitará sua confirmação para instalar."
                            else "Versão instalada: $current\nÚltima publicada: $tag\n\nNenhuma atualização mais recente foi identificada."
                        val dialog = android.app.AlertDialog.Builder(this)
                            .setTitle("Atualização do Copiloto IA 99")
                            .setMessage(message)
                            .setNegativeButton("Fechar", null)
                        if (newer && apkUrl.startsWith("https://github.com/")) {
                            dialog.setPositiveButton("BAIXAR ATUALIZAÇÃO") { _, _ -> openUpdateUrl(apkUrl) }
                        } else {
                            dialog.setPositiveButton("VER RELEASES") { _, _ -> openUpdateUrl(releaseUrl) }
                        }
                        dialog.show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Atualizações")
                        .setMessage("Não foi possível consultar o GitHub. Verifique a internet e tente novamente.")
                        .setPositiveButton("OK", null).show()
                }
            }
        }.start()
    }

    private fun openUpdateUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "Não foi possível abrir o download.", Toast.LENGTH_LONG).show() }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${OfferAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
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

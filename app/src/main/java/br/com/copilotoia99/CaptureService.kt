package br.com.copilotoia99

import android.app.*
import android.content.*
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.TextView
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Explicit-consent screen session. Does not click, accept or reject 99 rides. */
class CaptureService: Service() {
    companion object {
        const val EXTRA_RESULT_CODE="capture_result_code"
        const val EXTRA_DATA="capture_data"
        const val ACTION_STOP="br.com.copilotoia99.STOP"
        const val ACTION_UPDATE="br.com.copilotoia99.UPDATE"
        const val EXTRA_SUMMARY="summary"
    }
    private val handler=Handler(Looper.getMainLooper())
    private val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private var projection:MediaProjection?=null
    private var display:VirtualDisplay?=null
    private var reader:ImageReader?=null
    private var overlay:View?=null
    private var wm:WindowManager?=null
    private var busy=false
    private var lastScan=0L
    private var lastSeen=0L
    private var lastFingerprint=""
    private var stopped=false
    private val channel="copiloto_capture"
    override fun onBind(intent:Intent?)=null
    override fun onCreate(){super.onCreate();val nm=getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel(channel,"Captura autorizada do Copiloto",NotificationManager.IMPORTANCE_LOW))}
    override fun onStartCommand(intent:Intent?, flags:Int, startId:Int):Int {
        if(intent?.action==ACTION_STOP){stopSelf();return START_NOT_STICKY}
        if(projection!=null)return START_NOT_STICKY
        val code=intent?.getIntExtra(EXTRA_RESULT_CODE,Activity.RESULT_CANCELED)?:Activity.RESULT_CANCELED
        @Suppress("DEPRECATION") val data=if(Build.VERSION.SDK_INT>=33)intent?.getParcelableExtra(EXTRA_DATA,Intent::class.java) else intent?.getParcelableExtra(EXTRA_DATA)
        if(code!=Activity.RESULT_OK||data==null){stopSelf();return START_NOT_STICKY}
        val stop=PendingIntent.getService(this,1,Intent(this,CaptureService::class.java).setAction(ACTION_STOP),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=Notification.Builder(this,channel).setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Copiloto IA 99 ativo").setContentText("Captura de tela autorizada; toque para encerrar").setOngoing(true).addAction(Notification.Action.Builder(null,"Parar",stop).build()).build()
        if(Build.VERSION.SDK_INT>=29)startForeground(88,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(88,notification)
        val mgr=getSystemService(MediaProjectionManager::class.java)
        try {
            projection=mgr.getMediaProjection(code,data)
            projection?.registerCallback(object:MediaProjection.Callback(){override fun onStop(){handler.post{stopSelf()}}},handler)
            val metrics=resources.displayMetrics
            reader=ImageReader.newInstance(metrics.widthPixels,metrics.heightPixels,android.graphics.PixelFormat.RGBA_8888,2)
            reader?.setOnImageAvailableListener({r->handleFrame(r)},handler)
            display=projection?.createVirtualDisplay("CopilotoA54",metrics.widthPixels,metrics.heightPixels,metrics.densityDpi,DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,reader?.surface,null,handler)
            showOverlay("Copiloto: aguardando oferta")
            EvolutionRecorder.recordAsync(this,"capture_started")
        }catch(e:Exception){publish("Falha na captura: ${e.javaClass.simpleName}");stopSelf()}
        return START_NOT_STICKY
    }
    private fun handleFrame(r:ImageReader){
        val img=r.acquireLatestImage()?:return
        val now=SystemClock.elapsedRealtime()
        if(busy||now-lastScan<650){img.close();if(lastSeen>0&&now-lastSeen>1500)clearOffer();return}
        lastScan=now
        val plane=img.planes[0];val buf=plane.buffer
        val bitmap=Bitmap.createBitmap(img.width+((plane.rowStride-plane.pixelStride*img.width)/plane.pixelStride),img.height,Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buf);img.close()
        val cropped=Bitmap.createBitmap(bitmap,0,0,resources.displayMetrics.widthPixels.coerceAtMost(bitmap.width),resources.displayMetrics.heightPixels.coerceAtMost(bitmap.height))
        if(cropped!==bitmap)bitmap.recycle()
        busy=true
        recognizer.process(InputImage.fromBitmap(cropped,0)).addOnSuccessListener { result ->
            val parsed=OfferTextParser.parse(result.text)
            if(parsed.complete){
                lastSeen=SystemClock.elapsedRealtime()
                val fingerprint="${parsed.price}:${parsed.pickupKm}:${parsed.tripKm}:${parsed.pickupMinutes}:${parsed.tripMinutes}"
                if(fingerprint!=lastFingerprint){
                    lastFingerprint=fingerprint
                    EvolutionRecorder.recordAsync(this,"offer_detected",SystemClock.elapsedRealtime()-lastScan,"parsed")
                    val offer=OfferInput(parsed.price!!,parsed.pickupKm!!,parsed.tripKm!!,parsed.pickupMinutes!!,parsed.tripMinutes!!,4.99,"Desconhecida")
                    val p=getSharedPreferences("driver_limits",MODE_PRIVATE)
                    val limits=DriverLimits(p.getFloat("km",1.8f).toDouble(),p.getFloat("hour",35f).toDouble(),p.getFloat("fare",10f).toDouble(),0.0,p.getFloat("pickupKm",8f).toDouble(),p.getFloat("pickupMin",15f).toDouble())
                    val evaluation=OfferEvaluator.evaluate(offer,limits)
                    val summary="${evaluation.verdict} ${evaluation.score}/100 | R$ %.2f/km | R$ %.2f/h | NOTA NÃO LIDA".format(evaluation.perKm,evaluation.perHour)
                    showOverlay(summary)
                    publish(summary)
                    // OCR doesn't establish passenger rating: never persist as verified or use a rating threshold.
                    Thread { try{OfferStore(applicationContext).use {it.add(offer,evaluation)}}catch(_:Exception){} }.start()
                }
            }else if(lastSeen>0&&SystemClock.elapsedRealtime()-lastSeen>1500)clearOffer()
        }.addOnFailureListener{EvolutionRecorder.recordAsync(this,"ocr_failed");publish("Não foi possível interpretar a tela")}.addOnCompleteListener{cropped.recycle();busy=false}
    }
    private fun clearOffer(){EvolutionRecorder.recordAsync(this,"offer_ended");lastSeen=0;lastFingerprint="";showOverlay("Oferta encerrada / aguardando nova oferta")}
    private fun showOverlay(msg:String){
        if(!Settings.canDrawOverlays(this))return
        if(wm==null)wm=getSystemService(WINDOW_SERVICE) as WindowManager
        if(overlay==null){
            val tv=TextView(this).apply{setTextColor(android.graphics.Color.WHITE);setBackgroundColor(0xE51C2732.toInt());textSize=14f;setPadding(20,14,20,14);maxLines=3}
            val params=WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,android.graphics.PixelFormat.TRANSLUCENT).apply{gravity=Gravity.TOP; y=(resources.displayMetrics.density*38).toInt()}
            try{wm?.addView(tv,params);overlay=tv}catch(_:Exception){}
        }
        (overlay as? TextView)?.text=msg
    }
    private fun publish(msg:String){sendBroadcast(Intent(ACTION_UPDATE).setPackage(packageName).putExtra(EXTRA_SUMMARY,msg))}
    override fun onDestroy(){EvolutionRecorder.recordAsync(this,"capture_stopped");stopped=true;reader?.setOnImageAvailableListener(null,null);display?.release();reader?.close();projection?.stop();recognizer.close();if(overlay!=null)try{wm?.removeView(overlay)}catch(_:Exception){};super.onDestroy()}
}

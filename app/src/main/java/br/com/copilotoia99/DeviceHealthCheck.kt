package br.com.copilotoia99
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings

data class DeviceCheck(val label: String, val passed: Boolean, val detail: String, val required: Boolean)
object DeviceHealthCheck {
    fun run(context: Context): List<DeviceCheck> {
        val component = ComponentName(context, OfferAccessibilityService::class.java)
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == component } == true
        val overlay = Settings.canDrawOverlays(context)
        val location = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val notifications = Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return listOf(
            DeviceCheck("Acessibilidade", enabled, if (enabled) "Ativada" else "Ativar nas configurações do Android", true),
            DeviceCheck("Janela flutuante", overlay, if (overlay) "Autorizada" else "Permitir aparecer sobre outros aplicativos", true),
            DeviceCheck("Captura OCR", Build.VERSION.SDK_INT >= 30, "Android compatível; captura real só é verificada ao receber oferta", true),
            DeviceCheck("Notificações", notifications, if (notifications) "Autorizadas" else "Permissão opcional pendente", false),
            DeviceCheck("Localização", location, if (location) "Autorizada" else "Permissão opcional pendente", false)
        )
    }
}

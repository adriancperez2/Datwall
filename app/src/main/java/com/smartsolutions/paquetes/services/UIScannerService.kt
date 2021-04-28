
package com.smartsolutions.paquetes.services

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.smartsolutions.paquetes.helpers.USSDHelper

/**
 * Servicio de accesibilidad encargado de obtener el resultado de los
 * códigos ussd.
 * */
class UIScannerService : AccessibilityService() {

    /**
     * Indica si se está esperando el resultado de un código ussd
     * */
    private var waitingUssdCodeResult = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {
            ACTION_WAIT_USSD_CODE -> waitingUssdCodeResult = true
            ACTION_CANCEL_WAIT_USSD_CODE -> waitingUssdCodeResult = false
        }

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {

        val response = event.text.toTypedArray()

        /*
        * Si se está esperando el resultado de un código ussd y
        * es el código esperado.
        * */
        if (waitingUssdCodeResult && isExpectedUSSDCode(response)) {
            //Cancelo la espera.
            waitingUssdCodeResult = false

            //Preparo el intent.
            val intent = Intent(USSDHelper.ACTION_SEND_USSD_REQUEST)
                .putExtra(USSDHelper.EXTRA_RESULT, true)
                .putExtra(USSDHelper.EXTRA_RESPONSE, response)

            //Lanzo el broadcast.
            LocalBroadcastManager.getInstance(this)
                .sendBroadcast(intent)

            //Presiono el botón atras para cerrar el diálogo.
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
    }

    /**
     * Indica si es o no el resultado esperado.
     * */
    private fun isExpectedUSSDCode(textList: Array<CharSequence>): Boolean {
        val phoneServiceName = packageManager
            .getApplicationLabel(packageManager.getApplicationInfo("com.android.phone", 0))

        var string = ""

        textList.forEach {
            string += it.toString()
        }

        return !string.contains(phoneServiceName, true) &&
                !string.contains("ussd", true)
    }

    override fun onInterrupt() {

    }

    companion object {

        /**
         * Activa la espera del resultado de la ejecución de un código ussd.
         * */
        const val ACTION_WAIT_USSD_CODE = "com.smartsolutions.paquetes.action.WAIT_USSD_CODE"

        /**
         * Cancela la espera del resultado de la ejecución de un código ussd.
         * */
        const val ACTION_CANCEL_WAIT_USSD_CODE = "com.smarsolutions.paquetes.action.CANCEL_WAIT_USSD_CODE"
    }
}
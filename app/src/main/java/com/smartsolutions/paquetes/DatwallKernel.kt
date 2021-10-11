package com.smartsolutions.paquetes

import android.app.Activity
import android.app.ActivityManager
import android.app.PendingIntent
import android.content.*
import android.net.ConnectivityManager
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.MutableLiveData
import com.smartsolutions.paquetes.exceptions.MissingPermissionException
import com.smartsolutions.paquetes.helpers.*
import com.smartsolutions.paquetes.managers.contracts.*
import com.smartsolutions.paquetes.receivers.ChangeNetworkReceiver
import com.smartsolutions.paquetes.services.BubbleFloatingService
import com.smartsolutions.paquetes.services.DatwallService
import com.smartsolutions.paquetes.ui.MainActivity
import com.smartsolutions.paquetes.ui.PresentationActivity
import com.smartsolutions.paquetes.ui.SplashActivity
import com.smartsolutions.paquetes.ui.activation.ActivationActivity
import com.smartsolutions.paquetes.ui.permissions.PermissionsActivity
import com.smartsolutions.paquetes.ui.setup.SetupActivity
import com.smartsolutions.paquetes.watcher.*
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.CoroutineContext

@Singleton
class DatwallKernel @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val activationManager: IActivationManager,
    private val permissionManager: IPermissionsManager,
    private val configurationManager: IConfigurationManager,
    private val updateManager: IUpdateManager,
    private val dataPackageManager: IDataPackageManager,
    private val changeNetworkReceiver: Lazy<ChangeNetworkReceiver>,
    private val changeNetworkCallback: Lazy<ChangeNetworkCallback>,
    private val notificationHelper: NotificationHelper,
    private val packageMonitor: PackageMonitor,
    private val networkUtils: NetworkUtils,
    private val legacyConfiguration: LegacyConfigurationHelper,
    private val simManager: ISimManager,
    private val firewallHelper: FirewallHelper
) : IChangeNetworkHelper, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Default

    private var datwallBinder: DatwallService.DatwallBinder? = null

    private val mainServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            datwallBinder = service as DatwallService.DatwallBinder
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            datwallBinder = null
        }

    }

    val launchActivity = MutableLiveData<Class<out Activity>>()

    init {
        launch {

            context.dataStore.data.collect {
                BUBBLE_ON = it[PreferencesKeys.ENABLED_BUBBLE_FLOATING] == true
                FIREWALL_ON = it[PreferencesKeys.ENABLED_FIREWALL] == true
            }
        }
    }

    /**
     * Función principal que maqueta e inicia todos los servicios de la aplicación
     * y la actividad principal si está en primer plano.
     * */
    fun main(activity: Activity? = null) {

        //Crea los canales de notificaciones
        createNotificationChannels()

        //Restablece la configuración de la versión anterior
        setLegacyConfiguration()

        if (isInForeground()/*activity != null*/) {
            mainInForeground(activity)
        } else {
            mainInBackground()
        }
    }

    /**
     * Función principal que maqueta e inicia todos los servicios de la aplicación
     * y la actividad principal.
     * */
    private fun mainInForeground(activity: Activity?) {

        launch {
            //Crea o actualiza los paquetes de datos
            createOrUpdatePackages()

            when {
                //Verifica los permisos
                missingSomePermission() -> {
                    openPermissionsActivity()
                }
                //Verfica el registro y la activación
                !isRegisteredAndValid() -> {
                    openActivationActivity()
                }
                //Verfica las configuraciones iniciales
                missingSomeConfiguration() -> {
                    openSetupActivity()
                }
                else -> {
                    //Sincroniza la base de datos y enciende el rastreador
                    synchronizeDatabase()
                    //Inicia los servicios
                    startServices()
                    //Registra los broadcasts y los callbacks
                    registerBroadcastsAndCallbacks()
                    //Registra los workers
                    registerWorkers()
                    //Inicia la actividad principal
                    startMainActivity()
                }
            }
            //Cierra la actividad anterior
            activity?.finish()
        }
    }

    private fun mainInBackground() {

        launch {
            createOrUpdatePackages()

            when {
                missingSomePermission() -> {
                    notify(
                        context.getString(R.string.missing_permmissions_title_notification),
                        context.getString(R.string.missing_permmissions_description_notification)
                    )
                }
                !isRegisteredAndValid() -> {
                    notify(
                        context.getString(R.string.generic_needed_action_title_notification),
                        context.getString(R.string.generic_needed_action_description_notification)
                    )
                }
                missingSomeConfiguration() -> {
                    notify(
                        context.getString(R.string.missing_configuration_title_notification),
                        context.getString(R.string.missing_configuration_description_notification)
                    )
                }
                else -> {
                    synchronizeDatabase()
                    startServices()
                    registerBroadcastsAndCallbacks()
                    registerWorkers()
                }
            }
        }
    }

    private suspend fun createOrUpdatePackages() {
        withContext(Dispatchers.IO) {
            dataPackageManager.createOrUpdateDataPackages()
        }
    }

    /**
     * Establece las configuraciones del cortafuegos y la burbuja flotante
     * usando la versión anterior.
     *
     * Este método se eliminará en versiones posteriores.
     * */
    private fun setLegacyConfiguration() {
        if (!legacyConfiguration.isConfigurationRestored()) {
            legacyConfiguration.setFirewallLegacyConfiguration()
            legacyConfiguration.setBubbleFloatingLegacyConfiguration()
        }
    }

    /**
     * Se invoca cuando se encienden los datos móbiles.
     * */
    override fun setDataMobileStateOn() {
        (context as DatwallApplication).dataMobileOn = true

        datwallBinder?.startTrafficRegistration()

        launch {
            if (FIREWALL_ON) {
                startFirewall()
            }

            if (BUBBLE_ON) {
                startBubbleFloating()
            }

            if (networkUtils.getNetworkGeneration() == NetworkUtils.NetworkType.NETWORK_4G) {
                context.dataStore.edit {
                    it[PreferencesKeys.ENABLED_LTE] = true
                }
            }
        }
    }

    /**
     * Se invoca cuando se apagan los datos móbiles.
     * */
    override fun setDataMobileStateOff() {
        (context as DatwallApplication).dataMobileOn = false

        datwallBinder?.stopTrafficRegistration()

        if (FIREWALL_ON) {
          stopFirewall()
        }

        if (BUBBLE_ON) {
            stopBubbleFloating()
        }
    }

    /**
     * Indica si la aplicación ya está registrada en el servidor y
     * no está obsoleta o descontinuada.
     * */
    private suspend fun isRegisteredAndValid(): Boolean {
        val status = activationManager.canWork().second

        return status != IActivationManager.ApplicationStatuses.Discontinued &&
                status != IActivationManager.ApplicationStatuses.Unknown &&
                status != IActivationManager.ApplicationStatuses.Deprecated &&
                status != IActivationManager.ApplicationStatuses.TooMuchOld
    }

    private fun openActivationActivity() {
        openActivity(ActivationActivity::class.java)
    }

    /**
     * Indica si falta alguna configuración importante.
     * */
    private suspend fun missingSomeConfiguration(): Boolean {
        return configurationManager.getUncompletedConfigurations()
            .isNotEmpty()
    }

    private fun openSetupActivity() {
        openActivity(SetupActivity::class.java)
    }

    /**
     * Indica si falta algún permiso.
     * */
    private fun missingSomePermission(): Boolean {
        return permissionManager.getDeniedPermissions().isNotEmpty()
    }

    /**
     * Pide los permisos faltantes.
     * */
    private fun openPermissionsActivity() {
        openActivity(PermissionsActivity::class.java)
    }

    /**
     * Registra los broadcasts y los callbacks.
     * */
    @Suppress("DEPRECATION")
    private fun registerBroadcastsAndCallbacks() {

        //Detecta los cambios de las Sim
        simManager.registerSubscriptionChangedListener()

        //En apis 22 o menor se registra un receiver para escuchar los cambios de redes.
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            val filter = IntentFilter()
            filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)

            if (!changeNetworkReceiver.get().isRegister) {
                changeNetworkReceiver.get().register(context, filter)
            }
        } else {
            /* Si el sdk es api 23 o mayor se registra un callback de tipo
             * NetworkCallback en el ConnectivityManager para escuchar los cambios de redes.
             **/
            if (!changeNetworkCallback.get().isRegistered) {
                ContextCompat.getSystemService(context, ConnectivityManager::class.java)?.let {
                    changeNetworkCallback.get().register(it)
                }
            }
        }
    }

    /**
     * Registra los workers.
     * */
    private fun registerWorkers() {
        updateApplicationStatusJob = launch {
            if (!updateManager.wasScheduleUpdateApplicationStatusWorker()) {
                context.dataStore.data.collect {
                    val interval = it[PreferencesKeys.INTERVAL_UPDATE_SYNCHRONIZATION] ?: 24

                    updateManager.scheduleUpdateApplicationStatusWorker(interval)
                }
            }
        }
    }

    /**
     * Crea los canales de notificaciones.
     * */
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !notificationHelper.areCreatedNotificationChannels()
        ) {
            notificationHelper.createNotificationChannels()
        }
    }

    /**
     * Sincroniza la base de datos.
     * */
    private suspend fun synchronizeDatabase() {
        /* Fuerzo la sincronización de la base de datos para
         * garantizar la integridad de los datos. Esto no sobrescribe
         * los valores de acceso existentes.*/
        packageMonitor.forceSynchronization()
    }

    /**
     * Inicia los servicios.
     * */
    private fun startServices() {
        val datwallServiceIntent = Intent(context, DatwallService::class.java)

        if (isInForeground()) {
            context.startService(datwallServiceIntent)
        } else {
            ContextCompat.startForegroundService(context, datwallServiceIntent)
        }

        context.bindService(datwallServiceIntent, mainServiceConnection, Context.BIND_AUTO_CREATE)
    }

    /**
     * Inicia la actividad principal.
     * */
    private fun startMainActivity() {
        openActivity(MainActivity::class.java)
    }

    /**
     * Detiene todos los servicios y trabajos de la aplicación.
     * */
    fun stopAllDatwall(){

        simManager.unregisterSubscriptionChangedListener()

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {

            if (changeNetworkReceiver.get().isRegister) {
                changeNetworkReceiver.get().unregister(context)
            }
        } else {
            if (changeNetworkCallback.get().isRegistered) {
                ContextCompat.getSystemService(context, ConnectivityManager::class.java)?.let {
                    changeNetworkCallback.get().unregister(it)
                }
            }
        }

        updateManager.cancelUpdateApplicationStatusWorker()

        context.unbindService(mainServiceConnection)

        context.startService(Intent(context, DatwallService::class.java)
            .setAction(DatwallService.ACTION_STOP))

        stopBubbleFloating()
        stopFirewall()
    }

    private suspend fun startFirewall() {
        if (activationManager.canWork().first) {
            if (firewallHelper.startFirewallService() != null)
                throw MissingPermissionException(IPermissionsManager.VPN_PERMISSION_KEY)
        }
    }

    private suspend fun startBubbleFloating(){
        val isGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val permission = permissionManager.findPermission(IPermissionsManager.DRAW_OVERLAYS_CODE)
            if (permission?.checkPermission?.invoke(permission, context) == true){
                true
            }else {
                throw MissingPermissionException(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
            }
        } else {
            true
        }

        if (isGranted && activationManager.canWork().first) {
            try {
                context.startService(Intent(context, BubbleFloatingService::class.java))
            }catch (e: Exception){

            }
        }
    }

    fun stopBubbleFloating(turnOf: Boolean = false){
        if (turnOf){
            launch {
                context.dataStore.edit {
                    it[PreferencesKeys.ENABLED_BUBBLE_FLOATING] = false
                }
            }
        }
        try {
            context.stopService(Intent(context, BubbleFloatingService::class.java))
        }catch (e: Exception){

        }
    }

    fun stopFirewall(turnOf: Boolean = false){
        if (turnOf) {
            firewallHelper.establishFirewallEnabled(false)
        }

        firewallHelper.stopFirewallService()
    }

    private fun openActivity(activity: Class<out Activity>) {
        launchActivity.postValue(activity)
        /*ContextCompat.startActivity(
            context,
            Intent(context, activity)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            null
        )*/
    }

    fun isInForeground(): Boolean {
        val activityManager = ContextCompat.getSystemService(
            context,
            ActivityManager::class.java
        ) ?: return false

        return activityManager.runningAppProcesses.any {
            it.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                    it.processName == context.packageName
        }
    }

    private fun notify(title: String, description: String) {
        notificationHelper.notify(
            NotificationHelper.ALERT_NOTIFICATION_ID,
            notificationHelper.buildNotification(
                NotificationHelper.ALERT_CHANNEL_ID
            ).apply {
                setContentTitle(title)
                setContentText(description)
                setContentIntent(
                    PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, SplashActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                        } else {
                            PendingIntent.FLAG_UPDATE_CURRENT
                        }
                    )
                )
            }.build()
        )
    }

    companion object {
        private var updateApplicationStatusJob: Job? = null
        private var BUBBLE_ON = false
        private var FIREWALL_ON = false
    }
}
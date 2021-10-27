package com.smartsolutions.paquetes.helpers

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import com.smartsolutions.paquetes.PreferencesKeys
import com.smartsolutions.paquetes.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

/**
 * Obtiene las configuraciones de la versión anterior de la aplicación.
 * */
class LegacyConfigurationHelper @Inject constructor(
    @ApplicationContext
    private val context: Context
) : CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private lateinit var preferences: SharedPreferences

    init {
        launch {
            preferences = context
                .getSharedPreferences("data_mis_datos", Context.MODE_PRIVATE)
        }
    }

    /**
     * Indica si ya la versión anterior de la aplicación ha sido comprada.
     * */
    suspend fun isPurchased(): Boolean {
        return withContext(Dispatchers.IO) {
            preferences.getBoolean("l_p_f", false)
        }
    }

    /**
     * Obtiene todas los nombres de paquetes de las aplicaciones permitidas de la
     * versión anterior.
     *
     * @return [List] con los nombres de paquetes de las aplicaciones
     * permitidas por el cortafuegos.
     * */
    suspend fun getLegacyRules(): List<String> {
        val db = withContext(Dispatchers.IO) {
            context.openOrCreateDatabase("rules.db", Context.MODE_PRIVATE, null)
        }

        val result = mutableListOf<String>()

        try {
            withContext(Dispatchers.IO) {
                val cursor = db.query(
                    "apps",
                    arrayOf("package_name"),
                    "data_access = ?",
                    arrayOf("1"),
                    null,
                    null,
                    null
                )

                if (cursor.moveToFirst()) {
                    var packageName = cursor.getString(cursor.getColumnIndex("package_name"))

                    result.add(packageName)

                    while (cursor.moveToNext()) {
                        packageName = cursor.getString(cursor.getColumnIndex("package_name"))

                        result.add(packageName)
                    }
                }
                cursor.close()
            }

        } catch (e: Exception) {

        }

        return result
    }

    /**
     * Establece en el SharedPreferences que la configuración
     * ya fué restaurada.
     * */
    fun setConfigurationRestored() {
        launch {
            preferences.edit()
                .putBoolean(DB_CONFIGURATION_RESTORED, true)
                .apply()
        }
    }

    /**
     * Indica si la configuración de la base de datos
     * ya fué restaurada.
     * */
    suspend fun isConfigurationRestored(): Boolean {
        return withContext(Dispatchers.IO) {
            preferences.getBoolean(DB_CONFIGURATION_RESTORED, false)
        }
    }

    /**
     * Establece en el dataStore la configuración del cortafuegos de la versión anterior.
     * */
    fun setFirewallLegacyConfiguration() {
        launch {
            val preferences = context.getSharedPreferences(
                "com.smartsolutions.paquetes_preferences",
                Context.MODE_PRIVATE
            )

            context.settingsDataStore.edit {
                it[PreferencesKeys.ENABLED_FIREWALL] = preferences
                    .getBoolean("firewall_running", false)
            }
        }
    }

    /**
     * Establece en el dataStore la configuración de la burbuja
     * flotante de la versión anterior.
     * */
    fun setBubbleFloatingLegacyConfiguration() {
        launch {
            val preferences = context.getSharedPreferences(
                "com.smartsolutions.paquetes_preferences",
                Context.MODE_PRIVATE
            )

            context.settingsDataStore.edit {
                it[PreferencesKeys.ENABLED_BUBBLE_FLOATING] = preferences
                    .getBoolean("widget_floating", false)
            }
        }
    }

    companion object {
        const val DB_CONFIGURATION_RESTORED = "db_configuration_restored"
    }
}
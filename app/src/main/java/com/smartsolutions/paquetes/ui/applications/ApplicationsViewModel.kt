package com.smartsolutions.paquetes.ui.applications

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.*
import com.smartsolutions.paquetes.PreferencesKeys
import com.smartsolutions.paquetes.R
import com.smartsolutions.paquetes.dataStore
import com.smartsolutions.paquetes.managers.contracts.IIconManager
import com.smartsolutions.paquetes.repositories.contracts.IAppRepository
import com.smartsolutions.paquetes.repositories.models.App
import com.smartsolutions.paquetes.repositories.models.AppGroup
import com.smartsolutions.paquetes.repositories.models.IApp
import com.smartsolutions.paquetes.repositories.models.TrafficType
import com.smartsolutions.paquetes.ui.firewall.AppsListAdapter
import dagger.Lazy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import javax.inject.Inject

@HiltViewModel
class ApplicationsViewModel @Inject constructor(
    application: Application,
    private val appRepository: IAppRepository,
    val iconManager: Lazy<IIconManager>
) : AndroidViewModel(application) {

    /**
     * Lista de aplicaciones pendientes a actualizar.
     * */
    val appsToUpdate = mutableListOf<IApp>()

    private val _apps = MutableLiveData<List<IApp>>()

    private var filter: AppsFilter? = null

    private var key: String? = null

    init {
        viewModelScope.launch {
            getApplication<Application>()
                .dataStore.data.collect {
                    filter = AppsFilter.valueOf(
                        it[PreferencesKeys.APPS_FILTER] ?:
                        AppsFilter.Alphabetic.name)

                    if (_apps.value != null && key != null) {
                        _apps.postValue(orderAppsByFilter(key!!, filter!!))
                    }
                }
        }
    }

    /**
     * Confirma la actualización de las aplicaciones pendientes.
     * */
    fun commitUpdateApps(): Boolean {
        if (appsToUpdate.isNotEmpty()) {
            GlobalScope.launch(Dispatchers.IO) {
                appRepository.update(appsToUpdate)
                appsToUpdate.clear()
            }
            return true
        }
        return false
    }

    fun setFilter(filter: AppsFilter) {
        viewModelScope.launch {
            getApplication<Application>()
                .dataStore.edit {
                    it[PreferencesKeys.APPS_FILTER] = filter.name
                }
        }
    }

    fun getApps(key: String): LiveData<List<IApp>> {
        return appRepository.flowByGroup()
            .combine(getApplication<Application>().dataStore.data) { apps, preferences ->
                val filter = AppsFilter.valueOf(
                    preferences[PreferencesKeys.APPS_FILTER] ?:
                    AppsFilter.Alphabetic.name)

                return@combine when (key) {
                    SectionsPagerAdapter.USER_APPS -> {
                        orderAppsByFilter(
                            apps.filter { !it.system },
                            filter
                        )
                    }
                    SectionsPagerAdapter.SYSTEM_APPS -> {
                        orderAppsByFilter(
                            apps.filter { it.system },
                            filter
                        )
                    }
                    else -> throw IllegalArgumentException("Incorrect key")
                }
            }.asLiveData(Dispatchers.IO)
    }

    /*fun getApps(key: String): LiveData<List<IApp>> {
        this.key = key
        if (_apps.value == null && filter != null) {
            viewModelScope.launch(Dispatchers.IO) {

                delay(500)

                appRepository.flow().collect { apps ->
                    _apps.postValue(when (key) {
                        SectionsPagerAdapter.USER_APPS -> {
                            orderAppsByFilter(
                                apps.filter { !it.system },
                                filter!!
                            )
                        }
                        SectionsPagerAdapter.SYSTEM_APPS -> {
                            orderAppsByFilter(
                                apps.filter { it.system },
                                filter!!
                            )
                        }
                        else -> throw IllegalArgumentException("Incorrect key")
                    })
                }
            }
        }
        return _apps
    }*/

    fun getAppsByFilter(key: String): LiveData<List<IApp>> {
        return getApplication<Application>()
            .dataStore
            .data
            .map {
                val filter = AppsFilter.valueOf(
                    it[PreferencesKeys.APPS_FILTER] ?:
                    AppsFilter.Alphabetic.name)

                delay(300)

                return@map orderAppsByFilter(key, filter)
            }.asLiveData(Dispatchers.IO)
    }

    private suspend fun orderAppsByFilter(key: String, filter: AppsFilter): List<IApp> {
        return when (key) {
            SectionsPagerAdapter.USER_APPS -> {
                orderAppsByFilter(
                    appRepository
                        .getAllByGroup()
                        .filter { !it.system },
                    filter
                )
            }
            SectionsPagerAdapter.SYSTEM_APPS -> {
                orderAppsByFilter(
                    appRepository
                        .getAllByGroup()
                        .filter { it.system },
                    filter
                )
            }
            else -> throw IllegalArgumentException("Incorrect key")
        }
    }

    private fun orderAppsByFilter(apps: List<IApp>, filter: AppsFilter): List<IApp> {
        return when (filter) {
            AppsFilter.Alphabetic -> {
                //Organizo la lista por el nombre de las aplicaciones
                apps.sortedBy { it.name }
            }
            AppsFilter.InternetAccess -> {
                //Resultado final
                val finalList = mutableListOf<IApp>()

                //Aplicaciones permitidas
                val allowedApps = apps.filter { it.access }
                //Aplicaciones bloqueadas
                val blockedApps = apps.filter { !it.access }

                /*Indica si se deben agregar encabezados. Si una de las listas de aplicaciones
                 * está vacia, no se agregan encabezados.*/
                val addHeaders = allowedApps.isNotEmpty() && blockedApps.isNotEmpty()

                if (addHeaders) {
                    //Encabezado de aplicaciones permitidas.
                    finalList.add(HeaderApp.newInstance(
                        getApplication<Application>()
                            .getString(R.string.allowed_apps, allowedApps.size))
                    )
                }

                finalList.addAll(allowedApps)

                if (addHeaders) {
                    //Encabezado de aplicaciones bloqueadas
                    finalList.add(
                        HeaderApp.newInstance(
                            getApplication<Application>()
                                .getString(R.string.blocked_apps, blockedApps.size))
                    )
                }

                finalList.addAll(blockedApps)

                finalList
            }
            AppsFilter.TrafficType -> {
                val finalList = mutableListOf<IApp>()

                val freeApps = getAppsByTrafficType(apps, TrafficType.Free)
                val nationalApps = getAppsByTrafficType(apps, TrafficType.National)
                val internationalApps = getAppsByTrafficType(apps, TrafficType.International)

                /*Indica si se deben agregar encabezados. Si las tres
                listas están vacias, no se agregan encabezados.*/
                val addHeaders = !(freeApps.isEmpty() &&
                        nationalApps.isEmpty() &&
                        internationalApps.isEmpty())

                if (addHeaders && freeApps.isNotEmpty()) {
                    finalList.add(HeaderApp.newInstance(
                        getApplication<Application>()
                            .getString(R.string.free_apps, freeApps.size))
                    )
                }

                finalList.addAll(freeApps)

                if (addHeaders && nationalApps.isNotEmpty()) {
                    finalList.add(HeaderApp.newInstance(
                        getApplication<Application>()
                            .getString(R.string.national_apps, nationalApps.size)
                    ))
                }

                finalList.addAll(nationalApps)

                if (addHeaders && internationalApps.isNotEmpty()) {
                    finalList.add(HeaderApp.newInstance(
                        getApplication<Application>()
                            .getString(R.string.international_apps, internationalApps.size)
                    ))
                }

                finalList.addAll(internationalApps)

                finalList
            }
        }
    }

    private fun getAppsByTrafficType(apps: List<IApp>, trafficType: TrafficType): List<IApp> {
        val finalList = mutableListOf<IApp>()

        apps.forEach {
            if (it is App) {
                if (it.trafficType == trafficType)
                    finalList.add(it)
            } else if (it is AppGroup) {
                if (it.getMasterApp().trafficType == trafficType)
                    finalList.add(it)
            }
        }

        return finalList
    }
}

/**
 * Modelo que se usa para dibujar un encabezado en la lista. Solo
 * contiene el nombre del encabezado en la propiedad name. Las demas
 * propiedades están vacias o nulas. El método [accessHashCode] no está
 * soportado.
 * */
@Parcelize
private class HeaderApp private constructor(
    override var packageName: String,
    override var uid: Int,
    override var name: String,
    override var access: Boolean,
    override var system: Boolean,
    override var allowAnnotations: String?,
    override var blockedAnnotations: String?
) : IApp {

    override fun accessHashCode(): Long {
        throw UnsupportedOperationException("Not supported")
    }

    companion object {
        fun newInstance(headerText: String): HeaderApp {
            return HeaderApp(
                "",
                -1,
                headerText,
                access = false,
                system = false,
                null,
                null
            )
        }
    }
}

enum class AppsFilter {
    Alphabetic,
    InternetAccess,
    TrafficType
}
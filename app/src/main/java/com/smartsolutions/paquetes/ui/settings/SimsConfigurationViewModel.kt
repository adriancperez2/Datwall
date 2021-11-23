package com.smartsolutions.paquetes.ui.settings

import android.app.Application
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.*
import com.smartsolutions.paquetes.helpers.SimDelegate
import com.smartsolutions.paquetes.managers.contracts.IPermissionsManager
import com.smartsolutions.paquetes.managers.contracts.ISimManager2
import com.smartsolutions.paquetes.repositories.models.Sim
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SimsConfigurationViewModel @Inject constructor(
    application: Application,
    permissionsManager: IPermissionsManager,
    private val simManager: ISimManager2
): AndroidViewModel(application) {

    private val delegate = SimsDelegate(
        getApplication(),
        simManager,
        permissionsManager,
        viewModelScope
    )

    fun getSims(
        fragment: AbstractSettingsFragment,
        fragmentManager: FragmentManager
    ): LiveData<List<Sim>> {
        return delegate.getSims(fragment, fragmentManager)
    }

    fun saveChanges(defaultDataSim: Sim, defaultVoiceSim: Sim, onComplete: () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.Main){
                onComplete()
            }
        }
    }
}
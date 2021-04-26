package com.smartsolutions.paquetes.managers

import android.content.Context
import androidx.lifecycle.LiveData
import com.smartsolutions.paquetes.repositories.models.DataPackage
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class LegacyDataPackageManager @Inject constructor(
    @ApplicationContext
    private val context: Context
): IDataPackageManager {
    override fun getPackages(): LiveData<List<DataPackage>> {
        TODO("Not yet implemented")
    }

    override fun buyDataPackage(id: Int): Boolean {
        TODO("Not yet implemented")
    }

    override fun setBuyMode(mode: IDataPackageManager.BuyMode) {
        TODO("Not yet implemented")
    }

    override fun clearHistory() {
        TODO("Not yet implemented")
    }
}
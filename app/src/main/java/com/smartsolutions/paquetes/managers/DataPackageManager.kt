package com.smartsolutions.paquetes.managers

import android.content.Context
import androidx.datastore.preferences.core.edit
import com.smartsolutions.paquetes.PreferencesKeys
import com.smartsolutions.paquetes.R
import com.smartsolutions.paquetes.data.DataPackagesContract
import com.smartsolutions.paquetes.dataStore
import com.smartsolutions.paquetes.helpers.*
import com.smartsolutions.paquetes.repositories.contracts.IDataPackageRepository
import com.smartsolutions.paquetes.repositories.contracts.IPurchasedPackageRepository
import com.smartsolutions.paquetes.repositories.models.DataPackage
import com.smartsolutions.paquetes.repositories.models.PurchasedPackage
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import org.apache.commons.lang.SerializationUtils
import java.io.*
import java.lang.NumberFormatException
import java.util.*
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext
import kotlin.jvm.Throws


class DataPackageManager @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val dataPackageRepository: IDataPackageRepository,
    private val purchasedPackageRepository: IPurchasedPackageRepository,
    private val userDataBytesManager: IUserDataBytesManager,
    private val ussdHelper: USSDHelper,
    private val miCubacelClientManager: MiCubacelClientManager
): IDataPackageManager, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private val simsIndexName = "sims_index.bin"

    private var _buyMode: IDataPackageManager.BuyMode = IDataPackageManager.BuyMode.USSD
    override var buyMode: IDataPackageManager.BuyMode
        get() = _buyMode
        set(value) {
            launch {
                context.dataStore.edit {
                    it[PreferencesKeys.BUY_MODE] = value.name
                }
            }
            _buyMode = value
        }

    init {
        launch {
            context.dataStore.data.collect {
                _buyMode = IDataPackageManager.BuyMode
                    .valueOf(it[PreferencesKeys.BUY_MODE] ?: IDataPackageManager.BuyMode.USSD.name)
            }
        }
    }

    override fun configureDataPackages() {
        launch {
            ussdHelper.sendUSSDRequestLegacy("*133*1#")?.let { response ->
                //Texto del mensaje dividido en saltos de linea
                val text = response.string().split("\n")

                //Índice basado en 1 de la linea activa
                val simIndex = getActiveSimIndex(context)
                /*Objeto SimsIndex que contendrá todos los indices
                de los paquetes divididos en las respectivas lineas.*/
                val simsIndex = getSimsIndex()

                //Obtengo todos los paquetes de la base de datos
                dataPackageRepository.getAll().firstOrNull()?.let { packages ->
                    //Por cada linea del texto
                    text.forEach { menu ->

                        try {
                            /* Intento obtener el primer número del texto. Este número
                             * será el índice para ese conjunto de paquetes.*/
                            val index = Integer.parseInt(menu.trimStart()[0].toString())

                            when {
                                //Si es la bolsa diaria
                                menu.contains("Bolsa Diaria", true) -> {
                                    //Busco la bolsa diaria de entre los paquetes
                                    packages.firstOrNull {
                                        it.id == createDataPackageId(DataPackagesContract.DailyBag.name, DataPackagesContract.DailyBag.price)
                                    }?.let { daily ->
                                        //Si estoy en la linea 1
                                        if (simIndex == 1) {
                                            //Asigno el índice de la bolsa diaria a la linea 1
                                            simsIndex.indexDailyBagSim1 = index
                                            /*Activo la bolsa diaria para que pueda ser comprada
                                            * en la linea 1.*/
                                            daily.activeInSim1 = true
                                        //Si estoy en la linea 2
                                        } else if (simIndex == 2) {
                                            //Asigno el índice de la bolsa diaria a la linea 2
                                            simsIndex.indexDailyBagSim2 = index
                                            /*Activo la bolsa diaria para que pueda ser comprada
                                            * en la linea 2.*/
                                            daily.activeInSim2 = true
                                        }
                                    }
                                }
                                //Si son los paquetes de 3G
                                menu.contains("Paquetes", true) && !menu.contains("Paquetes LTE", true) -> {
                                    //Si estoy en la linea 1
                                    if (simIndex == 1) {
                                        //Asigno el índice de los paquetes 3G a la linea 1
                                        simsIndex.indexPackagesSim1 = index
                                    //Si estoy en la linea 2
                                    } else if (simIndex == 2) {
                                        //Asigno el índice de los paquetes 3G a la linea 2
                                        simsIndex.indexPackagesSim2 = index
                                    }
                                    //Activo los paquetes 3G para esta linea
                                    activateDataPackages(
                                        simIndex,
                                        DataPackage.NETWORK_3G_4G,
                                        packages)
                                }
                                //Si son los paquetes de LTE
                                menu.contains("Paquetes LTE", true) -> {
                                    //Si estoy en la linea 1
                                    if (simIndex == 1) {
                                        //Asigno el índice de los pequetes LTE a la linea 1
                                        simsIndex.indexPackagesLteSim1 = index
                                    //Si estoy en la linea 2
                                    } else if (simIndex == 2) {
                                        //Asigno el índice de los paquetes LTE a la linea 2
                                        simsIndex.indexPackagesLteSim2 = index
                                    }
                                    //Activo los paquetes LTE para esta linea
                                    activateDataPackages(
                                        simIndex,
                                        DataPackage.NETWORK_4G,
                                        packages)
                                }
                            }
                        } catch (e: NumberFormatException) {

                        }
                    }
                    //Por último actualizo los paquetes en base de datos y guardo los índices.
                    dataPackageRepository.update(packages)
                    setSimsIndex(simsIndex)
                }
            }
        }
    }

    override fun getPackages(): Flow<List<DataPackage>> = dataPackageRepository
        .getActives(getActiveSimIndex(context))

    @Throws(IllegalStateException::class)
    override suspend fun buyDataPackage(dataPackage: DataPackage) {
        when (buyMode) {
            IDataPackageManager.BuyMode.USSD -> {
                buyDataPackageForUSSD(dataPackage)
            }
            IDataPackageManager.BuyMode.MiCubacel -> {
                buyDataPackageForMiCubacel(dataPackage)
            }
        }
    }

    override fun registerDataPackage(smsBody: String) {

        val classes = DataPackagesContract.javaClass.declaredClasses

        classes.forEach {
            val smsKey = it.getDeclaredField("smsKey").get(null) as String

            if (smsBody.contains(smsKey)) {

                val name = it.getDeclaredField("name").get(null) as String
                val price = it.getDeclaredField("price").getFloat(null)

                launch {
                    dataPackageRepository.get(createDataPackageId(name, price))?.let { dataPackage ->
                        userDataBytesManager.addDataBytes(dataPackage)
                    }
                }

                return
            }
        }
    }

    override fun getHistory(): Flow<List<PurchasedPackage>> =
        purchasedPackageRepository.getAll()

    override fun clearHistory() {
        launch {
            purchasedPackageRepository.getAll().collect {
                purchasedPackageRepository.delete(it)
            }
        }
    }

    private suspend fun buyDataPackageForUSSD(dataPackage: DataPackage) {

        val simIndex = getActiveSimIndex(context)
        val simsIndex = getSimsIndex()

        val index = when (simIndex) {
            1 -> {
                when (dataPackage.network) {
                    DataPackage.NETWORK_4G -> simsIndex.indexPackagesLteSim1
                    DataPackage.NETWORK_3G_4G -> simsIndex.indexPackagesSim1
                    else -> -1
                }
            }
            2 -> {
                when (dataPackage.network) {
                    DataPackage.NETWORK_4G -> simsIndex.indexPackagesLteSim2
                    DataPackage.NETWORK_3G_4G -> simsIndex.indexPackagesSim2
                    else -> -1
                }
            }
            else -> -1
        }

        if (index == -1)
            throw IllegalStateException(context.getString(R.string.pkgs_not_configured))
        else if ((simIndex == 1 && !dataPackage.activeInSim1) || (simIndex == 2 && !dataPackage.activeInSim2))
            throw IllegalStateException(context.getString(R.string.pkg_not_configured))

        ussdHelper
            .sendUSSDRequestLegacy(
                buildDataPackageUssdCode(index, dataPackage.index),
                false)

        val purchasedPackage = PurchasedPackage(
            0,
            Date().time,
            PurchasedPackage.Origin.USSD,
            dataPackage.id
        )
        purchasedPackageRepository.create(purchasedPackage)
    }

    private suspend fun buyDataPackageForMiCubacel(dataPackage: DataPackage) {
        val simIndex = getActiveSimIndex(context)

        if ((simIndex == 1 && !dataPackage.activeInSim1) || (simIndex == 2 && !dataPackage.activeInSim2))
            throw IllegalStateException(context.getString(R.string.pkg_not_configured))

        val productGroups = miCubacelClientManager.getProducts()

        for (group in productGroups) {
            val product = group.firstOrNull { it.id == dataPackage.id }

            if (product != null) {
                miCubacelClientManager.buyProduct(product.urlBuy)

                val purchasedPackage = PurchasedPackage(
                    0,
                    Date().time,
                    PurchasedPackage.Origin.MICUBACEL,
                    dataPackage.id
                )

                purchasedPackageRepository.create(purchasedPackage)
                break
            }
        }
    }

    private fun activateDataPackages(
        simIndex: Int,
        @DataPackage.Networks
        network: String,
        packages: List<DataPackage>) {

        packages.forEach {
            if (it.network == network) {
                if (simIndex == 1)
                    it.activeInSim1 = true
                else if (simIndex == 2)
                    it.activeInSim2 = true
            }
        }
    }

    private fun setSimsIndex(simsIndex: SimsIndex) {
        val file = File(context.filesDir, simsIndexName)

        if (!file.exists() && !file.createNewFile())
            throw IOException()

        SerializationUtils.serialize(simsIndex, FileOutputStream(file))
    }

    private fun getSimsIndex(): SimsIndex {
        val file = File(context.filesDir, simsIndexName)

        if (!file.exists() && !file.createNewFile())
            throw IOException()

        try {
            return SerializationUtils.deserialize(FileInputStream(file)) as SimsIndex
        } catch (e: Exception) {

        }
        return SimsIndex()
    }

    /**
     * Objeto que contiene los índices de los paquetes en sus respectivas lineas.
     * */
    internal class SimsIndex: Serializable {
        /**
         * Índice de la bolsa diaria para la linea 1.
         * */
        var indexDailyBagSim1 = -1
        /**
         * Índice de la bolsa diaria para la linea 2.
         * */
        var indexDailyBagSim2 = -1
        /**
         * Índice de los paquetes 3G para la linea 1.
         * */
        var indexPackagesSim1 = -1
        /**
         * Índice de los paquetes 3G para la linea 2.
         * */
        var indexPackagesSim2 = -1
        /**
         * Índice de los paquetes LTE para la linea 1.
         * */
        var indexPackagesLteSim1 = -1
        /**
         * Índice de los paquetes LTE para la linea 2.
         * */
        var indexPackagesLteSim2 = -1
    }
}
package com.smartsolutions.paquetes.repositories

import androidx.lifecycle.LiveData
import com.smartsolutions.paquetes.data.IDataPackageDao
import com.smartsolutions.paquetes.repositories.contracts.IDataPackageRepository
import com.smartsolutions.paquetes.repositories.models.DataPackage
import javax.inject.Inject

class DataPackageRepository @Inject constructor(
    private val dataPackageDao: IDataPackageDao,
) : IDataPackageRepository {

    override fun getAll(): LiveData<List<DataPackage>> = dataPackageDao.getAll()

    override suspend fun get(id: String): DataPackage? = dataPackageDao.get(id)

    override suspend fun create(dataPackage: DataPackage): Long = dataPackageDao.create(dataPackage)

    override suspend fun create(dataPackages: List<DataPackage>): List<Long> = dataPackageDao.create(dataPackages)

    override suspend fun update(dataPackage: DataPackage): Int = dataPackageDao.update(dataPackage)

    override suspend fun update(dataPackages: List<DataPackage>): Int = dataPackageDao.update(dataPackages)

    override suspend fun delete(dataPackage: DataPackage): Int = dataPackageDao.delete(dataPackage)

    override suspend fun delete(dataPackages: List<DataPackage>): Int = dataPackageDao.delete(dataPackages)
}
package com.smartsolutions.paquetes.data

import androidx.room.*
import com.smartsolutions.paquetes.repositories.models.DataPackage
import kotlinx.coroutines.flow.Flow

@Dao
interface IDataPackageDao {

    @Query("SELECT * FROM data_packages")
    fun getAll(): Flow<List<DataPackage>>

    @Query("SELECT * FROM data_packages WHERE id = :id")
    suspend fun get(id: String): DataPackage?

    @Insert
    suspend fun create(dataPackage: DataPackage): Long

    @Insert
    suspend fun create(dataPackages: List<DataPackage>): List<Long>

    @Update
    suspend fun update(dataPackage: DataPackage): Int

    @Update
    suspend fun update(dataPackages: List<DataPackage>): Int

    @Delete
    suspend fun delete(dataPackage: DataPackage): Int

    @Delete
    suspend fun delete(dataPackages: List<DataPackage>): Int
}
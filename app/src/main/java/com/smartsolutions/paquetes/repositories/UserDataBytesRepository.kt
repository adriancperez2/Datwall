package com.smartsolutions.paquetes.repositories

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.smartsolutions.paquetes.repositories.contracts.IUserDataBytesRepository
import com.smartsolutions.paquetes.repositories.models.UserDataBytes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import javax.inject.Inject
import kotlin.coroutines.CoroutineContext

class UserDataBytesRepository @Inject constructor(
    @ApplicationContext
    private val context: Context,
    private val gson: Gson
): IUserDataBytesRepository, CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO

    private val store = "user_data_bytes.json"

    override fun getAll(): LiveData<List<UserDataBytes>> {
        launch {
            val list = read()

            if (list.isNotEmpty())
                liveData.postValue(list)
        }

        return liveData
    }

    override suspend fun all(): List<UserDataBytes> = read()

    override fun getByType(dataType: UserDataBytes.DataType): LiveData<UserDataBytes> {
        val live = MutableLiveData<UserDataBytes>()

        launch {
            read().firstOrNull { it.type == dataType }?.let {
                live.postValue(it)
            }
        }

        return live
    }

    override suspend fun byType(dataType: UserDataBytes.DataType): UserDataBytes? =
        read().firstOrNull { it.type == dataType }

    override suspend fun create(userDataBytes: UserDataBytes): Boolean {
        val list = read()

        if (list.contains(userDataBytes))
            return false

        list.add(userDataBytes)

        return write(list)
    }

    override suspend fun create(userDataBytesList: List<UserDataBytes>): Boolean {
        val list = read()

        var canWrite = false

        userDataBytesList.forEach {
            if (!list.contains(it)) {
                list.add(it)
                canWrite = true
            }
        }

        if (canWrite)
            return write(list)
        return false
    }

    override suspend fun update(userDataBytes: UserDataBytes): Boolean {
        val list = read()

        val index = list.indexOf(userDataBytes)

        if (index != -1) {
            list[index] = userDataBytes

            return write(list)
        }
        return false
    }

    override suspend fun update(userDataBytesList: List<UserDataBytes>): Boolean {
        val list = read()

        var canWrite = false

        userDataBytesList.forEach {
            val index = list.indexOf(it)

            if (index != -1) {
                list[index] = it
                canWrite = true
            }
        }

        if (canWrite)
            return write(list)
        return false
    }

    override suspend fun delete(userDataBytes: UserDataBytes): Boolean {
        val list = read()

        if (list.remove(userDataBytes))
            return write(list)
        return false
    }

    private fun write(list: List<UserDataBytes>): Boolean {
        synchronized(this) {
            liveData.postValue(list)

            val file = File(context.filesDir, store)

            if (!file.exists() && !file.createNewFile())
                return false

            try {

                val content = gson.toJson(list)

                val output = FileOutputStream(file)

                output.write(content.toByteArray())

                output.flush()

                output.close()

                return true

            } catch (e: Exception) {

            }
            return false
        }
    }

    private fun read(): MutableList<UserDataBytes> {
        synchronized(this) {
            val file = File(context.filesDir, store)

            if (!file.exists())
                return mutableListOf()

            return try {
                val input = FileInputStream(file)

                val content = input.bufferedReader()
                    .readText()

                input.close()

                val typeToken = object : TypeToken<MutableList<UserDataBytes>>() {}.type

                gson.fromJson(content, typeToken)
            } catch (e: Exception) {
                mutableListOf()
            }
        }
    }

    companion object {

        private val liveData: MutableLiveData<List<UserDataBytes>> = MutableLiveData()

    }
}
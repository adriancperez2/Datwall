package com.smartsolutions.paquetes.managers

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import javax.inject.Inject

/**
 * Clase administradora de los íconos de las aplicaciones
 * */
class IconManager @Inject constructor(context: Context) : Manager(context) {

    /**
     * Servicio que se usa para obtener los íconos de las aplicaciones
     * */
    private val packageManager = context.packageManager

    /**
     * Directorio cache de la aplicación
     * */
    private val cacheDir = File(context.cacheDir, "icon_cache")

    /**
     * Nombre base de los íconos
     * */
    private val baseIconName = "icon_"

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdir()
        }
    }


    /**
     * Obtiene y crea un ícono de una aplicación si no existe
     *
     * @param packageName - Nombre de paquete de la aplicación
     * @param versionName - Versión de la aplicación
     * */
    private fun create(packageName: String, versionName: String) {
        val file = File(cacheDir, makeIconName(packageName, versionName))

         if (file.createNewFile()) {
            val icon = getResizedBitmap(drawableToBitmap(packageManager.getApplicationIcon(packageName)), 100)

            try {
                icon.compress(Bitmap.CompressFormat.PNG, 100, FileOutputStream(file))
            } catch (e: FileNotFoundException) {

            }
        }
    }

    /**
     * Obtiene el ícono de la aplicación actualizado a la versión pasada como argumento
     *
     * @param packageName Nombre de paquete de la aplicación
     * @param versionName Versión de la aplicación que se usará para determinar si
     * se debe actualizar el ícono.
     *
     * @return Ícono de la aplicación
     * */
    fun get(packageName: String, versionName: String): Bitmap {
        //Instancio un file
        val iconFile = File(this.cacheDir, makeIconName(packageName, versionName))

        //Si el file no existe es porque o no se ha creado el ícono o tiene una versión diferente
        if (!iconFile.exists()) {
            //Creo o actualizo el ícono
            saveOrUpdate(packageName, versionName)
        }

        //Y después construyo el bitmap
        return BitmapFactory.decodeFile(iconFile.path)
    }

    /**
     * Actualiza el ícono de una aplicación
     * */
    private fun update(packageName: String, versionName: String, oldIcon: String) {
        val oldIconFile = File(cacheDir, oldIcon)

        if (oldIconFile.exists())
            oldIconFile.delete()

        create(packageName, versionName)
    }

    /**
     * Elimina la cache de íconos completa
     * */
    fun deleteAll() {
        cacheDir.listFiles()?.forEach {
            it.delete()
        }
    }

    /**
     * Elimina un ícono
     * */
    fun delete(packageName: String, versionName: String) {
        val file = File(cacheDir, makeIconName(packageName, versionName))

        if (file.exists())
            file.delete()
    }

    /**
     * Guarda o actualiza un ícono
     * */
    private fun saveOrUpdate(packageName: String, versionName: String) {
        //Obtengo la lista de íconos en cache
        cacheDir.list()?.forEach { name ->

            if (name.contains("${this.baseIconName}$packageName")) {
                //Si contiene el nombre de paquete es porque existe pero tiene una versión diferente.
                //Entonces actualizo el ícono
                update(packageName, versionName, name)
                //Y termino
                return
            }
        }
        //Sino encontré nada, creo el ícono
        create(packageName, versionName)
    }

    /**
     * Contruye el nombre del ícono basado en el nombre de paquete y la versión
     * */
    private fun makeIconName(packageName: String, versionName: String) = "${this.baseIconName}${packageName}_$versionName"

    /**
     * Redimenciona un Bitmap
     *
     * @param image - Imagen a redimencionar
     * @param maxSize - Tamaño que se le asignará a la imagen
     * @return Imagen redimencionada
     * */
    private fun getResizedBitmap(image: Bitmap, maxSize: Int): Bitmap {
        var width = image.width
        var height = image.height
        val bitmapRatio = width.toFloat() / height
        if (bitmapRatio > 1) {
            width = maxSize
            height = (width / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (height * bitmapRatio).toInt()
        }
        return Bitmap.createScaledBitmap(image, width, height, true)
    }

    /**
     * Convierte un Drawable a Bitmap
     *
     * @param drawable - Drawable a convertir
     * @return Bitmap
     * */
    private fun drawableToBitmap(drawable: Drawable): Bitmap {
        if (drawable is BitmapDrawable) {
            if (drawable.bitmap != null) {
                return drawable.bitmap
            }
        }
        val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        } else {
            Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        }
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bitmap
    }
}
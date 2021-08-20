package com.smartsolutions.paquetes.data

import com.smartsolutions.paquetes.annotations.Networks.Companion.NETWORK_3G_4G
import com.smartsolutions.paquetes.repositories.models.DataPackage
import com.smartsolutions.paquetes.data.DataPackages.PackageId.*
import com.smartsolutions.paquetes.managers.models.DataUnitBytes.*
import com.smartsolutions.paquetes.annotations.Networks.Companion.NETWORK_4G

object DataPackages {

    const val PACKAGES_VERSION = 1

    const val PROMO_BONUS_KEY = "Su cuenta ha sido recargada en promocion"

    val PACKAGES = arrayOf(
        DataPackage(
            DailyBag,
            "Bolsa Diaria",
            "Proporciona 200MB a consumir en 24 horas a partir del primer uso. " +
                    "Disponibles solo bajo la red LTE.",
            25f,
            0,
            DataValue(200.0, DataUnit.MB).toBytes(),
            0,
            NETWORK_4G,
            -1,
            1,
            "bolsa Diaria de 200MB"
        ),
        DataPackage(
            Combined_Basic,
            "Combinado básico",
            "Proporciona 600MB disponibles para todas las redes y 800MB solo para LTE. " +
                    "Ademas contará con 15min para llamadas y 20sms. La vigencia es de 30 dias " +
                    "a partir del primer uso de cualquiera de los tres recursos.",
            125f,
            DataValue(600.0, DataUnit.MB).toBytes(),
            DataValue(800.0, DataUnit.MB).toBytes(),
            DataValue(300.0, DataUnit.MB).toBytes(),
            NETWORK_3G_4G,
            1,
            30,
            "600MB +800MB LTE"
        ),
        DataPackage(
            Combined_Medium,
            "Combinado medio",
            "Proporciona 1.5GB disponibles para todas las redes y 2GB solo para LTE. " +
                    "Ademas contará con 35min para llamadas y 40sms. La vigencia es de 30 dias " +
                    "a partir del primer uso de cualquiera de los tres recursos.",
            250f,
            DataValue(1.5, DataUnit.GB).toBytes(),
            DataValue(2.0, DataUnit.GB).toBytes(),
            DataValue(300.0, DataUnit.MB).toBytes(),
            NETWORK_3G_4G,
            2,
            30,
            "1.5GB +2GB LTE"
        ),
        DataPackage(
            Combined_Extra,
            "Combinado extra",
            "Proporciona 3.5GB disponibles para todas las redes y 4.5GB solo para LTE. " +
                    "Ademas contará con 75min para llamadas y 80sms. La vigencia es de 30 dias " +
                    "a partir del primer uso de cualquiera de los tres recursos.",
            500f,
            DataValue(3.5, DataUnit.GB).toBytes(),
            DataValue(4.5, DataUnit.GB).toBytes(),
            DataValue(300.0, DataUnit.MB).toBytes(),
            NETWORK_3G_4G,
            3,
            30,
            "3.5GB +4.5GB LTE"
        ),
        DataPackage(
            P_1GB_LTE,
            "Paquete 1 GB LTE",
            "Proporciona 1GB disponible solo para la red LTE. " +
                    "Tiene una vigencia de 30 dias a partir del primer uso.",
            100f,
            DataValue(0.0, DataUnit.B).toBytes(),
            DataValue(1.0, DataUnit.GB).toBytes(),
            DataValue(300.0, DataUnit.MB).toBytes(),
            NETWORK_4G,
            1,
            30,
            "1GB solo LTE"
        ),
        DataPackage(
            P_2_5GB_LTE,
            "Paquete 2.5 GB LTE",
            "Proporciona 2.5GB disponible solo para la red LTE. " +
                    "Tiene una vigencia de 30 dias a partir del primer uso.",
            200f,
            DataValue(0.0, DataUnit.B).toBytes(),
            DataValue(2.5, DataUnit.GB).toBytes(),
            DataValue(300.0, DataUnit.MB).toBytes(),
            NETWORK_4G,
            2,
            30,
            "2.5GB solo LTE"
        ),
        DataPackage(
            P_4GB_12GB_LTE,
            "Paquete 16 GB",
            "Proporciona 4GB para todas las redes más 12GB disponible solo para la red LTE. " +
                    "Tiene una vigencia de 30 dias a partir del primer uso.",
            100f,
            DataValue(4.0, DataUnit.GB).toBytes(),
            DataValue(12.0, DataUnit.GB).toBytes(),
            DataValue(300.0, DataUnit.MB).toBytes(),
            NETWORK_4G,
            3,
            30,
            "12GB LTE" //TODO: Revisar la clave
        )
    )

    enum class PackageId {
        DailyBag,
        Combined_Basic,
        Combined_Medium,
        Combined_Extra,
        P_1GB_LTE,
        P_2_5GB_LTE,
        P_4GB_12GB_LTE
    }
}
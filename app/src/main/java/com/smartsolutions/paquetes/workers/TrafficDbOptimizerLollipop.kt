package com.smartsolutions.paquetes.workers

import android.content.Context
import android.os.Build
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.smartsolutions.paquetes.helpers.DateCalendarUtils
import com.smartsolutions.paquetes.managers.contracts.ISimManager
import com.smartsolutions.paquetes.managers.models.Traffic
import com.smartsolutions.paquetes.repositories.contracts.ITrafficRepository
import dagger.assisted.Assisted
import java.util.*
import java.util.concurrent.TimeUnit

@HiltWorker
class TrafficDbOptimizerLollipop(
    @Assisted
    context: Context,
    @Assisted
    parameters: WorkerParameters,
    private val trafficRepository: ITrafficRepository,
    private val simManager: ISimManager,
    private val dateCalendarUtils: DateCalendarUtils
) : CoroutineWorker(context, parameters) {


    override suspend fun doWork(): Result {

        val traffics = mutableListOf<Traffic>()

        val limitTime = dateCalendarUtils.getTimePeriod(DateCalendarUtils.PERIOD_YESTERDAY).first

        simManager.getInstalledSims().forEach { sim ->
            val trafficsToAdd = mutableListOf<Traffic>()
            var period = 0L to 1L

            val oldTraffics = trafficRepository.getAll(sim.id).onEach { trafficDb ->
                val traff = trafficsToAdd.firstOrNull { it.uid == trafficDb.uid }

                when {
                    traff == null -> {
                        trafficsToAdd.add(trafficDb)
                        period = if (trafficDb.startTime >= limitTime) {
                            DateCalendarUtils.getStartAndFinishHour(trafficDb.startTime)
                        } else {
                            val day = Date(trafficDb.startTime)
                            DateCalendarUtils.getZeroHour(
                                day
                            ).time to DateCalendarUtils.getLastHour(
                                day
                            ).time
                        }
                    }
                    trafficDb.startTime in period.first..period.second -> {
                        trafficsToAdd[trafficsToAdd.indexOf(traff)] += trafficDb
                    }
                    else -> {
                        traffics.addAll(trafficsToAdd)
                        trafficsToAdd.clear()
                        trafficsToAdd.add(trafficDb)
                        period = if (trafficDb.startTime >= limitTime) {
                            DateCalendarUtils.getStartAndFinishHour(trafficDb.startTime)
                        } else {
                            val day = Date(trafficDb.startTime)
                            DateCalendarUtils.getZeroHour(
                                day
                            ).time to DateCalendarUtils.getLastHour(
                                day
                            ).time
                        }
                    }
                }
            }

            if (oldTraffics.isNotEmpty() && traffics.isNotEmpty()) {
                trafficRepository.delete(oldTraffics)
                trafficRepository.create(traffics)
                traffics.clear()
            }
        }

        return Result.success()
    }


    companion object {

        private const val TAG_OPTIMIZER_WORKER = "tag_optimizer_worker"

        fun registerWorkerIfNeeded(context: Context, hoursInterval: Long) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                val request = PeriodicWorkRequestBuilder<TrafficDbOptimizerLollipop>(
                    hoursInterval,
                    TimeUnit.HOURS
                )
                    .addTag(TAG_OPTIMIZER_WORKER)
                    .build()

                val workManager = WorkManager.getInstance(context)
                workManager.cancelAllWorkByTag(TAG_OPTIMIZER_WORKER)
                workManager.enqueue(request)
            }
        }

    }
}
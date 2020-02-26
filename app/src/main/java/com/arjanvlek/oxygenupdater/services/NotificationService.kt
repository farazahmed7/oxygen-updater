package com.arjanvlek.oxygenupdater.services

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import com.arjanvlek.oxygenupdater.exceptions.OxygenUpdaterException
import com.arjanvlek.oxygenupdater.internal.objectMapper
import com.arjanvlek.oxygenupdater.internal.settings.SettingsManager
import com.arjanvlek.oxygenupdater.utils.Logger.logDebug
import com.arjanvlek.oxygenupdater.utils.Logger.logError
import com.arjanvlek.oxygenupdater.utils.Utils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.android.ext.android.inject

class NotificationService : FirebaseMessagingService() {

    private val settingsManager by inject<SettingsManager>()

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        try {
            //  Receive the notification contents but build / show the actual notification with a small random delay to avoid overloading the server.
            val messageContents = remoteMessage.data
            val displayDelayInSeconds = Utils.randomBetween(1, settingsManager.getPreference(SettingsManager.PROPERTY_NOTIFICATION_DELAY_IN_SECONDS, 1800))

            logDebug(TAG, "Displaying push notification in $displayDelayInSeconds second(s)")

            val taskData = PersistableBundle()
            val scheduler: JobScheduler? = application.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            if (scheduler == null) {
                logError(TAG, OxygenUpdaterException("Job scheduler service is not available"))
                return
            }

            val jobId = AVAILABLE_JOB_IDS
                .find { id -> scheduler.allPendingJobs.none { it.id == id } }
                ?: throw RuntimeException("There are too many notifications scheduled. Cannot schedule a new notification!")

            taskData.putString(
                DelayedPushNotificationDisplayer.KEY_NOTIFICATION_CONTENTS, objectMapper.writeValueAsString(messageContents)
            )

            val task = JobInfo.Builder(jobId, ComponentName(application, DelayedPushNotificationDisplayer::class.java))
                .setRequiresDeviceIdle(false)
                .setRequiresCharging(false)
                .setMinimumLatency(displayDelayInSeconds * 1000.toLong())
                .setExtras(taskData)

            if (Build.VERSION.SDK_INT >= 26) {
                task.setRequiresBatteryNotLow(false)
                task.setRequiresStorageNotLow(false)
            }

            scheduler.schedule(task.build())
        } catch (e: Exception) {
            logError(TAG, "Error dispatching push notification", e)
        }
    }

    companion object {
        private val AVAILABLE_JOB_IDS = listOf(8326, 8327, 8328, 8329, 8330, 8331, 8332, 8333)

        const val TAG = "NotificationService"
    }
}

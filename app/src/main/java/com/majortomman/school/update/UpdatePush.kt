package com.majortomman.school.update

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.majortomman.school.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Optional FCM transport. Builds without Firebase variables keep this disabled and continue to use
 * foreground and 24-hour WorkManager checks. Push data is never trusted as update metadata; it only
 * schedules a fresh download and signature verification of update-manifest.json.
 */
internal object UpdatePushRegistrar {
    private const val TAG = "SchoolUpdatePush"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun setEnabled(context: Context, enabled: Boolean) {
        if (!BuildConfig.UPDATE_PUSH_ENABLED) {
            Log.i(TAG, "FCM update push disabled: Firebase build variables are incomplete")
            return
        }
        scope.launch {
            runCatching {
                ensureFirebase(context.applicationContext)
                val messaging = FirebaseMessaging.getInstance()
                val topic = BuildConfig.FCM_UPDATE_TOPIC.ifBlank { UPDATE_PUSH_TOPIC_DEFAULT }
                if (enabled) messaging.subscribeToTopic(topic) else messaging.unsubscribeFromTopic(topic)
            }.onSuccess {
                Log.i(TAG, if (enabled) "subscribed to update topic" else "unsubscribed from update topic")
            }.onFailure { error ->
                Log.w(TAG, "unable to update FCM topic subscription", error)
            }
        }
    }

    private fun ensureFirebase(context: Context): FirebaseApp {
        FirebaseApp.getApps(context).firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }?.let { return it }
        val options = FirebaseOptions.Builder()
            .setApplicationId(BuildConfig.FCM_APPLICATION_ID)
            .setApiKey(BuildConfig.FCM_API_KEY)
            .setProjectId(BuildConfig.FCM_PROJECT_ID)
            .setGcmSenderId(BuildConfig.FCM_SENDER_ID)
            .build()
        return requireNotNull(FirebaseApp.initializeApp(context, options)) {
            "Firebase default app initialization returned null."
        }
    }
}

class SchoolUpdateMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        UpdatePreferences(applicationContext).savePushToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        if (data["type"] != "school_update") return
        if (!UpdatePreferences(applicationContext).settings().autoCheck) return
        val advertisedVersion = data["versionCode"]?.toLongOrNull()
        if (advertisedVersion != null && advertisedVersion <= BuildConfig.VERSION_CODE.toLong()) return
        enqueuePushUpdateCheck(applicationContext)
    }

    override fun onDeletedMessages() {
        if (UpdatePreferences(applicationContext).settings().autoCheck) {
            enqueuePushUpdateCheck(applicationContext)
        }
    }
}

internal fun enqueuePushUpdateCheck(context: Context) {
    val request = OneTimeWorkRequestBuilder<UpdateCheckWorker>()
        .setInputData(workDataOf(UPDATE_WORK_REASON_KEY to UPDATE_WORK_REASON_PUSH))
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .build()
    WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
        UPDATE_PUSH_CHECK_WORK_NAME,
        ExistingWorkPolicy.REPLACE,
        request,
    )
}

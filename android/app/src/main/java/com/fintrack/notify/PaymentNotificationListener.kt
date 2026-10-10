package com.fintrack.notify

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.fintrack.core.NotificationParser
import com.fintrack.data.TransactionStore
import java.util.concurrent.Executors

/**
 * Reads notifications from GPay, PhonePe, Paytm, BHIM and CRED only; every other app is ignored
 * before anything is read. Catches payments that never produce a bank SMS.
 */
class PaymentNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = NotificationParser.appFor(sbn.packageName) ?: return
        val n = sbn.notification ?: return
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
            ?.toString().orEmpty()
        // `when` stays the same when an app updates its notification, so updates dedupe.
        val at = if (n.`when` > 0) n.`when` else sbn.postTime
        val context = applicationContext
        worker.execute {
            try {
                val parsed = NotificationParser.parse(sbn.packageName, title, text, at) ?: return@execute
                TransactionStore.get(context).insert(parsed, "app:${app.name}", at, System.currentTimeMillis())
            } catch (e: Exception) {
                Log.e("FinTrack", "Failed to store notification", e)
            }
        }
    }

    companion object {
        private val worker = Executors.newSingleThreadExecutor()

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == context.packageName }
        }
    }
}

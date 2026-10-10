package com.fintrack.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

/** Runs for every incoming SMS, even when the app is closed. */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        val receivedAt = System.currentTimeMillis()

        // A long SMS arrives in several parts; join them per sender.
        val messages = parts.filterNotNull().groupBy { it.displayOriginatingAddress ?: "" }
            .map { (sender, ps) -> Triple(sender, ps.joinToString("") { it.displayMessageBody ?: "" }, ps.first().timestampMillis) }

        val pending = goAsync()
        Thread {
            try {
                messages.forEach { (sender, body, sentAt) ->
                    SmsIngest.ingest(context.applicationContext, sender, body, sentAt, receivedAt)
                }
            } catch (e: Exception) {
                Log.e("FinTrack", "Failed to store SMS", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}

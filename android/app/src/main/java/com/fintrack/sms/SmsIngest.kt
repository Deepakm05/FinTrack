package com.fintrack.sms

import android.content.Context
import android.provider.Telephony
import com.fintrack.core.SmsParser
import com.fintrack.data.TransactionStore

/** Parses an SMS and stores it if it is a bank / delivery transaction. */
object SmsIngest {

    fun ingest(context: Context, sender: String, body: String, sentAt: Long, receivedAt: Long): Boolean {
        val parsed = SmsParser.parse(sender, body, sentAt) ?: return false
        return TransactionStore.get(context).insert(parsed, sender, sentAt, receivedAt)
    }

    data class ImportResult(val scanned: Int, val added: Int)

    /** Scans the SMS inbox from [sinceMillis] onward. Safe to repeat: already stored SMS are skipped. */
    fun importInbox(context: Context, sinceMillis: Long): ImportResult {
        val projection = arrayOf(
            Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT,
        )
        var scanned = 0
        var added = 0
        context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection,
            "${Telephony.Sms.DATE} >= ?", arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                scanned++
                val sender = c.getString(0) ?: continue
                val body = c.getString(1) ?: continue
                val received = c.getLong(2)
                val sent = c.getLong(3).takeIf { it > 0 } ?: received
                if (ingest(context, sender, body, sent, received)) added++
            }
        }
        return ImportResult(scanned, added)
    }
}

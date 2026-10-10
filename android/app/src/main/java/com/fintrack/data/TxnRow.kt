package com.fintrack.data

import com.fintrack.core.Direction
import com.fintrack.core.SourceType

/** One stored transaction, as shown in the app and sent to the PC. */
data class TxnRow(
    val uid: String,
    val sender: String,
    val senderKey: String,
    val body: String,
    val sentAt: Long,
    val receivedAt: Long,
    val txnDate: String,
    val dateFromBody: Boolean,
    val amountPaise: Long,
    val direction: Direction,
    val sourceType: SourceType,
    val dupOf: String?,
    val dupScore: Double?,
    /** auto / possible (from the phone) or confirmed / rejected (decided on the PC). */
    val dupState: String?,
    val category: String?,
    val version: Long,
) {
    /** Linked duplicates are left out of totals unless the PC rejected the link. */
    val countsInTotals: Boolean get() = dupOf == null || dupState == "possible" || dupState == "rejected"
}

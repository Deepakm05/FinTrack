package com.fintrack.core

import java.time.LocalDate

/**
 * Where a transaction came from: a bank SMS, a delivery / shopping / ride app SMS, a UPI app notification,
 * or the merchant app's own notification ("You paid ₹349" from Swiggy, Uber, Rapido...).
 */
enum class SourceType { BANK, DELIVERY, UPI_APP, MERCHANT_APP }

/** Money out (DEBIT) or money in (CREDIT). */
enum class Direction { DEBIT, CREDIT }

/**
 * Result of parsing one SMS or payment-app notification. Only the date and amount are extracted; the masked text is kept
 * so the PC side can categorize it.
 */
data class ParsedSms(
    val senderKey: String,
    val sourceType: SourceType,
    val direction: Direction,
    val amountPaise: Long,
    val txnDate: LocalDate,
    /** true when [txnDate] was read from the SMS text, false when it fell back to the SMS time. */
    val dateFromBody: Boolean,
    /** Text with every account / card number replaced by XXXX. */
    val maskedBody: String,
)

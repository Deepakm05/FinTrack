package com.fintrack.core

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Rule-based parser for bank and delivery-app SMS. No AI: fixed patterns only.
 * Extracts the amount, the date and whether money went out or came in.
 */
object SmsParser {

    private val OTP = Regex("""(?<![a-z])(otp|one[- ]time password|verification code)(?![a-z])""")
    private val PAST_TXN = Regex("""(?<![a-z])(debited|spent|credited|paid|withdrawn|sent|deducted|charged)(?![a-z])""")

    /** Not a completed transaction: reminders, requests, failures. */
    private val NOT_A_TXN = Regex(
        """(?<![a-z])(is due|due on|due date|due by|amount due|amt due|will be (?:auto[- ]?)?debited|will be deducted|""" +
            """will be charged|payment request|collect request|has requested|requested (?:rs|inr|₹|money)|""" +
            """failed|declined|unsuccessful|insufficient|could not be processed|pre-?approved)(?![a-z])"""
    )

    private val DEBIT = Regex(
        """(?<![a-z])(debited|debit of|spent|paid|sent|withdrawn|withdrawal|purchase|purchased|charged|deducted|""" +
            """txn of|transaction of|payment of|transferred)(?![a-z])"""
    )
    private val CREDIT = Regex("""(?<![a-z])(credited|credit of|received|deposited|refund|refunded|reversed|reversal)(?![a-z])""")

    /** Delivery-app messages count only when they confirm an order / payment. */
    private val DELIVERY_TXN = Regex(
        """(?<![a-z])(paid|payment|placed|confirmed|delivered|debited|charged|order total|bill|total|amount of|""" +
            """order (?:of|for|worth)|refund|refunded)(?![a-z])"""
    )
    private val DELIVERY_REFUND = Regex("""(?<![a-z])(refund|refunded|credited|reversed)(?![a-z])""")
    private val PROMO = Regex("""(?<![a-z])(off|upto|up to|coupon|use code|promo|offer|discount|hurry|order now|sale|flat)(?![a-z])""")
    private val PROMO_OVERRIDE = Regex("""(?<![a-z])(paid|debited|charged|placed|refund|refunded)(?![a-z])""")

    private const val NUM = """(\d[\d,]*(?:\.\d{1,2})?)"""
    private val AMOUNT_PREFIX = Regex("""(?i)(?<![a-z])(?:rs\.?|inr|₹|rupees)\s*:?\s*$NUM""")
    /** SBI-style "debited by 1250.0" with no currency word. */
    private val AMOUNT_AFTER_VERB = Regex("""(?i)(?<![a-z])(?:debited|credited|deducted|spent|paid|sent|withdrawn|received)\s+(?:by|for|with|of)?\s*$NUM(?![\d/:-])""")
    private val AMOUNT_SUFFIX = Regex("""(?i)(?<![\d,.])$NUM\s*(?:/-|rs(?![a-z])|inr(?![a-z])|rupees)""")
    private val NOT_SPEND_CONTEXT = Regex("""(?i)(bal|balance|avl|available|limit|outstanding|o/s)""")

    private const val MON = """(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"""
    private val DATE_DMY = Regex("""(?<!\d)(\d{1,2})[-/.](\d{1,2})[-/.](\d{4}|\d{2})(?![\d:])""")
    private val DATE_YMD = Regex("""(?<!\d)(\d{4})[-/](\d{1,2})[-/](\d{1,2})(?!\d)""")
    private val DATE_D_MON_Y = Regex("""(?i)(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[-\s/]?$MON\.?[-\s/,']*(\d{4}|\d{2})(?![\d:])""")
    private val DATE_D_MON = Regex("""(?i)(?<!\d)(\d{1,2})(?:st|nd|rd|th)?[-\s/]?$MON(?![a-z])""")
    private val DATE_MON_D_Y = Regex("""(?i)(?<![a-z])$MON\.?\s+(\d{1,2})(?:st|nd|rd|th)?,?\s+(\d{4})(?!\d)""")

    private val MONTH_NAMES = listOf(
        "january", "february", "march", "april", "may", "june",
        "july", "august", "september", "october", "november", "december",
    )

    /** Body dates further than this from the SMS time are ignored (statements, due dates, etc.). */
    private const val MAX_DAYS_BEFORE = 45L
    private const val MAX_DAYS_AFTER = 2L

    fun parse(sender: String, body: String, sentAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): ParsedSms? {
        val senderKey = SenderClassifier.normalize(sender) ?: return null
        val text = body.replace(Regex("""\s+"""), " ").trim()
        val lower = text.lowercase()

        if (OTP.containsMatchIn(lower) && !PAST_TXN.containsMatchIn(lower)) return null
        if (NOT_A_TXN.containsMatchIn(lower)) return null

        val sourceType = SenderClassifier.classify(senderKey, lower) ?: return null
        val direction = when (sourceType) {
            SourceType.BANK -> bankDirection(lower)
            SourceType.DELIVERY -> deliveryDirection(lower)
            SourceType.UPI_APP, SourceType.MERCHANT_APP -> null // only notifications produce these
        } ?: return null

        val amountPaise = extractAmountPaise(text) ?: return null
        val smsDate = Instant.ofEpochMilli(sentAtMillis).atZone(zone).toLocalDate()
        val bodyDate = extractDate(text, smsDate)

        return ParsedSms(
            senderKey = senderKey,
            sourceType = sourceType,
            direction = direction,
            amountPaise = amountPaise,
            txnDate = bodyDate ?: smsDate,
            dateFromBody = bodyDate != null,
            maskedBody = Masker.mask(text),
        )
    }

    /** Whichever of debit / credit wording comes first wins ("debited from A/c ... credited to X"). */
    private fun bankDirection(lower: String): Direction? {
        val d = DEBIT.find(lower)?.range?.first
        val c = CREDIT.find(lower)?.range?.first
        return when {
            d == null && c == null -> null
            c == null -> Direction.DEBIT
            d == null -> Direction.CREDIT
            d <= c -> Direction.DEBIT
            else -> Direction.CREDIT
        }
    }

    private fun deliveryDirection(lower: String): Direction? {
        if (!DELIVERY_TXN.containsMatchIn(lower)) return null
        if (PROMO.containsMatchIn(lower) && !PROMO_OVERRIDE.containsMatchIn(lower)) return null
        return if (DELIVERY_REFUND.containsMatchIn(lower)) Direction.CREDIT else Direction.DEBIT
    }

    /** First amount in the text that is not a balance / limit figure. */
    fun extractAmountPaise(text: String): Long? {
        val candidates = AMOUNT_PREFIX.findAll(text).toList()
            .ifEmpty { AMOUNT_AFTER_VERB.findAll(text).toList() }
            .ifEmpty { AMOUNT_SUFFIX.findAll(text).toList() }
        for (m in candidates) {
            if (isBalanceContext(text, m.range.first)) continue
            val paise = toPaise(m.groupValues[1]) ?: continue
            if (paise > 0) return paise
        }
        return null
    }

    private fun isBalanceContext(text: String, start: Int): Boolean {
        var window = text.substring(maxOf(0, start - 20), start)
        val sentenceEnd = window.lastIndexOf(". ")
        if (sentenceEnd >= 0) window = window.substring(sentenceEnd + 2)
        return NOT_SPEND_CONTEXT.containsMatchIn(window)
    }

    private fun toPaise(raw: String): Long? {
        val clean = raw.replace(",", "").trimEnd('.')
        if (clean.isEmpty()) return null
        return try {
            BigDecimal(clean).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: ArithmeticException) {
            null
        } catch (e: NumberFormatException) {
            null
        }
    }

    /** Earliest plausible date in the text, or null. */
    fun extractDate(text: String, smsDate: LocalDate): LocalDate? {
        val found = mutableListOf<Pair<Int, LocalDate>>()
        fun add(index: Int, date: LocalDate?) {
            if (date != null && plausible(date, smsDate)) found += index to date
        }
        DATE_DMY.findAll(text).forEach { m ->
            add(m.range.first, date(year(m.groupValues[3]), m.groupValues[2].toInt(), m.groupValues[1].toInt()))
        }
        DATE_YMD.findAll(text).forEach { m ->
            add(m.range.first, date(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()))
        }
        DATE_D_MON_Y.findAll(text).forEach { m ->
            val month = month(m.groupValues[2], m.value) ?: return@forEach
            add(m.range.first, date(year(m.groupValues[3]), month, m.groupValues[1].toInt()))
        }
        DATE_D_MON.findAll(text).forEach { m ->
            val month = month(m.groupValues[2], m.value) ?: return@forEach
            add(m.range.first, inferYear(month, m.groupValues[1].toInt(), smsDate))
        }
        DATE_MON_D_Y.findAll(text).forEach { m ->
            val month = month(m.groupValues[1], m.value) ?: return@forEach
            add(m.range.first, date(m.groupValues[3].toInt(), month, m.groupValues[2].toInt()))
        }
        return found.minByOrNull { it.first }?.second
    }

    private fun plausible(date: LocalDate, smsDate: LocalDate): Boolean =
        !date.isAfter(smsDate.plusDays(MAX_DAYS_AFTER)) && !date.isBefore(smsDate.minusDays(MAX_DAYS_BEFORE))

    private fun year(raw: String): Int = raw.toInt().let { if (raw.length == 2) 2000 + it else it }

    private fun date(year: Int, month: Int, day: Int): LocalDate? =
        try {
            LocalDate.of(year, month, day)
        } catch (e: java.time.DateTimeException) {
            null
        }

    /** Year-less dates ("10 Oct") take the SMS year, or the previous year if that lands in the future. */
    private fun inferYear(month: Int, day: Int, smsDate: LocalDate): LocalDate? {
        val sameYear = date(smsDate.year, month, day) ?: return null
        return if (sameYear.isAfter(smsDate.plusDays(MAX_DAYS_AFTER))) date(smsDate.year - 1, month, day) else sameYear
    }

    /** Month number from a matched word, rejecting words that merely start like a month ("Mark"). */
    private fun month(abbrev: String, matched: String): Int? {
        val word = Regex("""(?i)$MON""").find(matched)?.value?.lowercase() ?: return null
        val index = MONTH_NAMES.indexOfFirst { it.startsWith(abbrev.lowercase()) }
        if (index < 0) return null
        val full = MONTH_NAMES[index]
        val ok = word == abbrev.lowercase() || word == "sept" || full.startsWith(word)
        return if (ok) index + 1 else null
    }
}

package com.fintrack.ui.model

import com.fintrack.core.Direction
import com.fintrack.core.Period
import com.fintrack.core.SourceType
import com.fintrack.core.TitleExtractor
import com.fintrack.data.TxnRow
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Another record of the same payment (the GPay notification for a bank SMS, a Swiggy SMS, ...). */
data class LinkedItem(val sourceLabel: String, val body: String, val dupState: String?)

data class TxnItem(
    val uid: String,
    val title: String,
    val sourceLabel: String,
    val sourceType: SourceType,
    val amountPaise: Long,
    val direction: Direction,
    val date: LocalDate,
    val time: String,
    val category: String?,
    val body: String,
    /** "possible" when this might be a duplicate that still counts until confirmed on the PC. */
    val dupState: String?,
    val linked: List<LinkedItem>,
)

data class DayGroup(val date: LocalDate, val label: String, val spentPaise: Long, val items: List<TxnItem>)

data class CategorySlice(val name: String, val paise: Long, val fraction: Float)

data class MonthSummary(
    val period: Period,
    val spentPaise: Long,
    val receivedPaise: Long,
    val payments: Int,
    val uncategorized: Int,
    val hiddenDuplicates: Int,
    val categories: List<CategorySlice>,
    val days: List<DayGroup>,
) {
    val items: List<TxnItem> get() = days.flatMap { it.items }

    companion object {
        fun empty(period: Period) = MonthSummary(period, 0, 0, 0, 0, 0, emptyList(), emptyList())
    }
}

data class SetupState(val smsGranted: Boolean, val notificationAccess: Boolean) {
    val complete: Boolean get() = smsGranted && notificationAccess
}

data class LinkState(
    val running: Boolean,
    val wifiMode: Boolean,
    val wifiAddress: String?,
    val port: Int,
    val token: String,
    val error: String?,
    /** Epoch millis of the last PC sync, or 0 if the PC never synced. */
    val lastSyncAt: Long = 0,
)

const val UNCATEGORIZED = "Uncategorized"

/** Turns stored rows into what the screens show. Pure Kotlin, unit-tested. */
object UiStateBuilder {

    private val dayFormat = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun month(
        rows: List<TxnRow>,
        period: Period,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): MonthSummary {
        val counted = rows.filter { it.countsInTotals }
        val hiddenChildren = rows.filter { !it.countsInTotals && it.dupOf != null }.groupBy { it.dupOf!! }

        val items = counted.sortedByDescending { it.sentAt }.map { r ->
            TxnItem(
                uid = r.uid,
                title = TitleExtractor.title(r.sourceType, r.senderKey, r.body),
                sourceLabel = TitleExtractor.sourceLabel(r.sourceType, r.senderKey),
                sourceType = r.sourceType,
                amountPaise = r.amountPaise,
                direction = r.direction,
                date = LocalDate.parse(r.txnDate),
                time = Instant.ofEpochMilli(r.sentAt).atZone(zone).format(timeFormat),
                category = r.category,
                body = r.body,
                dupState = if (r.dupOf != null && r.dupState == "possible") "possible" else null,
                linked = hiddenChildren[r.uid].orEmpty().map {
                    LinkedItem(TitleExtractor.sourceLabel(it.sourceType, it.senderKey), it.body, it.dupState)
                },
            )
        }

        val debits = counted.filter { it.direction == Direction.DEBIT }
        val spent = debits.sumOf { it.amountPaise }
        val byCategory = debits.groupBy { it.category ?: UNCATEGORIZED }
            .mapValues { (_, v) -> v.sumOf { it.amountPaise } }
            .entries.sortedByDescending { it.value }
        val top = byCategory.take(5)
        val rest = byCategory.drop(5).sumOf { it.value }
        val slices = top.map { CategorySlice(it.key, it.value, if (spent > 0) it.value.toFloat() / spent else 0f) } +
            if (rest > 0) listOf(CategorySlice("Other", rest, rest.toFloat() / spent)) else emptyList()

        val days = items.groupBy { it.date }.toSortedMap(compareByDescending { it }).map { (date, list) ->
            DayGroup(
                date = date,
                label = dayLabel(date, today),
                spentPaise = list.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise },
                items = list,
            )
        }

        return MonthSummary(
            period = period,
            spentPaise = spent,
            receivedPaise = counted.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise },
            payments = counted.size,
            uncategorized = counted.count { it.category == null },
            hiddenDuplicates = rows.size - counted.size,
            categories = slices,
            days = days,
        )
    }

    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val shortMonthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    /** "October 2026" for calendar months, "31 Oct – 29 Nov" otherwise. */
    fun periodTitle(p: Period, short: Boolean = false): String =
        if (p.start.dayOfMonth == 1 && p.end == YearMonth.from(p.start).atEndOfMonth()) {
            p.start.format(if (short) shortMonthYear else monthYear)
        } else {
            "${p.start.format(shortDate)} – ${p.end.format(shortDate)}"
        }

    /** "just now", "12 min ago", "Today 8:41 PM", "Yesterday 9:05 AM", "8 Oct 7:02 PM". */
    fun syncedAgo(at: Long, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
        if (at <= 0) return "never"
        val mins = (now - at) / 60_000
        if (mins < 1) return "just now"
        if (mins < 60) return "$mins min ago"
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = t.format(timeFormat)
        return when (t.toLocalDate()) {
            today -> "Today $time"
            today.minusDays(1) -> "Yesterday $time"
            else -> "${t.format(shortDate)} $time"
        }
    }

    fun dayLabel(date: LocalDate, today: LocalDate): String = when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(dayFormat)
    }
}

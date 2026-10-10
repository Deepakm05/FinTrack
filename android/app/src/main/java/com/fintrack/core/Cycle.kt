package com.fintrack.core

import java.time.LocalDate
import java.time.YearMonth

/** One spending period, both ends inclusive. */
data class Period(val start: LocalDate, val end: LocalDate) {
    /** Periods are named after the month they end in (31 Oct – 29 Nov is the "Nov" period). */
    val labelMonth: YearMonth get() = YearMonth.from(end)

    operator fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
}

/**
 * Spending periods that start on a fixed day each month: the 1st (calendar months), the last day of the month
 * (salary credited at month end: 31 Oct – 29 Nov, 30 Nov – 30 Dec, ...) or any day from 2 to 28.
 */
object Cycle {
    const val LAST_DAY = 0
    const val CALENDAR = 1

    fun startOf(month: YearMonth, startDay: Int): LocalDate =
        if (startDay == LAST_DAY) month.atEndOfMonth() else month.atDay(minOf(startDay, month.lengthOfMonth()))

    fun containing(date: LocalDate, startDay: Int): Period {
        val month = YearMonth.from(date)
        val startMonth = if (date.isBefore(startOf(month, startDay))) month.minusMonths(1) else month
        return Period(
            start = startOf(startMonth, startDay),
            end = startOf(startMonth.plusMonths(1), startDay).minusDays(1),
        )
    }

    /** The period that ends in [month]. */
    fun forMonth(month: YearMonth, startDay: Int): Period = containing(month.atDay(1), startDay)

    fun next(period: Period, startDay: Int): Period = containing(period.end.plusDays(1), startDay)

    fun previous(period: Period, startDay: Int): Period = containing(period.start.minusDays(1), startDay)

    fun describe(startDay: Int): String = when (startDay) {
        LAST_DAY -> "Last day of the month"
        CALENDAR -> "1st (calendar month)"
        else -> "Day $startDay of the month"
    }
}

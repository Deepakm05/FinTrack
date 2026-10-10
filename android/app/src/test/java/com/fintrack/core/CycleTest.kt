package com.fintrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class CycleTest {

    private fun d(m: Int, day: Int, y: Int = 2026) = LocalDate.of(y, m, day)

    @Test fun lastDayOfMonthPeriods() {
        val p = Cycle.containing(d(11, 10), Cycle.LAST_DAY)
        assertEquals(Period(d(10, 31), d(11, 29)), p)
        assertEquals(YearMonth.of(2026, 11), p.labelMonth)
        // The last day itself starts the next period.
        assertEquals(Period(d(11, 30), d(12, 30)), Cycle.containing(d(11, 30), Cycle.LAST_DAY))
        assertEquals(Period(d(10, 31), d(11, 29)), Cycle.containing(d(10, 31), Cycle.LAST_DAY))
        assertEquals(Period(d(9, 30), d(10, 30)), Cycle.containing(d(10, 30), Cycle.LAST_DAY))
    }

    @Test fun februaryAndYearBoundaries() {
        assertEquals(Period(d(1, 31), d(2, 27)), Cycle.containing(d(2, 15), Cycle.LAST_DAY))
        assertEquals(Period(d(2, 28), d(3, 30)), Cycle.containing(d(3, 1), Cycle.LAST_DAY))
        assertEquals(Period(d(12, 31, 2025), d(1, 30)), Cycle.containing(d(1, 5), Cycle.LAST_DAY))
        assertEquals(Period(d(2, 29, 2028), d(3, 30, 2028)), Cycle.containing(d(3, 1, 2028), Cycle.LAST_DAY))
    }

    @Test fun calendarAndCustomDay() {
        assertEquals(Period(d(10, 1), d(10, 31)), Cycle.containing(d(10, 10), Cycle.CALENDAR))
        assertEquals(Period(d(9, 25), d(10, 24)), Cycle.containing(d(10, 10), 25))
        assertEquals(Period(d(10, 25), d(11, 24)), Cycle.containing(d(10, 25), 25))
    }

    @Test fun navigationCoversEveryDayOnce() {
        for (startDay in listOf(Cycle.LAST_DAY, Cycle.CALENDAR, 5, 25, 28)) {
            var p = Cycle.containing(d(1, 15), startDay)
            repeat(14) {
                val n = Cycle.next(p, startDay)
                assertEquals("gapless for $startDay", p.end.plusDays(1), n.start)
                assertEquals(p, Cycle.previous(n, startDay))
                p = n
            }
        }
    }

    @Test fun periodsAreNamedByTheMonthTheyEndIn() {
        assertEquals(Period(d(10, 31), d(11, 29)), Cycle.forMonth(YearMonth.of(2026, 11), Cycle.LAST_DAY))
        assertEquals(Period(d(11, 1), d(11, 30)), Cycle.forMonth(YearMonth.of(2026, 11), Cycle.CALENDAR))
        assertTrue(d(11, 29) in Cycle.forMonth(YearMonth.of(2026, 11), Cycle.LAST_DAY))
        assertFalse(d(11, 30) in Cycle.forMonth(YearMonth.of(2026, 11), Cycle.LAST_DAY))
    }
}

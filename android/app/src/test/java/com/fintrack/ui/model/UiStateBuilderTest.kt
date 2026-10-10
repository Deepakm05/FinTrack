package com.fintrack.ui.model

import com.fintrack.core.Direction
import com.fintrack.core.SourceType
import com.fintrack.data.TxnRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class UiStateBuilderTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)

    private fun row(
        uid: String, amount: Long, day: Int, hour: Int = 12, type: SourceType = SourceType.BANK, key: String = "HDFCBK",
        body: String = "Rs $amount debited", dir: Direction = Direction.DEBIT, dupOf: String? = null,
        dupState: String? = null, category: String? = null,
    ) = TxnRow(
        uid, "VM-$key", key, body, ZonedDateTime.of(2026, 10, day, hour, 0, 0, 0, zone).toInstant().toEpochMilli(), 0,
        LocalDate.of(2026, 10, day).toString(), true, amount * 100, dir, type, dupOf, null, dupState, category, 1,
    )

    @Test fun groupsDaysHidesDuplicatesAndSumsCategories() {
        val rows = listOf(
            row("bank", 349, 10, 20, body = "Rs.349.00 spent on card XXXX at SWIGGY", category = "Food & Dining"),
            row("gpay", 349, 10, 20, type = SourceType.UPI_APP, key = "GPAY", body = "₹349 paid to Swiggy", dupOf = "bank", dupState = "auto"),
            row("tea", 40, 10, 9, type = SourceType.UPI_APP, key = "GPAY", body = "₹40 paid to Ramesh Tea Stall"),
            row("uber", 245, 9, type = SourceType.MERCHANT_APP, key = "UBER", body = "₹245 charged", category = "Transport"),
            row("maybe", 100, 8, type = SourceType.DELIVERY, key = "ZOMATO", dupOf = "x", dupState = "possible"),
            row("salary", 85000, 1, dir = Direction.CREDIT, body = "INR 85,000 credited by NEFT-ACME CORP."),
        )
        val m = UiStateBuilder.month(rows, YearMonth.of(2026, 10), today, zone)

        assertEquals((349 + 40 + 245 + 100) * 100L, m.spentPaise)
        assertEquals(8_500_000L, m.receivedPaise)
        assertEquals(5, m.payments)
        assertEquals(1, m.hiddenDuplicates)
        assertEquals(3, m.uncategorized)
        assertEquals(listOf("Today", "Yesterday", "Thu, 8 Oct", "Thu, 1 Oct"), m.days.map { it.label })

        val swiggy = m.days[0].items[0]
        assertEquals("Swiggy", swiggy.title)
        assertEquals("Google Pay", swiggy.linked.single().sourceLabel)
        assertNull(swiggy.dupState)
        assertEquals("Ramesh Tea Stall", m.days[0].items[1].title)
        assertEquals("possible", m.days[2].items[0].dupState)

        assertEquals(listOf("Food & Dining", "Transport", UNCATEGORIZED), m.categories.map { it.name })
        assertEquals(349f / 734f, m.categories[0].fraction, 1e-4f)
    }
}

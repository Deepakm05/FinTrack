package com.fintrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DuplicateMatcherTest {

    private val t0 = 1_791_000_000_000L
    private val min = 60_000L

    private fun bank(uid: String, at: Long, body: String = "Rs 349 spent on card XXXX", amount: Long = 34900) =
        DuplicateMatcher.Txn(uid, SourceType.BANK, "HDFCBK", body, amount, Direction.DEBIT, at)

    private fun swiggy(uid: String, at: Long, amount: Long = 34900) =
        DuplicateMatcher.Txn(uid, SourceType.DELIVERY, "SWIGGY", "Swiggy order of Rs 349 placed", amount, Direction.DEBIT, at)

    @Test fun strongMatchWithinTenMinutes() {
        val m = DuplicateMatcher.best(swiggy("d", t0), listOf(bank("b", t0 + 3 * min)))!!
        assertEquals("b", m.mainUid)
        assertEquals("d", m.dupUid)
        assertEquals(DuplicateMatcher.State.AUTO, m.state)
    }

    @Test fun mediumMatchWithinTwoHoursIsAuto() {
        val m = DuplicateMatcher.best(bank("b", t0), listOf(swiggy("d", t0 + 90 * min)))!!
        assertEquals(0.7, m.score, 1e-9)
        assertEquals(DuplicateMatcher.State.AUTO, m.state)
    }

    @Test fun weakMatchNeedsMerchantMention() {
        val later = t0 + 10 * 60 * min
        assertNull(DuplicateMatcher.best(swiggy("d", later), listOf(bank("b", t0))))
        val m = DuplicateMatcher.best(swiggy("d", later), listOf(bank("b", t0, body = "Rs 349 spent at SWIGGY")))!!
        assertEquals(DuplicateMatcher.State.POSSIBLE, m.state)
    }

    @Test fun differentAmountsOrSameSourceNeverMatch() {
        assertNull(DuplicateMatcher.best(swiggy("d", t0), listOf(bank("b", t0, amount = 35000))))
        assertNull(DuplicateMatcher.best(swiggy("d", t0), listOf(swiggy("d2", t0))))
    }

    @Test fun picksClosestInTime() {
        val m = DuplicateMatcher.best(swiggy("d", t0), listOf(bank("far", t0 + 8 * min), bank("near", t0 + 1 * min)))!!
        assertEquals("near", m.mainUid)
    }

    private fun gpay(uid: String, at: Long, amount: Long = 34900) =
        DuplicateMatcher.Txn(uid, SourceType.UPI_APP, "GPAY", "Paid ₹349 to Swiggy — Google Pay", amount, Direction.DEBIT, at)

    @Test fun bankSmsIsMainOverUpiNotification() {
        val m = DuplicateMatcher.best(gpay("g", t0), listOf(bank("b", t0 + 20_000)))!!
        assertEquals("b", m.mainUid)
        assertEquals("g", m.dupUid)
        assertEquals(DuplicateMatcher.State.AUTO, m.state)
    }

    @Test fun upiNotificationIsMainOverDeliverySms() {
        val m = DuplicateMatcher.best(swiggy("d", t0 + 5_000), listOf(gpay("g", t0)))!!
        assertEquals("g", m.mainUid)
        assertEquals("d", m.dupUid)
        assertEquals(1.0, m.score, 1e-9) // close in time and GPay text names Swiggy
    }

    @Test fun twoUpiNotificationsAreSeparatePayments() {
        assertNull(DuplicateMatcher.best(gpay("g1", t0), listOf(gpay("g2", t0 + 1000))))
    }
}

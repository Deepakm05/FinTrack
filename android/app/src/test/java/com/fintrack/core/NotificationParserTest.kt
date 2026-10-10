package com.fintrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class NotificationParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val at = ZonedDateTime.of(2026, 10, 10, 13, 5, 0, 0, zone).toInstant().toEpochMilli()

    private val GPAY = "com.google.android.apps.nbu.paisa.user"
    private val PHONEPE = "com.phonepe.app"
    private val PAYTM = "net.one97.paytm"

    private fun parse(pkg: String, title: String, text: String) = NotificationParser.parse(pkg, title, text, at, zone)

    @Test fun gpayPaidToMerchant() {
        val p = parse(GPAY, "Payment successful", "₹40 paid to Ramesh Tea Stall")!!
        assertEquals(SourceType.UPI_APP, p.sourceType)
        assertEquals("GPAY", p.senderKey)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(4000L, p.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 10), p.txnDate)
        assertFalse(p.dateFromBody)
    }

    @Test fun gpayMoneyReceived() {
        val p = parse(GPAY, "Rahul Kumar paid you ₹500.00", "Paid to your Bank account XXXX")!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(50000L, p.amountPaise)
    }

    @Test fun phonePeSentAndReceived() {
        assertEquals(Direction.DEBIT, parse(PHONEPE, "Paid ₹1,250 to ZEPTO", "Payment successful from account XX1234")!!.direction)
        assertEquals(Direction.CREDIT, parse(PHONEPE, "Received ₹300 from Anita", "Credited to your account")!!.direction)
        assertEquals(Direction.DEBIT, parse(PHONEPE, "Payment of ₹349 received by Swiggy", "")!!.direction)
    }

    @Test fun paytmWalletPayment() {
        val p = parse(PAYTM, "Paid Rs.60 to UBER", "from Paytm Wallet")!!
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(6000L, p.amountPaise)
    }

    @Test fun paymentWithRewardFooterKept() {
        val p = parse(GPAY, "₹200 paid to Meera", "You earned a scratch card!")!!
        assertEquals(20000L, p.amountPaise)
    }

    @Test fun nonPaymentsIgnored() {
        assertNull(parse(GPAY, "Rahul requested ₹500", "Tap to pay"))
        assertNull(parse(PHONEPE, "Payment failed", "₹200 to XYZ could not be completed"))
        assertNull(parse(PHONEPE, "Payment pending", "₹200 to XYZ is processing"))
        assertNull(parse(PAYTM, "Get ₹100 cashback", "On your next recharge. Recharge now!"))
        assertNull(parse(GPAY, "You won ₹21", "Scratch card reward"))
        assertNull(parse(PHONEPE, "₹500 added to UPI Lite", "Pay small amounts without PIN"))
        assertNull(parse(PAYTM, "Electricity bill of ₹1,200 is due", "Due on 15 Oct. Pay now"))
        assertNull(parse(GPAY, "New message", "Hi, how are you?"))
    }

    @Test fun otherAppsIgnored() {
        assertNull(NotificationParser.parse("com.whatsapp", "Rahul", "Paid ₹200 for dinner", at, zone))
    }

    @Test fun accountNumbersMasked() {
        val p = parse(PHONEPE, "Paid ₹1,250 to ZEPTO", "Payment successful from account XX1234")!!
        assertFalse(p.maskedBody.contains("1234"))
    }
}

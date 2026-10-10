package com.fintrack.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class SmsParserTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private val sentAt = ZonedDateTime.of(2026, 10, 10, 20, 41, 0, 0, zone).toInstant().toEpochMilli()

    private fun parse(sender: String, body: String) = SmsParser.parse(sender, body, sentAt, zone)

    @Test fun hdfcCardSpend() {
        val p = parse("VM-HDFCBK-S", "Rs.349.00 spent on HDFC Bank Card x1234 at SWIGGY on 2026-10-10:20:40:12. Avl Lmt: Rs.45,210.50. Not you? Call 18002586161")!!
        assertEquals(SourceType.BANK, p.sourceType)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(34900L, p.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 10), p.txnDate)
        assertTrue(p.dateFromBody)
        assertEquals("HDFCBK", p.senderKey)
    }

    @Test fun sbiUpiDebitThenCreditedToPayee() {
        val p = parse("AD-SBIUPI", "Dear UPI user A/C X5678 debited by 1,250.00 on date 09Oct26 trf to ZEPTO MARKETPLACE Refno 628312345678. If not u? call 1800111109. -SBI")!!
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(125000L, p.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 9), p.txnDate)
    }

    @Test fun iciciAccountDebitWithBalance() {
        val p = parse("JD-ICICIB-S", "ICICI Bank Acct XX901 debited for Rs 2,000.00 on 08-Oct-26; AMAZON PAY credited. UPI:628112233445. Call 18002662 for dispute. Avl Bal Rs 12,345.67")!!
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(200000L, p.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 8), p.txnDate)
    }

    @Test fun salaryCredit() {
        val p = parse("VM-AXISBK", "INR 85,000.00 credited to A/c no. XX4321 on 01-10-2026 by NEFT-ACME CORP. Avl Bal INR 1,02,340.00")!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(8500000L, p.amountPaise)
        assertEquals(LocalDate.of(2026, 10, 1), p.txnDate)
    }

    @Test fun balanceMentionedBeforeAmount() {
        val p = parse("VM-KOTAKB", "Avl bal: Rs 10,000.00 after debit of Rs 500.00 from Kotak a/c XX12 on 10/10/26")!!
        assertEquals(50000L, p.amountPaise)
        assertEquals(Direction.DEBIT, p.direction)
    }

    @Test fun swiggyOrderPlaced() {
        val p = parse("VM-SWIGGY", "Your Swiggy order #158234 of Rs 349 has been placed. Track it in the app.")!!
        assertEquals(SourceType.DELIVERY, p.sourceType)
        assertEquals(Direction.DEBIT, p.direction)
        assertEquals(34900L, p.amountPaise)
        assertFalse(p.dateFromBody)
        assertEquals(LocalDate.of(2026, 10, 10), p.txnDate)
    }

    @Test fun zomatoRefund() {
        val p = parse("JM-ZOMATO", "Refund of ₹220.50 for your Zomato order has been initiated to your original payment method.")!!
        assertEquals(Direction.CREDIT, p.direction)
        assertEquals(22050L, p.amountPaise)
    }

    @Test fun deliveryPromoIgnored() {
        assertNull(parse("VM-ZOMATO", "Hungry? Get FLAT Rs 100 OFF on orders above Rs 199. Use code YUMMY. Order now!"))
        assertNull(parse("AX-BLINKT", "Your order is out for delivery and will arrive in 8 minutes."))
    }

    @Test fun otpIgnoredButAlertWithOtpWarningKept() {
        assertNull(parse("VM-HDFCBK", "OTP for txn of Rs 2,499.00 at AMAZON on card XX1234 is 482913. Valid for 5 mins. Do not share."))
        assertNotNull(parse("VM-HDFCBK", "Rs 2,499.00 debited from a/c XX1234 on 10-10-26 to AMAZON. Never share OTP with anyone."))
    }

    @Test fun remindersRequestsAndFailuresIgnored() {
        assertNull(parse("VM-SBICRD", "Your SBI Card statement: Total Amt Due Rs 12,400.00, Min Amt Due Rs 620.00, due on 25-Oct-26."))
        assertNull(parse("VM-ICICIB", "Rs 499 will be debited from your a/c XX12 on 12-Oct-26 towards Netflix autopay."))
        assertNull(parse("VM-PAYTMB", "Ramesh has requested Rs 500 from you on Paytm UPI."))
        assertNull(parse("VM-HDFCBK", "Txn of Rs 999.00 on card XX1234 at MYNTRA declined due to insufficient balance."))
    }

    @Test fun personalAndPromoSendersIgnored() {
        assertNull(parse("+919876543210", "Paid Rs 200 for dinner, send back pls"))
        assertNull(parse("VM-AIRTEL", "Recharge with Rs 299 and get 2GB/day. Hurry!"))
        assertNull(parse("VM-HDFCBK", "Get a pre-approved personal loan up to Rs 5,00,000. Apply now."))
    }

    @Test fun unknownBankDetectedFromText() {
        val p = parse("VK-SARBNK", "Rs 750.00 debited from your account XX8812 on 10-Oct-2026 at DMART.")!!
        assertEquals(SourceType.BANK, p.sourceType)
        assertEquals(75000L, p.amountPaise)
    }

    @Test fun implausibleBodyDateFallsBackToSmsDate() {
        val p = parse("VM-HDFCBK", "Rs 100 debited from a/c XX1 on 01-01-2020.")!!
        assertFalse(p.dateFromBody)
        assertEquals(LocalDate.of(2026, 10, 10), p.txnDate)
    }

    @Test fun dateFormats() {
        val sms = LocalDate.of(2026, 10, 10)
        assertEquals(LocalDate.of(2026, 10, 9), SmsParser.extractDate("on 09Oct26 at", sms))
        assertEquals(LocalDate.of(2026, 10, 9), SmsParser.extractDate("on 9 October 2026", sms))
        assertEquals(LocalDate.of(2026, 10, 9), SmsParser.extractDate("on Oct 9, 2026", sms))
        assertEquals(LocalDate.of(2026, 10, 9), SmsParser.extractDate("on 09/10/2026", sms))
        assertEquals(LocalDate.of(2026, 10, 9), SmsParser.extractDate("on 2026-10-09", sms))
        assertEquals(LocalDate.of(2026, 10, 9), SmsParser.extractDate("on 9 Oct 10:30", sms))
        assertEquals(LocalDate.of(2025, 12, 30), SmsParser.extractDate("on 30 Dec", LocalDate.of(2026, 1, 2)))
        assertNull(SmsParser.extractDate("Mark 5 Rs", sms))
    }

    @Test fun amountFormats() {
        assertEquals(123456789L, SmsParser.extractAmountPaise("INR 12,34,567.89 spent"))
        assertEquals(50000L, SmsParser.extractAmountPaise("Rs.500, paid"))
        assertEquals(50000L, SmsParser.extractAmountPaise("paid 500/- to shop"))
        assertEquals(1050L, SmsParser.extractAmountPaise("₹10.5 paid"))
        assertNull(SmsParser.extractAmountPaise("Your order is on the way, 24 hrs 5 mins"))
    }

    @Test fun maskedTextNeverKeepsAccountDigits() {
        val p = parse("VM-HDFCBK", "Rs 500 debited from A/c no. 501004567890 to VPA 9876543210@ybl on 10-10-26. Card ending 4455 not used.")!!
        assertFalse(p.maskedBody.contains("501004567890"))
        assertFalse(p.maskedBody.contains("9876543210"))
        assertFalse(p.maskedBody.contains("4455"))
        assertTrue(p.maskedBody.contains("Rs 500"))
        assertTrue(p.maskedBody.contains("10-10-26"))
    }
}

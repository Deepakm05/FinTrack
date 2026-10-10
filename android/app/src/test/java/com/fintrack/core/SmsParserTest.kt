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

    // Real formats quoted in the docs of the open-source upi_sms_parser / bank_upi_sms_parser packages (pub.dev).
    @Test fun realWorldSamples() {
        val swiggy = SmsParser.parse("JM-SWIGGY", "Payment Confirmed! Your Swiggy Order #236190425176316 of Rs. 356.0 was successfully " +
            "paid on 26/04/2026 at 10:38 PM. Txn ID: 260298464000257. Enjoy your order!", at(2026, 4, 26), zone)!!
        assertEquals(SourceType.DELIVERY, swiggy.sourceType)
        assertEquals(35600L, swiggy.amountPaise)
        assertEquals(LocalDate.of(2026, 4, 26), swiggy.txnDate)

        val sbi = SmsParser.parse("JK-SBIUPI-S", "Dear UPI user A/C X6299 debited by 356.00 on date 26Apr26 trf to Swiggy Refno 260298464000",
            at(2026, 4, 26), zone)!!
        assertEquals(35600L, sbi.amountPaise)
        assertEquals(LocalDate.of(2026, 4, 26), sbi.txnDate)

        val pnb = SmsParser.parse("VM-PNBSMS", "A/c X0298 debited INR 80.00 Dt 20-05-26 21:25:48 to VARISH thru UPI:650693182061.Bal INR 165.00 " +
            "Not u?Fwd this SMS to 9264092640 to block UPI.-PNB", at(2026, 5, 20), zone)!!
        assertEquals(8000L, pnb.amountPaise)
        assertEquals(Direction.DEBIT, pnb.direction)
        assertEquals(LocalDate.of(2026, 5, 20), pnb.txnDate)
        assertFalse(pnb.maskedBody.contains("650693182061"))

        val pnbCr = SmsParser.parse("VM-PNBSMS", "A/c X0298 credited for INR 593.00 on 30-05-26 19:01:10 by ABHISHEK PANWA thru UPI.AvlBal INR 856.50-PNB",
            at(2026, 5, 30), zone)!!
        assertEquals(Direction.CREDIT, pnbCr.direction)
        assertEquals(59300L, pnbCr.amountPaise)

        val icici = SmsParser.parse("AX-ICICIT", "ICICI Bank Acct XX280 debited for Rs 598.10 on 02-Jun-26; Dominos Pizza credited. UPI:207554339160. " +
            "Call 18002662 for dispute.", at(2026, 6, 2), zone)!!
        assertEquals(Direction.DEBIT, icici.direction)
        assertEquals(59810L, icici.amountPaise)
        assertEquals(LocalDate.of(2026, 6, 2), icici.txnDate)

        val iciciCr = SmsParser.parse("AX-ICICIT", "Dear Customer, Acct XX280 is credited with Rs 21000.00 on 04-May-26 from ASHISH MAHENDRA. " +
            "UPI:122599212796-ICICI Bank.", at(2026, 5, 4), zone)!!
        assertEquals(Direction.CREDIT, iciciCr.direction)
        assertEquals(2_100_000L, iciciCr.amountPaise)

        val credit = SmsParser.parse("VM-HDFCBK", "Rs.1,372.00 credited to A/c XX1234 on 27-04-2026 17:13. Avl Bal: Rs.12,450", at(2026, 4, 27), zone)!!
        assertEquals(137200L, credit.amountPaise)

        // HDFC's multi-line UPI alert and card alert.
        val hdfcUpi = SmsParser.parse("VM-HDFCBK-S", "Sent Rs.40.00\nFrom HDFC Bank A/C *1234\nTo RAMESH TEA STALL\nOn 10/10/26\nRef 628312345678\n" +
            "Not You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", at(2026, 10, 10), zone)!!
        assertEquals(Direction.DEBIT, hdfcUpi.direction)
        assertEquals(4000L, hdfcUpi.amountPaise)
        assertFalse(hdfcUpi.maskedBody.contains("1234"))

        val hdfcCard = SmsParser.parse("VM-HDFCBK", "Spent Rs.349 On HDFC Bank Card 5678 At SWIGGY On 2026-10-10:20:40:12 Bal Rs.45210 Not You? " +
            "Call 18002586161/SMS BLOCK DC 5678 to 7308080808", at(2026, 10, 10), zone)!!
        assertEquals(34900L, hdfcCard.amountPaise)
        assertFalse(hdfcCard.maskedBody.contains("5678"))

        // Rejected: OTP, balance alert, personal message.
        assertNull(SmsParser.parse("JK-SBIUPI-S", "Your OTP for UPI transaction is 123456. Do not share. Amount: Rs.500", at(2026, 4, 26), zone))
        assertNull(SmsParser.parse("VM-SBIINB", "Your account balance is Rs.500. Maintain minimum balance.", at(2026, 4, 26), zone))
        assertNull(SmsParser.parse("+919876543210", "Hey, I paid you ₹500 for dinner, sent via phonepe!", at(2026, 4, 26), zone))
    }

    private fun at(y: Int, m: Int, d: Int) = ZonedDateTime.of(y, m, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()
}

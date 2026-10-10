package com.fintrack.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleExtractorTest {

    private fun t(type: SourceType, key: String, body: String) = TitleExtractor.title(type, key, body)

    @Test fun merchantRowsUseBrandName() {
        assertEquals("Swiggy", t(SourceType.DELIVERY, "SWIGGY", "Your order of Rs 349 placed"))
        assertEquals("Rapido", t(SourceType.MERCHANT_APP, "RAPIDO", "Pay ₹103 to your captain"))
    }

    @Test fun upiNotifications() {
        assertEquals("Ramesh Tea Stall", t(SourceType.UPI_APP, "GPAY", "Payment successful — ₹40 paid to Ramesh Tea Stall"))
        assertEquals("Rahul Kumar", t(SourceType.UPI_APP, "GPAY", "Rahul Kumar paid you ₹500.00 — Paid to your Bank account XXXX"))
        assertEquals("Anita", t(SourceType.UPI_APP, "PHONEPE", "Received ₹300 from Anita — Credited to your account"))
        assertEquals("Google Pay", t(SourceType.UPI_APP, "GPAY", "₹40 payment done"))
    }

    @Test fun bankSms() {
        assertEquals("Swiggy", t(SourceType.BANK, "HDFCBK", "Rs.349.00 spent on HDFC Bank Card XXXX at SWIGGY on 2026-10-10"))
        assertEquals("Dmart", t(SourceType.BANK, "SARBNK", "Rs 750.00 debited from your account XXXX on 10-Oct-2026 at DMART."))
        assertEquals("Acme Corp", t(SourceType.BANK, "AXISBK", "INR 85,000.00 credited to A/c no. XXXX on 01-10-2026 by NEFT-ACME CORP."))
        assertEquals("Paytmqr", t(SourceType.BANK, "HDFCBK", "Rs.40.00 debited from a/c XXXX to VPA paytmqr@paytm on 10-10-26"))
        assertEquals("HDFC Bank", t(SourceType.BANK, "HDFCBK", "Rs 500 debited from a/c XXXX on 10-10-26"))
    }

    @Test fun sourceLabels() {
        assertEquals("HDFC Bank SMS", TitleExtractor.sourceLabel(SourceType.BANK, "HDFCBK"))
        assertEquals("Google Pay", TitleExtractor.sourceLabel(SourceType.UPI_APP, "GPAY"))
        assertEquals("Uber app", TitleExtractor.sourceLabel(SourceType.MERCHANT_APP, "UBER"))
    }
}

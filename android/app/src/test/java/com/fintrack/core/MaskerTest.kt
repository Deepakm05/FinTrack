package com.fintrack.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MaskerTest {

    @Test fun masksPartlyMaskedNumbers() {
        assertEquals("A/c XXXX debited", Masker.mask("A/c XX1234 debited"))
        assertEquals("Card XXXX used", Masker.mask("Card ****1234 used"))
        assertEquals("card XXXX at", Masker.mask("card 1234XXXX5678 at"))
        assertEquals("Acct XXXX debited", Masker.mask("Acct x901 debited"))
    }

    @Test fun masksNumbersAfterKeywords() {
        assertEquals("a/c no. XXXX debited", Masker.mask("a/c no. 501004567890 debited"))
        assertEquals("Card ending XXXX", Masker.mask("Card ending 5678"))
        assertEquals("card ending with XXXX", Masker.mask("card ending with 12"))
        assertEquals("Acct no XXXX credited", Masker.mask("Acct no 1234-5678-90 credited"))
        assertEquals("account XXXX.", Masker.mask("account 9988."))
    }

    @Test fun masksLongDigitRuns() {
        assertEquals("UPI Ref XXXX", Masker.mask("UPI Ref 628312345678"))
        assertEquals("VPA XXXX@ybl", Masker.mask("VPA 9876543210@ybl"))
    }

    @Test fun keepsAmountsDatesAndMerchants() {
        val s = "Rs 1,23,456.78 spent at SWIGGY on 10-10-26 20:41"
        assertEquals(s, Masker.mask(s))
    }

    @Test fun isIdempotent() {
        val once = Masker.mask("A/c XX1234 and card 5678 ref 628312345678")
        assertEquals(once, Masker.mask(once))
    }
}

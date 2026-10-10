package com.fintrack.core

/**
 * Replaces every account / card number in an SMS with a fixed "XXXX" so the original length
 * is not revealed either. Amounts, dates and merchant names stay readable.
 */
object Masker {
    const val MASK = "XXXX"

    // Tokens mixing digits with mask characters: XX1234, ****1234, 1234XXXX5678, x1234.
    private val PARTLY_MASKED = Regex("""(?<![A-Za-z0-9])(?=[0-9Xx*#]*\d)(?=[0-9Xx*#]*[Xx*#])[0-9Xx*#]{3,}(?![A-Za-z0-9])""")

    // Numbers right after an account / card keyword: "A/c no. 501004567890", "card ending 5678", "BLOCK DC 5678".
    private val AFTER_KEYWORD = Regex(
        """(?i)((?<![a-z])(?:a/c|a/c\.|ac|acct|account|card|cc|dc)(?:\s*(?:no\.?|number|num|ending(?:\s+(?:with|in))?|ends?(?:\s+(?:with|in))?))?\s*[:.#-]?\s*)(\d[\d -]*\d|\d)(?![\d])"""
    )

    // Any remaining long digit run: account numbers, phone numbers, reference numbers.
    private val LONG_DIGITS = Regex("""(?<![\d])\d{9,}(?![\d])""")

    fun mask(body: String): String {
        var out = PARTLY_MASKED.replace(body, MASK)
        out = AFTER_KEYWORD.replace(out) { m -> m.groupValues[1] + MASK }
        out = LONG_DIGITS.replace(out, MASK)
        // Collapse "XXXX XXXX" style leftovers into one mask.
        return out.replace(Regex("""XXXX(?:[ -]?XXXX)+"""), MASK)
    }
}

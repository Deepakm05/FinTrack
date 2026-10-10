package com.fintrack.core

/**
 * A short, human name for a transaction row ("Swiggy", "Ramesh Tea Stall", "HDFC Bank"), from fixed patterns.
 * Display only: nothing is stored or sent.
 */
object TitleExtractor {

    private val BANK_NAMES = linkedMapOf(
        "HDFC" to "HDFC Bank", "ICICI" to "ICICI Bank", "SBI" to "SBI", "AXIS" to "Axis Bank", "KOTAK" to "Kotak Bank",
        "IDFC" to "IDFC First", "YESB" to "Yes Bank", "INDUS" to "IndusInd", "PNB" to "PNB", "BOB" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda", "CANBNK" to "Canara Bank", "UNIONB" to "Union Bank", "FEDBNK" to "Federal Bank",
        "PAYTM" to "Paytm", "AIRBNK" to "Airtel Payments", "AMEX" to "Amex", "HSBC" to "HSBC", "CITI" to "Citi",
        "ONECRD" to "OneCard", "SLICE" to "slice", "JUPITR" to "Jupiter",
    )

    private val UPI_APP_NAMES = mapOf(
        "GPAY" to "Google Pay", "PHONEPE" to "PhonePe", "PAYTM" to "Paytm", "BHIM" to "BHIM", "CRED" to "CRED",
    )

    // Payee / payer phrases, most specific first. Group 1 is the name.
    private val NAME_PATTERNS = listOf(
        Regex("""(?i)^(.{2,40}?) (?:paid|sent) you"""),
        Regex("""(?i)(?:paid|sent|payment) to ([A-Za-z][^.,|—\n]{1,40}?)(?=\s+(?:on|via|from|using|for|ref|upi|thru|through)\b|[.,|—\n]|$)"""),
        Regex("""(?i)received .{0,20}? from ([A-Za-z][^.,|—\n]{1,40}?)(?=\s+(?:on|via|ref|upi|thru|through)\b|[.,|—\n]|$)"""),
        Regex("""(?i)\btrf to ([A-Za-z][^.,|—\n]{1,40}?)(?=\s+(?:ref|refno|on)\b|[.,|—\n]|$)"""),
        Regex("""(?i)\bat ([A-Za-z][A-Za-z0-9&'. -]{1,40}?)(?=\s+(?:on|via|for|ref)\b|[.,|—\n]|$)"""),
        Regex("""(?i)\bvpa ([A-Za-z0-9._-]+)@"""),
        // ICICI: "debited for Rs 98.10 on 02-Jun-26; RAMESH STORES credited."
        Regex("""(?i);\s*([A-Za-z][A-Za-z0-9&'. -]{1,40}?) credited"""),
        Regex("""(?i)\bto ([A-Za-z][A-Za-z0-9&'. -]{1,40}?)(?=\s+(?:on|via|ref|upi|thru|through|dt)\b|[.,|—\n:]|$)"""),
        Regex("""(?i)\b(?:by|from) (?:neft|imps|rtgs|upi)?[- ]?(?:cr-?)?([A-Za-z][A-Za-z0-9&'. -]{1,40}?)(?=\s+(?:on|ref|thru|through)\b|[.,|—\n]|$)"""),
    )

    private val NOT_A_NAME = Regex("""(?i)^(your|you|a/c|ac|account|card|xxxx|the|bank|beneficiary|upi|vpa|merchant|rs|inr)\b""")

    fun title(sourceType: SourceType, senderKey: String, body: String): String {
        when (sourceType) {
            SourceType.DELIVERY, SourceType.MERCHANT_APP ->
                return SenderClassifier.deliveryBrand(senderKey)?.name ?: pretty(senderKey)
            else -> Unit
        }
        val lower = body.lowercase()
        SenderClassifier.DELIVERY_BRANDS.firstOrNull { b -> b.bodyNames.any { lower.contains(it) } }?.let { return it.name }
        for (rx in NAME_PATTERNS) {
            val name = rx.find(body)?.groupValues?.get(1)?.trim()?.trimEnd('-', '.', ' ') ?: continue
            if (name.length < 2 || NOT_A_NAME.containsMatchIn(name) || name.contains("XXXX")) continue
            return pretty(name)
        }
        return when (sourceType) {
            SourceType.UPI_APP -> UPI_APP_NAMES[senderKey] ?: pretty(senderKey)
            else -> BANK_NAMES.entries.firstOrNull { senderKey.contains(it.key) }?.value ?: pretty(senderKey)
        }
    }

    /** "RAMESH TEA STALL" / "Mr PRADEEP KUMAR" / "paytmqr" -> "Ramesh Tea Stall" / "Mr Pradeep Kumar" / "Paytmqr". */
    fun pretty(raw: String): String {
        val s = raw.replace(Regex("""\s+"""), " ").trim().take(28)
        return s.split(" ").joinToString(" ") { w ->
            val shouting = w.length > 1 && w.none { it.isLowerCase() }
            val allLower = w.none { it.isUpperCase() }
            if (shouting || allLower) w.lowercase().replaceFirstChar { it.uppercase() } else w
        }
    }

    fun sourceLabel(sourceType: SourceType, senderKey: String): String = when (sourceType) {
        SourceType.BANK -> BANK_NAMES.entries.firstOrNull { senderKey.contains(it.key) }?.value?.let { "$it SMS" } ?: "Bank SMS"
        SourceType.UPI_APP -> UPI_APP_NAMES[senderKey] ?: pretty(senderKey)
        SourceType.MERCHANT_APP -> (SenderClassifier.deliveryBrand(senderKey)?.name ?: pretty(senderKey)) + " app"
        SourceType.DELIVERY -> (SenderClassifier.deliveryBrand(senderKey)?.name ?: pretty(senderKey)) + " SMS"
    }
}

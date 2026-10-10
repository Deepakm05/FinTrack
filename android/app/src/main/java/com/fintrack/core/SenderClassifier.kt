package com.fintrack.core

/** Decides whether an SMS sender is a bank / payment app or a delivery / shopping app. */
object SenderClassifier {

    /** A delivery or shopping brand: [senderKeys] match the SMS header, [bodyNames] match bank SMS text. */
    data class Brand(val name: String, val senderKeys: List<String>, val bodyNames: List<String>)

    val DELIVERY_BRANDS = listOf(
        Brand("Swiggy", listOf("SWIGGY", "SWIGY", "INSTMT"), listOf("swiggy", "instamart")),
        Brand("Zomato", listOf("ZOMATO", "ZOMATU"), listOf("zomato")),
        Brand("Blinkit", listOf("BLINKT", "BLINKIT", "GROFRS"), listOf("blinkit", "grofers")),
        Brand("Zepto", listOf("ZEPTO", "ZEPTON"), listOf("zepto")),
        Brand("Amazon", listOf("AMAZON", "AMZN"), listOf("amazon")),
        Brand("Flipkart", listOf("FLPKRT", "FLIPKT", "FLPKART", "FKRT"), listOf("flipkart")),
        Brand("BigBasket", listOf("BIGBSK", "BGBSKT", "BBASKT", "BBNOW"), listOf("bigbasket", "bbnow")),
        Brand("Dunzo", listOf("DUNZO"), listOf("dunzo")),
        Brand("Myntra", listOf("MYNTRA"), listOf("myntra")),
        Brand("JioMart", listOf("JIOMRT", "JIOMART"), listOf("jiomart")),
        Brand("Meesho", listOf("MEESHO"), listOf("meesho")),
        Brand("Nykaa", listOf("NYKAA"), listOf("nykaa")),
        Brand("Ajio", listOf("AJIO"), listOf("ajio")),
        Brand("Dominos", listOf("DOMINO", "DPIZZA"), listOf("domino")),
    )

    /** UPI apps whose notifications FinTrack reads. [senderKeys] hold the key stored for those rows. */
    val UPI_APPS = listOf(
        Brand("Google Pay", listOf("GPAY"), listOf("google pay", "gpay")),
        Brand("PhonePe", listOf("PHONEPE"), listOf("phonepe")),
        Brand("Paytm", listOf("PAYTM"), listOf("paytm")),
        Brand("BHIM", listOf("BHIM"), listOf("bhim")),
        Brand("CRED", listOf("CRED"), listOf("cred")),
    )

    /** Substrings of bank / card / UPI-app SMS headers (DLT sender IDs). */
    val BANK_KEYS = listOf(
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "PNB", "BOI", "BOB", "BARODA", "CANBNK", "CANARA",
        "UNIONB", "IDFC", "YESB", "INDUS", "INDBNK", "FEDBNK", "FEDERL", "RBL", "AUBANK", "AUSFB",
        "PAYTM", "AIRBNK", "IOB", "CENTBK", "CBOI", "UCO", "HSBC", "SCBANK", "STANCB", "CITI",
        "AMEX", "IDBI", "DBS", "KVB", "BANDHN", "ONECRD", "SLICE", "JUPITR", "PHONPE", "PHONEPE",
        "GPAY", "BHIM", "UPI",
    )

    private val DLT_PREFIX = Regex("^[A-Z0-9]{2}-")
    private val DLT_SUFFIX = Regex("-[A-Z]$")
    private val BANKISH_BODY = Regex("""(?<![a-z])(a/c|acct|account|card|upi)(?![a-z])""")

    /**
     * "VM-HDFCBK-S" -> "HDFCBK". Returns null for phone numbers, which are personal SMS.
     */
    fun normalize(sender: String): String? {
        val s = sender.trim().uppercase()
        if (s.isEmpty()) return null
        if (s.none { it.isLetter() } && s.count { it.isDigit() } >= 7) return null
        return s.replace(DLT_PREFIX, "").replace(DLT_SUFFIX, "")
    }

    fun deliveryBrand(senderKey: String): Brand? =
        DELIVERY_BRANDS.firstOrNull { b -> b.senderKeys.any { senderKey.contains(it) } }

    fun upiApp(senderKey: String): Brand? = UPI_APPS.firstOrNull { b -> b.senderKeys.any { senderKey == it } }

    /**
     * Classifies an already-normalized sender. Unknown alphanumeric senders count as BANK only
     * when the text looks like an account / card / UPI alert.
     */
    fun classify(senderKey: String, lowerBody: String): SourceType? = when {
        deliveryBrand(senderKey) != null -> SourceType.DELIVERY
        BANK_KEYS.any { senderKey.contains(it) } -> SourceType.BANK
        senderKey.any { it.isLetter() } && BANKISH_BODY.containsMatchIn(lowerBody) -> SourceType.BANK
        else -> null
    }
}

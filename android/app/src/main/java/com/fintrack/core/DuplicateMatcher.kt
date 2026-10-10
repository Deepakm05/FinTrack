package com.fintrack.core

import kotlin.math.abs

/**
 * Finds records from different sources that describe the same payment, e.g.
 * HDFC "₹349 spent at SWIGGY" + GPay "Paid ₹349 to Swiggy" + Swiggy "order of Rs 349 placed".
 * One record stays the main one (bank SMS first, then UPI app, then delivery app);
 * the others are linked to it as duplicates and left out of totals.
 */
object DuplicateMatcher {

    const val STRONG_GAP_MS = 10 * 60 * 1000L
    const val MEDIUM_GAP_MS = 2 * 60 * 60 * 1000L
    const val WEAK_GAP_MS = 24 * 60 * 60 * 1000L

    /** Score at or above this is counted as a duplicate automatically; below it is only "possible". */
    const val AUTO_THRESHOLD = 0.7

    enum class State(val wire: String) { AUTO("auto"), POSSIBLE("possible") }

    data class Txn(
        val uid: String,
        val sourceType: SourceType,
        val senderKey: String,
        val body: String,
        val amountPaise: Long,
        val direction: Direction,
        val sentAt: Long,
    )

    data class Match(val mainUid: String, val dupUid: String, val score: Double, val gapMs: Long) {
        val state: State get() = if (score >= AUTO_THRESHOLD) State.AUTO else State.POSSIBLE
    }

    /** Lower rank = better main record. The bank SMS is the real money movement. */
    fun rank(type: SourceType): Int = when (type) {
        SourceType.BANK -> 0
        SourceType.UPI_APP -> 1
        SourceType.DELIVERY -> 2
    }

    private fun mainAndDup(a: Txn, b: Txn): Pair<Txn, Txn> = if (rank(a.sourceType) <= rank(b.sourceType)) a to b else b to a

    /** Null when the two can't be the same payment. */
    fun score(a: Txn, b: Txn): Double? {
        if (a.amountPaise != b.amountPaise || a.direction != b.direction) return null
        if (a.sourceType == b.sourceType) return null
        val (main, dup) = mainAndDup(a, b)
        val names = when (dup.sourceType) {
            SourceType.DELIVERY -> SenderClassifier.deliveryBrand(dup.senderKey)?.bodyNames
            SourceType.UPI_APP -> SenderClassifier.upiApp(dup.senderKey)?.bodyNames
            SourceType.BANK -> null
        }.orEmpty()
        val mainText = main.body.lowercase()
        val mentions = names.any { mainText.contains(it) }
        val gap = abs(a.sentAt - b.sentAt)
        val base = when {
            gap <= STRONG_GAP_MS -> 0.9
            gap <= MEDIUM_GAP_MS -> 0.7
            gap <= WEAK_GAP_MS && mentions -> 0.4
            else -> return null
        }
        return minOf(1.0, base + if (mentions) 0.1 else 0.0)
    }

    /** Best match for [new] among [candidates]: highest score, then smallest time gap. */
    fun best(new: Txn, candidates: List<Txn>): Match? =
        candidates.mapNotNull { other ->
            val s = score(new, other) ?: return@mapNotNull null
            val (main, dup) = mainAndDup(new, other)
            Match(main.uid, dup.uid, s, abs(new.sentAt - other.sentAt))
        }.sortedWith(compareByDescending<Match> { it.score }.thenBy { it.gapMs }).firstOrNull()
}

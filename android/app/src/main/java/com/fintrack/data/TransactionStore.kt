package com.fintrack.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.fintrack.core.Direction
import com.fintrack.core.DuplicateMatcher
import com.fintrack.core.ParsedSms
import com.fintrack.core.SourceType
import java.security.MessageDigest
import java.util.UUID

/** One stored transaction, as shown in the app and sent to the PC. */
data class TxnRow(
    val uid: String,
    val sender: String,
    val senderKey: String,
    val body: String,
    val sentAt: Long,
    val receivedAt: Long,
    val txnDate: String,
    val dateFromBody: Boolean,
    val amountPaise: Long,
    val direction: Direction,
    val sourceType: SourceType,
    val dupOf: String?,
    val dupScore: Double?,
    /** auto / possible (from the phone) or confirmed / rejected (decided on the PC). */
    val dupState: String?,
    val category: String?,
    val version: Long,
) {
    /** Linked duplicates are left out of totals unless the PC rejected the link. */
    val countsInTotals: Boolean get() = dupOf == null || dupState == "possible" || dupState == "rejected"
}

/**
 * Local SQLite store. Every change the PC needs to know about bumps the row's [TxnRow.version],
 * so the PC can pull only what changed since its last sync.
 */
class TransactionStore private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "fintrack.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE txn (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                uid TEXT NOT NULL UNIQUE,
                dedup_key TEXT NOT NULL UNIQUE,
                sender TEXT NOT NULL,
                sender_key TEXT NOT NULL,
                body TEXT NOT NULL,
                sent_at INTEGER NOT NULL,
                received_at INTEGER NOT NULL,
                txn_date TEXT NOT NULL,
                date_from_body INTEGER NOT NULL,
                amount_paise INTEGER NOT NULL,
                direction TEXT NOT NULL,
                source_type TEXT NOT NULL,
                dup_of TEXT,
                dup_score REAL,
                dup_state TEXT,
                category TEXT,
                version INTEGER NOT NULL
            )"""
        )
        db.execSQL("CREATE INDEX txn_version ON txn(version)")
        db.execSQL("CREATE INDEX txn_amount ON txn(amount_paise, sent_at)")
        db.execSQL("CREATE TABLE category (name TEXT PRIMARY KEY COLLATE NOCASE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    /**
     * Saves a parsed SMS and links it to a matching bank / delivery SMS if there is one.
     * Returns false when the same SMS was already stored.
     */
    fun insert(parsed: ParsedSms, sender: String, sentAt: Long, receivedAt: Long): Boolean {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val uid = UUID.randomUUID().toString()
            val values = ContentValues().apply {
                put("uid", uid)
                put("dedup_key", dedupKey(parsed.senderKey, parsed.maskedBody, sentAt))
                put("sender", sender)
                put("sender_key", parsed.senderKey)
                put("body", parsed.maskedBody)
                put("sent_at", sentAt)
                put("received_at", receivedAt)
                put("txn_date", parsed.txnDate.toString())
                put("date_from_body", if (parsed.dateFromBody) 1 else 0)
                put("amount_paise", parsed.amountPaise)
                put("direction", parsed.direction.name)
                put("source_type", parsed.sourceType.name)
                put("version", nextVersion(db))
            }
            if (db.insertWithOnConflict("txn", null, values, SQLiteDatabase.CONFLICT_IGNORE) == -1L) return false

            val newTxn = DuplicateMatcher.Txn(
                uid, parsed.sourceType, parsed.senderKey, parsed.maskedBody,
                parsed.amountPaise, parsed.direction, sentAt,
            )
            DuplicateMatcher.best(newTxn, dupCandidates(db, newTxn))?.let { m ->
                db.update(
                    "txn",
                    ContentValues().apply {
                        put("dup_of", m.bankUid)
                        put("dup_score", m.score)
                        put("dup_state", m.state.wire)
                        put("version", nextVersion(db))
                    },
                    "uid = ?", arrayOf(m.deliveryUid),
                )
            }
            db.setTransactionSuccessful()
            return true
        } finally {
            db.endTransaction()
        }
    }

    /** Opposite-source rows with the same amount within 24 h that are not linked yet. */
    private fun dupCandidates(db: SQLiteDatabase, t: DuplicateMatcher.Txn): List<DuplicateMatcher.Txn> {
        val other = if (t.sourceType == SourceType.BANK) SourceType.DELIVERY else SourceType.BANK
        val sql = """SELECT uid, source_type, sender_key, body, amount_paise, direction, sent_at FROM txn a
            WHERE uid != ? AND amount_paise = ? AND direction = ? AND source_type = ?
              AND sent_at BETWEEN ? AND ?
              AND dup_of IS NULL
              AND NOT EXISTS (SELECT 1 FROM txn b WHERE b.dup_of = a.uid)"""
        val args = arrayOf(
            t.uid, t.amountPaise.toString(), t.direction.name, other.name,
            (t.sentAt - DuplicateMatcher.WEAK_GAP_MS).toString(), (t.sentAt + DuplicateMatcher.WEAK_GAP_MS).toString(),
        )
        return db.rawQuery(sql, args).use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        DuplicateMatcher.Txn(
                            c.getString(0), SourceType.valueOf(c.getString(1)), c.getString(2), c.getString(3),
                            c.getLong(4), Direction.valueOf(c.getString(5)), c.getLong(6),
                        )
                    )
                }
            }
        }
    }

    fun changesSince(version: Long, limit: Int): List<TxnRow> =
        query("SELECT * FROM txn WHERE version > ? ORDER BY version LIMIT ?", version.toString(), limit.toString())

    fun recent(limit: Int): List<TxnRow> =
        query("SELECT * FROM txn ORDER BY sent_at DESC LIMIT ?", limit.toString())

    /** Rows whose transaction date starts with [yearMonth] ("2026-10"). */
    fun forMonth(yearMonth: String): List<TxnRow> =
        query("SELECT * FROM txn WHERE txn_date LIKE ? ORDER BY sent_at DESC", "$yearMonth-%")

    fun count(): Long = readableDatabase.rawQuery("SELECT COUNT(*) FROM txn", null).use { it.moveToFirst(); it.getLong(0) }

    fun maxVersion(): Long = maxVersion(readableDatabase)

    /**
     * Applies what the PC decided. These do not bump versions: the PC is the source of truth
     * for categories and duplicate decisions, so there is nothing to send back.
     */
    fun applyFromPc(categories: List<String>?, labels: Map<String, String?>, dupStates: Map<String, String>): Int {
        val db = writableDatabase
        var applied = 0
        db.beginTransaction()
        try {
            if (categories != null) {
                db.delete("category", null, null)
                categories.forEach { db.insertWithOnConflict("category", null, ContentValues().apply { put("name", it) }, SQLiteDatabase.CONFLICT_IGNORE) }
            }
            labels.forEach { (uid, category) ->
                applied += db.update("txn", ContentValues().apply { put("category", category) }, "uid = ?", arrayOf(uid))
            }
            dupStates.forEach { (uid, state) ->
                applied += db.update("txn", ContentValues().apply { put("dup_state", state) }, "uid = ? AND dup_of IS NOT NULL", arrayOf(uid))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return applied
    }

    private fun query(sql: String, vararg args: String): List<TxnRow> =
        readableDatabase.rawQuery(sql, args).use { c -> buildList { while (c.moveToNext()) add(c.toRow()) } }

    private fun Cursor.toRow(): TxnRow {
        fun s(name: String) = getString(getColumnIndexOrThrow(name))
        fun l(name: String) = getLong(getColumnIndexOrThrow(name))
        fun nullable(name: String) = getColumnIndexOrThrow(name).let { if (isNull(it)) null else getString(it) }
        val scoreIdx = getColumnIndexOrThrow("dup_score")
        return TxnRow(
            uid = s("uid"), sender = s("sender"), senderKey = s("sender_key"), body = s("body"),
            sentAt = l("sent_at"), receivedAt = l("received_at"), txnDate = s("txn_date"),
            dateFromBody = l("date_from_body") == 1L, amountPaise = l("amount_paise"),
            direction = Direction.valueOf(s("direction")), sourceType = SourceType.valueOf(s("source_type")),
            dupOf = nullable("dup_of"), dupScore = if (isNull(scoreIdx)) null else getDouble(scoreIdx),
            dupState = nullable("dup_state"), category = nullable("category"), version = l("version"),
        )
    }

    private fun maxVersion(db: SQLiteDatabase): Long =
        db.rawQuery("SELECT COALESCE(MAX(version), 0) FROM txn", null).use { it.moveToFirst(); it.getLong(0) }

    private fun nextVersion(db: SQLiteDatabase): Long = maxVersion(db) + 1

    /** Same sender + same text + same minute = the same SMS seen twice (live receiver and inbox import). */
    private fun dedupKey(senderKey: String, body: String, sentAt: Long): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$senderKey|${sentAt / 60_000}|$body".toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        @Volatile private var instance: TransactionStore? = null
        fun get(context: Context): TransactionStore =
            instance ?: synchronized(this) { instance ?: TransactionStore(context).also { instance = it } }
    }
}

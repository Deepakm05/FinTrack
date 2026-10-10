package com.fintrack.server

import android.content.Context
import com.fintrack.BuildConfig
import com.fintrack.data.Prefs
import com.fintrack.data.TransactionStore
import com.fintrack.data.TxnRow
import com.fintrack.sms.SmsIngest
import fi.iki.elonen.NanoHTTPD
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Small HTTP API the PC talks to. Every request must carry `Authorization: Bearer <token>`.
 *
 *  GET  /api/status                      device id, counts, latest version
 *  GET  /api/changes?since=V&limit=N     rows changed after version V (incremental pull)
 *  POST /api/push                        categories, labels and duplicate decisions from the PC
 *  POST /api/import {"days": N}          scan older SMS from the inbox
 */
class ApiServer(private val context: Context, host: String, port: Int) : NanoHTTPD(host, port) {

    private val prefs = Prefs(context)
    private val store = TransactionStore.get(context)

    override fun serve(session: IHTTPSession): Response = try {
        if (!authorized(session)) {
            json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "missing or wrong token"))
        } else {
            route(session)
        }
    } catch (e: IllegalArgumentException) {
        json(Response.Status.BAD_REQUEST, JSONObject().put("error", e.message ?: "bad request"))
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.toString()))
    }

    private fun route(session: IHTTPSession): Response = when (session.method to session.uri) {
        Method.GET to "/api/status" -> json(
            Response.Status.OK,
            JSONObject()
                .put("app", "fintrack")
                .put("app_version", BuildConfig.VERSION_NAME)
                .put("device_id", prefs.deviceId)
                .put("installed_at", prefs.installedAt)
                .put("count", store.count())
                .put("max_version", store.maxVersion())
                .put("server_time", System.currentTimeMillis()),
        )
        Method.GET to "/api/changes" -> {
            val since = session.parameters["since"]?.firstOrNull()?.toLongOrNull() ?: 0L
            val limit = (session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 500).coerceIn(1, 2000)
            val rows = store.changesSince(since, limit)
            json(
                Response.Status.OK,
                JSONObject()
                    .put("rows", JSONArray(rows.map { it.toJson() }))
                    .put("max_version", store.maxVersion())
                    .put("has_more", rows.size == limit),
            )
        }
        Method.POST to "/api/push" -> {
            val body = readJson(session)
            val categories = body.optJSONArray("categories")?.let { a -> List(a.length()) { a.getString(it) } }
            val labels = body.optJSONArray("labels")?.let { a ->
                (0 until a.length()).associate { i ->
                    val o = a.getJSONObject(i)
                    o.getString("uid") to (if (o.isNull("category")) null else o.getString("category"))
                }
            } ?: emptyMap()
            val dupStates = body.optJSONArray("dup_states")?.let { a ->
                (0 until a.length()).associate { i ->
                    val o = a.getJSONObject(i)
                    val state = o.getString("state")
                    require(state in setOf("auto", "possible", "confirmed", "rejected")) { "bad dup state: $state" }
                    o.getString("uid") to state
                }
            } ?: emptyMap()
            val applied = store.applyFromPc(categories, labels, dupStates)
            json(Response.Status.OK, JSONObject().put("applied", applied))
        }
        Method.POST to "/api/import" -> {
            val days = readJson(session).optInt("days", 30).coerceIn(1, 3650)
            val result = SmsIngest.importInbox(context, System.currentTimeMillis() - days * 86_400_000L)
            json(Response.Status.OK, JSONObject().put("scanned", result.scanned).put("added", result.added))
        }
        else -> json(Response.Status.NOT_FOUND, JSONObject().put("error", "no such endpoint"))
    }

    private fun authorized(session: IHTTPSession): Boolean {
        val header = session.headers["authorization"] ?: return false
        val given = header.removePrefix("Bearer ").trim().toByteArray()
        return MessageDigest.isEqual(given, prefs.token.toByteArray())
    }

    private fun readJson(session: IHTTPSession): JSONObject {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"] ?: return JSONObject()
        return JSONObject(raw)
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString())

    private fun TxnRow.toJson(): JSONObject = JSONObject()
        .put("uid", uid)
        .put("sender", sender)
        .put("sender_key", senderKey)
        .put("body", body)
        .put("sent_at", sentAt)
        .put("received_at", receivedAt)
        .put("txn_date", txnDate)
        .put("date_from_body", dateFromBody)
        .put("amount_paise", amountPaise)
        .put("direction", direction.name)
        .put("source_type", sourceType.name)
        .put("dup_of", dupOf ?: JSONObject.NULL)
        .put("dup_score", dupScore ?: JSONObject.NULL)
        .put("dup_state", dupState ?: JSONObject.NULL)
        .put("version", version)
}

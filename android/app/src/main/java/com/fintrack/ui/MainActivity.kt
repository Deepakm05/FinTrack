package com.fintrack.ui

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import com.fintrack.core.Direction
import com.fintrack.data.Prefs
import com.fintrack.data.TransactionStore
import com.fintrack.data.TxnRow
import com.fintrack.server.LinkService
import com.fintrack.sms.SmsIngest
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Single screen: permissions, this month's summary, PC link controls and recent transactions. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var store: TransactionStore
    private val main = Handler(Looper.getMainLooper())

    private lateinit var permissionBox: LinearLayout
    private lateinit var summary: TextView
    private lateinit var linkSwitch: Switch
    private lateinit var modeGroup: RadioGroup
    private lateinit var addressText: TextView
    private lateinit var tokenText: TextView
    private lateinit var list: LinearLayout

    private val money = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    private val dayFormat = DateTimeFormatter.ofPattern("dd MMM", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        store = TransactionStore.get(this)
        setContentView(buildUi())
        if (!hasSmsPermission()) requestPermissionsNow()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        refresh()
    }

    // ---------------------------------------------------------------- UI

    private fun buildUi(): View {
        val root = vertical().apply { setPadding(dp(16), dp(16), dp(16), dp(32)) }

        root.addView(text("FinTrack", 24f, bold = true))
        root.addView(text("Bank & delivery SMS → date + amount. Categorize on your PC.", 13f, color = GREY))

        permissionBox = card().apply {
            addView(text("SMS permission needed", 16f, bold = true))
            addView(text(
                "FinTrack reads only bank and delivery-app SMS, on this phone.\n\n" +
                    "If Android says \"Restricted setting\": open App info → ⋮ (top right) → " +
                    "Allow restricted settings, then come back and tap Grant.", 13f,
            ))
            addView(row(
                button("Grant") { requestPermissionsNow() },
                button("App info") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
                },
            ))
        }
        root.addView(permissionBox)

        summary = text("", 15f)
        root.addView(card().apply {
            addView(text("This month", 16f, bold = true))
            addView(summary)
        })

        linkSwitch = Switch(this).apply {
            text = "PC link"
            textSize = 16f
            setOnClickListener { if (isChecked) LinkService.start(this@MainActivity) else LinkService.stop(this@MainActivity); main.postDelayed({ refresh() }, 500) }
        }
        modeGroup = RadioGroup(this).apply {
            orientation = RadioGroup.HORIZONTAL
            addView(RadioButton(this@MainActivity).apply { id = ID_WIFI; text = "Wi-Fi" })
            addView(RadioButton(this@MainActivity).apply { id = ID_USB; text = "USB only" })
            check(if (prefs.wifiMode) ID_WIFI else ID_USB)
            setOnCheckedChangeListener { _, checked ->
                prefs.wifiMode = checked == ID_WIFI
                if (LinkService.running) LinkService.start(this@MainActivity) // restart with new binding
                main.postDelayed({ refresh() }, 500)
            }
        }
        addressText = text("", 14f).apply { setTextIsSelectable(true) }
        tokenText = text("", 14f).apply { setTextIsSelectable(true); typeface = Typeface.MONOSPACE }
        root.addView(card().apply {
            addView(linkSwitch)
            addView(modeGroup)
            addView(addressText)
            addView(text("Access token (paste into PC setup once):", 13f, color = GREY))
            addView(tokenText)
            addView(row(
                button("Copy token") { copy(prefs.token) },
                button("New token") { confirmNewToken() },
            ))
        })

        root.addView(row(
            button("Import older SMS") { chooseImport() },
            button("Refresh") { refresh() },
        ))

        root.addView(text("Recent transactions", 16f, bold = true).apply { setPadding(0, dp(16), 0, dp(4)) })
        list = vertical()
        root.addView(list)

        return ScrollView(this).apply {
            fitsSystemWindows = true
            setBackgroundColor(Color.WHITE)
            addView(root)
        }
    }

    private fun refresh() {
        permissionBox.visibility = if (hasSmsPermission()) View.GONE else View.VISIBLE
        linkSwitch.isChecked = LinkService.running
        addressText.text = when {
            LinkService.lastError != null -> "Could not start: ${LinkService.lastError}"
            !LinkService.running -> "PC link is off."
            prefs.wifiMode -> "PC connects to: http://${LinkService.wifiAddress() ?: "<no Wi-Fi>"}:${Prefs.PORT}"
            else -> "USB only. On the PC run:\nadb forward tcp:${Prefs.PORT} tcp:${Prefs.PORT}\nthen connect to http://127.0.0.1:${Prefs.PORT}"
        }
        tokenText.text = prefs.token

        Thread {
            val month = LocalDate.now().toString().substring(0, 7)
            val monthRows = store.forMonth(month)
            val recent = store.recent(200)
            main.post { render(monthRows, recent) }
        }.start()
    }

    private fun render(monthRows: List<TxnRow>, recent: List<TxnRow>) {
        val counted = monthRows.filter { it.countsInTotals }
        val spent = counted.filter { it.direction == Direction.DEBIT }.sumOf { it.amountPaise }
        val received = counted.filter { it.direction == Direction.CREDIT }.sumOf { it.amountPaise }
        val uncategorized = counted.count { it.category == null }
        val hidden = monthRows.size - counted.size
        summary.text = "Spent ${rupees(spent)} · Received ${rupees(received)}\n" +
            "${counted.size} transactions · $uncategorized uncategorized" +
            if (hidden > 0) " · $hidden duplicates hidden" else ""

        list.removeAllViews()
        if (recent.isEmpty()) list.addView(text("Nothing yet. New bank / delivery SMS will appear here.", 14f, color = GREY))
        recent.forEach { list.addView(txnView(it)) }
    }

    private fun txnView(t: TxnRow): View {
        val sent = Instant.ofEpochMilli(t.sentAt).atZone(ZoneId.systemDefault())
        val date = LocalDate.parse(t.txnDate)
        val sign = if (t.direction == Direction.DEBIT) "−" else "+"
        val dup = when {
            t.dupOf == null -> ""
            t.dupState == "possible" -> "  · possible duplicate"
            t.countsInTotals -> ""
            else -> "  · duplicate"
        }
        return vertical().apply {
            setPadding(0, dp(8), 0, dp(8))
            alpha = if (t.countsInTotals) 1f else 0.5f
            addView(row(
                text("${date.format(dayFormat)}  $sign${rupees(t.amountPaise)}", 15f, bold = true,
                    color = if (t.direction == Direction.DEBIT) Color.rgb(0xB0, 0x20, 0x20) else Color.rgb(0x1B, 0x7A, 0x3A)),
                text("  [${t.category ?: "—"}]", 14f, color = GREY),
            ))
            addView(text("${t.senderKey} · ${sent.format(timeFormat)}$dup", 12f, color = GREY))
            addView(text(t.body, 12f).apply { maxLines = 2 })
        }
    }

    // ---------------------------------------------------------------- actions

    private fun hasSmsPermission() =
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissionsNow() {
        val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
        if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
        requestPermissions(perms.toTypedArray(), 1)
    }

    private fun chooseImport() {
        if (!hasSmsPermission()) return requestPermissionsNow()
        val labels = arrayOf("Since install", "Last 30 days", "Last 90 days", "Last 365 days")
        AlertDialog.Builder(this)
            .setTitle("Import SMS from the inbox")
            .setItems(labels) { _, which ->
                val since = when (which) {
                    0 -> prefs.installedAt
                    1 -> System.currentTimeMillis() - 30 * DAY
                    2 -> System.currentTimeMillis() - 90 * DAY
                    else -> System.currentTimeMillis() - 365 * DAY
                }
                toast("Importing…")
                Thread {
                    val r = SmsIngest.importInbox(this, since)
                    main.post { toast("Scanned ${r.scanned} SMS, added ${r.added} transactions"); refresh() }
                }.start()
            }
            .show()
    }

    private fun confirmNewToken() {
        AlertDialog.Builder(this)
            .setTitle("Create a new token?")
            .setMessage("Your PC will stop connecting until you paste the new token into its setup.")
            .setPositiveButton("New token") { _, _ ->
                prefs.regenerateToken()
                if (LinkService.running) LinkService.start(this)
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun copy(value: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FinTrack token", value))
        toast("Token copied")
    }

    // ---------------------------------------------------------------- view helpers

    private fun rupees(paise: Long) = money.format(paise / 100.0)
    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun vertical() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun row(vararg children: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        children.forEach { addView(it) }
    }

    private fun card() = vertical().apply {
        setPadding(dp(12), dp(12), dp(12), dp(12))
        setBackgroundColor(Color.rgb(0xF3, 0xF4, 0xF6))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(12) }
    }

    private fun text(value: String, size: Float, bold: Boolean = false, color: Int = Color.rgb(0x11, 0x18, 0x27)) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    companion object {
        private const val ID_WIFI = 101
        private const val ID_USB = 102
        private const val DAY = 86_400_000L
        private val GREY = Color.rgb(0x6B, 0x72, 0x80)
    }
}

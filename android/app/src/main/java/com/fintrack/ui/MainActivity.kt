package com.fintrack.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.fintrack.data.Prefs
import com.fintrack.data.TransactionStore
import com.fintrack.notify.PaymentNotificationListener
import com.fintrack.server.LinkService
import com.fintrack.sms.SmsIngest
import com.fintrack.ui.model.LinkState
import com.fintrack.ui.model.MonthSummary
import com.fintrack.ui.model.SetupState
import com.fintrack.ui.model.UiStateBuilder
import com.fintrack.ui.screens.AppActions
import com.fintrack.ui.screens.AppUiState
import com.fintrack.ui.screens.FinTrackAppContent
import com.fintrack.ui.theme.FinTrackTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

class MainActivity : ComponentActivity() {

    /** Bumped whenever something may have changed: resume, new SMS / notification, PC push, link toggled. */
    private var refreshTick by mutableIntStateOf(0)
    private val onStoreChanged: () -> Unit = { runOnUiThread { refreshTick++ } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        TransactionStore.get(this).addListener(onStoreChanged)
        setContent { FinTrackTheme { AppRoot(refreshTick, onChanged = { refreshTick++ }) } }
    }

    override fun onResume() {
        super.onResume()
        refreshTick++
    }

    override fun onDestroy() {
        TransactionStore.get(this).removeListener(onStoreChanged)
        super.onDestroy()
    }

    /** Recomposes whenever [tick] changes, so permission / link state below is re-read too. */
    @Composable
    private fun AppRoot(tick: Int, onChanged: () -> Unit) {
        val prefs = remember { Prefs(this) }
        val store = remember { TransactionStore.get(this) }
        val scope = rememberCoroutineScope()
        var month by remember { mutableStateOf(YearMonth.now()) }
        var summary by remember { mutableStateOf(MonthSummary.empty(month)) }

        val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            onChanged()
        }

        LaunchedEffect(tick, month) {
            summary = withContext(Dispatchers.IO) { UiStateBuilder.month(store.forMonth(month.toString()), month) }
        }

        val state = AppUiState(
            summary = summary,
            setup = SetupState(smsGranted = hasSmsPermission(), notificationAccess = PaymentNotificationListener.isEnabled(this)),
            link = LinkState(
                running = LinkService.running,
                wifiMode = prefs.wifiMode,
                wifiAddress = LinkService.wifiAddress(),
                port = Prefs.PORT,
                token = prefs.token,
                error = LinkService.lastError,
            ),
            isCurrentMonth = month >= YearMonth.now(),
        )

        fun refreshSoon() = scope.launch { delay(600); onChanged() }

        val actions = AppActions(
            prevMonth = { month = month.minusMonths(1) },
            nextMonth = { if (month < YearMonth.now()) month = month.plusMonths(1) },
            grantSms = {
                val perms = mutableListOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
                if (Build.VERSION.SDK_INT >= 33) perms += Manifest.permission.POST_NOTIFICATIONS
                permissionLauncher.launch(perms.toTypedArray())
            },
            openNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
            openAppInfo = {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
            },
            setLink = { on ->
                if (on) LinkService.start(this) else LinkService.stop(this)
                refreshSoon()
            },
            setWifiMode = { wifi ->
                prefs.wifiMode = wifi
                if (LinkService.running) LinkService.start(this) // restart with the new binding
                refreshSoon()
            },
            copy = { text ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FinTrack", text))
                toast("Copied")
            },
            newToken = {
                prefs.regenerateToken()
                if (LinkService.running) LinkService.start(this)
                onChanged()
            },
            importSms = { days ->
                if (!hasSmsPermission()) {
                    toast("Allow SMS access first")
                } else {
                    toast("Importing…")
                    scope.launch {
                        val since = if (days == 0) prefs.installedAt else System.currentTimeMillis() - days * 86_400_000L
                        val r = withContext(Dispatchers.IO) { SmsIngest.importInbox(this@MainActivity, since) }
                        toast("Checked ${r.scanned} SMS, added ${r.added} payments")
                        onChanged()
                    }
                }
            },
        )

        FinTrackAppContent(state, actions)
    }

    private fun hasSmsPermission() =
        checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}

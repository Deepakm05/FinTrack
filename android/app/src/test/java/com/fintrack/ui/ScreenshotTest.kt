package com.fintrack.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.fintrack.core.Cycle
import com.fintrack.core.Direction
import com.fintrack.core.Period
import com.fintrack.core.SourceType
import com.fintrack.data.TxnRow
import com.fintrack.ui.model.LinkState
import com.fintrack.ui.model.SetupState
import com.fintrack.ui.model.UiStateBuilder
import com.fintrack.ui.screens.AppActions
import com.fintrack.ui.screens.AppUiState
import com.fintrack.ui.screens.FinTrackAppContent
import com.fintrack.ui.screens.Tab
import com.fintrack.ui.screens.TxnDetailContent
import com.fintrack.ui.theme.FinTrackTheme
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Renders every screen on the JVM (no emulator). CI records these and publishes them to the
 * `ui-snapshots` branch so the design can be reviewed without installing the app.
 */
class ScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar")

    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 10)

    private fun row(
        uid: String, amount: Long, day: Int, hour: Int, min: Int, type: SourceType, key: String, body: String,
        dir: Direction = Direction.DEBIT, dupOf: String? = null, dupState: String? = null, category: String? = null,
    ) = TxnRow(
        uid, key, key, body, ZonedDateTime.of(2026, 10, day, hour, min, 0, 0, zone).toInstant().toEpochMilli(), 0,
        LocalDate.of(2026, 10, day).toString(), true, amount * 100, dir, type, dupOf, null, dupState, category, 1,
    )

    private val rows = listOf(
        row("swg", 349, 10, 20, 41, SourceType.BANK, "HDFCBK", "Rs.349.00 spent on HDFC Bank Card XXXX at SWIGGY on 2026-10-10:20:40:12. Avl Lmt: Rs.45,210.50", category = "Food & Dining"),
        row("swg-g", 349, 10, 20, 41, SourceType.UPI_APP, "GPAY", "Payment successful — ₹349 paid to Swiggy", dupOf = "swg", dupState = "auto"),
        row("swg-a", 349, 10, 20, 42, SourceType.MERCHANT_APP, "SWIGGY", "Payment received — ₹349 paid for your order from Meghana Foods", dupOf = "swg", dupState = "auto"),
        row("tea", 40, 10, 9, 5, SourceType.UPI_APP, "GPAY", "Payment successful — ₹40 paid to Ramesh Tea Stall"),
        row("uber", 245, 9, 18, 30, SourceType.MERCHANT_APP, "UBER", "Thanks for riding — ₹245.32 has been charged to your UPI", category = "Transport"),
        row("rapido", 103, 9, 9, 12, SourceType.BANK, "SBIUPI", "Dear UPI user A/C XXXX debited by 103.00 on date 09Oct26 trf to RAPIDO CAPTAIN Refno XXXX", category = "Transport"),
        row("zepto", 1250, 8, 19, 2, SourceType.UPI_APP, "PHONEPE", "Paid ₹1,250 to ZEPTO — Payment successful from account XXXX", category = "Groceries"),
        row("amazon", 1299, 7, 13, 15, SourceType.MERCHANT_APP, "AMAZON", "Payment of ₹1,299 successful for your order", category = "Shopping"),
        row("zomato", 420, 6, 21, 10, SourceType.DELIVERY, "ZOMATO", "Your Zomato order of Rs 420 has been delivered", dupOf = "x", dupState = "possible"),
        row("rent", 18000, 5, 10, 0, SourceType.BANK, "HDFCBK", "Rs 18,000.00 debited from a/c XXXX to VPA landlord@okhdfc on 05-10-26", category = "Rent"),
        row("salary", 85000, 1, 9, 30, SourceType.BANK, "AXISBK", "INR 85,000.00 credited to A/c no. XXXX on 01-10-2026 by NEFT-ACME CORP.", dir = Direction.CREDIT, category = "Transfers"),
    )

    private val october = Period(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31))
    private val summary = UiStateBuilder.month(rows, october, today, zone)
    private val link = LinkState(running = true, wifiMode = true, wifiAddress = "192.168.1.5", port = 8765,
        token = "q3Zt9xVb2LmN8kPwR4sYc1Hd", error = null,
        lastSyncAt = ZonedDateTime.of(2026, 10, 10, 20, 50, 0, 0, zone).toInstant().toEpochMilli())
    private val state = AppUiState(summary, SetupState(smsGranted = true, notificationAccess = true), link,
        cycleStartDay = Cycle.CALENDAR)

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) =
        paparazzi.snapshot(name) { FinTrackTheme(darkTheme = dark) { content() } }

    @Test fun homeLight() = shot("home_light") { FinTrackAppContent(state, AppActions()) }

    @Test fun homeDark() = shot("home_dark", dark = true) { FinTrackAppContent(state, AppActions()) }

    @Test fun homeSetupNeeded() = shot("home_setup") {
        FinTrackAppContent(state.copy(setup = SetupState(smsGranted = true, notificationAccess = false),
            link = link.copy(running = false)), AppActions())
    }

    @Test fun homeEmpty() = shot("home_empty") {
        FinTrackAppContent(state.copy(summary = UiStateBuilder.month(emptyList(), october, today, zone),
            setup = SetupState(smsGranted = false, notificationAccess = false), link = link.copy(running = false)), AppActions())
    }

    /** Default setting: periods start on the last day of the month (salary at month end). */
    @Test fun homeSalaryCycle() = shot("home_salary_cycle") {
        val p = Period(LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 30))
        FinTrackAppContent(state.copy(summary = UiStateBuilder.month(rows, p, today, zone), cycleStartDay = Cycle.LAST_DAY), AppActions())
    }

    @Test fun activityLight() = shot("activity_light") { FinTrackAppContent(state, AppActions(), initialTab = Tab.ACTIVITY) }

    @Test fun activityDark() = shot("activity_dark", dark = true) {
        FinTrackAppContent(state, AppActions(), initialTab = Tab.ACTIVITY)
    }

    @Test fun linkOn() = shot("link_on") { FinTrackAppContent(state, AppActions(), initialTab = Tab.LINK) }

    @Test fun linkOffUsbDark() = shot("link_off_usb_dark", dark = true) {
        FinTrackAppContent(state.copy(link = link.copy(running = false, wifiMode = false)), AppActions(), initialTab = Tab.LINK)
    }

    @Test fun detail() = shot("detail") {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            TxnDetailContent(summary.items.first())
        }
    }

    @Test fun detailDark() = shot("detail_dark", dark = true) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerLowest) {
            TxnDetailContent(summary.items.first { it.title == "Rapido" || it.title.startsWith("Rapido") })
        }
    }
}

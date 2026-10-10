@file:OptIn(ExperimentalMaterial3Api::class)

package com.fintrack.ui.screens

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.fintrack.ui.model.LinkState
import com.fintrack.ui.model.MonthSummary
import com.fintrack.ui.model.SetupState
import com.fintrack.ui.model.TxnItem

data class AppUiState(
    val summary: MonthSummary,
    val setup: SetupState,
    val link: LinkState,
    val isCurrentPeriod: Boolean = true,
    /** See [com.fintrack.core.Cycle]: 0 = last day of month, 1 = calendar month, 2..28 = that day. */
    val cycleStartDay: Int = com.fintrack.core.Cycle.LAST_DAY,
)

/** Everything the screens can ask for. Defaults do nothing, for previews and screenshot tests. */
data class AppActions(
    val prevMonth: () -> Unit = {},
    val nextMonth: () -> Unit = {},
    val setCycleStart: (Int) -> Unit = {},
    val grantSms: () -> Unit = {},
    val openNotificationAccess: () -> Unit = {},
    val openAppInfo: () -> Unit = {},
    val setLink: (Boolean) -> Unit = {},
    val setWifiMode: (Boolean) -> Unit = {},
    val copy: (String) -> Unit = {},
    val newToken: () -> Unit = {},
    /** 0 = since install, else number of days. */
    val importSms: (Int) -> Unit = {},
)

enum class Tab(val label: String) { HOME("Home"), ACTIVITY("Activity"), LINK("PC link") }

@Composable
fun FinTrackAppContent(
    state: AppUiState,
    actions: AppActions,
    initialTab: Tab = Tab.HOME,
    initialSelected: TxnItem? = null,
) {
    var tab by rememberSaveable { mutableIntStateOf(initialTab.ordinal) }
    var selected by androidx.compose.runtime.remember { mutableStateOf(initialSelected) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t.ordinal,
                        onClick = { tab = t.ordinal },
                        icon = {
                            Icon(
                                when (t) {
                                    Tab.HOME -> Icons.Filled.Home
                                    Tab.ACTIVITY -> Icons.AutoMirrored.Filled.List
                                    Tab.LINK -> Icons.Filled.Share
                                },
                                contentDescription = null,
                            )
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (Tab.entries[tab]) {
            Tab.HOME -> HomeScreen(state, actions, modifier, onOpen = { selected = it }, onSeeAll = { tab = Tab.ACTIVITY.ordinal },
                onOpenLink = { tab = Tab.LINK.ordinal })
            Tab.ACTIVITY -> ActivityScreen(state, actions, modifier, onOpen = { selected = it })
            Tab.LINK -> LinkScreen(state.link, actions, modifier)
        }
    }

    selected?.let { item -> TxnDetailSheet(item, onDismiss = { selected = null }) }
}

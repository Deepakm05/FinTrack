package com.fintrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardType
import com.fintrack.core.Cycle
import com.fintrack.ui.model.UiStateBuilder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fintrack.ui.model.TxnItem
import com.fintrack.ui.theme.AmountStyle
import java.time.format.DateTimeFormatter
import java.util.Locale


@Composable
fun HomeScreen(
    state: AppUiState,
    actions: AppActions,
    modifier: Modifier = Modifier,
    onOpen: (TxnItem) -> Unit,
    onSeeAll: () -> Unit,
    onOpenLink: () -> Unit,
) {
    val s = state.summary
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state, actions, onOpenLink) }
        item { HeroCard(state, actions) }
        if (!state.setup.complete) item { SetupCard(state, actions) }
        if (s.categories.isNotEmpty()) {
            item {
                SectionCard {
                    SectionTitle("Where it went")
                    Spacer(Modifier.height(14.dp))
                    CategoryBars(s.categories)
                    if (s.uncategorized > 0) {
                        Spacer(Modifier.height(12.dp))
                        Text("Categorize the rest from your PC with Claude or the FinTrack console.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SectionCard {
                SectionTitle("Recent") {
                    if (s.payments > 5) TextButton(onClick = onSeeAll) { Text("See all") }
                }
                val recent = s.items.take(5)
                if (recent.isEmpty()) {
                    EmptyState("No payments yet",
                        if (state.isCurrentPeriod) "New bank SMS and payment-app notifications show up here as they arrive."
                        else "Nothing was recorded in this period.")
                } else {
                    recent.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                        TxnListRow(item) { onOpen(item) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

@Composable
private fun Header(state: AppUiState, actions: AppActions, onOpenLink: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("FinTrack", style = MaterialTheme.typography.headlineSmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(
                    if (state.link.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                Spacer(Modifier.width(6.dp))
                Text(
                    (if (state.link.running) "PC link on" else "PC link off") +
                        " · synced " + UiStateBuilder.syncedAgo(state.link.lastSyncAt),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (!state.link.running) TextButton(onClick = onOpenLink) { Text("Connect PC") }
    }
}

@Composable
private fun HeroCard(state: AppUiState, actions: AppActions) {
    val s = state.summary
    val onHero = MaterialTheme.colorScheme.onPrimaryContainer
    var choosing by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = onHero, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.prevMonth) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
                }
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { choosing = true }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(UiStateBuilder.periodTitle(s.period), style = MaterialTheme.typography.titleSmall)
                    Text("Starts on: ${Cycle.describe(state.cycleStartDay).lowercase()} ▾", style = MaterialTheme.typography.labelSmall,
                        color = onHero.copy(alpha = 0.7f))
                }
                IconButton(onClick = actions.nextMonth, enabled = !state.isCurrentPeriod) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
                }
            }
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Spent", style = MaterialTheme.typography.labelLarge, color = onHero.copy(alpha = 0.75f))
                Text(rupees(s.spentPaise, whole = true), style = MaterialTheme.typography.displaySmall.merge(AmountStyle))
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat("Received", rupees(s.receivedPaise, whole = true))
                    Stat("Payments", s.payments.toString())
                    Stat("To categorize", s.uncategorized.toString())
                }
                if (s.hiddenDuplicates > 0) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${s.hiddenDuplicates} duplicate record${if (s.hiddenDuplicates == 1) "" else "s"} merged, so nothing is counted twice",
                        style = MaterialTheme.typography.bodySmall, color = onHero.copy(alpha = 0.75f),
                    )
                }
            }
        }
    }
    if (choosing) {
        CycleDialog(state.cycleStartDay, onPick = { actions.setCycleStart(it); choosing = false }, onDismiss = { choosing = false })
    }
}

@Composable
private fun CycleDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf(if (current in 2..28) current.toString() else "25") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Each period starts on") },
        text = {
            Column {
                listOf(Cycle.LAST_DAY, Cycle.CALENDAR).forEach { day ->
                    Row(Modifier.fillMaxWidth().clickable { onPick(day) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = current == day, onClick = { onPick(day) })
                        Column {
                            Text(Cycle.describe(day), style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (day == Cycle.LAST_DAY) "e.g. 31 Oct – 29 Nov, for a salary credited at month end"
                                else "1 Oct – 31 Oct",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = current in 2..28, onClick = { custom.toIntOrNull()?.takeIf { it in 2..28 }?.let(onPick) })
                    Text("Day", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = custom, onValueChange = { v -> custom = v.filter { it.isDigit() }.take(2) },
                        singleLine = true, modifier = Modifier.width(72.dp),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { custom.toIntOrNull()?.takeIf { it in 2..28 }?.let(onPick) }) { Text("Use") }
                }
                Text("The PC uses the same periods after its next sync.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.titleMedium.merge(AmountStyle))
        Text(label, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
    }
}

@Composable
private fun SetupCard(state: AppUiState, actions: AppActions) {
    SectionCard {
        SectionTitle("Finish setup")
        Spacer(Modifier.height(4.dp))
        Text("FinTrack needs both to catch every payment.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        SetupRow("Read bank & shopping SMS", state.setup.smsGranted, "Allow", actions.grantSms)
        SetupRow("Read payment-app notifications", state.setup.notificationAccess, "Turn on", actions.openNotificationAccess)
        Spacer(Modifier.height(8.dp))
        Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Greyed out or \"Restricted setting\"? Open App info → ⋮ → Allow restricted settings.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = actions.openAppInfo) { Text("App info") }
            }
        }
    }
}

@Composable
private fun SetupRow(label: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (done) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = if (done) "Done" else "Needed",
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        if (!done) FilledTonalButton(onClick = onClick) { Text(action) }
    }
}

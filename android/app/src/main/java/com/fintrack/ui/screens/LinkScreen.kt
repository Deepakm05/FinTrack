@file:OptIn(ExperimentalMaterial3Api::class)

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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.fintrack.ui.model.LinkState

@Composable
fun LinkScreen(link: LinkState, actions: AppActions, modifier: Modifier = Modifier) {
    var showToken by remember { mutableStateOf(false) }
    var confirmNewToken by remember { mutableStateOf(false) }
    var chooseImport by remember { mutableStateOf(false) }
    val address = if (link.wifiMode) "http://${link.wifiAddress ?: "<no Wi-Fi>"}:${link.port}" else "http://127.0.0.1:${link.port}"

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("PC link", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 4.dp)) }

        item {
            Surface(
                color = if (link.running) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = RoundedCornerShape(24.dp),
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(
                                if (link.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null,
                                tint = if (link.running) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (link.running) "PC link is on" else "PC link is off",
                                style = MaterialTheme.typography.titleMedium)
                            Text(
                                (if (link.running) "Your PC can sync while this is on" else "Turn on to sync with your PC") +
                                    "\nLast synced: " + com.fintrack.ui.model.UiStateBuilder.syncedAgo(link.lastSyncAt),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = link.running, onCheckedChange = actions.setLink)
                    }
                    if (link.error != null) {
                        Spacer(Modifier.height(10.dp))
                        Text("Couldn't start: ${link.error}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(16.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = link.wifiMode, onClick = { actions.setWifiMode(true) },
                            shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Wi-Fi") }
                        SegmentedButton(selected = !link.wifiMode, onClick = { actions.setWifiMode(false) },
                            shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("USB only") }
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Address")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(address, style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = { actions.copy(address) }) { Text("Copy") }
                }
                Spacer(Modifier.height(16.dp))
                SectionTitle("Access token")
                Spacer(Modifier.height(8.dp))
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (showToken) link.token else "•".repeat(16) + link.token.takeLast(4),
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { actions.copy(link.token) }) { Text("Copy token") }
                    OutlinedButton(onClick = { confirmNewToken = true }) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("New token")
                    }
                }
            }
        }

        item {
            SectionCard {
                SectionTitle("Connect from your PC")
                Spacer(Modifier.height(10.dp))
                val steps = if (link.wifiMode) listOf(
                    "Keep the phone and PC on the same Wi-Fi.",
                    "Turn PC link on above.",
                    "On the PC run:  fintrack setup --phone-url $address --token <token>",
                    "Ask Claude: \"categorize my expenses this period\".",
                ) else listOf(
                    "Connect the phone with a USB cable (USB debugging on).",
                    "On the PC run:  adb forward tcp:${link.port} tcp:${link.port}",
                    "Then:  fintrack setup --phone-url $address --token <token>",
                    "Turn PC link on above and ask Claude to categorize.",
                )
                steps.forEachIndexed { i, step -> Step(i + 1, step) }
            }
        }

        item {
            SectionCard {
                SectionTitle("Older SMS")
                Spacer(Modifier.height(4.dp))
                Text("FinTrack tracks new messages from install. Add older bank SMS from your inbox any time; " +
                    "nothing is added twice.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = { chooseImport = true }) { Text("Import older SMS") }
            }
        }

        item {
            Text(
                "Your data stays on your phone and your PC. FinTrack has no account and no cloud, and every request " +
                    "from the PC needs the token. Account and card numbers are masked before anything is saved.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }

    if (confirmNewToken) {
        AlertDialog(
            onDismissRequest = { confirmNewToken = false },
            title = { Text("Create a new token?") },
            text = { Text("Your PC will stop connecting until you run fintrack setup with the new token.") },
            confirmButton = { TextButton(onClick = { confirmNewToken = false; actions.newToken() }) { Text("New token") } },
            dismissButton = { TextButton(onClick = { confirmNewToken = false }) { Text("Cancel") } },
        )
    }
    if (chooseImport) {
        AlertDialog(
            onDismissRequest = { chooseImport = false },
            title = { Text("Import SMS from") },
            text = {
                Column {
                    listOf(0 to "Since install", 30 to "Last 30 days", 90 to "Last 90 days", 365 to "Last 365 days").forEach { (days, label) ->
                        TextButton(onClick = { chooseImport = false; actions.importSms(days) }, modifier = Modifier.fillMaxWidth()) {
                            Text(label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { chooseImport = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.size(24.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Text("$n", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 2.dp))
    }
}

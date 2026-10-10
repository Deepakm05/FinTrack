@file:OptIn(ExperimentalMaterial3Api::class)

package com.fintrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fintrack.core.Direction
import com.fintrack.ui.model.TxnItem
import com.fintrack.ui.theme.AmountStyle
import java.time.format.DateTimeFormatter
import java.util.Locale

private enum class Filter(val label: String) { ALL("All"), SPENT("Spent"), RECEIVED("Received"), TODO("Uncategorized") }


@Composable
fun ActivityScreen(state: AppUiState, actions: AppActions, modifier: Modifier = Modifier, onOpen: (TxnItem) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(Filter.ALL) }

    val days = remember(state.summary, query, filter) {
        state.summary.days.mapNotNull { day ->
            val items = day.items.filter { item ->
                val f = when (filter) {
                    Filter.ALL -> true
                    Filter.SPENT -> item.direction == Direction.DEBIT
                    Filter.RECEIVED -> item.direction == Direction.CREDIT
                    Filter.TODO -> item.category == null
                }
                f && (query.isBlank() || item.title.contains(query, true) || item.body.contains(query, true) ||
                    (item.category?.contains(query, true) == true))
            }
            if (items.isEmpty()) null else day.copy(items = items)
        }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Activity", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                MonthStepper(state, actions)
            }
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search payee or message", maxLines = 1) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "Clear") }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    unfocusedBorderColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Filter.entries.toList()) { f ->
                    FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
                }
            }
        }
        if (days.isEmpty()) {
            item {
                SectionCard {
                    EmptyState(
                        if (query.isNotBlank() || filter != Filter.ALL) "Nothing matches" else "No payments in this period",
                        if (query.isNotBlank() || filter != Filter.ALL) "Try another search or filter."
                        else "Bank SMS and payment-app notifications appear here as they arrive.",
                    )
                }
            }
        }
        days.forEach { day ->
            item(key = day.date.toString()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(day.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            if (day.spentPaise > 0) {
                                Text("−" + rupees(day.spentPaise), style = MaterialTheme.typography.bodySmall.merge(AmountStyle),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        day.items.forEach { TxnListRow(it) { onOpen(it) } }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthStepper(state: AppUiState, actions: AppActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLowest, shape = RoundedCornerShape(50)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.prevMonth) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous period")
            }
            Text(com.fintrack.ui.model.UiStateBuilder.periodTitle(state.summary.period, short = true),
                style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = actions.nextMonth, enabled = !state.isCurrentPeriod) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next period")
            }
        }
    }
}

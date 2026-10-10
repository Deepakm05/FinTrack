@file:OptIn(ExperimentalMaterial3Api::class)

package com.fintrack.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fintrack.core.Direction
import com.fintrack.ui.model.TxnItem
import com.fintrack.ui.theme.AmountStyle
import com.fintrack.ui.theme.LocalMoneyColors
import java.time.format.DateTimeFormatter
import java.util.Locale

private val fullDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

@Composable
fun TxnDetailSheet(item: TxnItem, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        TxnDetailContent(item)
    }
}

/** The sheet's content, separate so screenshot tests can render it without a sheet. */
@Composable
fun TxnDetailContent(item: TxnItem) {
    val money = LocalMoneyColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp)
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(item.title, item.category, item.sourceType, size = 56)
        Spacer(Modifier.height(10.dp))
        Text(item.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            signedRupees(item.amountPaise, item.direction),
            style = MaterialTheme.typography.displaySmall.merge(AmountStyle),
            color = if (item.direction == Direction.CREDIT) money.credit else money.debit,
        )
        Spacer(Modifier.height(8.dp))
        CategoryTag(item.category)
        Spacer(Modifier.height(20.dp))

        SectionCardFlat {
            DetailLine("Date", item.date.format(fullDate))
            DetailLine("Time", item.time)
            DetailLine("Recorded from", item.sourceLabel)
            DetailLine("Type", if (item.direction == Direction.DEBIT) "Money out" else "Money in")
            if (item.category == null) {
                Spacer(Modifier.height(6.dp))
                Text("Not categorized yet. Ask Claude on your PC, or set it in the FinTrack console.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(16.dp))
        MessageBlock("Message", item.sourceLabel, item.body)

        if (item.dupState == "possible") {
            Spacer(Modifier.height(12.dp))
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()) {
                Text("This might be the same payment as another record. It's counted until you confirm or reject it " +
                    "in the console's Duplicates tab.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.padding(12.dp))
            }
        }

        if (item.linked.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text("Also recorded as", style = MaterialTheme.typography.titleSmall, modifier = Modifier.fillMaxWidth())
            Text("Same payment, counted once.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item.linked.forEach { MessageBlock(null, it.sourceLabel, it.body) }
            }
        }
    }
}

@Composable
private fun SectionCardFlat(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) { content() }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun MessageBlock(title: String?, source: String, body: String) {
    Column(Modifier.fillMaxWidth()) {
        if (title != null) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
        }
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                Text(source, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

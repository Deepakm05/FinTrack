package com.fintrack.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fintrack.core.Direction
import com.fintrack.core.SourceType
import com.fintrack.ui.model.CategorySlice
import com.fintrack.ui.model.TxnItem
import com.fintrack.ui.model.UNCATEGORIZED
import com.fintrack.ui.theme.AmountStyle
import com.fintrack.ui.theme.LocalMoneyColors
import java.text.NumberFormat
import java.util.Locale

private val inr = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val inrWhole = (NumberFormat.getCurrencyInstance(Locale("en", "IN")).clone() as NumberFormat).apply {
    maximumFractionDigits = 0
}

/** ₹1,23,456.50 (or ₹1,23,457 with [whole]). */
fun rupees(paise: Long, whole: Boolean = false): String = (if (whole) inrWhole else inr).format(paise / 100.0)

fun signedRupees(paise: Long, direction: Direction): String =
    (if (direction == Direction.DEBIT) "−" else "+") + rupees(paise)

@Composable
fun categoryColor(name: String?): Color {
    val palette = LocalMoneyColors.current.categories
    if (name == null || name == UNCATEGORIZED) return palette.last()
    return palette[(name.hashCode() and Int.MAX_VALUE) % (palette.size - 1)]
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun Pill(text: String, container: Color, content: Color, modifier: Modifier = Modifier) {
    Surface(color = container, contentColor = content, shape = RoundedCornerShape(50), modifier = modifier) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

/** Round badge with the payee's initial, tinted by category. */
@Composable
fun Avatar(title: String, category: String?, sourceType: SourceType, size: Int = 42) {
    val tint = categoryColor(category)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(tint.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "₹",
            color = tint,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
fun TxnListRow(item: TxnItem, onClick: () -> Unit) {
    val money = LocalMoneyColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(item.title, item.category, item.sourceType)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val linked = if (item.linked.isNotEmpty()) " · +${item.linked.size} linked" else ""
                Text("${item.sourceLabel} · ${item.time}$linked", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryTag(item.category)
                if (item.dupState == "possible") {
                    Pill("Possible duplicate", MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            signedRupees(item.amountPaise, item.direction),
            style = MaterialTheme.typography.titleMedium.merge(AmountStyle),
            color = if (item.direction == Direction.CREDIT) money.credit else money.debit,
        )
    }
}

@Composable
fun CategoryTag(category: String?) {
    if (category == null) {
        Pill("Uncategorized", MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        val c = categoryColor(category)
        Pill(category, c.copy(alpha = 0.14f), c)
    }
}

@Composable
fun CategoryBars(slices: List<CategorySlice>) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        slices.forEach { s ->
            val color = categoryColor(s.name)
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(8.dp))
                    Text(s.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${(s.fraction * 100).toInt()}%", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(rupees(s.paise, whole = true), style = MaterialTheme.typography.bodyMedium.merge(AmountStyle),
                        fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(s.fraction.coerceIn(0.02f, 1f)).height(8.dp)
                        .clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
        }
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center) {
            Text("₹", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

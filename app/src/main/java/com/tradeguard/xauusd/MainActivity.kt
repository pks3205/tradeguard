package com.tradeguard.xauusd

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tradeguard.xauusd.model.ChecklistCatalog
import com.tradeguard.xauusd.model.Rule
import com.tradeguard.xauusd.store.ChecklistStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                ChecklistScreen()
            }
        }
    }
}

private val Gold = Color(0xFFF5B301)
private val SurfaceDark = Color(0xFF1A1D24)
private val Muted = Color(0xFF9AA4B2)
private val Good = Color(0xFF4CAF50)
private val BuyGreen = Color(0xFF2E7D32)
private val SellRed = Color(0xFFC62828)

private enum class Direction(val label: String) { BUY("BUY"), SELL("SELL") }

@Composable
private fun ChecklistScreen() {
    val context = LocalContext.current
    val store = remember { ChecklistStore(context) }
    var setupId by remember { mutableStateOf(ChecklistCatalog.setups.first().id) }
    var direction by remember { mutableStateOf(Direction.BUY) }
    val checked = remember { mutableStateMapOf<String, Boolean>() }

    val setup = ChecklistCatalog.setups.first { it.id == setupId }
    val rules = if (direction == Direction.BUY) setup.buy else setup.sell
    val done = rules.count { checked[it.id] == true }

    fun toggle(rule: Rule) {
        val next = !(checked[rule.id] ?: store.isChecked(rule.id))
        checked[rule.id] = next
        store.setChecked(rule.id, next)
    }

    fun reset() {
        rules.forEach {
            checked.remove(it.id)
            store.clear(it.id)
        }
    }

    Scaffold(containerColor = Color(0xFF0E1014)) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "XAUUSD Setup Checklist",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Gold
            )
            Text(
                text = "Manual 15-minute trading checklist — verify each rule on your own chart. This app never places trades.",
                style = MaterialTheme.typography.bodyMedium,
                color = Muted
            )

            Text("Setup", style = MaterialTheme.typography.labelLarge, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChecklistCatalog.setups.forEach { s ->
                    SelectorButton(
                        label = s.shortTitle,
                        selected = s.id == setupId,
                        color = Gold,
                        onClick = { setupId = s.id },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SelectorButton("BUY", direction == Direction.BUY, BuyGreen, { direction = Direction.BUY }, Modifier.weight(1f))
                SelectorButton("SELL", direction == Direction.SELL, SellRed, { direction = Direction.SELL }, Modifier.weight(1f))
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(setup.title, fontWeight = FontWeight.SemiBold, color = Gold)
                    Text(
                        setup.subtitle,
                        color = Color(0xFFE0E4EA),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Progress: $done / ${rules.size} rules",
                        color = if (done == rules.size) Good else Muted,
                        fontWeight = FontWeight.Bold
                    )
                    LinearProgressIndicator(
                        progress = { if (rules.isEmpty()) 0f else done.toFloat() / rules.size },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = SurfaceDark)) {
                Column(Modifier.padding(vertical = 8.dp)) {
                    rules.forEach { rule ->
                        RuleRow(rule, checked[rule.id] ?: store.isChecked(rule.id), direction) { toggle(rule) }
                    }
                }
            }

            if (done == rules.size && rules.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF14331A))) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("All rules checked", fontWeight = FontWeight.Bold, color = Good)
                        Text(
                            "Now manually verify every level on your broker chart before acting. No trade is placed by this app.",
                            color = Color(0xFFE0E4EA),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            TextButton(onClick = { reset() }) { Text("Reset this checklist") }

            WarningCard()
        }
    }
}

@Composable
private fun RuleRow(rule: Rule, isChecked: Boolean, direction: Direction, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = if (direction == Direction.BUY) BuyGreen else SellRed
            )
        )
        Text(
            rule.label,
            color = Color(0xFFE0E4EA),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 4.dp)
        )
    }
}

@Composable
private fun SelectorButton(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            colors = ButtonDefaults.buttonColors(containerColor = color)
        ) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(label, color = Muted) }
    }
}

@Composable
private fun WarningCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1B10))) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Read before trading", fontWeight = FontWeight.SemiBold, color = Color(0xFFFFB74D))
            Text(
                "This is a manual checklist, not financial advice and not a trading signal. " +
                    "Always confirm every rule on your own broker chart. Different feeds can show " +
                    "different prices/timings, so verify against your broker before acting.",
                color = Color(0xFFE0E4EA),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

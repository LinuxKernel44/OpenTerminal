package com.openterminal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.openterminal.data.AppTheme
import com.openterminal.data.CommandHistory
import com.openterminal.data.Settings
import com.openterminal.data.TerminalPalette
import com.openterminal.term.SessionManager

@Composable
private fun Sheet(backdrop: Backdrop, colors: GlassColors, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        GlassPanel(
            backdrop, colors,
            Modifier.align(Alignment.Center).systemBarsPadding().padding(16.dp).fillMaxWidth().heightIn(max = 640.dp)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, color = colors.onGlass, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    GlassButton(onDismiss, backdrop, colors, minWidth = 40.dp) { Text("Done", color = colors.onGlass) }
                }
                content()
            }
        }
    }
}

@Composable
private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, colors: GlassColors, shown: String, onChange: (Float) -> Unit) {
    Column {
        Row {
            Text(label, color = colors.onGlass, modifier = Modifier.weight(1f))
            Text(shown, color = colors.onGlassDim)
        }
        Slider(
            value, { onChange(it); Settings.save() }, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent, inactiveTrackColor = colors.onGlassDim.copy(alpha = 0.25f)),
        )
    }
}

@Composable
private fun SettingSwitch(label: String, checked: Boolean, colors: GlassColors, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = colors.onGlass, modifier = Modifier.weight(1f))
        Switch(checked, { onChange(it); Settings.save() }, colors = SwitchDefaults.colors(checkedTrackColor = colors.accent))
    }
}

@Composable
fun SettingsSheet(backdrop: Backdrop, colors: GlassColors, onDismiss: () -> Unit) {
    Sheet(backdrop, colors, "Settings", onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Theme", color = colors.onGlassDim, fontSize = 13.sp)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppTheme.entries.forEach { t ->
                    GlassButton({ Settings.theme = t; Settings.save() }, backdrop, colors, selected = Settings.theme == t) {
                        Text(t.label, color = colors.onGlass, fontSize = 13.sp)
                    }
                }
            }
            Text("Terminal colors", color = colors.onGlassDim, fontSize = 13.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TerminalPalette.entries.forEach { p ->
                    GlassButton({
                        Settings.palette = p; Settings.save()
                        SessionManager.sessions.forEach { it.applyPalette() }
                    }, backdrop, colors, selected = Settings.palette == p) {
                        Text(p.label, color = colors.onGlass, fontSize = 12.sp)
                    }
                }
            }
            SettingSlider("Glass blur", Settings.blurRadius, 0f..24f, colors, "${Settings.blurRadius.toInt()} dp") { Settings.blurRadius = it }
            SettingSlider("Lens refraction", Settings.lensStrength, 0f..2f, colors, "%.1fx".format(Settings.lensStrength)) { Settings.lensStrength = it }
            SettingSlider("Glass opacity", Settings.glassOpacity, 0f..0.9f, colors, "${(Settings.glassOpacity * 100).toInt()}%") { Settings.glassOpacity = it }
            SettingSlider("Font size", Settings.fontSize.toFloat(), 8f..28f, colors, "${Settings.fontSize} sp") { Settings.fontSize = it.toInt() }
            SettingSwitch("Special keys bar", Settings.showKeyBar, colors) { Settings.showKeyBar = it }
            SettingSwitch("Command suggestions", Settings.showSuggestions, colors) { Settings.showSuggestions = it }
            SettingSwitch("Haptic feedback", Settings.haptics, colors) { Settings.haptics = it }
            GlassButton({ CommandHistory.clear() }, backdrop, colors, Modifier.fillMaxWidth()) { Text("Clear command history", color = colors.onGlass) }
            Text("OpenTerminal 0.1.0 · GPL-3.0 · liquid glass by Kyant0/AndroidLiquidGlass", color = colors.onGlassDim, fontSize = 12.sp)
        }
    }
}

@Composable
fun HistorySheet(backdrop: Backdrop, colors: GlassColors, onRun: (String) -> Unit, onDismiss: () -> Unit) {
    val items = remember { CommandHistory.all }
    Sheet(backdrop, colors, "History", onDismiss) {
        if (items.isEmpty()) {
            Text("No commands yet. Commands you run will show up here.", color = colors.onGlassDim)
        } else {
            LazyColumn(Modifier.heightIn(max = 460.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items) { cmd ->
                    Text(
                        cmd, color = colors.onGlass, fontFamily = FontFamily.Monospace, fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().clickable { onRun(cmd) }.padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

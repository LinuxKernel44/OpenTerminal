package com.openterminal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.openterminal.shizuku.ShizukuManager
import com.openterminal.shizuku.ShizukuState

private data class Step(val title: String, val body: String, val action: String?, val onAction: (() -> Unit)?)

@Composable
fun OnboardingScreen(state: ShizukuState, backdrop: Backdrop, colors: GlassColors, onOpenUrl: (String) -> Unit) {
    val step = when (state) {
        ShizukuState.NotInstalled -> Step(
            "Install Shizuku",
            "OpenTerminal runs its shell through Shizuku, which gives it adb-level (shell) privileges without a computer.\n\nInstall the Shizuku app first.",
            "Get Shizuku",
        ) { onOpenUrl("https://shizuku.rikka.app/download/") }
        ShizukuState.NotRunning -> Step(
            "Start Shizuku",
            "Shizuku is installed but its service is not running.\n\n1. Open Shizuku\n2. Enable Developer options and Wireless debugging in Android settings\n3. Tap \"Start via Wireless debugging\" in Shizuku (or run its start command from a computer with adb)\n\nCome back here once it says \"Shizuku is running\".",
            "Open Shizuku",
        ) { ShizukuManager.openShizukuApp() }
        ShizukuState.NoPermission -> Step(
            "Grant permission",
            "Shizuku is running. Allow OpenTerminal to use it to start a shell with shell privileges.",
            "Grant permission",
        ) { ShizukuManager.requestPermission() }
        ShizukuState.Connecting -> Step("Connecting…", "Starting the shell service.", null, null)
        ShizukuState.Ready -> Step("Ready", "", null, null)
    }
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("OpenTerminal", color = colors.onGlass, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text("An adb-grade terminal powered by Shizuku", color = colors.onGlassDim, fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp, bottom = 28.dp))
        GlassPanel(backdrop, colors, Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(step.title, color = colors.onGlass, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text(step.body, color = colors.onGlassDim, fontSize = 15.sp, lineHeight = 22.sp)
                if (step.action != null && step.onAction != null) {
                    GlassButton(step.onAction, backdrop, colors, Modifier.fillMaxWidth(), height = 48.dp, selected = true) {
                        Text(step.action, color = colors.onGlass, fontWeight = FontWeight.Medium)
                    }
                }
                GlassButton({ ShizukuManager.refresh() }, backdrop, colors, Modifier.fillMaxWidth(), height = 44.dp) {
                    Text("Check again", color = colors.onGlass)
                }
            }
        }
    }
}

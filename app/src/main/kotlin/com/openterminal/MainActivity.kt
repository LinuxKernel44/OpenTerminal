package com.openterminal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import com.openterminal.shizuku.ShizukuManager
import com.openterminal.shizuku.ShizukuState
import com.openterminal.ui.GlassScaffold
import com.openterminal.ui.OnboardingScreen
import com.openterminal.ui.TerminalScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { App() }
    }

    override fun onResume() {
        super.onResume()
        ShizukuManager.refresh()
    }

    @Composable
    private fun App() {
        GlassScaffold { backdrop, colors ->
            if (ShizukuManager.state == ShizukuState.Ready) {
                TerminalScreen(backdrop, colors)
            } else {
                OnboardingScreen(ShizukuManager.state, backdrop, colors) { url ->
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            }
        }
    }
}

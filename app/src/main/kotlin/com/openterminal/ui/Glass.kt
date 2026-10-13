package com.openterminal.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.shapes.Capsule
import com.openterminal.data.AppTheme
import com.openterminal.data.Settings

/** Colors derived from the selected app theme. */
data class GlassColors(
    val dark: Boolean,
    val base: Color,
    val blobs: List<Color>,
    val onGlass: Color,
    val onGlassDim: Color,
    val surface: Color,
    val accent: Color,
)

@Composable
fun glassColors(): GlassColors {
    val systemDark = isSystemInDarkTheme()
    return when (Settings.theme) {
        AppTheme.Aurora -> auroraColors
        AppTheme.Midnight -> midnightColors
        AppTheme.Amoled -> amoledColors
        AppTheme.System -> if (systemDark) auroraColors else lightColors
    }
}

private val auroraColors = GlassColors(
    dark = true, base = Color(0xFF0B1020),
    blobs = listOf(Color(0xFF7C3AED), Color(0xFF06B6D4), Color(0xFFEC4899), Color(0xFF22C55E)),
    onGlass = Color.White, onGlassDim = Color(0xB3FFFFFF), surface = Color(0xFF111827), accent = Color(0xFF7DD3FC),
)
private val midnightColors = GlassColors(
    dark = true, base = Color(0xFF05070F),
    blobs = listOf(Color(0xFF1E3A8A), Color(0xFF312E81), Color(0xFF0F766E), Color(0xFF1D4ED8)),
    onGlass = Color.White, onGlassDim = Color(0xB3FFFFFF), surface = Color(0xFF0B1224), accent = Color(0xFF93C5FD),
)
private val amoledColors = GlassColors(
    dark = true, base = Color.Black,
    blobs = listOf(Color(0xFF1F2937), Color(0xFF312E81), Color(0xFF0F172A), Color(0xFF3B0764)),
    onGlass = Color.White, onGlassDim = Color(0xB3FFFFFF), surface = Color.Black, accent = Color(0xFFA5B4FC),
)
private val lightColors = GlassColors(
    dark = false, base = Color(0xFFEFF3FB),
    blobs = listOf(Color(0xFFA5B4FC), Color(0xFF67E8F9), Color(0xFFF9A8D4), Color(0xFF86EFAC)),
    onGlass = Color(0xFF0F172A), onGlassDim = Color(0xB30F172A), surface = Color.White, accent = Color(0xFF2563EB),
)

/** Animated colorful background; it is the layer the glass panels refract. */
@Composable
fun GlassScaffold(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(Backdrop, GlassColors) -> Unit,
) {
    val colors = glassColors()
    val backdrop = rememberLayerBackdrop()
    val t = rememberInfiniteTransition(label = "bg")
    val phase by t.animateFloat(0f, 1f, infiniteRepeatable(tween(40000, easing = LinearEasing), RepeatMode.Restart), label = "phase")
    Box(modifier.fillMaxSize().background(colors.base)) {
        Box(
            Modifier.fillMaxSize().layerBackdrop(backdrop).drawBehind {
                drawRect(colors.base)
                val w = size.width
                val h = size.height
                val r = maxOf(w, h) * 0.65f
                colors.blobs.forEachIndexed { i, c ->
                    val a = (phase + i / colors.blobs.size.toFloat()) * 2f * Math.PI.toFloat()
                    val cx = w * (0.5f + 0.45f * kotlin.math.cos(a + i))
                    val cy = h * (0.5f + 0.45f * kotlin.math.sin(a * 0.8f + i * 1.7f))
                    drawCircle(
                        Brush.radialGradient(listOf(c.copy(alpha = if (colors.dark) 0.55f else 0.7f), Color.Transparent), Offset(cx, cy), r),
                        radius = r, center = Offset(cx, cy),
                    )
                }
            },
        )
        content(backdrop, colors)
    }
}

/** A liquid glass surface (blur + vibrancy + refraction lens) over [backdrop]. */
@Composable
fun GlassPanel(
    backdrop: Backdrop,
    colors: GlassColors,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    cornerRadius: Dp = 28.dp,
    tint: Color = Color.Unspecified,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val blur = Settings.blurRadius
    val lensK = Settings.lensStrength
    val opacity = Settings.glassOpacity
    Box(
        modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(blur.dp.toPx())
                if (lensK > 0f) lens(cornerRadius.toPx() * 0.6f * lensK, cornerRadius.toPx() * lensK)
            },
            onDrawSurface = {
                drawRect(colors.surface.copy(alpha = opacity))
                if (tint != Color.Unspecified) drawRect(tint)
            },
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    backdrop: Backdrop,
    colors: GlassColors,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    minWidth: Dp = 40.dp,
    height: Dp = 40.dp,
    content: @Composable RowScope.() -> Unit,
) {
    GlassPanel(
        backdrop, colors,
        modifier.heightIn(min = height).height(height).clickable(role = Role.Button, onClick = onClick),
        shape = Capsule(),
        cornerRadius = height / 2,
        tint = if (selected) colors.accent.copy(alpha = 0.45f) else Color.Unspecified,
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

package com.grocer.billing.ui.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Ambient Liquid Gradient Background that gives translucent glass surfaces
 * rich depth and realistic refraction highlights.
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        GlassBackgroundTop,
                        Color(0xFFE8F2EC),
                        GlassBackgroundBottom
                    )
                )
            )
    ) {
        // Fluid luminous ambient orb (top-right Kirana green glow)
        Box(
            modifier = Modifier
                .size(320.dp)
                .offset(x = 180.dp, y = (-60).dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GreenPrimary.copy(alpha = 0.16f),
                            Color(0xFF4CAF50).copy(alpha = 0.08f),
                            Color.Transparent
                        )
                    )
                )
        )
        // Fluid luminous ambient orb (bottom-left mint glow)
        Box(
            modifier = Modifier
                .size(360.dp)
                .align(Alignment.BottomStart)
                .offset(x = (-100).dp, y = 100.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF00B0FF).copy(alpha = 0.07f),
                            GreenPrimary.copy(alpha = 0.10f),
                            Color.Transparent
                        )
                    )
                )
        )

        content()
    }
}

/**
 * Translucent Liquid Glass Card with specular light-catching borders
 * and soft ambient drop shadow.
 */
@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(22.dp),
    elevation: Dp = 6.dp,
    onClick: (() -> Unit)? = null,
    tint: Color = Color.White.copy(alpha = 0.78f),
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Box(
        modifier = modifier
            .shadow(
                elevation = elevation,
                shape = shape,
                ambientColor = GlassShadow,
                spotColor = GlassShadow
            )
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        tint,
                        tint.copy(alpha = 0.65f)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            GlassBorderHighlight,
                            Color.White.copy(alpha = 0.35f),
                            GlassBorderSubtle
                        )
                    )
                ),
                shape = shape
            )
            .then(clickModifier)
    ) {
        // Specular gloss light reflection along top edge
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.8f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier.padding(18.dp),
            content = content
        )
    }
}

/**
 * Luminous Liquid Pill Button with glossy top highlight sheen
 * and tactile Kirana green gradient.
 */
@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    enabled: Boolean = true,
    gradientColors: List<Color> = listOf(GreenPrimary, GreenDark),
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(
                elevation = if (enabled) 8.dp else 0.dp,
                shape = shape,
                ambientColor = GreenPrimary.copy(alpha = 0.35f),
                spotColor = GreenDark.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                if (enabled) {
                    Brush.horizontalGradient(gradientColors)
                } else {
                    Brush.horizontalGradient(listOf(Color(0xFFB0BEC5), Color(0xFF90A4AE)))
                }
            )
            .border(
                BorderStroke(
                    width = 1.2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.55f),
                            Color.White.copy(alpha = 0.15f)
                        )
                    )
                ),
                shape = shape
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        // Top specular gloss sheen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.Transparent
                        )
                    )
                )
        )

        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Translucent Glass Secondary / Outlined Action Button
 */
@Composable
fun LiquidGlassSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .shadow(elevation = 2.dp, shape = shape, ambientColor = GlassShadow)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.82f),
                        Color.White.copy(alpha = 0.55f)
                    )
                )
            )
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.9f),
                            GreenPrimary.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape = shape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
    }
}

/**
 * Translucent Liquid Glass Badge / Pill Chip
 */
@Composable
fun LiquidGlassBadge(
    modifier: Modifier = Modifier,
    tint: Color = GreenPrimary,
    shape: Shape = RoundedCornerShape(12.dp),
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .border(
                BorderStroke(1.dp, tint.copy(alpha = 0.35f)),
                shape = shape
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        content = content
    )
}

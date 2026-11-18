package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.rememberTactileHaptics
import com.example.ui.theme.AppleGothicBodyFontFamily
import com.example.ui.theme.LocalNothingColors
import com.example.ui.theme.NothingBlack
import com.example.ui.theme.NothingPureWhite
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun NothingCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color? = null,
    borderColor: Color? = null,
    cornerRadius: Dp = 28.dp,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val colors = LocalNothingColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(cornerRadius),
        color = backgroundColor ?: colors.card,
        border = BorderStroke(1.dp, borderColor ?: colors.cardBorder.copy(alpha = 0.65f))
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun NothingSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    dotColor: Color? = null,
    trailingText: String? = null
) {
    val colors = LocalNothingColors.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (dotColor != null) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
            }
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                color = colors.inkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingText != null) {
            Text(
                text = trailingText,
                style = MaterialTheme.typography.labelMedium,
                color = colors.inkSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
fun <T> NothingSegmentedPill(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    testTagPrefix: String = "segment"
) {
    val colors = LocalNothingColors.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.pillTrack)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEach { (value, label) ->
            val isSelected = value == selected
            val bg by animateColorAsState(
                targetValue = if (isSelected) colors.pillActiveContainer else Color.Transparent,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                label = "segment_bg"
            )
            val fg by animateColorAsState(
                targetValue = if (isSelected) colors.pillActiveContent else colors.inkSecondary,
                label = "segment_fg"
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(50))
                    .background(bg)
                    .clickable { onSelect(value) }
                    .testTag("${testTagPrefix}_${label.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = fg,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Pure Essential Space asterisk / starburst glyph with NO border, NO circle, and NO hexagon container.
 */
@Composable
fun EssentialSpaceLogo(
    modifier: Modifier = Modifier,
    color: Color? = null,
    size: Dp = 26.dp
) {
    val colors = LocalNothingColors.current
    val tint = color ?: colors.inkPrimary
    Canvas(modifier = modifier.size(size)) {
        val cx = this.size.width / 2f
        val cy = this.size.height / 2f
        val arm = this.size.minDimension * 0.44f
        val strokeW = this.size.minDimension * 0.145f

        // 6-ray organic/industrial Essential Space asterisk (no outer frame)
        val angles = doubleArrayOf(0.0, 58.0, 118.0)
        for (deg in angles) {
            val rad = Math.toRadians(deg)
            val dx = (arm * cos(rad)).toFloat()
            val dy = (arm * sin(rad)).toFloat()
            drawLine(
                color = tint,
                start = Offset(cx - dx, cy - dy),
                end = Offset(cx + dx, cy + dy),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Bold, chunky Nothing-style horizontal capsule slider (monochrome).
 */
@Composable
fun NothingHorizontalDotSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    bipolarFromCenter: Boolean = false,
    testTag: String = "horizontal_slider"
) {
    val colors = LocalNothingColors.current
    val minV = valueRange.start
    val maxV = valueRange.endInclusive
    val norm = ((value - minV) / (maxV - minV)).coerceIn(0f, 1f)
    var widthPx by remember { mutableFloatStateOf(1f) }
    val haptics = rememberTactileHaptics()
    var lastDetent by remember { mutableIntStateOf((norm * 20f).roundToInt()) }

    fun emitHapticForNorm(t: Float, isSnapCenter: Boolean = false, forceTick: Boolean = false) {
        val detent = (t * 20f).roundToInt()
        if (forceTick || detent != lastDetent || isSnapCenter) {
            lastDetent = detent
            haptics.tick(isCenterSnap = isSnapCenter)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(RoundedCornerShape(50))
            .background(colors.pillTrack)
            .border(1.dp, colors.cardBorder, RoundedCornerShape(50))
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(minV, maxV) {
                detectTapGestures { offset ->
                    val pad = 26.dp.toPx()
                    val usable = (widthPx - pad * 2f).coerceAtLeast(1f)
                    val t = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                    val next = minV + t * (maxV - minV)
                    emitHapticForNorm(t, isSnapCenter = bipolarFromCenter && abs(next) < 0.05f, forceTick = true)
                    onValueChange(next)
                }
            }
            .pointerInput(minV, maxV) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val pad = 26.dp.toPx()
                        val usable = (widthPx - pad * 2f).coerceAtLeast(1f)
                        val t = ((offset.x - pad) / usable).coerceIn(0f, 1f)
                        lastDetent = (t * 20f).roundToInt()
                        haptics.tick(isCenterSnap = false)
                    }
                ) { change, _ ->
                    change.consume()
                    val pad = 26.dp.toPx()
                    val usable = (widthPx - pad * 2f).coerceAtLeast(1f)
                    val t = ((change.position.x - pad) / usable).coerceIn(0f, 1f)
                    val next = minV + t * (maxV - minV)
                    val snappedCenter = bipolarFromCenter && abs(next) < (maxV - minV) * 0.025f && abs(value) >= (maxV - minV) * 0.025f
                    emitHapticForNorm(t, isSnapCenter = snappedCenter)
                    onValueChange(next)
                }
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 24.dp)
        ) {
            val cy = size.height / 2f
            val barH = 22.dp.toPx()
            val thumbX = size.width * norm

            // Background dot markers
            val dotCount = 13
            for (i in 0 until dotCount) {
                val dx = size.width * (i / (dotCount - 1).toFloat())
                val isCenter = bipolarFromCenter && i == dotCount / 2
                drawCircle(
                    color = if (isCenter) colors.inkSecondary else colors.inkMuted.copy(alpha = 0.35f),
                    radius = if (isCenter) 3.dp.toPx() else 1.8.dp.toPx(),
                    center = Offset(dx, cy)
                )
            }

            // Fat monochrome filled capsule bar
            if (bipolarFromCenter) {
                val centerX = size.width * 0.5f
                val left = minOf(centerX, thumbX)
                val right = maxOf(centerX, thumbX)
                drawRoundRect(
                    color = colors.inkPrimary.copy(alpha = 0.22f),
                    topLeft = Offset(left, cy - barH / 2f),
                    size = Size((right - left).coerceAtLeast(4.dp.toPx()), barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                )
            } else {
                drawRoundRect(
                    color = colors.inkPrimary.copy(alpha = 0.22f),
                    topLeft = Offset(0f, cy - barH / 2f),
                    size = Size(thumbX.coerceAtLeast(barH), barH),
                    cornerRadius = CornerRadius(barH / 2f, barH / 2f)
                )
            }

            // Chunky hardware thumb pill
            drawCircle(
                color = colors.inkPrimary,
                radius = 13.dp.toPx(),
                center = Offset(thumbX, cy)
            )
            drawCircle(
                color = colors.card,
                radius = 4.dp.toPx(),
                center = Offset(thumbX, cy)
            )
        }
    }
}

/**
 * Bold, chunky Nothing-style vertical hardware fader (monochrome).
 */
@Composable
fun NothingVerticalPillFader(
    label: String,
    valueDb: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = -12f..12f,
    height: Dp = 196.dp,
    compact: Boolean = false,
    testTag: String = "vertical_fader"
) {
    val colors = LocalNothingColors.current
    val minV = valueRange.start
    val maxV = valueRange.endInclusive
    val norm = ((valueDb - minV) / (maxV - minV)).coerceIn(0f, 1f)
    var heightPx by remember { mutableFloatStateOf(1f) }
    val faderWidth = if (compact) 46.dp else 86.dp
    val haptics = rememberTactileHaptics()
    var lastDbStep by remember { mutableIntStateOf(valueDb.roundToInt()) }

    fun emitFaderHaptic(nextDb: Float, forceTick: Boolean = false) {
        val step = nextDb.roundToInt()
        val snappedZero = nextDb == 0f && lastDbStep != 0
        if (forceTick || step != lastDbStep || snappedZero) {
            lastDbStep = step
            haptics.tick(isCenterSnap = snappedZero)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val rounded = (valueDb * 10f).roundToInt() / 10f
        Text(
            text = if (rounded > 0.05f) "+$rounded" else "$rounded",
            fontFamily = AppleGothicBodyFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 11.sp else 14.sp,
            color = if (abs(valueDb) > 0.2f) colors.inkPrimary else colors.inkSecondary,
            maxLines = 1
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(faderWidth)
                .height(height)
                .clip(RoundedCornerShape(if (compact) 24.dp else 32.dp))
                .background(colors.pillTrack)
                .border(1.dp, colors.cardBorder, RoundedCornerShape(if (compact) 24.dp else 32.dp))
                .onSizeChanged { heightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(minV, maxV) {
                    detectTapGestures { offset ->
                        val pad = 20.dp.toPx()
                        val usable = (heightPx - pad * 2f).coerceAtLeast(1f)
                        val t = 1f - ((offset.y - pad) / usable).coerceIn(0f, 1f)
                        val raw = minV + t * (maxV - minV)
                        val snapped = if (abs(raw) < 0.35f) 0f else raw
                        emitFaderHaptic(snapped, forceTick = true)
                        onValueChange(snapped)
                    }
                }
                .pointerInput(minV, maxV) {
                    detectDragGestures(
                        onDragStart = {
                            lastDbStep = valueDb.roundToInt()
                            haptics.tick(isCenterSnap = false)
                        }
                    ) { change, _ ->
                        change.consume()
                        val pad = 20.dp.toPx()
                        val usable = (heightPx - pad * 2f).coerceAtLeast(1f)
                        val t = 1f - ((change.position.y - pad) / usable).coerceIn(0f, 1f)
                        val raw = minV + t * (maxV - minV)
                        val snapped = if (abs(raw) < 0.35f) 0f else raw
                        emitFaderHaptic(snapped)
                        onValueChange(snapped)
                    }
                }
                .testTag(testTag)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = 16.dp, horizontal = if (compact) 6.dp else 10.dp)
            ) {
                val cx = size.width / 2f
                val zeroY = size.height * 0.5f
                val thumbY = size.height * (1f - norm)

                // Crisp dot scale inside track
                val dots = 9
                for (i in 0 until dots) {
                    val dy = size.height * (i / (dots - 1).toFloat())
                    val isZero = i == dots / 2
                    drawCircle(
                        color = if (isZero) colors.inkSecondary else colors.inkMuted.copy(alpha = 0.35f),
                        radius = if (isZero) 2.5.dp.toPx() else 1.5.dp.toPx(),
                        center = Offset(cx, dy)
                    )
                }

                // Fat solid column fill from 0 dB center line
                val topY = minOf(zeroY, thumbY)
                val botY = maxOf(zeroY, thumbY)
                val barW = if (compact) 24.dp.toPx() else 52.dp.toPx()
                drawRoundRect(
                    color = colors.inkPrimary.copy(alpha = 0.18f),
                    topLeft = Offset(cx - barW / 2f, topY),
                    size = Size(barW, (botY - topY).coerceAtLeast(4.dp.toPx())),
                    cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx())
                )

                // Fat hardware slider cap bar
                val capW = if (compact) 30.dp.toPx() else 60.dp.toPx()
                val capH = if (compact) 14.dp.toPx() else 20.dp.toPx()
                drawRoundRect(
                    color = colors.inkPrimary,
                    topLeft = Offset(cx - capW / 2f, thumbY - capH / 2f),
                    size = Size(capW, capH),
                    cornerRadius = CornerRadius(capH / 2f, capH / 2f)
                )

                // Center tactile notch inside cap
                drawRoundRect(
                    color = colors.card,
                    topLeft = Offset(cx - capW * 0.25f, thumbY - 1.5.dp.toPx()),
                    size = Size(capW * 0.5f, 3.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.inkPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
fun NothingSwitchPill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    activeDotColor: Color? = null,
    testTag: String = "switch_pill"
) {
    val colors = LocalNothingColors.current
    val bg by animateColorAsState(
        targetValue = if (checked) colors.inkPrimary else colors.pillTrack,
        label = "switch_bg"
    )
    val dotOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 0.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "switch_dot"
    )

    Box(
        modifier = modifier
            .size(width = 62.dp, height = 36.dp)
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (checked) colors.inkPrimary else colors.cardBorder, RoundedCornerShape(50))
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp)
            .testTag(testTag),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .padding(start = dotOffset)
                .size(26.dp)
                .clip(CircleShape)
                .background(if (checked) (activeDotColor ?: colors.card) else colors.inkMuted)
        )
    }
}

/**
 * Hardware-style Power LED Switch for Top Bar:
 * - Light Mode: White chassis; Dark Mode: Black chassis.
 * - ON: Luminous glowing green LED pill with radial halo bloom.
 * - OFF: Clean unlit pill with NO glow and NO red shimmer.
 */
@Composable
fun NothingHardwarePowerSwitch(
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    testTag: String = "master_power_button"
) {
    val colors = LocalNothingColors.current
    val haptics = rememberTactileHaptics()
    val pulseTransition = rememberInfiniteTransition(label = "power_led_pulse")
    val greenBreath by pulseTransition.animateFloat(
        initialValue = 0.72f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "green_breath"
    )

    val chassisColor = if (colors.isDark) NothingBlack else NothingPureWhite
    val containerBg by animateColorAsState(
        targetValue = chassisColor,
        animationSpec = tween(220),
        label = "power_bg"
    )
    val borderTint by animateColorAsState(
        targetValue = if (enabled) {
            colors.green.copy(alpha = 0.92f)
        } else {
            colors.cardBorder
        },
        animationSpec = tween(220),
        label = "power_border"
    )
    val labelTint by animateColorAsState(
        targetValue = if (enabled) {
            if (colors.isDark) colors.greenGlow else Color(0xFF0A8F3D)
        } else {
            colors.inkSecondary
        },
        animationSpec = tween(220),
        label = "power_label"
    )

    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(containerBg)
            .border(
                width = if (enabled) 1.6.dp else 1.dp,
                color = borderTint,
                shape = RoundedCornerShape(50)
            )
            .clickable {
                haptics.tick(isCenterSnap = true)
                onToggle()
            }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        // Green LED glow bloom ONLY when ON; completely unlit (no glow) when OFF
        if (enabled) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val glowCenter = Offset(22.dp.toPx(), size.height / 2f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.greenGlow.copy(alpha = 0.44f * greenBreath),
                            colors.green.copy(alpha = 0.18f * greenBreath),
                            Color.Transparent
                        ),
                        center = glowCenter,
                        radius = size.height * 1.05f
                    ),
                    radius = size.height * 1.05f,
                    center = glowCenter
                )
            }
        }

        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // LED indicator lens
            Canvas(modifier = Modifier.size(16.dp)) {
                val center = Offset(size.width / 2f, size.height / 2f)
                if (enabled) {
                    drawCircle(
                        color = colors.green.copy(alpha = 0.36f * greenBreath),
                        radius = size.minDimension * 0.56f,
                        center = center
                    )
                    drawCircle(
                        color = colors.greenGlow,
                        radius = size.minDimension * 0.36f,
                        center = center
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.85f),
                        radius = size.minDimension * 0.14f,
                        center = Offset(center.x - 1.dp.toPx(), center.y - 1.dp.toPx())
                    )
                } else {
                    // Clean extinguished LED housing (no glow, no red shimmer)
                    drawCircle(
                        color = colors.inkMuted.copy(alpha = 0.25f),
                        radius = size.minDimension * 0.34f,
                        center = center
                    )
                    drawCircle(
                        color = colors.cardBorder,
                        radius = size.minDimension * 0.34f,
                        center = center,
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                }
            }

            Text(
                text = if (enabled) "ON" else "OFF",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = labelTint
            )
        }
    }
}

/**
 * Optical Nothing dot-matrix framing brackets for QR scanner, preset sheets, and Essential capture cards.
 */
@Composable
fun NothingMatrixReticleOverlay(
    modifier: Modifier = Modifier,
    bracketColor: Color? = null,
    dotAccentColor: Color? = null,
    cornerLength: Dp = 18.dp,
    strokeWidth: Dp = 2.dp
) {
    val colors = LocalNothingColors.current
    val stroke = bracketColor ?: colors.inkPrimary.copy(alpha = 0.65f)
    val dot = dotAccentColor ?: colors.inkMuted.copy(alpha = 0.6f)

    Canvas(modifier = modifier) {
        val len = cornerLength.toPx()
        val sw = strokeWidth.toPx()
        val inset = 8.dp.toPx()
        val w = size.width
        val h = size.height

        // Top-Left
        drawLine(stroke, Offset(inset, inset + len), Offset(inset, inset), sw, StrokeCap.Round)
        drawLine(stroke, Offset(inset, inset), Offset(inset + len, inset), sw, StrokeCap.Round)
        // Top-Right
        drawLine(stroke, Offset(w - inset - len, inset), Offset(w - inset, inset), sw, StrokeCap.Round)
        drawLine(stroke, Offset(w - inset, inset), Offset(w - inset, inset + len), sw, StrokeCap.Round)
        // Bottom-Left
        drawLine(stroke, Offset(inset, h - inset - len), Offset(inset, h - inset), sw, StrokeCap.Round)
        drawLine(stroke, Offset(inset, h - inset), Offset(inset + len, h - inset), sw, StrokeCap.Round)
        // Bottom-Right
        drawLine(stroke, Offset(w - inset - len, h - inset), Offset(w - inset, h - inset), sw, StrokeCap.Round)
        drawLine(stroke, Offset(w - inset, h - inset), Offset(w - inset, h - inset - len), sw, StrokeCap.Round)

        // Subtle dot-matrix index dots along top & bottom edges
        val r = 1.4.dp.toPx()
        val center = w / 2f
        for (k in -2..2) {
            drawCircle(dot, r, Offset(center + k * 7.dp.toPx(), inset + 1.dp.toPx()))
            drawCircle(dot, r, Offset(center + k * 7.dp.toPx(), h - inset - 1.dp.toPx()))
        }
    }
}

/**
 * Essential Space inspired animated dot-matrix voice waveform driven by live microphone RMS amplitude.
 */
@Composable
fun NothingVoiceWaveformView(
    amplitude: Float,
    modifier: Modifier = Modifier,
    barColor: Color? = null,
    barCount: Int = 20
) {
    val colors = LocalNothingColors.current
    val tint = barColor ?: colors.yellow
    val pulse by rememberInfiniteTransition(label = "voice_wave").animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "voice_phase"
    )

    Canvas(modifier = modifier) {
        val gap = size.width / barCount.toFloat()
        val barW = (gap * 0.52f).coerceIn(3f, 8f)
        val cy = size.height / 2f
        val maxHalf = size.height * 0.44f
        val amp = amplitude.coerceIn(0.08f, 1f)

        for (i in 0 until barCount) {
            val env = 1f - abs(i - (barCount - 1) / 2f) / (barCount / 1.8f)
            val ripple = 0.45f + 0.55f * abs(sin(pulse + i * 0.55f))
            val halfH = (3.dp.toPx() + maxHalf * amp * env.coerceIn(0.25f, 1f) * ripple)
                .coerceAtMost(maxHalf)
            val x = i * gap + gap / 2f
            drawLine(
                color = tint.copy(alpha = 0.45f + 0.55f * amp),
                start = Offset(x, cy - halfH),
                end = Offset(x, cy + halfH),
                strokeWidth = barW,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Geometric Nothing Ear / Wireless Audio silhouette with concentric Glyph halo rings for Bluetooth popup.
 */
@Composable
fun NothingBudsGlyphIllustration(
    accentColor: Color,
    inkColor: Color,
    modifier: Modifier = Modifier
) {
    val pulse by rememberInfiniteTransition(label = "bt_halo").animateFloat(
        initialValue = 0.85f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bt_ring"
    )

    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val baseR = size.minDimension * 0.42f

        // Concentric dot-matrix / glyph halo
        drawCircle(
            color = accentColor.copy(alpha = 0.14f),
            radius = baseR * pulse,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.35f),
            radius = baseR * 0.86f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.5.dp.toPx())
        )

        val dotCount = 16
        for (i in 0 until dotCount) {
            val a = (2.0 * PI * i) / dotCount
            val dx = cx + (baseR * 1.06f * cos(a)).toFloat()
            val dy = cy + (baseR * 1.06f * sin(a)).toFloat()
            drawCircle(
                color = accentColor.copy(alpha = if (i % 4 == 0) 0.7f else 0.25f),
                radius = if (i % 4 == 0) 2.2.dp.toPx() else 1.2.dp.toPx(),
                center = Offset(dx, dy)
            )
        }

        // Left & Right translucent industrial earbud silhouettes
        val stemW = size.minDimension * 0.09f
        val stemH = size.minDimension * 0.34f
        val headR = size.minDimension * 0.13f

        // Left bud
        val lX = cx - size.minDimension * 0.14f
        val budTop = cy - size.minDimension * 0.16f
        drawRoundRect(
            color = inkColor.copy(alpha = 0.88f),
            topLeft = Offset(lX - stemW / 2f, budTop + headR * 0.4f),
            size = Size(stemW, stemH),
            cornerRadius = CornerRadius(stemW / 2f, stemW / 2f)
        )
        drawCircle(
            color = inkColor,
            radius = headR,
            center = Offset(lX - headR * 0.35f, budTop + headR * 0.65f)
        )
        drawCircle(
            color = accentColor,
            radius = 2.4.dp.toPx(),
            center = Offset(lX, budTop + stemH * 0.72f)
        )

        // Right bud
        val rX = cx + size.minDimension * 0.14f
        drawRoundRect(
            color = inkColor.copy(alpha = 0.88f),
            topLeft = Offset(rX - stemW / 2f, budTop + headR * 0.4f),
            size = Size(stemW, stemH),
            cornerRadius = CornerRadius(stemW / 2f, stemW / 2f)
        )
        drawCircle(
            color = inkColor,
            radius = headR,
            center = Offset(rX + headR * 0.35f, budTop + headR * 0.65f)
        )
        drawCircle(
            color = Color(0xFFF22626),
            radius = 2.4.dp.toPx(),
            center = Offset(rX, budTop + stemH * 0.72f)
        )
    }
}


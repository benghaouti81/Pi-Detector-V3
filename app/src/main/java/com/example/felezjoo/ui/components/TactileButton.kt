package com.example.felezjoo.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LabBorder
import com.example.ui.theme.LabBorderGlow
import com.example.ui.theme.LabOnPrimary
import com.example.ui.theme.LabPrimary
import com.example.ui.theme.LabPrimaryPressed
import com.example.ui.theme.LabSecondary
import com.example.ui.theme.LabSecondaryPressed
import com.example.ui.theme.LabTextMuted
import com.example.ui.theme.LabTextPrimary
import com.example.ui.theme.LabTextSecondary

/**
 * TactileButton: High-fidelity interactive button with tactile spring scaling
 * and immediate visible color transformation when pressed.
 */
@Composable
fun TactileButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = LabPrimary,
    pressedColor: Color = if (containerColor == LabPrimary) LabPrimaryPressed else if (containerColor == LabSecondary) LabSecondaryPressed else containerColor.copy(alpha = 0.75f),
    contentColor: Color = LabOnPrimary,
    pressedContentColor: Color = contentColor,
    shape: Shape = RoundedCornerShape(8.dp),
    border: BorderStroke? = null,
    pressedBorder: BorderStroke? = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ButtonScale"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> containerColor.copy(alpha = 0.35f)
            isPressed -> pressedColor
            else -> containerColor
        },
        animationSpec = spring(stiffness = Spring.StiffnessHigh),
        label = "ButtonBgColor"
    )

    val animatedContentColor by animateColorAsState(
        targetValue = when {
            !enabled -> contentColor.copy(alpha = 0.4f)
            isPressed -> pressedContentColor
            else -> contentColor
        },
        label = "ButtonContentColor"
    )

    val currentBorder = when {
        !enabled -> null
        isPressed -> pressedBorder ?: border
        else -> border
    }

    Box(
        modifier = modifier
            .scale(animatedScale)
            .defaultMinSize(minHeight = 44.dp)
            .clip(shape)
            .then(
                if (currentBorder != null) Modifier.border(currentBorder, shape) else Modifier
            )
            .background(animatedBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null, // We provide direct physical scale & color animation
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides animatedContentColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * TactileOutlinedButton: Outlined button that fills with translucent accent color
 * and transforms border and background dynamically when pressed.
 */
@Composable
fun TactileOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    borderColor: Color = LabBorder,
    pressedBorderColor: Color = LabPrimary,
    accentColor: Color = LabPrimary,
    contentColor: Color = LabTextPrimary,
    pressedContentColor: Color = Color.White,
    shape: Shape = RoundedCornerShape(8.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val animatedScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.96f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "OutlinedScale"
    )

    val animatedBgColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Transparent
            isPressed -> accentColor.copy(alpha = 0.28f)
            else -> accentColor.copy(alpha = 0.04f)
        },
        label = "OutlinedBgColor"
    )

    val animatedBorderColor by animateColorAsState(
        targetValue = when {
            !enabled -> borderColor.copy(alpha = 0.3f)
            isPressed -> pressedBorderColor
            else -> borderColor
        },
        label = "OutlinedBorderColor"
    )

    val animatedTextColor by animateColorAsState(
        targetValue = when {
            !enabled -> contentColor.copy(alpha = 0.35f)
            isPressed -> pressedContentColor
            else -> contentColor
        },
        label = "OutlinedTextColor"
    )

    Box(
        modifier = modifier
            .scale(animatedScale)
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, animatedBorderColor), shape)
            .background(animatedBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides animatedTextColor) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content
            )
        }
    }
}

/**
 * TactileChip: Distinctive selector chip with tactile feedback and glowing selection.
 */
@Composable
fun TactileChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    activeColor: Color = LabPrimary,
    shape: Shape = RoundedCornerShape(6.dp)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "ChipScale"
    )

    val bgColor by animateColorAsState(
        targetValue = when {
            selected && isPressed -> activeColor.copy(alpha = 0.40f)
            selected -> activeColor.copy(alpha = 0.22f)
            isPressed -> LabBorderGlow.copy(alpha = 0.20f)
            else -> Color(0xFF101928)
        },
        label = "ChipBg"
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            selected -> activeColor
            isPressed -> activeColor.copy(alpha = 0.6f)
            else -> LabBorder
        },
        label = "ChipBorder"
    )

    val textColor by animateColorAsState(
        targetValue = when {
            selected -> activeColor
            isPressed -> Color.White
            else -> LabTextSecondary
        },
        label = "ChipText"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minHeight = 36.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, borderColor), shape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        androidx.compose.material3.Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Medium
        )
    }
}

/**
 * TactileIconButton: Professional icon button with smooth spring press scaling,
 * visible background flash on tap, and responsive tint.
 */
@Composable
fun TactileIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.White,
    pressedTint: Color = LabPrimary,
    containerColor: Color = Color.Transparent,
    pressedContainerColor: Color = tint.copy(alpha = 0.22f),
    shape: Shape = RoundedCornerShape(8.dp),
    contentDescription: String? = null,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.88f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "IconBtnScale"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isPressed && enabled) pressedContainerColor else containerColor,
        label = "IconBtnBg"
    )

    val currentTint by animateColorAsState(
        targetValue = when {
            !enabled -> tint.copy(alpha = 0.38f)
            isPressed -> pressedTint
            else -> tint
        },
        label = "IconBtnTint"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
            .clip(shape)
            .background(bgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides currentTint) {
            content()
        }
    }
}


package app.gyrolet.mpvrx.ui.player.controls.components

import app.gyrolet.mpvrx.ui.icons.Icon
import app.gyrolet.mpvrx.ui.icons.Icons

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import app.gyrolet.mpvrx.ui.theme.AppShapeScale
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.gyrolet.mpvrx.ui.theme.spacing
import kotlinx.coroutines.delay

@Composable
fun SpeedControlSlider(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  val speedPresets = listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 4.0f)

  val currentIndex = speedPresets.indexOfFirst { 
    kotlin.math.abs(it - currentSpeed) < 0.05f 
  }.coerceIn(0, speedPresets.size - 1)

  val primaryColor = MaterialTheme.colorScheme.primary
  val onSurfaceColor = MaterialTheme.colorScheme.onSurface

  Surface(
    shape = AppShapeScale.medium,
    color = Color.Transparent,
    contentColor = Color.White,
    tonalElevation = 0.dp,
    shadowElevation = 0.dp,
    modifier = modifier.animateContentSize(),
  ) {
    Box(
      modifier = Modifier.padding(
        vertical = MaterialTheme.spacing.small,
        horizontal = MaterialTheme.spacing.medium,
      ),
      contentAlignment = Alignment.Center,
    ) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
            Row(
              modifier = Modifier.width(280.dp),
              horizontalArrangement = Arrangement.SpaceBetween,
              verticalAlignment = Alignment.CenterVertically,
            ) {
              speedPresets.forEach { speed ->
                val isCurrentSpeed = kotlin.math.abs(currentSpeed - speed) < 0.05f
                Text(
                  text = "${speed.format()}x",
                  fontSize = if (isCurrentSpeed) 13.sp else 10.sp,
                  fontWeight = if (isCurrentSpeed) FontWeight.Bold else FontWeight.Normal,
                  color = if (isCurrentSpeed) {
                    primaryColor
                  } else {
                    Color.White.copy(alpha = 0.7f)
                  },
                )
              }
            }

            Canvas(
              modifier = Modifier
                .width(280.dp)
                .height(3.dp),
            ) {
              val trackWidth = size.width
              val trackHeight = 3.dp.toPx()
              val centerY = size.height / 2
              val segmentWidth = trackWidth / (speedPresets.size - 1)

              drawLine(
                color = Color.White.copy(alpha = 0.35f),
                start = Offset(0f, centerY),
                end = Offset(trackWidth, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
              )

              val progressX = currentIndex * segmentWidth
              drawLine(
                color = primaryColor,
                start = Offset(0f, centerY),
                end = Offset(progressX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round,
              )

              speedPresets.forEachIndexed { index, _ ->
                val tickX = index * segmentWidth
                drawCircle(
                  color = if (index <= currentIndex) {
                    primaryColor
                  } else {
                    Color.White.copy(alpha = 0.7f)
                  },
                  radius = 2.5.dp.toPx(),
                  center = Offset(tickX, centerY),
                )
              }
            }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.Center,
        ) {
          Icon(
            imageVector = Icons.Filled.FastForward,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text = "${currentSpeed.format()}x Speed Playing",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 4.dp),
          )
        }
      }
    }
  }
}

@Composable
fun CompactSpeedIndicator(
  currentSpeed: Float,
  modifier: Modifier = Modifier,
) {
  Box(
    modifier = modifier
      .background(
        color = Color.Transparent,
        shape = AppShapeScale.full
      )
      .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small),
    contentAlignment = Alignment.Center,
  ) {
    Text(
      text = "${currentSpeed.format()}x",
      fontSize = 14.sp,
      fontWeight = FontWeight.Bold,
      style = MaterialTheme.typography.bodyLarge,
      color = Color.White
    )
  }
}

private fun Float.format(): String {
  return when {
    this % 1.0f == 0.0f -> this.toInt().toString()
    else -> String.format("%.2f", this).trimEnd('0').trimEnd('.')
  }
}

package com.whattoeat.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开屏动画：
 *
 *   🧐 从屏幕下方弹到中央（带回弹）
 *    → 停顿一下
 *    → 变成 😋
 *    → 慢慢放大
 *    → 渐隐
 *    → 进入主页面（默认就是「今日」Tab）
 *
 * 全部用 Animatable 手动编排，没有引入 lottie 之类的库，几百 KB 都不用加。
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 纵向偏移（dp）：正值 = 在屏幕下方
    val offsetY = remember { Animatable(220f) }
    val scale = remember { Animatable(0.35f) }
    val alpha = remember { Animatable(1f) }
    var emoji by remember { mutableStateOf("🧐") }

    LaunchedEffect(Unit) {
        // 1) 弹入：位移用弹性动画，缩放用弹簧（回弹手感来自 spring）
        launch {
            offsetY.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            )
        }
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow,
            ),
        )

        // 2) 变脸：🧐 → 😋
        delay(220)
        emoji = "😋"

        // 3) 慢慢变大
        scale.animateTo(
            targetValue = 1.9f,
            animationSpec = tween(durationMillis = 820, easing = LinearOutSlowInEasing),
        )

        // 4) 渐隐
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        )

        // 5) 交给上层切到主页面
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji,
            fontSize = 96.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationY = offsetY.value.dp.toPx()
                this.alpha = alpha.value
            },
        )
    }
}

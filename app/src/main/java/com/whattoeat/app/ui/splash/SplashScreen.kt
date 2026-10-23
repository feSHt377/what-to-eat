package com.whattoeat.app.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
 *
 * 标题「WhatToEat」单独放在下面，跟着 emoji 一起淡出，
 * 不会跟着 emoji 一起放大，所以文字始终是清晰可读的。
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
    // 标题比 emoji 晚一点出现，慢一点消失，整体更有层次
    val titleAlpha = remember { Animatable(0f) }
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

        // 2) 标题淡入
        titleAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        )

        // 3) 变脸：🧐 → 😋
        delay(220)
        emoji = "😋"

        // 4) 慢慢变大
        scale.animateTo(
            targetValue = 1.9f,
            animationSpec = tween(durationMillis = 820, easing = LinearOutSlowInEasing),
        )

        // 5) 渐隐（emoji 先淡，标题跟着淡）
        alpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 460, easing = FastOutSlowInEasing),
        )
        titleAlpha.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        )

        // 6) 交给上层切到主页面
        onFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // emoji 会放大到 1.9 倍，如果直接放在标题上面会压住标题。
            // 所以给它一个固定高度的外框（而不是让 emoji 自己撑开），
            // 放大的部分由 graphicsLayer 在框内溢出，标题始终在框下方，不会被盖住。
            Box(
                modifier = Modifier.height(200.dp),
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

            Text(
                text = "WhatToEat",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 20.dp)
                    .graphicsLayer { this.alpha = titleAlpha.value },
            )

            Text(
                text = "吃了什么，就知道该吃多少",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .graphicsLayer { this.alpha = titleAlpha.value },
            )
        }
    }
}

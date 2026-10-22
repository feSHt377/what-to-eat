package com.whattoeat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.whattoeat.app.ui.MainScreen
import com.whattoeat.app.ui.splash.SplashScreen
import com.whattoeat.app.ui.theme.WhatToEatTheme

/**
 * 整个 App 只有一个 Activity（单 Activity 架构）。
 * 页面切换由 Compose 内部状态完成，不用 Fragment，也不用 Navigation 库。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as WhatToEatApp).container

        setContent {
            WhatToEatTheme {
                // rememberSaveable：旋转屏幕不会重播开屏动画
                var showSplash by rememberSaveable { mutableStateOf(true) }

                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    MainScreen(repository = container.repository)
                }
            }
        }
    }
}

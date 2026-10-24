package com.whattoeat.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whattoeat.app.ui.MainScreen
import com.whattoeat.app.ui.diary.DiaryViewModel
import com.whattoeat.app.ui.settings.SettingsViewModel
import com.whattoeat.app.ui.splash.SplashScreen
import com.whattoeat.app.ui.summary.SummaryViewModel
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

                // 三个 ViewModel 在这里就创建好（而不是等到进 MainScreen 之后），
                // 这样它们会在开屏动画那 2 秒里就开始查库 + 算数据，
                // 玩家第一次点 Tab 就没有「加载中」的卡顿。
                val diaryViewModel: DiaryViewModel =
                    viewModel(factory = DiaryViewModel.factory(container.repository))
                val summaryViewModel: SummaryViewModel =
                    viewModel(factory = SummaryViewModel.factory(container.repository))
                val settingsViewModel: SettingsViewModel =
                    viewModel(
                        factory = SettingsViewModel.factory(
                            container.repository,
                            container.backupManager,
                            container.releaseChecker,
                        ),
                    )

                if (showSplash) {
                    SplashScreen(onFinished = { showSplash = false })
                } else {
                    MainScreen(
                        repository = container.repository,
                        diaryViewModel = diaryViewModel,
                        summaryViewModel = summaryViewModel,
                        settingsViewModel = settingsViewModel,
                    )
                }
            }
        }
    }
}

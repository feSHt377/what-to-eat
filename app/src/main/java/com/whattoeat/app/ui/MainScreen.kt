package com.whattoeat.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.ui.add.AddFoodScreen
import com.whattoeat.app.ui.diary.DiaryScreen
import com.whattoeat.app.ui.diary.DiaryViewModel
import com.whattoeat.app.ui.settings.SettingsScreen
import com.whattoeat.app.ui.settings.SettingsViewModel
import com.whattoeat.app.ui.summary.SummaryScreen
import com.whattoeat.app.ui.summary.SummaryViewModel

/**
 * App 主界面：3 个 Tab + 一个全屏的「添加食物」页面。
 *
 *  0 今日：每天吃了什么、还剩多少热量
 *  1 汇总：周期性数据（7/14/30 天）+ 代谢估算
 *  2 设置：个人档案（身高体重性别代谢）+ 食物模板管理 + 账号占位
 *
 * 页面不多，用状态切换就够；等以后页面变多再引入 navigation-compose。
 */
@Composable
fun MainScreen(repository: DietRepository) {

    val diaryViewModel: DiaryViewModel = viewModel(factory = DiaryViewModel.factory(repository))
    val summaryViewModel: SummaryViewModel = viewModel(factory = SummaryViewModel.factory(repository))
    val settingsViewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(repository))

    val diaryState by diaryViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddScreen by remember { mutableStateOf(false) }
    var pendingMeal by remember { mutableStateOf(MealType.suggestFor()) }

    if (showAddScreen) {
        AddFoodScreen(
            repository = repository,
            date = diaryState.date,
            initialMeal = pendingMeal,
            onDismiss = { showAddScreen = false },
        )
        return
    }

    Scaffold(
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = {
                        // 按当前时间猜一个餐次，少点一次
                        pendingMeal = MealType.suggestFor()
                        showAddScreen = true
                    },
                ) {
                    Text("＋", fontSize = 24.sp)
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("今日") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("汇总") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("设置") })
            }

            when (tab) {
                0 -> DiaryScreen(
                    state = diaryState,
                    onPrevDay = diaryViewModel::previousDay,
                    onNextDay = diaryViewModel::nextDay,
                    onToday = diaryViewModel::goToday,
                    onDeleteEntry = diaryViewModel::deleteEntry,
                    onAddToMeal = { meal ->
                        pendingMeal = meal
                        showAddScreen = true
                    },
                )

                1 -> SummaryScreen(
                    state = summaryState,
                    onSelectPeriod = summaryViewModel::setPeriod,
                )

                else -> SettingsScreen(
                    state = settingsState,
                    onSaveProfile = settingsViewModel::saveProfile,
                    onSaveFood = settingsViewModel::saveFood,
                    onDeleteFood = settingsViewModel::deleteFood,
                )
            }
        }
    }
}

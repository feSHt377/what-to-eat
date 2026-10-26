package com.whattoeat.app.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.ui.add.AddFoodScreen
import com.whattoeat.app.ui.diary.DiaryScreen
import com.whattoeat.app.ui.diary.DiaryViewModel
import com.whattoeat.app.ui.settings.SettingsScreen
import com.whattoeat.app.ui.settings.SettingsViewModel
import com.whattoeat.app.ui.settings.UpdateStatus
import com.whattoeat.app.ui.summary.SummaryScreen
import com.whattoeat.app.ui.summary.SummaryViewModel

/**
 * App 主界面：底部导航栏（3 个 Tab）+ 一个全屏的「添加食物」页面。
 *
 *  0 今日：每天吃了什么、还剩多少热量
 *  1 汇总：周期性数据（7/14/30 天）+ 代谢估算
 *  2 设置：个人档案（身高体重性别代谢）+ 食物模板管理 + 账号占位
 *
 * 页面不多，用状态切换就够；等以后页面变多再引入 navigation-compose。
 *
 * 关于「切 Tab 卡顿」：
 *  三个 ViewModel 由 MainActivity 在**开屏动画期间**就创建好，
 *  它们内部用 SharingStarted.Eagerly + Dispatchers.Default 提前把数据查好、算好，
 *  所以这里 collectAsStateWithLifecycle 拿到的第一帧就是有数据的，不会出现「先空后满」的卡顿。
 */
@Composable
fun MainScreen(
    repository: DietRepository,
    diaryViewModel: DiaryViewModel,
    summaryViewModel: SummaryViewModel,
    settingsViewModel: SettingsViewModel,
) {

    val diaryState by diaryViewModel.uiState.collectAsStateWithLifecycle()
    val summaryState by summaryViewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val updateStatus by settingsViewModel.updateStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val installPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { settingsViewModel.resumeUpdateInstallation() }

    // 在主界面处理，而不是只在设置 Tab 中处理：下载完成即自动调起安装。
    LaunchedEffect(updateStatus) {
        when (updateStatus) {
            is UpdateStatus.ReadyToInstall -> settingsViewModel.resumeUpdateInstallation()
            is UpdateStatus.InstallPermissionRequired -> {
                try {
                    installPermissionLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ),
                    )
                } catch (_: ActivityNotFoundException) {
                    settingsViewModel.onUnknownAppSourcesUnavailable()
                }
            }
            else -> Unit
        }
    }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showAddScreen by remember { mutableStateOf(false) }
    var pendingMeal by remember { mutableStateOf(MealType.suggestFor()) }

    if (showAddScreen) {
        // 「添加食物」是全屏页，它自己处理返回键（BackHandler），关闭后回到原来的 Tab
        AddFoodScreen(
            repository = repository,
            date = diaryState.date,
            initialMeal = pendingMeal,
            onDismiss = { showAddScreen = false },
        )
        return
    }

    // 系统返回键：在「汇总 / 设置」时先回到「今日」，在「今日」时交给系统退出 App
    BackHandler(enabled = tab != 0) { tab = 0 }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("今日") },
                    alwaysShowLabel = true,
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("汇总") },
                    alwaysShowLabel = true,
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") },
                    alwaysShowLabel = true,
                )
            }
        },
        floatingActionButton = {
            if (tab == 0) {
                FloatingActionButton(
                    onClick = {
                        // 按当前时间猜一个餐次，少点一次
                        pendingMeal = MealType.suggestFor()
                        showAddScreen = true
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "添加食物")
                }
            }
        },
    ) { innerPadding ->
        // 底部导航栏的高度已经由 Scaffold 的 innerPadding 扣掉了，
        // 这里额外给的 bottom padding 是为了给 FAB 留出位置：
        // 只有「今日」页有 FAB，汇总/设置页不需要那么多空白。
        val listBottomPadding = if (tab == 0) 96.dp else 24.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                    onSaveWeight = diaryViewModel::saveWeight,
                    onClearWeight = diaryViewModel::clearWeight,
                    onAddWater = diaryViewModel::addWater,
                    onUndoWater = diaryViewModel::undoWater,
                    onClearWater = diaryViewModel::clearWater,
                    contentPadding = PaddingValues(bottom = listBottomPadding),
                )

                1 -> SummaryScreen(
                    state = summaryState,
                    onSelectPeriod = summaryViewModel::setPeriod,
                    contentPadding = PaddingValues(bottom = listBottomPadding),
                )

                else -> SettingsScreen(
                    state = settingsState,
                    onSaveProfile = settingsViewModel::saveProfile,
                    onSaveFood = settingsViewModel::saveFood,
                    onDeleteFood = settingsViewModel::deleteFood,
                    backupBusy = settingsViewModel.backupBusy.collectAsStateWithLifecycle().value,
                    backupMessage = settingsViewModel.backupMessage.collectAsStateWithLifecycle().value,
                    onClearBackupMessage = settingsViewModel::clearBackupMessage,
                    onExportBackup = settingsViewModel::exportBackup,
                    onImportBackup = settingsViewModel::importBackup,
                    updateStatus = settingsViewModel.updateStatus.collectAsStateWithLifecycle().value,
                    onCheckUpdates = settingsViewModel::checkForUpdates,
                    contentPadding = PaddingValues(bottom = listBottomPadding),
                )
            }
        }
    }
}

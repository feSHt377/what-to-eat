package com.whattoeat.app.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.whattoeat.app.BuildConfig
import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.data.update.ReleaseChecker
import com.whattoeat.app.domain.ActivityLevel
import com.whattoeat.app.domain.Goal
import com.whattoeat.app.domain.Metabolism
import com.whattoeat.app.domain.Sex
import com.whattoeat.app.domain.UserProfile
import com.whattoeat.app.ui.common.LabeledValue
import com.whattoeat.app.ui.common.SectionCard
import com.whattoeat.app.ui.common.fmt0
import com.whattoeat.app.ui.common.fmt1
import com.whattoeat.app.ui.theme.NutrientColors
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 「设置」页：
 *  1. 账号（本版只做占位，不接后端）
 *  2. 个人档案：身高 / 体重 / 性别 / 出生年 / 活动量 / 目标 / 代谢
 *  3. 食物模板管理：新增、编辑、删除
 */
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onSaveProfile: (UserProfile) -> Unit,
    onSaveFood: (FoodEntity) -> Unit,
    onDeleteFood: (FoodEntity) -> Unit,
    backupBusy: Boolean,
    backupMessage: String?,
    onClearBackupMessage: () -> Unit,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    updateStatus: UpdateStatus,
    onCheckUpdates: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 96.dp),
) {
    var showNewFoodDialog by remember { mutableStateOf(false) }
    var editingFood by remember { mutableStateOf<FoodEntity?>(null) }
    var deletingFood by remember { mutableStateOf<FoodEntity?>(null) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }
    val context = LocalContext.current
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri -> uri?.let(onExportBackup) }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> pendingImportUri = uri }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BackupCard(
                busy = backupBusy,
                onExport = {
                    val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
                    exportLauncher.launch("WhatToEat-backup-$timestamp.zip")
                },
                onImport = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            UpdateCard(
                status = updateStatus,
                onCheck = onCheckUpdates,
                onOpenRelease = { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                },
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item {
            ProfileCard(
                profile = state.profile,
                autoTdee = state.autoEstimate?.effectiveTdee,
                autoSourceLabel = state.autoEstimate?.source?.shortLabel,
                onSave = onSaveProfile,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        // ---------- 食物模板标题栏 ----------
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "食物模板（${state.foods.size}）",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                TextButton(onClick = { showNewFoodDialog = true }) { Text("+ 新增模板") }
            }
        }

        // ---------- 模板列表 ----------
        items(items = state.foods, key = { it.id }) { food ->
            FoodTemplateRow(
                food = food,
                onEdit = { editingFood = food },
                onDelete = { deletingFood = food },
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }

        item {
            Text(
                text = "内置模板也可以改或删；改模板不会影响已经记录过的历史数据。",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }

        item { AboutCard(Modifier.padding(horizontal = 16.dp)) }

        item { Spacer(Modifier.height(8.dp)) }
    }

    // ---------- 新增 / 编辑弹窗 ----------
    // 备份操作期间拦截系统返回，避免文件选择结果迟到后重新执行危险操作。
    BackHandler(enabled = backupBusy) { }
    if (showNewFoodDialog) {
        FoodTemplateDialog(
            initial = FoodEntity(
                id = 0,
                name = "",
                category = "我的",
                kcalPer100g = 0.0,
                proteinPer100g = 0.0,
                fatPer100g = 0.0,
                carbPer100g = 0.0,
                defaultGrams = 100.0,
                builtIn = false,
            ),
            isNew = true,
            onDismiss = { showNewFoodDialog = false },
            onSave = { food ->
                onSaveFood(food)
                showNewFoodDialog = false
            },
        )
    }

    editingFood?.let { food ->
        FoodTemplateDialog(
            initial = food,
            isNew = false,
            onDismiss = { editingFood = null },
            onSave = { updated ->
                onSaveFood(updated)
                editingFood = null
            },
        )
    }

    deletingFood?.let { food ->
        AlertDialog(
            onDismissRequest = { deletingFood = null },
            title = { Text("删除模板") },
            text = { Text("确定删除「${food.name}」吗？已记录的历史数据不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteFood(food)
                    deletingFood = null
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deletingFood = null }) { Text("取消") } },
        )
    }

    if (backupMessage != null) {
        AlertDialog(
            onDismissRequest = onClearBackupMessage,
            title = { Text("数据备份") },
            text = { Text(backupMessage) },
            confirmButton = { TextButton(onClick = onClearBackupMessage) { Text("知道了") } },
        )
    }

    pendingImportUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingImportUri = null },
            title = { Text("确认导入并覆盖？") },
            text = {
                Text("导入会完整替换当前档案、食物模板、饮食、体重和饮水记录，且无法撤销。\n\n建议先导出当前数据作为备份。ZIP 校验失败时不会改变现有数据。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingImportUri = null
                    onImportBackup(uri)
                }) { Text("导入并覆盖") }
            },
            dismissButton = { TextButton(onClick = { pendingImportUri = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun AccountCard(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text("账号", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "本版本还没有接入账号系统，饮食数据只保存在这台手机上。\n可以使用 ZIP 备份；后续再支持登录和多设备同步。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = { }, enabled = false) { Text("登录 / 注册（暂未开放）") }
    }
}

@Composable
private fun BackupCard(
    busy: Boolean,
    onExport: () -> Unit,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Text("数据备份", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "导出为一个 ZIP 文件，包含个人配置、食物模板、饮食、体重和饮水记录。导入会覆盖当前数据。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        if (busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(24.dp).height(24.dp))
                Spacer(Modifier.width(10.dp))
                Text("正在处理备份，请勿关闭应用…", style = MaterialTheme.typography.bodySmall)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onExport, modifier = Modifier.weight(1f)) { Text("一键导出") }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) { Text("一键导入") }
            }
        }
    }
}

@Composable
private fun UpdateCard(
    status: UpdateStatus,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    SectionCard(modifier = modifier) {
        Text("应用更新", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text("当前版本 ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        when (status) {
            UpdateStatus.Idle -> Text("准备检查更新…", style = MaterialTheme.typography.bodySmall)
            UpdateStatus.Checking -> Text("正在检查 GitHub Releases…", style = MaterialTheme.typography.bodySmall)
            UpdateStatus.Current -> Text(
                "已是最新版本",
                color = NutrientColors.Fiber,
                style = MaterialTheme.typography.bodySmall,
            )
            is UpdateStatus.Available -> {
                Text(
                    "发现新版本 ${status.version}",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("即将开始下载", style = MaterialTheme.typography.bodySmall)
            }
            is UpdateStatus.Downloading -> {
                Text("正在下载新版本 ${status.version}…", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                status.progress?.let { progress ->
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth())
                    Text("已下载 $progress%", style = MaterialTheme.typography.labelSmall)
                } ?: LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("下载完成后会自动打开系统安装界面。", style = MaterialTheme.typography.labelSmall)
            }
            is UpdateStatus.ReadyToInstall -> Text(
                "安装包已下载，正在打开系统安装界面…",
                color = NutrientColors.Fiber,
                style = MaterialTheme.typography.bodySmall,
            )
            is UpdateStatus.InstallPermissionRequired -> Text(
                "请在系统设置中允许 WhatToEat 安装未知来源应用，返回后会继续安装。",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodySmall,
            )
            UpdateStatus.Installing -> Text(
                "安装界面已打开，请按系统提示确认安装。",
                color = NutrientColors.Fiber,
                style = MaterialTheme.typography.bodySmall,
            )
            is UpdateStatus.Error -> Text(
                status.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onCheck,
                enabled = status !is UpdateStatus.Checking && status !is UpdateStatus.Downloading,
            ) {
                Text("重新检查")
            }
            TextButton(onClick = { onOpenRelease(ReleaseChecker.RELEASES_URL) }) {
                Text("查看发布页")
            }
        }
        Text(
            "更新包从公开 GitHub Release 下载，完成后调起系统安装界面；是否安装由你确认。饮食数据不上传。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    autoTdee: Double?,
    autoSourceLabel: String?,
    onSave: (UserProfile) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 本地草稿：改了没点保存时不影响数据库里已存的值
    var sex by remember(profile) { mutableStateOf(profile.sex) }
    var goal by remember(profile) { mutableStateOf(profile.goal) }
    var activity by remember(profile) { mutableStateOf(profile.activity) }
    var heightText by remember(profile) { mutableStateOf(profile.heightCm.fmt0()) }
    var weightText by remember(profile) { mutableStateOf(profile.weightKg.fmt1()) }
    var yearText by remember(profile) { mutableStateOf(profile.birthYear.toString()) }
    var tdeeText by remember(profile) { mutableStateOf(profile.manualTdeeKcal?.fmt0() ?: "") }
    var waterGoalText by remember(profile) { mutableStateOf(profile.waterGoalMl.toString()) }
    val waterGoalValid = waterGoalText.toIntOrNull()
        ?.let(com.whattoeat.app.domain.Hydration::isValidGoal) == true
    // 自定义热量差：留空 = 用所选目标的预设值
    var deltaText by remember(profile) { mutableStateOf(profile.customDeltaKcal?.fmt0() ?: "") }

    SectionCard(modifier = modifier) {
        Text("个人档案", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        // ---- 性别 ----
        Text("性别", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Sex.entries.forEach { option ->
                FilterChip(
                    selected = sex == option,
                    onClick = { sex = option },
                    label = { Text(option.label) },
                )
            }
        }

        // ---- 身高 / 体重 ----
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = heightText,
                onValueChange = { heightText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("身高 cm") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = weightText,
                onValueChange = { weightText = it.filter { c -> c.isDigit() || c == '.' } },
                label = { Text("体重 kg") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        // ---- 出生年 ----
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = yearText,
            onValueChange = { yearText = it.filter { c -> c.isDigit() } },
            label = { Text("出生年份") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // ---- 活动量 ----
        Spacer(Modifier.height(12.dp))
        Text("活动量", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ActivityLevel.entries.forEach { option ->
                FilterChip(
                    selected = activity == option,
                    onClick = { activity = option },
                    label = { Text(option.label) },
                )
            }
        }

        // ---- 目标 ----
        Spacer(Modifier.height(12.dp))
        Text("目标", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Goal.entries.forEach { option ->
                FilterChip(
                    selected = goal == option,
                    onClick = { goal = option },
                    label = { Text(option.label) },
                )
            }
        }

        // ---- 自定义每日热量差 ----
        Spacer(Modifier.height(12.dp))
        Text("每日热量差", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = deltaText,
            // 允许负号：缺口就是负数
            onValueChange = { input ->
                deltaText = input.filter { it.isDigit() || it == '.' || it == '-' }
            },
            label = { Text("留空 = 用目标预设值") },
            placeholder = { Text("例如 -500，或 300") },
            prefix = { Text("kcal  ") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        val customDelta = deltaText.trim().toDoubleOrNull()
        val effectiveDelta = customDelta ?: goal.dailyDeltaKcal
        val baseTdee = tdeeText.toDoubleOrNull()?.takeIf { it > 0 } ?: autoTdee
        Text(
            text = buildString {
                append("负数 = 缺口（减脂），正数 = 盈余（增肌）。")
                append("现在生效的是 ")
                append(if (effectiveDelta > 0) "+" else "")
                append(effectiveDelta.fmt0())
                append(" kcal")
                if (customDelta == null) {
                    append("（${goal.label}的预设值）")
                } else {
                    append("（你自定义的）")
                }
                append("。")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (baseTdee != null) {
            val targetKcal = (baseTdee + effectiveDelta)
                .coerceAtLeast(Metabolism.MIN_SAFE_KCAL)
            val floored = (baseTdee + effectiveDelta) < Metabolism.MIN_SAFE_KCAL
            Spacer(Modifier.height(2.dp))
            Text(
                text = buildString {
                    append("每日目标 ≈ ${targetKcal.fmt0()} kcal（代谢 ${baseTdee.fmt0()} ")
                    append(if (effectiveDelta >= 0) "+ " else "− ")
                    append("${kotlin.math.abs(effectiveDelta).fmt0()}）")
                    if (floored) {
                        append("；已按安全下限 ${Metabolism.MIN_SAFE_KCAL.fmt0()} kcal 兜底")
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (floored) NutrientColors.Over else MaterialTheme.colorScheme.primary,
            )
        }

        // ---- 代谢（TDEE）----
        Spacer(Modifier.height(12.dp))
        Text("每日代谢 TDEE", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = tdeeText,
            onValueChange = { tdeeText = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("留空 = 自动（kcal）") },
            placeholder = { Text("例如 2300") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = buildString {
                append("留空时的自动值")
                if (autoTdee != null) {
                    append("：约 ${autoTdee.fmt0()} kcal")
                    autoSourceLabel?.let { append("（$it）") }
                } else {
                    append("：按性别取平均值（男 ${Metabolism.AVERAGE_TDEE_MALE.fmt0()} / 女 ${Metabolism.AVERAGE_TDEE_FEMALE.fmt0()} kcal）")
                }
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "优先级：手动填写 > 用你的饮食+体重记录自动反推 > 性别平均值。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- 饮水目标 ----
        Spacer(Modifier.height(12.dp))
        Text("每日饮水目标", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = waterGoalText,
            onValueChange = { waterGoalText = it.filter(Char::isDigit).take(5) },
            label = { Text("饮水目标 ml") },
            placeholder = { Text("例如 2000") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            isError = !waterGoalValid,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "默认 2000 ml，可设置 250–10000 ml；在「今日」页记录每次喝水。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // ---- 保存 ----
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                onSave(
                    profile.copy(
                        sex = sex,
                        goal = goal,
                        activity = activity,
                        heightCm = heightText.toDoubleOrNull() ?: profile.heightCm,
                        weightKg = weightText.toDoubleOrNull() ?: profile.weightKg,
                        birthYear = yearText.toIntOrNull() ?: profile.birthYear,
                        manualTdeeKcal = tdeeText.toDoubleOrNull()?.takeIf { it > 0 },
                        customDeltaKcal = deltaText.trim().toDoubleOrNull(),
                        waterGoalMl = waterGoalText.toIntOrNull()
                            ?.takeIf(com.whattoeat.app.domain.Hydration::isValidGoal)
                            ?: profile.waterGoalMl,
                    )
                )
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = waterGoalValid,
        ) { Text("保存档案") }
    }
}

@Composable
private fun FoodTemplateRow(
    food: FoodEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${food.category} · ${food.kcalPer100g.fmt0()} kcal/100g · 蛋白 ${food.proteinPer100g.fmt0()} / 脂 ${food.fatPer100g.fmt0()} / 碳 ${food.carbPer100g.fmt0()} / 纤维 ${food.fiberPer100g.fmt1()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit) { Text("编辑") }
        TextButton(onClick = onDelete) { Text("删除") }
    }
}

@Composable
private fun FoodTemplateDialog(
    initial: FoodEntity,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: (FoodEntity) -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var category by remember { mutableStateOf(initial.category) }
    var kcalText by remember { mutableStateOf(if (initial.kcalPer100g > 0) initial.kcalPer100g.fmt0() else "") }
    var proteinText by remember { mutableStateOf(if (initial.proteinPer100g > 0) initial.proteinPer100g.fmt1() else "") }
    var fatText by remember { mutableStateOf(if (initial.fatPer100g > 0) initial.fatPer100g.fmt1() else "") }
    var carbText by remember { mutableStateOf(if (initial.carbPer100g > 0) initial.carbPer100g.fmt1() else "") }
    var fiberText by remember { mutableStateOf(if (initial.fiberPer100g > 0) initial.fiberPer100g.fmt1() else "") }
    var gramsText by remember { mutableStateOf(initial.defaultGrams.fmt0()) }

    val kcal = kcalText.toDoubleOrNull()
    val canSave = name.isNotBlank() && kcal != null && kcal > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "新增食物模板" else "编辑食物模板") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("热量 kcal / 100g *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = proteinText,
                        onValueChange = { proteinText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("蛋白 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = fatText,
                        onValueChange = { fatText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("脂肪 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(6.dp))
                    OutlinedTextField(
                        value = carbText,
                        onValueChange = { carbText = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("碳水 g") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = fiberText,
                    onValueChange = { fiberText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("膳食纤维 g") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("默认一份克数") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canSave) {
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                category = category.trim().ifBlank { "我的" },
                                kcalPer100g = kcal ?: 0.0,
                                proteinPer100g = proteinText.toDoubleOrNull() ?: 0.0,
                                fatPer100g = fatText.toDoubleOrNull() ?: 0.0,
                                carbPer100g = carbText.toDoubleOrNull() ?: 0.0,
                                fiberPer100g = fiberText.toDoubleOrNull() ?: 0.0,
                                defaultGrams = gramsText.toDoubleOrNull()?.takeIf { it > 0 } ?: 100.0,
                            )
                        )
                    }
                },
                enabled = canSave,
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun AboutCard(modifier: Modifier = Modifier) {
    SectionCard(modifier = modifier) {
        Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        LabeledValue("版本", BuildConfig.VERSION_NAME)
        LabeledValue("数据存储", "仅本机（SQLite）；更新检查不传输饮食数据")
        LabeledValue("许可协议", "PolyForm Noncommercial 1.0.0")
        Spacer(Modifier.height(6.dp))
        Text(
            text = "本项目仅授权非商业用途。任何商业使用须先取得作者书面授权。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "热量与营养数值为近似参考值，不构成医疗或营养学建议。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

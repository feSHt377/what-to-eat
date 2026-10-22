package com.whattoeat.app.ui.add

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.domain.Nutrients
import com.whattoeat.app.ui.common.fmt0
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 添加食物页（全屏）。两条录入路径：
 *
 *  A. 选模板食物（推荐，营养数据齐全）→ 输入克数
 *  B. 自定义记录（临时食物，只记名称 + 热量，可选填三大营养素）
 *
 * 写入后停留在列表页，方便连续加几样。
 */
@Composable
fun AddFoodScreen(
    repository: DietRepository,
    date: LocalDate,
    initialMeal: MealType,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    var query by rememberSaveable { mutableStateOf("") }
    var mealName by rememberSaveable { mutableStateOf(initialMeal.name) }
    var selectedFood by remember { mutableStateOf<FoodEntity?>(null) }
    var showCustomDialog by remember { mutableStateOf(false) }

    val meal = MealType.fromName(mealName)
    val foodFlow = remember(query) { repository.searchFoods(query) }
    val foods by foodFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {

        // ---------- 顶部栏 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Text(
                text = "添加食物",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onDismiss) { Text("完成") }
        }

        // ---------- 餐次 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealType.entries.forEach { type ->
                FilterChip(
                    selected = type.name == mealName,
                    onClick = { mealName = type.name },
                    label = { Text(type.label) },
                )
            }
        }

        // ---------- 自定义入口 ----------
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { showCustomDialog = true },
                label = { Text("自定义记录（只填名称和热量）") },
            )
        }

        // ---------- 搜索 ----------
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            label = { Text("搜索模板食物") },
            placeholder = { Text("例如：米饭、鸡胸肉、苹果") },
            singleLine = true,
        )

        Text(
            text = "记入「${meal.label}」· $date",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider()

        // ---------- 模板列表 ----------
        LazyColumn(modifier = Modifier.weight(1f)) {
            items(items = foods, key = { it.id }) { food ->
                FoodRow(food = food, onClick = { selectedFood = food })
                HorizontalDivider(Modifier.padding(start = 16.dp))
            }
            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "没有想要的？用上面的「自定义记录」，或者在设置页新增食物模板。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }

    // ---------- 模板食物：输入克数 ----------
    selectedFood?.let { food ->
        GramDialog(
            food = food,
            mealLabel = meal.label,
            onDismiss = { selectedFood = null },
            onConfirm = { grams ->
                scope.launch {
                    repository.addEntry(food = food, grams = grams, meal = meal, date = date)
                    selectedFood = null
                }
            },
        )
    }

    // ---------- 自定义记录 ----------
    if (showCustomDialog) {
        CustomEntryDialog(
            mealLabel = meal.label,
            onDismiss = { showCustomDialog = false },
            onConfirm = { name, kcal, protein, fat, carb ->
                scope.launch {
                    repository.addCustomEntry(
                        date = date,
                        meal = meal,
                        name = name,
                        nutrients = Nutrients(kcal = kcal, proteinG = protein, fatG = fat, carbG = carb),
                    )
                    showCustomDialog = false
                }
            },
        )
    }
}

@Composable
private fun FoodRow(food: FoodEntity, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(food.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${food.category} · 默认 ${food.defaultGrams.fmt0()} g",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "${food.kcalPer100g.fmt0()} kcal/100g",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** 模板食物 → 输入克数 */
@Composable
private fun GramDialog(
    food: FoodEntity,
    mealLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var gramsText by remember { mutableStateOf(food.defaultGrams.fmt0()) }
    val grams = gramsText.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${food.name}（记入$mealLabel）") },
        text = {
            Column {
                Text(
                    text = "每 100 克 ${food.kcalPer100g.fmt0()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = gramsText,
                    onValueChange = { gramsText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("吃了多少克") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (grams != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "约 ${(food.kcalPer100g * grams / 100.0).fmt0()} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { grams?.let(onConfirm) },
                enabled = grams != null && grams > 0,
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

/**
 * 自定义记录弹窗。
 * 只有「名称 + 热量」是必填，三大营养素可以不填（很多人根本不知道自己吃了多少蛋白）。
 */
@Composable
private fun CustomEntryDialog(
    mealLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, kcal: Double, protein: Double, fat: Double, carb: Double) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kcalText by remember { mutableStateOf("") }
    var proteinText by remember { mutableStateOf("") }
    var fatText by remember { mutableStateOf("") }
    var carbText by remember { mutableStateOf("") }

    val kcal = kcalText.toDoubleOrNull()
    val canConfirm = name.isNotBlank() && kcal != null && kcal > 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("自定义记录（$mealLabel）") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("吃了什么 *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = kcalText,
                    onValueChange = { kcalText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("热量 kcal *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "下面三项可以留空",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
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
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (canConfirm) {
                        onConfirm(
                            name.trim(),
                            kcal ?: 0.0,
                            proteinText.toDoubleOrNull() ?: 0.0,
                            fatText.toDoubleOrNull() ?: 0.0,
                            carbText.toDoubleOrNull() ?: 0.0,
                        )
                    }
                },
                enabled = canConfirm,
            ) { Text("添加") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

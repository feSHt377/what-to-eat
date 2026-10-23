package com.whattoeat.app.ui.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.db.LogEntryEntity
import com.whattoeat.app.data.toNutrients
import com.whattoeat.app.domain.DailyTargets
import com.whattoeat.app.domain.MealType
import com.whattoeat.app.domain.Nutrients
import com.whattoeat.app.domain.TdeeEstimator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 「每日记录」页面的界面状态。
 * 一次渲染需要的所有数字都在这里算好，Composable 里只负责画。
 */
data class DiaryUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<LogEntryEntity> = emptyList(),
    val totals: Nutrients = Nutrients.ZERO,
    val targets: DailyTargets = DailyTargets(2000.0, 120.0, 55.0, 220.0),
    val tdee: Double = 2000.0,
    val usingAdaptiveTdee: Boolean = false,
    /** 当前日期已经记录的体重；null = 这天没称，输入框就是空的 */
    val weightKg: Double? = null,
    /** 一共记录过多少次体重。少于 2 次就没法算趋势，界面要提示 */
    val weightLogCount: Int = 0,
    /** 最近 28 天里有几天记了饮食。反推代谢还要求这个够数 */
    val loggedDayCount: Int = 0,
) {
    val isOverKcal: Boolean get() = totals.kcal > targets.kcal
    val remainingKcal: Double get() = targets.kcal - totals.kcal

    /** 体重够不够两次 —— 不够的话代谢只能退回公式/平均值 */
    val hasEnoughWeightForTrend: Boolean get() = weightLogCount >= MIN_WEIGHT_POINTS

    fun entriesOf(meal: MealType): List<LogEntryEntity> = entries.filter { it.meal == meal.name }

    fun subtotalOf(meal: MealType): Nutrients =
        Nutrients.sum(entriesOf(meal).map { it.toNutrients() })

    companion object {
        /** 算体重趋势的最低要求：至少两个点才能连成线 */
        const val MIN_WEIGHT_POINTS = 2
    }
}

/**
 * 每日记录页 ViewModel。
 *
 * 目标热量来自 [TdeeEstimator]：数据够了就用「体重趋势反推」的结果，
 * 数据不够就退回公式估算 —— 这样每日页和汇总页的数字始终一致。
 */
class DiaryViewModel(private val repository: DietRepository) : ViewModel() {

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date.asStateFlow()

    /**
     * 日期一变就切换到新一天的数据流（flatMapLatest）。
     * Room 返回 Flow，所以增删记录后 UI 会自动刷新，无需手动 refresh。
     *
     * 线程/性能上的三个选择：
     *  - `flowOn(Dispatchers.Default)`：下面这段「算 28 天摄入 + 跑体重回归 + 算 TDEE」
     *    是在上游协程里做的，Room 的查询结果一到就开始算，全放主线程会掉帧。
     *  - `SharingStarted.Eagerly`：ViewModel 一创建就开始收集，不用等界面真的来订阅。
     *    这样开屏动画那两秒里数据就已经查好了，切 Tab 不会有等待。
     *  - 数据本身来自 Repository 的**共享流**，三个页面共用同一次数据库查询。
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DiaryUiState> = combine(
        _date.flatMapLatest { repository.entriesOf(it) },
        repository.profile,
        repository.weights,
        _date.flatMapLatest { date ->
            repository.entriesBetween(date.minusDays(TdeeEstimator.WINDOW_DAYS - 1), date)
        },
        _date,
    ) { entries, profile, weights, windowEntries, date ->

        // 最近 28 天里每天的总摄入
        val dailyKcal: Map<LocalDate, Double> = windowEntries
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.kcal } }

        val weightPoints = weights.mapNotNull { log ->
            runCatching { LocalDate.parse(log.date) to log.weightKg }.getOrNull()
        }

        val estimate = TdeeEstimator.estimate(profile, dailyKcal, weightPoints)

        DiaryUiState(
            date = date,
            entries = entries,
            totals = Nutrients.sum(entries.map { it.toNutrients() }),
            targets = DailyTargets.from(profile, estimate.effectiveTdee),
            tdee = estimate.effectiveTdee,
            usingAdaptiveTdee = estimate.usingAdaptive,
            // 只取「当前这一天」的体重：没记录就是 null，界面上显示空输入框
            weightKg = weights.firstOrNull { it.date == date.toString() }?.weightKg,
            weightLogCount = weights.size,
            loggedDayCount = dailyKcal.size,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = DiaryUiState(),
        )

    fun previousDay() = _date.update { it.minusDays(1) }

    fun nextDay() = _date.update { it.plusDays(1) }

    fun goToday() {
        _date.value = LocalDate.now()
    }

    /**
     * 记录当前查看日期的体重。
     * 同一天重复记会覆盖（数据库里 date 是主键），符合「一天一个数」的直觉。
     */
    fun saveWeight(weightKg: Double) {
        if (weightKg <= 0.0) return
        val targetDate = _date.value
        viewModelScope.launch { repository.saveWeight(targetDate, weightKg) }
    }

    /** 删除当前日期的体重记录（用户点错时用） */
    fun clearWeight() {
        val targetDate = _date.value
        viewModelScope.launch { repository.deleteWeight(targetDate) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { repository.deleteEntry(id) }
    }

    companion object {
        fun factory(repository: DietRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { DiaryViewModel(repository) }
        }
    }
}

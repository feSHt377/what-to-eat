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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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
) {
    val isOverKcal: Boolean get() = totals.kcal > targets.kcal
    val remainingKcal: Double get() = targets.kcal - totals.kcal

    fun entriesOf(meal: MealType): List<LogEntryEntity> = entries.filter { it.meal == meal.name }

    fun subtotalOf(meal: MealType): Nutrients =
        Nutrients.sum(entriesOf(meal).map { it.toNutrients() })
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
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DiaryUiState> = combine(
        _date.flatMapLatest { repository.entriesOf(it) },
        repository.profile(),
        repository.weights(),
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = DiaryUiState(),
    )

    fun previousDay() = _date.update { it.minusDays(1) }

    fun nextDay() = _date.update { it.plusDays(1) }

    fun goToday() {
        _date.value = LocalDate.now()
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

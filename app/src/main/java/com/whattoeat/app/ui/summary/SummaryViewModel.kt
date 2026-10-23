package com.whattoeat.app.ui.summary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.db.LogEntryEntity
import com.whattoeat.app.data.db.WeightLogEntity
import com.whattoeat.app.domain.DailyTargets
import com.whattoeat.app.domain.TdeeEstimate
import com.whattoeat.app.domain.TdeeEstimator
import com.whattoeat.app.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

/** 某一天的汇总点，用于画柱状图和列表 */
data class DailyPoint(
    val date: LocalDate,
    val kcal: Double,
    val logged: Boolean,
    val inTarget: Boolean,
)

/**
 * 「周期汇总」页面的状态。
 */
data class SummaryUiState(
    val periodDays: Int = 7,
    val points: List<DailyPoint> = emptyList(),
    val loggedDays: Int = 0,
    val totalKcal: Double = 0.0,
    val avgKcal: Double = 0.0,
    val avgProtein: Double = 0.0,
    val avgFat: Double = 0.0,
    val avgCarb: Double = 0.0,
    val avgFiber: Double = 0.0,
    val onTargetDays: Int = 0,
    val cumulativeBalance: Double = 0.0,
    val estimate: TdeeEstimate? = null,
    val targets: DailyTargets = DailyTargets(0.0, 0.0, 0.0, 0.0),
    val weightChangeKg: Double? = null,
) {
    /** 累计缺口为负 = 有热量缺口（在减脂方向） */
    val isDeficit: Boolean get() = cumulativeBalance <= 0.0
    val targetKcal: Double get() = targets.kcal
}

/**
 * 周期汇总 ViewModel。
 *
 * 统计口径（重要，写在界面上也要说清楚）：
 *  - 平均摄入只按「确实有记录的天」算，不会把你没记录的日子当成 0 拉低平均
 *  - 累计热量差 = Σ(当天摄入 − 当天目标)，只在有记录的天上累加
 */
class SummaryViewModel(private val repository: DietRepository) : ViewModel() {

    private val today = LocalDate.now()

    private val _periodDays = MutableStateFlow(7)
    val periodDays: StateFlow<Int> = _periodDays.asStateFlow()

    /**
     * 汇总页状态。
     *
     *  - `flowOn(Dispatchers.Default)`：把「按天分组求和 + 体重回归 + TDEE」挪出主线程。
     *  - `SharingStarted.Eagerly`：ViewModel 一创建就开始算，开屏动画期间就能备好，
     *    玩家第一次点「汇总」直接看到数据。
     */
    val uiState: StateFlow<SummaryUiState> = combine(
        repository.profile,
        repository.weights,
        // 一次拿满 28 天，切换周期时不重新查库
        repository.entriesBetween(today.minusDays(TdeeEstimator.WINDOW_DAYS - 1), today),
        _periodDays,
    ) { profile, weights, entries, period ->
        buildState(profile, weights, entries, period)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SummaryUiState(),
        )

    fun setPeriod(days: Int) {
        _periodDays.value = days
    }

    private fun buildState(
        profile: UserProfile,
        weights: List<WeightLogEntity>,
        entries: List<LogEntryEntity>,
        periodDays: Int,
    ): SummaryUiState {

        // ---- 1) 每日摄入（Map：日期 → 当天总热量）----
        val kcalByDate: Map<LocalDate, Double> = entries
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.kcal } }

        val nutrientsByDate = entries
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, list) ->
                doubleArrayOf(
                    list.sumOf { it.proteinG },
                    list.sumOf { it.fatG },
                    list.sumOf { it.carbG },
                    list.sumOf { it.fiberG },
                )
            }

        // ---- 2) TDEE 估算（始终按最近 28 天）----
        val weightPoints = weights.mapNotNull { log ->
            runCatching { LocalDate.parse(log.date) to log.weightKg }.getOrNull()
        }
        val estimate = TdeeEstimator.estimate(profile, kcalByDate, weightPoints)
        val targets = DailyTargets.from(profile, estimate.effectiveTdee)

        // ---- 3) 选定周期内的每日点 ----
        val points = (periodDays - 1 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            val kcal = kcalByDate[date]
            DailyPoint(
                date = date,
                kcal = kcal ?: 0.0,
                logged = kcal != null,
                inTarget = kcal != null && kcal <= targets.kcal,
            )
        }

        val loggedPoints = points.filter { it.logged }
        val loggedDays = loggedPoints.size

        val totals = loggedPoints.sumOf { it.kcal }
        val avgKcal = if (loggedDays > 0) totals / loggedDays else 0.0

        val avgProtein = if (loggedDays > 0) {
            loggedPoints.sumOf { nutrientsByDate[it.date]?.get(0) ?: 0.0 } / loggedDays
        } else 0.0
        val avgFat = if (loggedDays > 0) {
            loggedPoints.sumOf { nutrientsByDate[it.date]?.get(1) ?: 0.0 } / loggedDays
        } else 0.0
        val avgCarb = if (loggedDays > 0) {
            loggedPoints.sumOf { nutrientsByDate[it.date]?.get(2) ?: 0.0 } / loggedDays
        } else 0.0
        val avgFiber = if (loggedDays > 0) {
            loggedPoints.sumOf { nutrientsByDate[it.date]?.get(3) ?: 0.0 } / loggedDays
        } else 0.0

        val cumulativeBalance = loggedPoints.sumOf { it.kcal - targets.kcal }
        val onTargetDays = loggedPoints.count { it.inTarget }

        // ---- 4) 周期内体重变化 ----
        val periodStart = today.minusDays((periodDays - 1).toLong())
        val periodWeights = weightPoints.filter { it.first >= periodStart }.sortedBy { it.first }
        val weightChange = if (periodWeights.size >= 2) {
            periodWeights.last().second - periodWeights.first().second
        } else {
            null
        }

        return SummaryUiState(
            periodDays = periodDays,
            points = points,
            loggedDays = loggedDays,
            totalKcal = totals,
            avgKcal = avgKcal,
            avgProtein = avgProtein,
            avgFat = avgFat,
            avgCarb = avgCarb,
            avgFiber = avgFiber,
            onTargetDays = onTargetDays,
            cumulativeBalance = cumulativeBalance,
            estimate = estimate,
            targets = targets,
            weightChangeKg = weightChange,
        )
    }

    companion object {
        fun factory(repository: DietRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SummaryViewModel(repository) }
        }
    }
}

package com.whattoeat.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.domain.TdeeEstimate
import com.whattoeat.app.domain.TdeeEstimator
import com.whattoeat.app.domain.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val foods: List<FoodEntity> = emptyList(),
    /** 自动算出来的代谢值（用于提示"留空的话会用这个数"） */
    val autoEstimate: TdeeEstimate? = null,
)

/**
 * 设置页 ViewModel：个人档案（含代谢）、食物模板增删改。
 */
class SettingsViewModel(private val repository: DietRepository) : ViewModel() {

    private val today = LocalDate.now()

    /**
     * 设置页状态。
     * 同其他页：Eagerly 提前加载 + 计算放到 Default 线程，不占主线程。
     */
    val uiState: StateFlow<SettingsUiState> = combine(
        repository.profile,
        repository.allFoods,
        repository.weights,
        repository.entriesBetween(today.minusDays(TdeeEstimator.WINDOW_DAYS - 1), today),
    ) { profile, foods, weights, entries ->

        val dailyKcal = entries
            .groupBy { LocalDate.parse(it.date) }
            .mapValues { (_, list) -> list.sumOf { it.kcal } }

        val weightPoints = weights.mapNotNull { log ->
            runCatching { LocalDate.parse(log.date) to log.weightKg }.getOrNull()
        }

        SettingsUiState(
            profile = profile,
            foods = foods,
            autoEstimate = TdeeEstimator.estimate(profile, dailyKcal, weightPoints),
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = SettingsUiState(),
        )

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { repository.saveProfile(profile) }
    }

    fun saveFood(food: FoodEntity) {
        viewModelScope.launch { repository.saveFood(food) }
    }

    fun deleteFood(food: FoodEntity) {
        viewModelScope.launch { repository.deleteFood(food) }
    }

    companion object {
        fun factory(repository: DietRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}

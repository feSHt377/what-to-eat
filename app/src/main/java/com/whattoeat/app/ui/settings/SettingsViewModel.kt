package com.whattoeat.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.whattoeat.app.BuildConfig
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.backup.BackupManager
import com.whattoeat.app.data.update.AppUpdateManager
import com.whattoeat.app.data.update.GitHubAsset
import com.whattoeat.app.data.update.ReleaseChecker
import com.whattoeat.app.data.update.VersionComparator
import com.whattoeat.app.data.db.FoodEntity
import com.whattoeat.app.domain.TdeeEstimate
import com.whattoeat.app.domain.TdeeEstimator
import com.whattoeat.app.domain.UserProfile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val foods: List<FoodEntity> = emptyList(),
    /** 自动算出来的代谢值（用于提示"留空的话会用这个数"） */
    val autoEstimate: TdeeEstimate? = null,
)

sealed interface UpdateStatus {
    data object Idle : UpdateStatus
    data object Checking : UpdateStatus
    data object Current : UpdateStatus
    data class Available(val version: String, val asset: GitHubAsset) : UpdateStatus
    data class Downloading(val version: String, val progress: Int?) : UpdateStatus
    data class ReadyToInstall(val version: String) : UpdateStatus
    data class InstallPermissionRequired(val version: String) : UpdateStatus
    data object Installing : UpdateStatus
    data class Error(val message: String) : UpdateStatus
}

/**
 * 设置页 ViewModel：个人档案（含代谢）、食物模板增删改。
 */
class SettingsViewModel(
    private val repository: DietRepository,
    private val backupManager: BackupManager,
    private val releaseChecker: ReleaseChecker,
    private val appUpdateManager: AppUpdateManager,
) : ViewModel() {

    private val today = LocalDate.now()
    private var updateJob: Job? = null

    val backupBusy = MutableStateFlow(false)
    val backupMessage = MutableStateFlow<String?>(null)
    val updateStatus = MutableStateFlow<UpdateStatus>(UpdateStatus.Idle)

    init {
        checkForUpdates()
    }

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

    fun exportBackup(uri: Uri) {
        if (backupBusy.value) return
        viewModelScope.launch {
            backupBusy.value = true
            backupMessage.value = runCatching { backupManager.exportTo(uri) }
                .fold(
                    onSuccess = { "备份导出成功，请妥善保存 ZIP 文件" },
                    onFailure = { "导出失败：${it.readableMessage()}" },
                )
            backupBusy.value = false
        }
    }

    fun importBackup(uri: Uri) {
        if (backupBusy.value) return
        viewModelScope.launch {
            backupBusy.value = true
            backupMessage.value = runCatching { backupManager.importFrom(uri) }
                .fold(
                    onSuccess = { "导入完成，档案、食物模板、饮食、体重和饮水记录已恢复" },
                    onFailure = { "导入失败，原有数据未改变：${it.readableMessage()}" },
                )
            backupBusy.value = false
        }
    }

    private fun downloadUpdate(available: UpdateStatus.Available) {
        updateStatus.value = UpdateStatus.Downloading(available.version, null)
        runCatching {
            appUpdateManager.downloadApk(available.asset) { progress ->
                updateStatus.value = UpdateStatus.Downloading(available.version, progress)
            }
        }.onSuccess {
            updateStatus.value = UpdateStatus.ReadyToInstall(available.version)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            updateStatus.value = UpdateStatus.Error("更新失败：${error.readableMessage()}")
        }
    }

    /** 安装界面已经调起，避免页面重组或切回设置页时重复弹出。 */
    fun onInstallationLaunched() {
        updateStatus.value = UpdateStatus.Installing
    }

    fun resumeUpdateInstallation() {
        if (updateStatus.value !is UpdateStatus.ReadyToInstall &&
            updateStatus.value !is UpdateStatus.InstallPermissionRequired
        ) return
        runCatching { appUpdateManager.installDownloadedApk() }
            .onSuccess { onInstallationLaunched() }
            .onFailure { error ->
                if (error is AppUpdateManager.InstallPermissionRequiredException) {
                    onUpdatePermissionRequired()
                } else {
                    updateStatus.value = UpdateStatus.Error("无法安装更新：${error.readableMessage()}")
                }
            }
    }

    fun onUpdatePermissionRequired() {
        val status = updateStatus.value
        val version = when (status) {
            is UpdateStatus.ReadyToInstall -> status.version
            is UpdateStatus.InstallPermissionRequired -> status.version
            else -> null
        } ?: return
        updateStatus.value = UpdateStatus.InstallPermissionRequired(version)
    }

    fun onUnknownAppSourcesUnavailable() {
        if (updateStatus.value is UpdateStatus.InstallPermissionRequired) {
            updateStatus.value = UpdateStatus.Error("系统未提供安装权限设置，请手动允许安装未知来源应用后重试")
        }
    }

    fun clearBackupMessage() {
        backupMessage.value = null
    }

    fun checkForUpdates() {
        if (updateJob?.isActive == true) return
        updateJob = viewModelScope.launch(Dispatchers.IO) {
            updateStatus.value = UpdateStatus.Checking
            updateStatus.value = runCatching { releaseChecker.latestRelease() }
                .fold(
                    onSuccess = { release ->
                        if (VersionComparator.compare(release.tag_name, BuildConfig.VERSION_NAME) > 0) {
                            val apk = release.apkAsset()
                                ?: throw IOException("最新 Release 没有可下载的 APK 附件")
                            UpdateStatus.Available(release.tag_name, apk)
                        } else {
                            UpdateStatus.Current
                        }
                    },
                    onFailure = { UpdateStatus.Error(it.readableMessage()) },
                )
            // 在同一个更新任务里直接下载，避免两个任务的启停状态互相竞争。
            (updateStatus.value as? UpdateStatus.Available)?.let(::downloadUpdate)
        }
    }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: "请检查文件和网络后重试"

    companion object {
        fun factory(
            repository: DietRepository,
            backupManager: BackupManager,
            releaseChecker: ReleaseChecker,
            appUpdateManager: AppUpdateManager,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository, backupManager, releaseChecker, appUpdateManager) }
        }
    }
}

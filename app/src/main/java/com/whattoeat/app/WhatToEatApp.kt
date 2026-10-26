package com.whattoeat.app

import android.app.Application
import android.content.Context
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.backup.BackupManager
import com.whattoeat.app.data.db.AppDatabase
import com.whattoeat.app.data.update.AppUpdateManager
import com.whattoeat.app.data.update.ReleaseChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * 极简「依赖注入容器」。
 *
 * 正式项目常见做法是用 Hilt，但 Hilt 需要额外的注解处理器，第一次做 Android
 * 很容易卡在构建配置上。这里用手写容器：整个 App 只有一个实例，简单、够用、看得懂。
 * 以后要换 Hilt，只需要把这个类的字段改成 @Provides 即可。
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.create(context)

    /**
     * App 级别的协程作用域：用于不随页面销毁的初始化工作，
     * 同时也是 Repository 里那些「共享查询流」的宿主作用域。
     *
     * 用 SupervisorJob：一个子任务失败不会连带干掉其它任务。
     */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val repository: DietRepository = DietRepository(database, appScope)

    // 备份操作会等待此任务，避免首次启动的初始化与立即导入互相覆盖。
    private val initialization = appScope.async { repository.ensureSeedData() }

    val backupManager: BackupManager = BackupManager(
        database,
        context.contentResolver,
        initialization,
    )
    val releaseChecker: ReleaseChecker = ReleaseChecker()
    val appUpdateManager: AppUpdateManager = AppUpdateManager(context)
}

class WhatToEatApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

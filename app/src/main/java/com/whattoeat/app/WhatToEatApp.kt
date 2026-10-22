package com.whattoeat.app

import android.app.Application
import android.content.Context
import com.whattoeat.app.data.DietRepository
import com.whattoeat.app.data.db.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 极简「依赖注入容器」。
 *
 * 正式项目常见做法是用 Hilt，但 Hilt 需要额外的注解处理器，第一次做 Android
 * 很容易卡在构建配置上。这里用手写容器：整个 App 只有一个实例，简单、够用、看得懂。
 * 以后要换 Hilt，只需要把这个类的字段改成 @Provides 即可。
 */
class AppContainer(context: Context) {

    private val database: AppDatabase = AppDatabase.create(context)

    val repository: DietRepository = DietRepository(database)

    /** App 级别的协程作用域：用于不随页面销毁的初始化工作 */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        // 首次启动时灌入内置食物库（幂等，已有数据就跳过）
        appScope.launch { repository.ensureSeedData() }
    }
}

class WhatToEatApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

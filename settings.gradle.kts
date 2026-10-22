// Gradle 设置文件：声明去哪里下载插件/依赖，以及本项目包含哪些模块。
pluginManagement {
    repositories {
        google()            // Android 官方依赖、AGP 插件
        mavenCentral()      // Kotlin、第三方库
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    // 禁止在模块里单独声明仓库，统一在这里管理，避免版本混乱
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "WhatToEat"
include(":app")

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)          // Room 的注解处理器需要 KSP
}

android {
    namespace = "com.whattoeat.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.whattoeat.app"
        // minSdk 26 是有意选的：可以直接用 java.time.*（LocalDate），
        // 否则要额外开 coreLibraryDesugaring。Android 8.0 已覆盖绝大多数设备。
        minSdk = 26
        targetSdk = 36
        // 与 GitHub Release 的 tag 保持一致：v0.2.0 → versionName 0.2.0
        versionCode = 2
        versionName = "0.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// Kotlin 2.x 的写法：用 compilerOptions 而不是已废弃的 kotlinOptions
kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Room 把数据库表结构导出成 JSON，方便以后写迁移（升级数据库版本时对照）
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    // ---- Compose ----
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    debugImplementation(libs.compose.ui.tooling)

    // ---- AndroidX 基础 ----
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)   // collectAsStateWithLifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose) // viewModel() + 工厂 DSL

    // ---- 本地数据库 ----
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)                    // 支持 Flow 查询
    ksp(libs.androidx.room.compiler)

    // ---- 协程 ----
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)       // 版本化 ZIP 备份

    // ---- 测试 ----
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

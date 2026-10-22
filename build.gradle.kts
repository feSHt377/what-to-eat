// 根构建脚本：只负责"声明插件版本"，不在这里写具体配置。
// 具体配置都在 app/build.gradle.kts 里。
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

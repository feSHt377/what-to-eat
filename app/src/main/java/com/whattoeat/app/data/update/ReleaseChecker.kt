package com.whattoeat.app.data.update

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
)

/** 公开仓库的 Release 检查。无需登录，也不在客户端保存访问令牌。 */
class ReleaseChecker(
    private val apiUrl: String = API_URL,
    private val timeoutMillis: Int = 10_000,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun latestRelease(): GitHubRelease {
        val connection = URI(apiUrl).toURL().openConnection() as HttpURLConnection
        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "WhatToEat-Android")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.close()
                if (status == 404) {
                    // 仓库公开但还没有 Release 时 latest 也返回 404；再查公开仓库元数据区分。
                    val repositoryConnection = URI(REPOSITORY_API_URL).toURL().openConnection() as HttpURLConnection
                    try {
                        repositoryConnection.connectTimeout = timeoutMillis
                        repositoryConnection.readTimeout = timeoutMillis
                        repositoryConnection.setRequestProperty("User-Agent", "WhatToEat-Android")
                        if (repositoryConnection.responseCode == 200) {
                            throw IOException("仓库尚未发布 Release")
                        }
                    } finally {
                        repositoryConnection.disconnect()
                    }
                }
                throw IOException("GitHub 更新服务暂时不可用（HTTP $status），请确认仓库已公开并稍后再试")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val release = json.decodeFromString<GitHubRelease>(body)
            if (release.draft || release.prerelease || release.html_url.isBlank()) {
                throw IOException("Release 信息无效")
            }
            release
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val RELEASES_URL = "https://github.com/feSHt377/what-to-eat/releases"
        const val API_URL = "https://api.github.com/repos/feSHt377/what-to-eat/releases/latest"
        const val REPOSITORY_API_URL = "https://api.github.com/repos/feSHt377/what-to-eat"
    }
}

/** 按数字段比较 v0.2.0 / 0.2.0；同数字版本时正式版高于预发布版。 */
object VersionComparator {
    fun compare(first: String, second: String): Int {
        val a = parse(first)
        val b = parse(second)
        val count = maxOf(a.numbers.size, b.numbers.size)
        for (i in 0 until count) {
            val difference = (a.numbers.getOrElse(i) { 0L })
                .compareTo(b.numbers.getOrElse(i) { 0L })
            if (difference != 0) return difference
        }
        return when {
            !a.prerelease && b.prerelease -> 1
            a.prerelease && !b.prerelease -> -1
            else -> 0
        }
    }

    private data class Version(val numbers: List<Long>, val prerelease: Boolean)

    private fun parse(value: String): Version {
        val normalized = value.trim().removePrefix("v").removePrefix("V")
        val base = normalized.substringBefore('-').substringBefore('+')
        val numbers = base.split('.').map { part ->
            part.takeWhile { it.isDigit() }.toLongOrNull() ?: 0L
        }
        return Version(numbers, '-' in normalized.substringBefore('+'))
    }
}

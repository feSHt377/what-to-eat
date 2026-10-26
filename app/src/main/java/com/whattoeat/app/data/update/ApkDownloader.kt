package com.whattoeat.app.data.update

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/** 将 APK 下载到临时文件，完整校验通过后才替换旧安装包。 */
class ApkDownloader(private val timeoutMillis: Int = 20_000) {
    fun download(
        asset: GitHubAsset,
        updateDirectory: File,
        onProgress: (Int?) -> Unit = {},
    ): File {
        if (!updateDirectory.isDirectory && !updateDirectory.mkdirs()) {
            throw IOException("无法创建更新目录")
        }
        val apkFile = File(updateDirectory, APK_FILE_NAME)
        val partialFile = File(updateDirectory, "$APK_FILE_NAME.download")
        partialFile.delete()
        onProgress(null)

        val connection = URI(asset.browser_download_url).toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.setRequestProperty("Accept", "application/vnd.android.package-archive")
            connection.setRequestProperty("User-Agent", "WhatToEat-Android")
            val status = connection.responseCode
            if (status !in 200..299) {
                connection.errorStream?.close()
                throw IOException("下载安装包失败（HTTP $status）")
            }

            val expectedBytes = connection.contentLengthLong.takeIf { it >= 0L }
            connection.inputStream.buffered().use { input ->
                // Release 返回错误网页时也不应把该网页保存成 APK。
                input.mark(APK_MAGIC.size)
                val magic = ByteArray(APK_MAGIC.size)
                var magicBytes = 0
                while (magicBytes < magic.size) {
                    val count = input.read(magic, magicBytes, magic.size - magicBytes)
                    if (count < 0) break
                    magicBytes += count
                }
                input.reset()
                if (!magic.contentEquals(APK_MAGIC)) {
                    throw IOException("下载的文件不是有效的 APK 安装包")
                }

                partialFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloadedBytes = 0L
                    var lastProgress = -2
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        downloadedBytes += count
                        val progress = expectedBytes
                            ?.takeIf { it > 0L }
                            ?.let { ((downloadedBytes * 100L) / it).toInt().coerceIn(0, 99) }
                            ?: -1
                        if (progress != lastProgress) {
                            onProgress(progress.takeIf { it >= 0 })
                            lastProgress = progress
                        }
                    }
                    if (expectedBytes != null && downloadedBytes != expectedBytes) {
                        throw IOException("安装包下载不完整，请重试")
                    }
                }
            }
            if (!partialFile.renameTo(apkFile)) {
                throw IOException("无法保存安装包")
            }
            return apkFile
        } catch (error: Throwable) {
            partialFile.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val APK_FILE_NAME = "WhatToEat-update.apk"
        private val APK_MAGIC = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
    }
}

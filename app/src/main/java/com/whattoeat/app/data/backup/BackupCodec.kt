package com.whattoeat.app.data.backup

import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** 纯 JVM 的 ZIP 编解码：不依赖 Android，可直接做单测。 */
object BackupCodec {
    private const val MANIFEST_FILE = "manifest.json"
    private const val MAX_ENTRY_BYTES = 64 * 1024 * 1024
    private const val MAX_ENTRIES = 8
    private const val MAX_TOTAL_BYTES = 128 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true // 允许兼容格式相同、字段更多的备份
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    fun write(output: OutputStream, data: BackupData) {
        data.validate()
        ZipOutputStream(output.buffered()).use { zip ->
            zip.putNextEntry(ZipEntry(MANIFEST_FILE))
            zip.write(json.encodeToString(BackupManifest()).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry(BackupManifest.DATA_FILE))
            zip.write(json.encodeToString(data).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    fun read(input: InputStream): BackupData {
        var manifestBytes: ByteArray? = null
        var dataBytes: ByteArray? = null
        var entryCount = 0
        var totalBytes = 0

        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                require(++entryCount <= MAX_ENTRIES) { "备份中的文件数量异常" }
                require(!entry.isDirectory) { "备份格式不正确" }
                val bytes = readLimited(zip)
                totalBytes += bytes.size
                require(totalBytes <= MAX_TOTAL_BYTES) { "备份解压后过大" }
                when (entry.name) {
                    MANIFEST_FILE -> {
                        require(manifestBytes == null) { "备份清单重复" }
                        manifestBytes = bytes
                    }
                    BackupManifest.DATA_FILE -> {
                        require(dataBytes == null) { "备份数据重复" }
                        dataBytes = bytes
                    }
                    // 不解压到磁盘，因此额外文件不会被写入任意路径。
                }
                zip.closeEntry()
            }
        }

        val manifest = decode<BackupManifest>(
            manifestBytes ?: throw IllegalArgumentException("备份缺少 manifest.json"),
        )
        require(manifest.app == "WhatToEat") { "这不是 WhatToEat 的备份" }
        require(manifest.formatVersion == BackupData.CURRENT_FORMAT_VERSION) {
            "不支持此备份版本，请先升级 WhatToEat"
        }
        require(manifest.dataFile == BackupManifest.DATA_FILE) { "备份清单无效" }
        return decode<BackupData>(
            dataBytes ?: throw IllegalArgumentException("备份缺少 data.json"),
        ).also { it.validate() }
    }

    private fun readLimited(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_ENTRY_BYTES) { "备份文件过大" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private inline fun <reified T> decode(bytes: ByteArray): T = try {
        json.decodeFromString<T>(bytes.toString(Charsets.UTF_8))
    } catch (e: SerializationException) {
        throw IllegalArgumentException("备份内容损坏或格式不正确", e)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("备份内容损坏或格式不正确", e)
    }
}

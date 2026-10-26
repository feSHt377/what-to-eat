package com.whattoeat.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class UpdateTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `只选择HTTPS的APK附件`() {
        val release = GitHubRelease(
            tag_name = "v0.4.0",
            html_url = "https://github.com/feSHt377/what-to-eat/releases/tag/v0.4.0",
            assets = listOf(
                GitHubAsset("checksums.txt", "https://example.com/checksums.txt"),
                GitHubAsset("unsafe.apk", "http://example.com/unsafe.apk"),
                GitHubAsset("app-release.apk", "https://example.com/app-release.apk"),
            ),
        )
        assertEquals("app-release.apk", release.apkAsset()?.name)
    }

    @Test
    fun `没有APK附件时不返回下载目标`() {
        val release = GitHubRelease("v0.4.0", "https://example.com")
        assertNull(release.apkAsset())
    }

    @Test
    fun `下载APK并报告进度`() {
        withServer("/update.apk", APK_CONTENT) { server ->
            val directory = temporaryFolder.newFolder("updates")
            val progress = mutableListOf<Int?>()
            val file = ApkDownloader().download(
                asset = asset(server),
                updateDirectory = directory,
                onProgress = progress::add,
            )
            assertEquals(ApkDownloader.APK_FILE_NAME, file.name)
            assertTrue(APK_CONTENT.contentEquals(file.readBytes()))
            assertEquals(null, progress.first())
            assertTrue(progress.filterNotNull().last() < 100)
        }
    }

    @Test
    fun `非APK内容不会保存为安装包`() {
        withServer("/update.apk", "not an apk".toByteArray()) { server ->
            val directory = temporaryFolder.newFolder("updates")
            assertThrows(IOException::class.java) {
                ApkDownloader().download(asset(server), directory)
            }
            assertTrue(directory.listFiles()?.isEmpty() == true)
        }
    }

    @Test
    fun `HTTP错误不会留下安装包`() {
        withServer("/update.apk", APK_CONTENT, status = 404) { server ->
            val directory = temporaryFolder.newFolder("updates")
            assertThrows(IOException::class.java) {
                ApkDownloader().download(asset(server), directory)
            }
            assertTrue(directory.listFiles()?.isEmpty() == true)
        }
    }

    private fun asset(server: ServerSocket) = GitHubAsset(
        name = "app-release.apk",
        browser_download_url = "http://127.0.0.1:${server.localPort}/update.apk",
    )

    /** 只用 JDK 标准 socket，Android 测试类路径不需要额外 HTTP 服务器依赖。 */
    private fun withServer(
        path: String,
        content: ByteArray,
        status: Int = 200,
        block: (ServerSocket) -> Unit,
    ) {
        ServerSocket(0).use { server ->
            val thread = thread {
                server.accept().use { socket ->
                    val request = socket.getInputStream().bufferedReader()
                    assertTrue(request.readLine().startsWith("GET $path "))
                    var header = request.readLine()
                    while (!header.isNullOrEmpty()) header = request.readLine()
                    val output = socket.getOutputStream()
                    output.write("HTTP/1.1 $status Test\r\nContent-Length: ${content.size}\r\nConnection: close\r\n\r\n".toByteArray())
                    output.write(content)
                    output.flush()
                }
            }
            block(server)
            thread.join(5_000)
        }
    }

    companion object {
        private val APK_CONTENT = byteArrayOf(0x50, 0x4b, 0x03, 0x04) +
            "test apk payload".toByteArray(StandardCharsets.UTF_8)
    }
}

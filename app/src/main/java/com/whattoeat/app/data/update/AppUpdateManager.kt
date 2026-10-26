package com.whattoeat.app.data.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException

/** 下载 GitHub Release 的 APK，并通过 FileProvider 交给系统安装器。 */
class AppUpdateManager(
    private val context: Context,
    private val downloader: ApkDownloader = ApkDownloader(),
) {

    class InstallPermissionRequiredException : IOException("需要允许安装未知来源应用")

    fun downloadApk(
        asset: GitHubAsset,
        onProgress: (Int?) -> Unit,
    ): File {
        return downloader.download(
            asset = asset,
            updateDirectory = File(context.filesDir, "updates"),
            onProgress = onProgress,
        )
    }

    /** 系统会显示安装确认界面，并负责校验 APK 签名及版本。 */
    fun installDownloadedApk() {
        if (!context.packageManager.canRequestPackageInstalls()) {
            throw InstallPermissionRequiredException()
        }
        val apkFile = File(File(context.filesDir, "updates"), ApkDownloader.APK_FILE_NAME)
        if (!apkFile.isFile) throw IOException("安装包不存在，请重新下载")

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            throw IOException("设备上没有可用的 APK 安装器")
        }
    }

    companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

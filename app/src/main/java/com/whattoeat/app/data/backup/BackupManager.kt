package com.whattoeat.app.data.backup

import android.content.ContentResolver
import android.net.Uri
import androidx.room.withTransaction
import com.whattoeat.app.BuildConfig
import com.whattoeat.app.data.db.AppDatabase
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.time.Instant

/** 只负责备份；页面通过 SAF 文件选择器提供 Uri，不需要存储权限。 */
class BackupManager(
    private val db: AppDatabase,
    private val contentResolver: ContentResolver,
    private val initialization: Deferred<Unit>,
) {
    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        initialization.await()
        // 先完整读完四张表，再开始写用户选择的文件，避免查询失败留下空备份。
        val data = db.withTransaction {
            BackupData(
                appVersion = BuildConfig.VERSION_NAME,
                exportedAt = Instant.now().toString(),
                profile = requireNotNull(db.profileDao().get()) { "档案尚未准备好，请稍后重试" },
                foods = db.foodDao().snapshot(),
                entries = db.logDao().snapshot(),
                weights = db.weightDao().snapshot(),
            )
        }
        val stream = contentResolver.openOutputStream(uri, "wt")
            ?: throw IOException("无法写入所选文件")
        stream.use { BackupCodec.write(it, data) }
    }

    suspend fun importFrom(uri: Uri) = withContext(Dispatchers.IO) {
        initialization.await()
        // 在任何 DELETE 之前读完并验证整个 ZIP；损坏文件不会破坏现有数据。
        val stream = contentResolver.openInputStream(uri)
            ?: throw IOException("无法读取所选文件")
        val data = stream.use { BackupCodec.read(it) }

        db.withTransaction {
            // 明确采用整包恢复，避免不同备份按名称合并产生重复或不一致。
            db.logDao().deleteAll()
            db.weightDao().deleteAll()
            db.foodDao().deleteAll()
            db.profileDao().deleteAll()

            db.foodDao().insertAll(data.foods)
            db.logDao().insertAll(data.entries)
            data.weights.forEach { db.weightDao().upsert(it) }
            db.profileDao().upsert(data.profile)
        }
    }
}

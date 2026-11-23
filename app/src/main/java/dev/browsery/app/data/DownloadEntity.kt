package dev.browsery.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Catatan unduhan (hanya dari tab biasa; tab privat tidak pernah menyimpan unduhan). */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val referer: String?,
    val fileName: String,
    val mimeType: String?,
    val state: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val etag: String?,
    val lastModified: String?,
    val acceptRanges: Boolean,
    val chunks: String?,
    val error: String?,
    val filePath: String?,
    val contentUri: String?,
    val createdAt: Long,
)

/** Metode non-suspend dipanggil hanya dari thread latar (DownloadManager memakai Dispatchers.IO). */
@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    fun get(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state IN ('QUEUED','RUNNING')")
    fun active(): List<DownloadEntity>

    @Query("SELECT * FROM downloads WHERE state IN ('COMPLETED','FAILED')")
    fun finished(): List<DownloadEntity>

    @Insert
    fun insert(e: DownloadEntity): Long

    @Update
    fun update(e: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM downloads WHERE state IN ('COMPLETED','FAILED')")
    fun clearFinished()
}

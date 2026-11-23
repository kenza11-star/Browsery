package dev.browsery.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versi 2 (v1 -> v2: tabel `downloads`). Perubahan skema berikutnya WAJIB lewat Migration
 * (tidak memakai fallbackToDestructiveMigration agar data pengguna terjaga).
 */
@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        ShortcutEntity::class,
        SavedTabEntity::class,
        DownloadEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun savedTabDao(): SavedTabDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `downloads` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`url` TEXT NOT NULL, " +
                        "`referer` TEXT, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`mimeType` TEXT, " +
                        "`state` TEXT NOT NULL, " +
                        "`totalBytes` INTEGER NOT NULL, " +
                        "`downloadedBytes` INTEGER NOT NULL, " +
                        "`etag` TEXT, " +
                        "`lastModified` TEXT, " +
                        "`acceptRanges` INTEGER NOT NULL, " +
                        "`chunks` TEXT, " +
                        "`error` TEXT, " +
                        "`filePath` TEXT, " +
                        "`contentUri` TEXT, " +
                        "`createdAt` INTEGER NOT NULL)",
                )
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "browsery.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}

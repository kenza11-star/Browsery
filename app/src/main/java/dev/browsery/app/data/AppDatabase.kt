package dev.browsery.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Versi 1. Perubahan skema berikutnya WAJIB lewat Migration (tidak memakai
 * fallbackToDestructiveMigration agar data pengguna terjaga).
 */
@Database(
    entities = [
        BookmarkEntity::class,
        HistoryEntity::class,
        ShortcutEntity::class,
        SavedTabEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun savedTabDao(): SavedTabDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "browsery.db",
                ).build().also { instance = it }
            }
    }
}

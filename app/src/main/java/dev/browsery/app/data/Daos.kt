package dev.browsery.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query(
        "SELECT * FROM bookmarks WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' " +
            "ORDER BY createdAt DESC",
    )
    fun observe(q: String): Flow<List<BookmarkEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE url = :url)")
    fun isBookmarked(url: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM bookmarks")
    suspend fun deleteAll()
}

@Dao
interface HistoryDao {
    @Query(
        "SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' " +
            "ORDER BY visitedAt DESC LIMIT 500",
    )
    fun observe(q: String): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAll()
}

@Dao
interface ShortcutDao {
    @Query("SELECT * FROM shortcuts ORDER BY position, id")
    fun observeAll(): Flow<List<ShortcutEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM shortcuts")
    suspend fun nextPosition(): Int

    @Insert
    suspend fun insert(shortcut: ShortcutEntity): Long

    @Update
    suspend fun update(shortcut: ShortcutEntity)

    @Delete
    suspend fun delete(shortcut: ShortcutEntity)
}

@Dao
abstract class SavedTabDao {
    @Query("SELECT * FROM saved_tabs ORDER BY position")
    abstract suspend fun getAll(): List<SavedTabEntity>

    @Query("DELETE FROM saved_tabs")
    abstract suspend fun clear()

    @Insert
    abstract suspend fun insertAll(tabs: List<SavedTabEntity>)

    @Transaction
    open suspend fun replaceAll(tabs: List<SavedTabEntity>) {
        clear()
        insertAll(tabs)
    }
}

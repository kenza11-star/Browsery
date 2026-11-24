package dev.browsery.app.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index(value = ["url"], unique = true)])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val createdAt: Long,
)

@Entity(tableName = "history", indices = [Index(value = ["visitedAt"]), Index(value = ["url"])])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val position: Int,
)

/** Hanya tab NORMAL yang pernah disimpan. Tab privat tidak ditulis ke disk. */
@Entity(tableName = "saved_tabs")
data class SavedTabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val position: Int,
    val isActive: Boolean,
)

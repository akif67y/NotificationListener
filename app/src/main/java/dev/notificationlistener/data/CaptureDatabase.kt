package dev.notificationlistener.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class CapturedNotification(
    val id: Long,
    val notificationKey: String,
    val packageName: String,
    val postedAt: Long,
    val title: String?,
    val text: String?,
    val bigText: String?,
    val textLines: String?,
    val structuredMessages: String?,
    val conversationTitle: String?,
    val isGroupSummary: Boolean,
    val capturedAt: Long
)

class CaptureDatabase(context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE captures (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                notification_key TEXT NOT NULL,
                package_name TEXT NOT NULL,
                posted_at INTEGER NOT NULL,
                title TEXT,
                text TEXT,
                big_text TEXT,
                text_lines TEXT,
                structured_messages TEXT,
                conversation_title TEXT,
                is_group_summary INTEGER NOT NULL,
                captured_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX captures_posted_at ON captures(posted_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(capture: CapturedNotification) {
        val values = ContentValues().apply {
            put("notification_key", capture.notificationKey)
            put("package_name", capture.packageName)
            put("posted_at", capture.postedAt)
            put("title", capture.title)
            put("text", capture.text)
            put("big_text", capture.bigText)
            put("text_lines", capture.textLines)
            put("structured_messages", capture.structuredMessages)
            put("conversation_title", capture.conversationTitle)
            put("is_group_summary", if (capture.isGroupSummary) 1 else 0)
            put("captured_at", capture.capturedAt)
        }
        writableDatabase.insertOrThrow("captures", null, values)
    }

    fun recent(limit: Int = 100): List<CapturedNotification> {
        val safeLimit = limit.coerceIn(1, 500).toString()
        return readableDatabase.query(
            "captures",
            null,
            null,
            null,
            null,
            null,
            "posted_at DESC, id DESC",
            safeLimit
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        CapturedNotification(
                            id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            notificationKey = cursor.getString(cursor.getColumnIndexOrThrow("notification_key")),
                            packageName = cursor.getString(cursor.getColumnIndexOrThrow("package_name")),
                            postedAt = cursor.getLong(cursor.getColumnIndexOrThrow("posted_at")),
                            title = cursor.nullableString("title"),
                            text = cursor.nullableString("text"),
                            bigText = cursor.nullableString("big_text"),
                            textLines = cursor.nullableString("text_lines"),
                            structuredMessages = cursor.nullableString("structured_messages"),
                            conversationTitle = cursor.nullableString("conversation_title"),
                            isGroupSummary = cursor.getInt(cursor.getColumnIndexOrThrow("is_group_summary")) == 1,
                            capturedAt = cursor.getLong(cursor.getColumnIndexOrThrow("captured_at"))
                        )
                    )
                }
            }
        }
    }

    fun deleteAll() {
        writableDatabase.delete("captures", null, null)
    }

    fun removeExpired(retentionDays: Int = 7) {
        val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1000L
        writableDatabase.delete("captures", "captured_at < ?", arrayOf(cutoff.toString()))
    }

    companion object {
        private const val DATABASE_NAME = "notification_captures.db"
        private const val DATABASE_VERSION = 1
    }
}

private fun android.database.Cursor.nullableString(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

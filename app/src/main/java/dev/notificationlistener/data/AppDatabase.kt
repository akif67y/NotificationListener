package dev.notificationlistener.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        NotificationSnapshotEntity::class,
        ConversationEntity::class,
        MessageEntity::class,
        CourseEntity::class,
        AcademicSignalEntity::class,
        AcademicEventEntity::class,
        EventRevisionEntity::class
    ],
    version = 4,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): AppDao

    companion object {
        private const val DATABASE_NAME = "notification_captures.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .addMigrations(MIGRATION_1_2)
                .addMigrations(MIGRATION_2_3)
                .addMigrations(MIGRATION_3_4)
                .build()
                .also { instance = it }
        }

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // V0 used SQLiteOpenHelper and did not explicitly mark its INTEGER
                // primary key NOT NULL. Room validates that constraint, so rebuild the
                // legacy table and copy every raw snapshot before introducing relations.
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `captures_room` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `notification_key` TEXT NOT NULL,
                        `package_name` TEXT NOT NULL,
                        `posted_at` INTEGER NOT NULL,
                        `title` TEXT,
                        `text` TEXT,
                        `big_text` TEXT,
                        `text_lines` TEXT,
                        `structured_messages` TEXT,
                        `conversation_title` TEXT,
                        `is_group_summary` INTEGER NOT NULL,
                        `captured_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO `captures_room` (
                        `id`, `notification_key`, `package_name`, `posted_at`,
                        `title`, `text`, `big_text`, `text_lines`,
                        `structured_messages`, `conversation_title`,
                        `is_group_summary`, `captured_at`
                    )
                    SELECT `id`, `notification_key`, `package_name`, `posted_at`,
                           `title`, `text`, `big_text`, `text_lines`,
                           `structured_messages`, `conversation_title`,
                           `is_group_summary`, `captured_at`
                    FROM `captures`
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE `captures`")
                db.execSQL("ALTER TABLE `captures_room` RENAME TO `captures`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `captures_posted_at` ON `captures` (`posted_at`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `courses` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `code` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `section` TEXT NOT NULL,
                        `aliases` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_courses_code` ON `courses` (`code`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `conversations` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `platform` TEXT NOT NULL,
                        `external_key` TEXT NOT NULL,
                        `display_name` TEXT NOT NULL,
                        `enabled` INTEGER NOT NULL,
                        `default_course_id` INTEGER,
                        `last_seen` INTEGER NOT NULL,
                        FOREIGN KEY(`default_course_id`) REFERENCES `courses`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_conversations_platform_external_key` ON `conversations` (`platform`, `external_key`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_conversations_default_course_id` ON `conversations` (`default_course_id`)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `messages` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `snapshot_id` INTEGER NOT NULL,
                        `conversation_id` INTEGER NOT NULL,
                        `platform` TEXT NOT NULL,
                        `sender` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `message_timestamp` INTEGER NOT NULL,
                        `captured_at` INTEGER NOT NULL,
                        `fingerprint` TEXT NOT NULL,
                        FOREIGN KEY(`snapshot_id`) REFERENCES `captures`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_messages_fingerprint` ON `messages` (`fingerprint`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_snapshot_id` ON `messages` (`snapshot_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_conversation_id` ON `messages` (`conversation_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_messages_message_timestamp` ON `messages` (`message_timestamp`)")
                // Existing V0 snapshots remain available. Only newly arriving snapshots are
                // normalized because legacy rows do not reliably preserve message boundaries.
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `local_category` TEXT NOT NULL DEFAULT 'IRRELEVANT'")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `relevance_score` REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE `messages` ADD COLUMN `analysis_state` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `academic_signals` (
                        `id` TEXT NOT NULL,
                        `conversation_id` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `course_code` TEXT,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `urgency` TEXT NOT NULL,
                        `should_notify` INTEGER NOT NULL,
                        `evidence_message_ids` TEXT NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_signals_conversation_id` ON `academic_signals` (`conversation_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_signals_created_at` ON `academic_signals` (`created_at`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `academic_events` (
                        `event_key` TEXT NOT NULL,
                        `conversation_id` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `course_code` TEXT,
                        `title` TEXT NOT NULL,
                        `summary` TEXT NOT NULL,
                        `starts_at` INTEGER,
                        `due_at` INTEGER,
                        `location` TEXT,
                        `details` TEXT,
                        `status` TEXT NOT NULL,
                        `confidence` REAL NOT NULL,
                        `urgency` TEXT NOT NULL,
                        `should_notify` INTEGER NOT NULL,
                        `evidence_message_ids` TEXT NOT NULL,
                        `last_signal_id` TEXT NOT NULL,
                        `archived` INTEGER NOT NULL,
                        `created_at` INTEGER NOT NULL,
                        `updated_at` INTEGER NOT NULL,
                        PRIMARY KEY(`event_key`),
                        FOREIGN KEY(`conversation_id`) REFERENCES `conversations`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_events_conversation_id` ON `academic_events` (`conversation_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_events_course_code` ON `academic_events` (`course_code`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_events_starts_at` ON `academic_events` (`starts_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_events_due_at` ON `academic_events` (`due_at`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_academic_events_updated_at` ON `academic_events` (`updated_at`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `event_revisions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `event_key` TEXT NOT NULL,
                        `signal_id` TEXT NOT NULL,
                        `action` TEXT NOT NULL,
                        `change_summary` TEXT,
                        `created_at` INTEGER NOT NULL,
                        FOREIGN KEY(`event_key`) REFERENCES `academic_events`(`event_key`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_revisions_event_key` ON `event_revisions` (`event_key`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_event_revisions_signal_id` ON `event_revisions` (`signal_id`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_event_revisions_created_at` ON `event_revisions` (`created_at`)")
            }
        }
    }
}

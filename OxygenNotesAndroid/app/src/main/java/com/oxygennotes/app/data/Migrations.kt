package com.oxygennotes.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit migrations for AppDatabase.
 *
 * Versions 1 and 2 were development-only and their exact schemas were not
 * preserved. These migrations perform a clean recreation of both tables so
 * that old installs can upgrade without a crash. Data from pre-v3 installs
 * is not retained (same behaviour as the former fallbackToDestructiveMigration,
 * but explicit and auditable).
 *
 * ACCEPTED: Data loss on v1/v2 → v3 is intentional for development builds
 * where no real user data existed. No production users were on those versions.
 *
 * From version 3 onwards every migration MUST preserve existing data.
 * Use ALTER TABLE / temp-table copy pattern — never DROP the live table.
 */

private fun SupportSQLiteDatabase.recreateTables() {
    // Drop old tables (order matters: notes references folders)
    execSQL("DROP TABLE IF EXISTS `notes`")
    execSQL("DROP TABLE IF EXISTS `folders`")

    execSQL("""
        CREATE TABLE IF NOT EXISTS `folders` (
            `id`        INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `name`      TEXT    NOT NULL,
            `parentId`  INTEGER,
            `sortOrder` INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(`parentId`) REFERENCES `folders`(`id`) ON DELETE CASCADE
        )
    """.trimIndent())
    execSQL("CREATE INDEX IF NOT EXISTS `index_folders_parentId` ON `folders` (`parentId`)")

    execSQL("""
        CREATE TABLE IF NOT EXISTS `notes` (
            `id`          INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `title`       TEXT    NOT NULL,
            `content`     TEXT    NOT NULL,
            `folderId`    INTEGER,
            `isPinned`    INTEGER NOT NULL DEFAULT 0,
            `isFullWidth` INTEGER NOT NULL DEFAULT 0,
            `createdAt`   INTEGER NOT NULL,
            `modifiedAt`  INTEGER NOT NULL,
            FOREIGN KEY(`folderId`) REFERENCES `folders`(`id`) ON DELETE SET NULL
        )
    """.trimIndent())
    execSQL("CREATE INDEX IF NOT EXISTS `index_notes_folderId` ON `notes` (`folderId`)")
}

/** v1 → v3: full recreation (v1 schema was not documented). */
val MIGRATION_1_3 = object : Migration(1, 3) {
    override fun migrate(db: SupportSQLiteDatabase) = db.recreateTables()
}

/** v2 → v3: full recreation (v2 schema was not documented). */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) = db.recreateTables()
}

/** v3 → v4: add sortOrder to notes; initialise from createdAt so existing order is preserved. */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `notes` ADD COLUMN `sortOrder` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("UPDATE `notes` SET `sortOrder` = `createdAt`")
    }
}

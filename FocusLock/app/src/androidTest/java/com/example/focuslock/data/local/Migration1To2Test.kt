package com.example.focuslock.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration1To2Test {

    private val testDbName = "migration-test-focuslock.db"

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FocusDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migrate1To2_preservesSavedContentsWithDefaultOpenCountAndCreatesDailyUsageTable() {
        // 1. Création de la base en version 1 et insertion d'un post sauvegardé
        helper.createDatabase(testDbName, 1).apply {
            execSQL(
                """
                INSERT INTO `saved_contents` (
                    `id`, `sourceUrl`, `extractedCaption`, `userTitle`, `personalNote`,
                    `tag`, `savedAtTimestamp`, `isReviewed`, `isFavorite`,
                    `thumbnailLocalPath`, `mediaLocalPath`
                ) VALUES (
                    'post-v1',
                    'https://www.instagram.com/reel/Abc123/',
                    'Légende originale',
                    'Mobilité épaules',
                    'Faire 3 séries',
                    'Sport',
                    1762600000000,
                    0,
                    1,
                    NULL,
                    NULL
                )
                """.trimIndent()
            )
            close()
        }

        // 2. Exécution de MIGRATION_1_2 et validation du schéma v2
        val dbV2 = helper.runMigrationsAndValidate(
            testDbName,
            2,
            true,
            FocusDatabase.MIGRATION_1_2
        )

        // 3. Vérification que la ligne v1 est intacte et que openCount vaut 0
        dbV2.query("SELECT id, userTitle, tag, isFavorite, openCount FROM saved_contents WHERE id = 'post-v1'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("post-v1", cursor.getString(0))
                assertEquals("Mobilité épaules", cursor.getString(1))
                assertEquals("Sport", cursor.getString(2))
                assertEquals(1, cursor.getInt(3))
                assertEquals(0, cursor.getInt(4)) // openCount = 0 par défaut
            }

        // 4. Vérification que la nouvelle table daily_usage accepte les écritures
        dbV2.execSQL(
            "INSERT INTO `daily_usage` (`epochDay`, `packageName`, `totalMs`) VALUES (20768, 'com.instagram.android', 900000)"
        )
        dbV2.query("SELECT totalMs FROM daily_usage WHERE epochDay = 20768 AND packageName = 'com.instagram.android'")
            .use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(900_000L, cursor.getLong(0))
            }

        dbV2.close()
    }
}

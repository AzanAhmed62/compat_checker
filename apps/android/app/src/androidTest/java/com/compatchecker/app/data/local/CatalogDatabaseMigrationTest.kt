package com.compatchecker.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Executes the Room 1-to-2 migration on a real Android runtime. Schema 1
 * contains the original cached product/detail tables; schema 2 adds the brand
 * slug and the offline guide cache.
 */
@RunWith(AndroidJUnit4::class)
class CatalogDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        CompatDatabase::class.java,
    )

    @Test
    fun migrate1To2PreservesCachedProductsAndAddsGuideTables() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                "INSERT INTO cached_products " +
                    "(id, name, brandName, categoryName, categorySlug, " +
                    "modelNumber, summary, imagePath, ruleCount, cachedAt) " +
                    "VALUES ('product-1', 'Fixture', 'Brand', 'Category', " +
                    "'category', NULL, NULL, NULL, 1, 1234)",
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            CompatDatabase.MIGRATION_1_2,
        )
        try {
            database.query("SELECT id, brandSlug FROM cached_products").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("product-1", cursor.getString(0))
                assertEquals("", cursor.getString(1))
            }
            database.query("SELECT COUNT(*) FROM cached_guides").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
            database.query("SELECT COUNT(*) FROM cached_guide_details").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            database.close()
        }
    }

    private companion object {
        const val TEST_DB = "catalog-migration-test"
    }
}

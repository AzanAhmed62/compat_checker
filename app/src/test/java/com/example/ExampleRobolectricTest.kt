package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.compatchecker.data.local.CompatDatabase
import com.example.compatchecker.data.local.ProductEntity
import com.example.compatchecker.domain.model.Product
import com.example.compatchecker.domain.model.ProductCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var database: CompatDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            CompatDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Compat Checker", appName)
    }

    @Test
    fun `room database inserts and queries product entity`() = runBlocking {
        val testProduct = Product(
            id = "test_s24u",
            name = "Samsung Galaxy S24 Ultra",
            brand = "Samsung",
            modelNumber = "SM-S928B",
            category = ProductCategory.SMARTPHONE,
            releaseYear = 2024,
            formFactor = "Titanium",
            connectorType = "USB-C 3.2",
            summary = "Test device",
            technicalNotes = "Notes"
        )

        database.productDao().insertProducts(listOf(ProductEntity.fromDomain(testProduct)))
        val retrieved = database.productDao().getProductByIdDirect("test_s24u")

        assertNotNull(retrieved)
        assertEquals("Samsung Galaxy S24 Ultra", retrieved?.name)
        assertEquals("SM-S928B", retrieved?.modelNumber)
    }
}

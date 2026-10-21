package com.example

import com.example.compatchecker.data.remote.DemoCatalogDataSource
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.ConfidenceLevel
import com.example.compatchecker.domain.model.ProductCategory
import com.example.compatchecker.domain.model.RiskLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityEvaluationUnitTest {

    @Test
    fun `demo catalog contains valid smartphones and repair components`() {
        val products = DemoCatalogDataSource.demoProducts
        assertTrue("Catalog should contain multiple items", products.size >= 5)

        val smartphones = products.filter { it.category == ProductCategory.SMARTPHONE }
        val batteries = products.filter { it.category == ProductCategory.BATTERY }
        val displays = products.filter { it.category == ProductCategory.REPLACEMENT_DISPLAY }

        assertTrue(smartphones.isNotEmpty())
        assertTrue(batteries.isNotEmpty())
        assertTrue(displays.isNotEmpty())
    }

    @Test
    fun `compatibility rules include explicit evidence citations`() {
        val rules = DemoCatalogDataSource.demoRules
        assertTrue(rules.isNotEmpty())

        val s24DisplayRule = rules.find { it.id == "rule_s24u_disp_oem" }
        assertNotNull(s24DisplayRule)
        assertEquals(CompatibilityStatus.COMPATIBLE, s24DisplayRule?.status)
        assertEquals(ConfidenceLevel.HIGH_VERIFIED, s24DisplayRule?.confidence)
        assertTrue(s24DisplayRule?.evidenceSources?.isNotEmpty() == true)

        val s23ToS24IncompatibleRule = rules.find { it.id == "rule_s23u_disp_on_s24u" }
        assertNotNull(s23ToS24IncompatibleRule)
        assertEquals(CompatibilityStatus.INCOMPATIBLE, s23ToS24IncompatibleRule?.status)
        assertEquals(RiskLevel.HIGH_SERIALIZED_LOCKOUT, s23ToS24IncompatibleRule?.riskLevel)

        val iphone15BatteryRule = rules.find { it.id == "rule_ip15_batt_oem" }
        assertNotNull(iphone15BatteryRule)
        assertEquals(CompatibilityStatus.CONDITIONAL, iphone15BatteryRule?.status)
        assertEquals(RiskLevel.MODERATE, iphone15BatteryRule?.riskLevel)
    }

    @Test
    fun `search filtering matches model numbers and aliases`() {
        val products = DemoCatalogDataSource.demoProducts

        // Query by model number
        val modelMatches = products.filter { it.modelNumber.contains("SM-S928B", ignoreCase = true) }
        assertEquals(1, modelMatches.size)
        assertEquals("Samsung Galaxy S24 Ultra", modelMatches.first().name)

        // Query by alias
        val aliasMatches = products.filter { it.aliases.any { a -> a.contains("Husky", ignoreCase = true) } }
        assertEquals(1, aliasMatches.size)
        assertEquals("Google Pixel 8 Pro", aliasMatches.first().name)
    }
}

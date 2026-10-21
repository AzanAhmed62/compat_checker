package com.compatchecker.app.ui.product

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionPatchTest {
    @Test
    fun `patch includes trimmed non-blank fields`() {
        val patch = buildCorrectionPatch(
            name = "  Aurora X1 ",
            summary = " Revised summary ",
            notes = " Connector pin 4 differs ",
        )

        assertEquals("Aurora X1", patch["name"]?.jsonPrimitive?.content)
        assertEquals("Revised summary", patch["summary"]?.jsonPrimitive?.content)
        assertEquals("Connector pin 4 differs", patch["technical_notes"]?.jsonPrimitive?.content)
    }

    @Test
    fun `patch omits blank fields`() {
        val patch = buildCorrectionPatch(name = "Aurora X2", summary = "  ", notes = "")

        assertTrue(patch.containsKey("name"))
        assertFalse(patch.containsKey("summary"))
        assertFalse(patch.containsKey("technical_notes"))
        assertEquals(JsonPrimitive("Aurora X2"), patch["name"])
    }
}

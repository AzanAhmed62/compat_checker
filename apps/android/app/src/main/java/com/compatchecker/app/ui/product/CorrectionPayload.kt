package com.compatchecker.app.ui.product

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds the server-validated product_edit patch without sending blank fields. */
internal fun buildCorrectionPatch(
    name: String,
    summary: String,
    notes: String,
): JsonObject = buildJsonObject {
    if (name.isNotBlank()) put("name", name.trim())
    if (summary.isNotBlank()) put("summary", summary.trim())
    if (notes.isNotBlank()) put("technical_notes", notes.trim())
}

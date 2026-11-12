package com.compatchecker.app.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.Brand
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.ui.difficultyLabel
import kotlinx.serialization.json.JsonObject

private val evidenceTypeOptions = listOf(
    "oem_document", "service_bulletin", "teardown", "lab_test", "community_report", "other",
)
private val relationshipOptions = listOf(
    "fits", "works_with", "same_as", "variant_of", "not_compatible_with",
)
private val difficultyOptions = listOf("easy", "moderate", "difficult", "expert")

@Composable
private fun PickerField(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    selected.ifBlank { stringResource(R.string.contribution_choose) },
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DialogError(message: String?) {
    if (!message.isNullOrBlank()) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun DialogBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 480.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

@Composable
fun NewProductContributionDialog(
    brands: List<Brand>,
    categories: List<Category>,
    busy: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (JsonObject) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var slug by remember { mutableStateOf("") }
    var slugTouched by remember { mutableStateOf(false) }
    var brandId by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf("") }
    var modelNumber by remember { mutableStateOf("") }
    var releaseYear by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var aliases by remember { mutableStateOf("") }
    var specLabel by remember { mutableStateOf("") }
    var specValue by remember { mutableStateOf("") }
    var specUnit by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.contribution_new_product)) },
        text = {
            DialogBody {
                OutlinedTextField(
                    name,
                    {
                        name = it
                        if (!slugTouched) slug = slugify(it)
                    },
                    label = { Text(stringResource(R.string.contribution_product_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    slug,
                    {
                        slug = it
                        slugTouched = true
                    },
                    label = { Text(stringResource(R.string.contribution_slug)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    label = stringResource(R.string.contribution_brand),
                    selected = brands.firstOrNull { it.id == brandId }?.name.orEmpty(),
                    options = brands.map { it.id to it.name },
                    onSelect = { brandId = it },
                )
                PickerField(
                    label = stringResource(R.string.contribution_category),
                    selected = categories.firstOrNull { it.id == categoryId }?.name.orEmpty(),
                    options = categories.map { it.id to it.name },
                    onSelect = { categoryId = it },
                )
                OutlinedTextField(
                    modelNumber,
                    { modelNumber = it },
                    label = { Text(stringResource(R.string.contribution_model_number)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    releaseYear,
                    { releaseYear = it.filter(Char::isDigit).take(4) },
                    label = { Text(stringResource(R.string.contribution_release_year)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    summary,
                    { summary = it.take(2000) },
                    label = { Text(stringResource(R.string.contribution_summary)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    notes,
                    { notes = it.take(8000) },
                    label = { Text(stringResource(R.string.contribution_technical_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    aliases,
                    { aliases = it },
                    label = { Text(stringResource(R.string.contribution_aliases)) },
                    supportingText = { Text(stringResource(R.string.contribution_aliases_help)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    stringResource(R.string.contribution_optional_spec),
                    style = MaterialTheme.typography.labelMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        specLabel,
                        { specLabel = it },
                        label = { Text(stringResource(R.string.contribution_spec_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        specValue,
                        { specValue = it },
                        label = { Text(stringResource(R.string.contribution_spec_value)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(
                    specUnit,
                    { specUnit = it },
                    label = { Text(stringResource(R.string.contribution_spec_unit)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                DialogError(localError ?: serverError)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    localError = null
                    runCatching {
                        buildNewProductPayload(
                            name = name,
                            slug = slug,
                            brandId = brandId,
                            categoryId = categoryId,
                            modelNumber = modelNumber,
                            releaseYear = releaseYear.toIntOrNull(),
                            summary = summary,
                            technicalNotes = notes,
                            connectorType = null,
                            formFactor = null,
                            aliases = aliases.split(',').map(String::trim).filter(String::isNotBlank),
                            specs = if (specLabel.isBlank() && specValue.isBlank()) {
                                emptyList()
                            } else {
                                listOf(SpecDraft(specLabel, specValue, specUnit))
                            },
                        )
                    }
                        .onSuccess(onSubmit)
                        .onFailure { localError = it.message }
                },
            ) { Text(stringResource(R.string.action_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun NewRuleContributionDialog(
    products: List<ProductSummary>,
    busy: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (JsonObject) -> Unit,
) {
    var subjectId by remember { mutableStateOf("") }
    var objectId by remember { mutableStateOf("") }
    var relationship by remember { mutableStateOf("fits") }
    var confidence by remember { mutableStateOf("50") }
    var summary by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var modifications by remember { mutableStateOf("") }
    var evidenceTitle by remember { mutableStateOf("") }
    var evidenceUrl by remember { mutableStateOf("") }
    var evidenceType by remember { mutableStateOf("oem_document") }
    var publisher by remember { mutableStateOf("") }
    var excerpt by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.contribution_new_rule)) },
        text = {
            DialogBody {
                Text(
                    stringResource(R.string.contribution_rule_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PickerField(
                    label = stringResource(R.string.contribution_subject_product),
                    selected = products.firstOrNull { it.id == subjectId }?.name.orEmpty(),
                    options = products.map { it.id to "${it.name} (${it.modelNumber ?: it.brandName})" },
                    onSelect = { subjectId = it },
                )
                PickerField(
                    label = stringResource(R.string.contribution_object_product),
                    selected = products.firstOrNull { it.id == objectId }?.name.orEmpty(),
                    options = products.map { it.id to "${it.name} (${it.modelNumber ?: it.brandName})" },
                    onSelect = { objectId = it },
                )
                PickerField(
                    label = stringResource(R.string.contribution_relationship),
                    selected = relationshipLabelForForm(relationship),
                    options = relationshipOptions.map { it to relationshipLabelForForm(it) },
                    onSelect = { relationship = it },
                )
                OutlinedTextField(
                    confidence,
                    { confidence = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.contribution_confidence)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    summary,
                    { summary = it.take(300) },
                    label = { Text(stringResource(R.string.contribution_summary)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    details,
                    { details = it.take(8000) },
                    label = { Text(stringResource(R.string.contribution_technical_notes)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    modifications,
                    { modifications = it.take(2000) },
                    label = { Text(stringResource(R.string.contribution_required_modifications)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                EvidenceInputFields(
                    title = evidenceTitle,
                    onTitle = { evidenceTitle = it },
                    url = evidenceUrl,
                    onUrl = { evidenceUrl = it },
                    type = evidenceType,
                    onType = { evidenceType = it },
                    publisher = publisher,
                    onPublisher = { publisher = it },
                    excerpt = excerpt,
                    onExcerpt = { excerpt = it },
                )
                DialogError(localError ?: serverError)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    localError = null
                    runCatching {
                        buildNewRulePayload(
                            subjectProductId = subjectId,
                            objectProductId = objectId,
                            relationship = relationship,
                            confidence = confidence.toIntOrNull() ?: -1,
                            summary = summary,
                            technicalDetails = details,
                            requiredModifications = modifications,
                            evidence = listOf(
                                EvidenceDraft(evidenceType, evidenceTitle, evidenceUrl, publisher, excerpt),
                            ),
                        )
                    }
                        .onSuccess(onSubmit)
                        .onFailure { localError = it.message }
                },
            ) { Text(stringResource(R.string.action_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
fun EvidenceContributionDialog(
    ruleId: String,
    busy: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (EvidenceDraft) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("oem_document") }
    var publisher by remember { mutableStateOf("") }
    var excerpt by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.contribution_evidence)) },
        text = {
            DialogBody {
                Text(
                    stringResource(R.string.contribution_evidence_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                EvidenceInputFields(
                    title = title,
                    onTitle = { title = it },
                    url = url,
                    onUrl = { url = it },
                    type = type,
                    onType = { type = it },
                    publisher = publisher,
                    onPublisher = { publisher = it },
                    excerpt = excerpt,
                    onExcerpt = { excerpt = it },
                )
                DialogError(localError ?: serverError)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    localError = null
                    val draft = EvidenceDraft(type, title, url, publisher, excerpt)
                    runCatching { buildEvidencePayload(ruleId, draft) }
                        .onSuccess { onSubmit(draft) }
                        .onFailure { localError = it.message }
                },
            ) { Text(stringResource(R.string.action_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun EvidenceInputFields(
    title: String,
    onTitle: (String) -> Unit,
    url: String,
    onUrl: (String) -> Unit,
    type: String,
    onType: (String) -> Unit,
    publisher: String,
    onPublisher: (String) -> Unit,
    excerpt: String,
    onExcerpt: (String) -> Unit,
) {
    Text(
        stringResource(R.string.contribution_evidence),
        style = MaterialTheme.typography.labelMedium,
    )
    PickerField(
        label = stringResource(R.string.contribution_evidence_type),
        selected = evidenceTypeLabel(type),
        options = evidenceTypeOptions.map { it to evidenceTypeLabel(it) },
        onSelect = onType,
    )
    OutlinedTextField(
        title,
        { onTitle(it.take(250)) },
        label = { Text(stringResource(R.string.contribution_evidence_title)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        url,
        { onUrl(it.take(500)) },
        label = { Text(stringResource(R.string.contribution_evidence_url)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        publisher,
        { onPublisher(it.take(150)) },
        label = { Text(stringResource(R.string.contribution_evidence_publisher)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        excerpt,
        { onExcerpt(it.take(4000)) },
        label = { Text(stringResource(R.string.contribution_evidence_excerpt)) },
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )
}

private data class StepInput(val key: Int, var title: String = "", var body: String = "")

@Composable
fun NewGuideContributionDialog(
    busy: Boolean,
    serverError: String?,
    onDismiss: () -> Unit,
    onSubmit: (JsonObject) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var difficulty by remember { mutableStateOf("moderate") }
    var nextKey by remember { mutableStateOf(1) }
    val steps = remember { mutableStateListOf(StepInput(0)) }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.contribution_guide)) },
        text = {
            DialogBody {
                Text(
                    stringResource(R.string.contribution_guide_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    title,
                    { title = it.take(200) },
                    label = { Text(stringResource(R.string.contribution_guide_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    summary,
                    { summary = it.take(1000) },
                    label = { Text(stringResource(R.string.contribution_summary)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    body,
                    { body = it.take(50000) },
                    label = { Text(stringResource(R.string.contribution_guide_body)) },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                PickerField(
                    label = stringResource(R.string.contribution_difficulty),
                    selected = difficultyLabel(difficulty),
                    options = difficultyOptions.map { it to difficultyLabel(it) },
                    onSelect = { difficulty = it },
                )
                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            stringResource(R.string.contribution_step_number, index + 1),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = { steps.remove(step) },
                            enabled = steps.size > 1 && !busy,
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                    OutlinedTextField(
                        step.title,
                        { steps[index] = step.copy(title = it.take(160)) },
                        label = { Text(stringResource(R.string.contribution_step_title)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        step.body,
                        { steps[index] = step.copy(body = it.take(5000)) },
                        label = { Text(stringResource(R.string.contribution_step_body)) },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedButton(
                    onClick = {
                        steps.add(StepInput(nextKey))
                        nextKey += 1
                    },
                    enabled = steps.size < 100 && !busy,
                ) { Text(stringResource(R.string.contribution_add_step)) }
                DialogError(localError ?: serverError)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    localError = null
                    runCatching {
                        buildGuidePayload(
                            title = title,
                            summary = summary,
                            bodyMarkdown = body,
                            difficulty = difficulty,
                            steps = steps.map { GuideStepDraft(it.title, it.body) },
                        )
                    }
                        .onSuccess(onSubmit)
                        .onFailure { localError = it.message }
                },
            ) { Text(stringResource(R.string.action_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun relationshipLabelForForm(value: String): String = when (value) {
    "fits" -> stringResource(R.string.relationship_fits)
    "works_with" -> stringResource(R.string.relationship_works_with)
    "same_as" -> stringResource(R.string.relationship_same_as)
    "variant_of" -> stringResource(R.string.relationship_variant_of)
    "not_compatible_with" -> stringResource(R.string.relationship_not_compatible)
    else -> value.replace('_', ' ')
}

@Composable
private fun evidenceTypeLabel(value: String): String = when (value) {
    "oem_document" -> stringResource(R.string.evidence_type_oem_document)
    "service_bulletin" -> stringResource(R.string.evidence_type_service_bulletin)
    "teardown" -> stringResource(R.string.evidence_type_teardown)
    "lab_test" -> stringResource(R.string.evidence_type_lab_test)
    "community_report" -> stringResource(R.string.evidence_type_community_report)
    else -> stringResource(R.string.evidence_type_other)
}

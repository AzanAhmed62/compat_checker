package com.compatchecker.app.ui.checker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.CheckedRule
import com.compatchecker.app.domain.model.CompatibilityResult
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CompatibilityRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.EvidenceList
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.OutcomeBadge
import com.compatchecker.app.ui.components.OutcomeExplanation
import com.compatchecker.app.ui.components.RelationshipBadge
import com.compatchecker.app.ui.components.SectionTitle
import com.compatchecker.app.ui.components.VerificationChip
import com.compatchecker.app.ui.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckerScreen(
    initialProductId: String?,
    catalog: CatalogRepository,
    compatibility: CompatibilityRepository,
    onRuleClick: (String) -> Unit,
) {
    val viewModel: CheckerViewModel = compatViewModel {
        CheckerViewModel(initialProductId, catalog, compatibility)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.checker_title)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.checker_subtitle), style = MaterialTheme.typography.bodyMedium)
            }
            item {
                ProductSlot(
                    label = stringResource(R.string.checker_product_a),
                    product = state.productA,
                    onChoose = { viewModel.openPicker(PickerTarget.A) },
                    onClear = viewModel::clearA,
                )
            }
            item {
                ProductSlot(
                    label = stringResource(R.string.checker_product_b),
                    product = state.productB,
                    onChoose = { viewModel.openPicker(PickerTarget.B) },
                    onClear = viewModel::clearB,
                )
            }
            item {
                Button(
                    onClick = viewModel::check,
                    enabled = state.productA != null && state.productB != null && !state.checking,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.checking) {
                        CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.checker_run))
                    }
                }
            }
            if (state.error != null) {
                item {
                    Text(state.error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            state.result?.let { result ->
                item { ResultSummary(result) }
                if (result.rules.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.section_evidence)) }
                    items(result.rules, key = { it.id }) { rule ->
                        CheckedRuleCard(rule, onClick = { onRuleClick(rule.id) })
                    }
                }
            }
        }
    }

    if (state.pickerTarget != null) {
        ProductPickerDialog(
            query = state.pickerQuery,
            loading = state.pickerLoading,
            products = state.pickerResults,
            onQueryChange = viewModel::onPickerQueryChange,
            onChoose = viewModel::choose,
            onDismiss = viewModel::closePicker,
        )
    }
}

@Composable
private fun ProductSlot(
    label: String,
    product: ProductSummary?,
    onChoose: () -> Unit,
    onClear: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            if (product == null) {
                OutlinedButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_choose))
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(product.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            listOfNotNull(product.brandName, product.modelNumber).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = onClear) {
                        Icon(Icons.Outlined.Clear, contentDescription = stringResource(R.string.action_clear))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultSummary(result: CompatibilityResult) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutcomeBadge(result.outcome)
            OutcomeExplanation(result.outcome)
            if (result.sameProduct) {
                Text(stringResource(R.string.checker_same_product), style = MaterialTheme.typography.bodyMedium)
            } else if (result.known) {
                Text(
                    stringResource(R.string.checker_summary, result.ruleCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.checker_no_rule),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!result.checkedAt.isNullOrBlank()) {
                Text(
                    stringResource(R.string.checker_checked, formatDate(result.checkedAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CheckedRuleCard(rule: CheckedRule, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                RelationshipBadge(rule.relationship)
                VerificationChip(rule.verification)
            }
            Text(
                "${rule.subjectProductName} → ${rule.objectProductName}",
                style = MaterialTheme.typography.titleSmall,
            )
            if (!rule.summary.isNullOrBlank()) {
                Text(rule.summary, style = MaterialTheme.typography.bodyMedium)
            }
            if (!rule.technicalDetails.isNullOrBlank()) {
                Text(rule.technicalDetails, style = MaterialTheme.typography.bodySmall)
            }
            if (!rule.requiredModifications.isNullOrBlank()) {
                Text(
                    stringResource(R.string.section_required_modifications) + ": " + rule.requiredModifications,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                listOf(
                    stringResource(R.string.confidence_value, rule.confidence),
                    stringResource(R.string.votes_confirm_dispute, rule.confirmCount, rule.disputeCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (rule.evidence.isNotEmpty()) {
                EvidenceList(rule.evidence)
            }
        }
    }
}

@Composable
private fun ProductPickerDialog(
    query: String,
    loading: Boolean,
    products: List<ProductSummary>,
    onQueryChange: (String) -> Unit,
    onChoose: (ProductSummary) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.checker_pick_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text(stringResource(R.string.search_hint)) },
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                when {
                    loading -> LoadingPane()
                    products.isEmpty() -> Text(stringResource(R.string.state_no_results))
                    else -> LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(products, key = { it.id }) { product ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onChoose(product) },
                            ) {
                                Column(Modifier.padding(10.dp)) {
                                    Text(product.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        listOfNotNull(product.brandName, product.modelNumber).joinToString(" · "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

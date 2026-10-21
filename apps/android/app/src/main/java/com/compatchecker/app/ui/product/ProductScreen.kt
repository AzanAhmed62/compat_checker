package com.compatchecker.app.ui.product

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.Comment
import com.compatchecker.app.domain.model.ProductDetail
import com.compatchecker.app.domain.model.ProductRule
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CommunityRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.EmptyPane
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.MetaDivider
import com.compatchecker.app.ui.components.RelationshipBadge
import com.compatchecker.app.ui.components.SectionTitle
import com.compatchecker.app.ui.components.VerificationChip
import com.compatchecker.app.ui.formatDate as formatDateValue
import com.compatchecker.app.ui.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductScreen(
    productId: String,
    catalog: CatalogRepository,
    community: CommunityRepository,
    profiles: ProfileRepository,
    auth: AuthRepository,
    onBack: () -> Unit,
    onAuthRequired: () -> Unit,
    onRuleClick: (String) -> Unit,
    onGuideClick: (String) -> Unit,
    onCompare: (String) -> Unit,
) {
    val viewModel: ProductViewModel = compatViewModel {
        ProductViewModel(productId, catalog, community, profiles, auth)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showCorrection by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            if (it == "Correction submitted for review") showCorrection = false
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.authPrompt) {
        if (state.authPrompt) {
            viewModel.consumeAuthPrompt()
            onAuthRequired()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(state.product?.name ?: stringResource(R.string.section_overview), maxLines = 1)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.product != null) {
                        IconButton(onClick = viewModel::toggleFavorite, enabled = !state.favoriteBusy) {
                            Icon(
                                if (state.product!!.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (state.product!!.isFavorite) {
                                    stringResource(R.string.action_unfavorite)
                                } else {
                                    stringResource(R.string.action_favorite)
                                },
                            )
                        }
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_compare)) },
                                onClick = {
                                    menuOpen = false
                                    onCompare(productId)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.section_correction)) },
                                onClick = {
                                    menuOpen = false
                                    showCorrection = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_report)) },
                                onClick = {
                                    menuOpen = false
                                    showReport = true
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading && state.product == null -> LoadingPane(Modifier.padding(padding))
            state.error != null && state.product == null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.product == null -> EmptyPane(stringResource(R.string.state_no_results), Modifier.padding(padding))
            else -> {
                val product = state.product!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { ProductHeader(product) }
                    item {
                        Button(
                            onClick = { onCompare(productId) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_compare)) }
                    }
                    item { SectionTitle(stringResource(R.string.section_specifications)) }
                    if (product.specs.isEmpty()) {
                        item { Text(stringResource(R.string.state_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(product.specs) { spec ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(
                                    spec.label + if (spec.critical) " *" else "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    listOfNotNull(spec.value, spec.unit).joinToString(" "),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (spec.critical) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                        }
                    }
                    if (product.aliases.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.section_aliases)) }
                        item { Text(product.aliases.joinToString(", "), style = MaterialTheme.typography.bodyMedium) }
                    }
                    item { MetaDivider() }
                    item { SectionTitle(stringResource(R.string.section_compatibility)) }
                    if (product.rules.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.no_rules),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(product.rules, key = { it.id }) { rule ->
                            ProductRuleCard(rule, onClick = { onRuleClick(rule.id) })
                        }
                    }
                    if (product.guides.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.section_guides)) }
                        items(product.guides, key = { it.id }) { guide ->
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { onGuideClick(guide.id) },
                            ) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(guide.title, style = MaterialTheme.typography.titleSmall)
                                    if (!guide.summary.isNullOrBlank()) {
                                        Text(guide.summary, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                        }
                    }
                    item { MetaDivider() }
                    item {
                        SectionTitle(
                            stringResource(R.string.section_comments) + " (${product.commentCount})",
                        )
                    }
                    if (state.comments.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.state_empty),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        items(state.comments, key = { it.id }) { comment -> CommentCard(comment) }
                    }
                    item { AddCommentBox(busy = state.commentBusy, onSubmit = viewModel::addComment) }
                    item { MetaDivider() }
                    item { SectionTitle(stringResource(R.string.section_correction)) }
                    item {
                        Text(stringResource(R.string.contribution_help), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { showCorrection = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.section_correction))
                        }
                    }
                }
            }
        }
    }

    if (showReport) {
        ReportDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason, details ->
                viewModel.report(reason, details)
                showReport = false
            },
        )
    }
    if (showCorrection) {
        CorrectionDialog(
            busy = state.correctionBusy,
            onDismiss = { showCorrection = false },
            onSubmit = viewModel::submitCorrection,
        )
    }
}

@Composable
private fun ProductHeader(product: ProductDetail) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        product.media.firstOrNull()?.let { media ->
            AsyncImage(
                model = media.url,
                contentDescription = media.alt,
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentScale = ContentScale.Crop,
            )
        }
        Text(product.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            listOfNotNull(product.brandName, product.modelNumber, product.releaseYear?.toString())
                .joinToString(" · "),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        if (!product.summary.isNullOrBlank()) {
            Text(product.summary, style = MaterialTheme.typography.bodyLarge)
        }
        if (!product.technicalNotes.isNullOrBlank()) {
            SectionTitle(stringResource(R.string.section_technical_details))
            Text(product.technicalNotes, style = MaterialTheme.typography.bodyMedium)
        }
        product.connectorType?.let {
            Text("Connector: $it", style = MaterialTheme.typography.bodySmall)
        }
        product.formFactor?.let {
            Text("Form factor: $it", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ProductRuleCard(rule: ProductRule, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RelationshipBadge(rule.relationship)
                VerificationChip(rule.verification)
            }
            Text(rule.otherProductName, style = MaterialTheme.typography.titleSmall)
            if (!rule.summary.isNullOrBlank()) {
                Text(rule.summary, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                listOfNotNull(
                    stringResource(R.string.confidence_value, rule.confidence),
                    stringResource(R.string.evidence_count, rule.evidenceCount),
                    stringResource(R.string.votes_confirm_dispute, rule.confirmCount, rule.disputeCount),
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CommentCard(comment: Comment) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(comment.displayName, style = MaterialTheme.typography.labelLarge)
                Text(
                    "@${comment.username} · ${statusLabel(comment.role)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
            Text(
                formatDateValue(comment.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddCommentBox(busy: Boolean, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it.take(4000) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.add_comment)) },
            placeholder = { Text(stringResource(R.string.comment_hint)) },
            minLines = 2,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = {
                    if (text.isNotBlank()) {
                        onSubmit(text)
                        text = ""
                    }
                },
                enabled = !busy && text.isNotBlank(),
            ) { Text(stringResource(R.string.action_send)) }
        }
    }
}

@Composable
private fun CorrectionDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var summary by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.section_correction)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contribution_help), style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.contribution_name)) }, singleLine = true)
                OutlinedTextField(summary, { summary = it }, label = { Text(stringResource(R.string.contribution_summary)) }, minLines = 2)
                OutlinedTextField(notes, { notes = it }, label = { Text(stringResource(R.string.contribution_notes)) }, minLines = 2)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(name, summary, notes) },
                enabled = !busy && (name.isNotBlank() || summary.isNotBlank() || notes.isNotBlank()),
            ) { Text(stringResource(R.string.action_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String, String?) -> Unit) {
    var reason by remember { mutableStateOf("false_claim") }
    var details by remember { mutableStateOf("") }
    val reasons = listOf(
        "spam" to stringResource(R.string.report_spam),
        "false_claim" to stringResource(R.string.report_false_claim),
        "unsafe_advice" to stringResource(R.string.report_unsafe),
        "other" to stringResource(R.string.report_other),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_report)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                reasons.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { reason = value }.padding(4.dp),
                    ) {
                        androidx.compose.material3.RadioButton(selected = reason == value, onClick = { reason = value })
                        Text(label)
                    }
                }
                OutlinedTextField(
                    details,
                    { details = it.take(2000) },
                    label = { Text(stringResource(R.string.report_details)) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSubmit(reason, details.ifBlank { null }) }) {
                Text(stringResource(R.string.action_submit))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

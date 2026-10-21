package com.compatchecker.app.ui.rules

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.RuleDetail
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CompatibilityRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.EvidenceList
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.RelationshipBadge
import com.compatchecker.app.ui.components.SectionTitle
import com.compatchecker.app.ui.components.VerificationChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleScreen(
    ruleId: String,
    compatibility: CompatibilityRepository,
    auth: AuthRepository,
    onBack: () -> Unit,
    onAuthRequired: () -> Unit,
) {
    val viewModel: RuleViewModel = compatViewModel { RuleViewModel(ruleId, compatibility, auth) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
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
                title = { Text(stringResource(R.string.section_compatibility)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.rule == null -> Text(stringResource(R.string.state_no_results), Modifier.padding(padding).padding(24.dp))
            else -> RuleBody(
                rule = state.rule!!,
                voting = state.voting,
                onVote = viewModel::vote,
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun RuleBody(
    rule: RuleDetail,
    voting: Boolean,
    onVote: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RelationshipBadge(rule.relationship)
                        VerificationChip(rule.verification)
                    }
                    Text(
                        "${rule.subjectBrandName} ${rule.subjectProductName} → ${rule.objectBrandName} ${rule.objectProductName}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (!rule.summary.isNullOrBlank()) {
                        Text(rule.summary, style = MaterialTheme.typography.bodyLarge)
                    }
                    Text(
                        listOf(
                            stringResource(R.string.confidence_value, rule.confidence),
                            rule.riskLevel?.let { "Risk: ${it.replace('_', ' ')}" } ?: "",
                            stringResource(R.string.evidence_count, rule.evidenceCount),
                        ).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        if (!rule.technicalDetails.isNullOrBlank()) {
            item {
                SectionTitle(stringResource(R.string.section_technical_details))
                Text(rule.technicalDetails, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (!rule.requiredModifications.isNullOrBlank()) {
            item {
                SectionTitle(stringResource(R.string.section_required_modifications))
                Text(rule.requiredModifications, style = MaterialTheme.typography.bodyMedium)
            }
        }
        item { SectionTitle(stringResource(R.string.section_evidence)) }
        item { EvidenceList(rule.evidence) }
        item { SectionTitle(stringResource(R.string.section_votes)) }
        item {
            Text(
                stringResource(R.string.votes_confirm_dispute, rule.confirmCount, rule.disputeCount),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (rule.myVote != null) {
                Text("Your vote: ${rule.myVote}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                if (rule.myVote == "confirm") {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_confirm))
                    }
                } else {
                    Button(onClick = { onVote("confirm") }, enabled = !voting, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_confirm))
                    }
                }
                if (rule.myVote == "dispute") {
                    OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_dispute))
                    }
                } else {
                    OutlinedButton(onClick = { onVote("dispute") }, enabled = !voting, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.action_dispute))
                    }
                }
            }
        }
    }
}

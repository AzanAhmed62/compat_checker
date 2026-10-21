package com.compatchecker.app.ui.requests

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.Category
import com.compatchecker.app.domain.model.CommunityRequest
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.CommunityRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.EmptyPane
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.formatDate
import com.compatchecker.app.ui.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(
    community: CommunityRepository,
    catalog: CatalogRepository,
    auth: AuthRepository,
    onAuthRequired: () -> Unit,
) {
    val viewModel: RequestsViewModel = compatViewModel {
        RequestsViewModel(community, catalog, auth)
    }
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
                title = { Text(stringResource(R.string.requests_title)) },
                actions = {
                    IconButton(onClick = viewModel::showCreate) {
                        Icon(Icons.Outlined.Add, contentDescription = stringResource(R.string.action_new_request))
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.requests.isEmpty() -> EmptyPane(stringResource(R.string.request_empty), Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.requests_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.requests, key = { it.id }) { request ->
                    RequestCard(
                        request = request,
                        voting = state.votingId == request.id,
                        onVote = { viewModel.vote(request) },
                    )
                }
            }
        }
    }

    if (state.showCreate) {
        NewRequestDialog(
            categories = state.categories,
            busy = state.creating,
            onDismiss = viewModel::hideCreate,
            onSubmit = viewModel::create,
        )
    }
}

@Composable
private fun RequestCard(request: CommunityRequest, voting: Boolean, onVote: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(request.title, style = MaterialTheme.typography.titleMedium)
            Text(request.description, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOf(
                    statusLabel(request.status),
                    request.categoryName ?: "",
                    formatDate(request.createdAt),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.request_votes, request.voteCount),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = onVote, enabled = !voting) {
                    Icon(Icons.Outlined.ThumbUp, contentDescription = null)
                    Text(
                        stringResource(R.string.action_confirm),
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun NewRequestDialog(
    categories: List<Category>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (String, String, String?) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var categoryId by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.new_request_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title,
                    { title = it.take(150) },
                    label = { Text(stringResource(R.string.request_title_label)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    description,
                    { description = it.take(5000) },
                    label = { Text(stringResource(R.string.request_description_label)) },
                    minLines = 3,
                )
                Text(stringResource(R.string.request_category_label), style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = categoryId == null,
                        onClick = { categoryId = null },
                        label = { Text(stringResource(R.string.request_no_category)) },
                    )
                }
                categories.take(6).forEach { category ->
                    FilterChip(
                        selected = categoryId == category.id,
                        onClick = { categoryId = category.id },
                        label = { Text(category.name) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmit(title, description, categoryId) },
                enabled = !busy && title.length >= 5 && description.length >= 10,
            ) { Text(stringResource(R.string.action_submit)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

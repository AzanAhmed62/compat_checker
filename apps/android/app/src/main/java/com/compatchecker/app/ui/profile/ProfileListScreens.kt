package com.compatchecker.app.ui.profile

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.AppNotification
import com.compatchecker.app.domain.model.Contribution
import com.compatchecker.app.domain.model.ProductSummary
import com.compatchecker.app.domain.repository.CatalogRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.EmptyPane
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.ProductCard
import com.compatchecker.app.ui.formatDate
import com.compatchecker.app.ui.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    catalog: CatalogRepository,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
) {
    val viewModel: FavoritesViewModel = compatViewModel { FavoritesViewModel(catalog) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorites_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.items.isEmpty() -> EmptyPane(stringResource(R.string.favorites_empty), Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { product ->
                    ProductCard(product, onClick = { onProductClick(product.id) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContributionsScreen(
    profiles: ProfileRepository,
    onBack: () -> Unit,
) {
    val viewModel: ContributionsViewModel = compatViewModel { ContributionsViewModel(profiles) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.contributions_title)) },
                navigationIcon = { BackButton(onBack) },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.items.isEmpty() -> EmptyPane(stringResource(R.string.contributions_empty), Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Text(
                        stringResource(R.string.contribution_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(state.items, key = { it.id }) { contribution ->
                    ContributionCard(contribution)
                }
            }
        }
    }
}

@Composable
private fun ContributionCard(contribution: Contribution) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                statusLabel(contribution.status),
                style = MaterialTheme.typography.titleSmall,
                color = if (contribution.status == "approved") {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                stringResource(R.string.contribution_kind, statusLabel(contribution.kind)),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                stringResource(R.string.contribution_date, formatDate(contribution.createdAt)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!contribution.reviewNote.isNullOrBlank()) {
                Text(
                    stringResource(R.string.contribution_review_note, contribution.reviewNote),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    profiles: ProfileRepository,
    onBack: () -> Unit,
    onOpenNotification: (AppNotification) -> Unit,
) {
    val viewModel: NotificationsViewModel = compatViewModel { NotificationsViewModel(profiles) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notifications_title)) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    if (state.items.any { it.readAt == null }) {
                        androidx.compose.material3.TextButton(onClick = viewModel::markAllRead) {
                            Text(stringResource(R.string.action_mark_all_read))
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.items.isEmpty() -> EmptyPane(stringResource(R.string.notifications_empty), Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.items, key = { it.id }) { notification ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable {
                            viewModel.markRead(notification)
                            onOpenNotification(notification)
                        },
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    notification.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                )
                                if (notification.readAt == null) {
                                    Text(
                                        stringResource(R.string.notification_unread),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                            if (!notification.body.isNullOrBlank()) {
                                Text(notification.body, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                formatDate(notification.createdAt),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    IconButton(onClick = onBack) {
        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.action_back))
    }
}

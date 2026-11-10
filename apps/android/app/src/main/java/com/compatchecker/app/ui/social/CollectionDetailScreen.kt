package com.compatchecker.app.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ImageNotSupported
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.CollectionDetail
import com.compatchecker.app.domain.model.CollectionItem
import com.compatchecker.app.domain.repository.SocialRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.EmptyPane
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.SectionTitle
import com.compatchecker.app.ui.formatDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String,
    social: SocialRepository,
    onBack: () -> Unit,
    onProductClick: (String) -> Unit,
) {
    val viewModel: CollectionDetailViewModel = compatViewModel {
        CollectionDetailViewModel(collectionId, social)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDelete by remember { mutableStateOf(false) }
    val removedMessage = stringResource(R.string.collection_item_removed)

    LaunchedEffect(state.deleted) {
        if (state.deleted) onBack()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.collection_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    if (state.collection?.isOwner == true) {
                        IconButton(
                            onClick = { confirmDelete = true },
                            enabled = !state.actionBusy,
                        ) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(R.string.action_delete))
                        }
                    }
                },
            )
        },
    ) { padding ->
        val collection = state.collection
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            collection == null -> EmptyPane(stringResource(R.string.collection_not_found), Modifier.padding(padding))
            else -> CollectionBody(
                collection = collection,
                actionBusy = state.actionBusy,
                modifier = Modifier.padding(padding),
                onProductClick = onProductClick,
                onRemove = { productId -> viewModel.removeItem(productId, removedMessage) },
                onVisibilityChange = viewModel::setPublic,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.collection_delete_title)) },
            text = { Text(stringResource(R.string.collection_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete()
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CollectionBody(
    collection: CollectionDetail,
    actionBusy: Boolean,
    modifier: Modifier = Modifier,
    onProductClick: (String) -> Unit,
    onRemove: (String) -> Unit,
    onVisibilityChange: (Boolean) -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        collection.title,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    VisibilityChip(collection.isPublic)
                }
                if (!collection.description.isNullOrBlank()) {
                    Text(collection.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    stringResource(
                        R.string.collection_owner,
                        collection.ownerDisplayName.ifBlank { "@${collection.ownerUsername}" },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    pluralStringResource(
                        R.plurals.collection_items,
                        collection.items.size,
                        collection.items.size,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (collection.isOwner) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(
                                if (collection.isPublic) {
                                    R.string.collection_make_private
                                } else {
                                    R.string.collection_make_public
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                        androidx.compose.material3.Switch(
                            checked = collection.isPublic,
                            onCheckedChange = onVisibilityChange,
                            enabled = !actionBusy,
                        )
                    }
                }
            }
        }
        item { SectionTitle(stringResource(R.string.section_products)) }
        if (collection.items.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.collection_empty_items),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(collection.items, key = { it.productId }) { item ->
                CollectionItemCard(
                    item = item,
                    canRemove = collection.isOwner,
                    busy = actionBusy,
                    onClick = { onProductClick(item.productId) },
                    onRemove = { onRemove(item.productId) },
                )
            }
        }
    }
}

@Composable
private fun CollectionItemCard(
    item: CollectionItem,
    canRemove: Boolean,
    busy: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .padding(0.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp),
                    )
                } else {
                    Icon(
                        Icons.Outlined.ImageNotSupported,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    item.productName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    listOfNotNull(item.brandName, item.modelNumber).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!item.note.isNullOrBlank()) {
                    Text(item.note, style = MaterialTheme.typography.bodySmall)
                }
                val added = formatDate(item.addedAt)
                if (added.isNotBlank()) {
                    Text(
                        stringResource(R.string.collection_added_on, added),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (canRemove) {
                IconButton(onClick = onRemove, enabled = !busy) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = stringResource(R.string.collection_remove_item),
                    )
                }
            }
        }
    }
}

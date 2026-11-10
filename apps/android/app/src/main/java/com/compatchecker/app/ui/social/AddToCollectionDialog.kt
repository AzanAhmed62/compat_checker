package com.compatchecker.app.ui.social

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.CollectionSummary
import com.compatchecker.app.domain.repository.SocialRepository
import com.compatchecker.app.ui.displayMessage
import kotlinx.coroutines.launch

@Composable
fun AddToCollectionDialog(
    social: SocialRepository,
    productId: String,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    onManageCollections: () -> Unit,
) {
    var collections by remember { mutableStateOf<List<CollectionSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var title by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(productId) {
        runCatching { social.collections() }
            .onSuccess {
                collections = it
                loading = false
            }
            .onFailure {
                error = it.displayMessage()
                loading = false
            }
    }

    fun add(collectionId: String) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            runCatching { social.addToCollection(collectionId, productId, null) }
                .onSuccess { onAdded() }
                .onFailure {
                    error = it.displayMessage()
                    busy = false
                }
        }
    }

    fun createAndAdd() {
        if (busy || title.isBlank()) return
        busy = true
        error = null
        scope.launch {
            runCatching {
                val id = social.createCollection(title, null, false)
                social.addToCollection(id, productId, null)
            }.onSuccess { onAdded() }
                .onFailure {
                    error = it.displayMessage()
                    busy = false
                }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(stringResource(R.string.add_to_collection_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                } else {
                    if (collections.isEmpty()) {
                        Text(
                            stringResource(R.string.add_to_collection_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            stringResource(R.string.add_to_collection_choose),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            items(collections, key = { it.id }) { collection ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !busy) { add(collection.id) }
                                        .padding(vertical = 10.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        collection.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    VisibilityChip(collection.isPublic)
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(120) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.add_to_collection_new)) },
                        singleLine = true,
                        enabled = !busy,
                    )
                    TextButton(onClick = onManageCollections, enabled = !busy) {
                        Text(stringResource(R.string.add_to_collection_manage))
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = ::createAndAdd,
                enabled = !busy && !loading && title.isNotBlank(),
            ) { Text(stringResource(R.string.add_to_collection_create)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text(stringResource(R.string.action_close))
            }
        },
    )
}

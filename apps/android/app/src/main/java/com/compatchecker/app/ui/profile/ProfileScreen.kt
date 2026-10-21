package com.compatchecker.app.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.AlertDialog
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
import com.compatchecker.app.domain.repository.AuthRepository
import com.compatchecker.app.domain.repository.ProfileRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.DateText
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.formatDate
import com.compatchecker.app.ui.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    signedIn: Boolean,
    auth: AuthRepository,
    profiles: ProfileRepository,
    onSignIn: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenContributions: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val viewModel: ProfileViewModel = compatViewModel { ProfileViewModel(auth, profiles) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmDeletion by remember { mutableStateOf(false) }

    LaunchedEffect(signedIn) {
        if (signedIn) viewModel.load()
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.profile_title)) }) },
    ) { padding ->
        when {
            !signedIn -> GuestProfile(Modifier.padding(padding), onSignIn)
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            state.profile == null -> GuestProfile(Modifier.padding(padding), onSignIn)
            else -> {
                val profile = state.profile!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(profile.displayName, style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "@${profile.username} · ${stringResource(R.string.profile_role, statusLabel(profile.role))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (!profile.bio.isNullOrBlank()) {
                                Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                stringResource(
                                    R.string.profile_points,
                                    state.reputation?.points ?: profile.reputationPoints,
                                    statusLabel(state.reputation?.tier ?: profile.reputationTier),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(
                                stringResource(R.string.profile_status, statusLabel(profile.accountStatus)),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (profile.deletionRequestedAt != null) {
                                Text(
                                    stringResource(R.string.deletion_pending_note),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                    ProfileLink(stringResource(R.string.profile_favorites), profile.favoriteCount.toString(), onOpenFavorites)
                    ProfileLink(stringResource(R.string.profile_contributions), "", onOpenContributions)
                    ProfileLink(
                        stringResource(R.string.profile_notifications),
                        if (profile.unreadNotifications > 0) {
                            stringResource(R.string.profile_unread, profile.unreadNotifications)
                        } else "",
                        onOpenNotifications,
                    )
                    ProfileLink(stringResource(R.string.profile_settings), "", onOpenSettings)

                    if (state.reputation?.entries?.isNotEmpty() == true) {
                        Text(stringResource(R.string.section_recent_activity), style = MaterialTheme.typography.titleMedium)
                        state.reputation!!.entries.take(10).forEach { entry ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    entry.reason.replace('_', ' '),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    if (entry.delta > 0) "+${entry.delta}" else entry.delta.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (entry.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }

                    if (profile.deletionRequestedAt != null) {
                        OutlinedButton(
                            onClick = viewModel::cancelDeletion,
                            enabled = !state.actionBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_cancel_deletion)) }
                    } else {
                        TextButton(onClick = { confirmDeletion = true }) {
                            Text(
                                stringResource(R.string.action_request_deletion),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = viewModel::signOut,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.action_sign_out)) }
                }
            }
        }
    }

    if (confirmDeletion) {
        AlertDialog(
            onDismissRequest = { confirmDeletion = false },
            title = { Text(stringResource(R.string.action_request_deletion)) },
            text = { Text(stringResource(R.string.delete_warning)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDeletion = false
                        viewModel.requestDeletion()
                    },
                ) { Text(stringResource(R.string.action_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletion = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun GuestProfile(modifier: Modifier = Modifier, onSignIn: () -> Unit) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.profile_guest_title), style = MaterialTheme.typography.headlineSmall)
        Text(stringResource(R.string.profile_guest_body), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_sign_in))
        }
    }
}

@Composable
private fun ProfileLink(title: String, detail: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (detail.isNotBlank()) {
                Text(detail, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Icon(Icons.Outlined.ChevronRight, contentDescription = null)
        }
    }
}

package com.compatchecker.app.ui.social

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Person
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.PublicProfile
import com.compatchecker.app.domain.model.RecentContribution
import com.compatchecker.app.domain.repository.SocialRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.SectionTitle
import com.compatchecker.app.ui.formatDate
import com.compatchecker.app.ui.statusLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublicProfileScreen(
    userId: String,
    social: SocialRepository,
    signedIn: Boolean,
    onBack: () -> Unit,
    onAuthRequired: () -> Unit,
) {
    val viewModel: PublicProfileViewModel = compatViewModel {
        PublicProfileViewModel(userId, social)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

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
                title = { Text(stringResource(R.string.public_profile_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val profile = state.profile
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.error != null -> ErrorPane(state.error!!, viewModel::load, Modifier.padding(padding))
            profile == null -> ErrorPane(stringResource(R.string.profile_not_found), viewModel::load, Modifier.padding(padding))
            else -> ProfileBody(
                profile = profile,
                signedIn = signedIn,
                followBusy = state.followBusy,
                modifier = Modifier.padding(padding),
                onToggleFollow = {
                    if (signedIn) viewModel.toggleFollow() else onAuthRequired()
                },
            )
        }
    }
}

@Composable
private fun ProfileBody(
    profile: PublicProfile,
    signedIn: Boolean,
    followBusy: Boolean,
    modifier: Modifier = Modifier,
    onToggleFollow: () -> Unit,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (profile.avatarUrl != null) {
                            AsyncImage(
                                model = profile.avatarUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(88.dp),
                            )
                        } else {
                            Icon(
                                Icons.Outlined.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(44.dp),
                            )
                        }
                    }
                    Text(
                        profile.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "@${profile.username} · ${statusLabel(profile.role)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!profile.bio.isNullOrBlank()) {
                        Text(
                            profile.bio,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        stringResource(
                            R.string.profile_points,
                            profile.reputationPoints,
                            statusLabel(profile.reputationTier),
                        ),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (profile.isSelf) {
                        Text(
                            stringResource(R.string.profile_self),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else if (profile.isFollowing) {
                        OutlinedButton(
                            onClick = onToggleFollow,
                            enabled = !followBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_unfollow)) }
                    } else {
                        Button(
                            onClick = onToggleFollow,
                            enabled = !followBusy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (signedIn) R.string.action_follow
                                    else R.string.profile_follow_sign_in,
                                ),
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCard(
                    value = profile.followers.toString(),
                    label = stringResource(R.string.profile_followers),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = profile.following.toString(),
                    label = stringResource(R.string.profile_following),
                    modifier = Modifier.weight(1f),
                )
                StatCard(
                    value = profile.approvedContributions.toString(),
                    label = stringResource(R.string.profile_approved_contributions),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item {
            Text(
                stringResource(
                    R.string.profile_published_rules,
                    profile.publishedRules,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val joined = formatDate(profile.createdAt)
            if (joined.isNotBlank()) {
                Text(
                    stringResource(R.string.profile_member_since, joined),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item { SectionTitle(stringResource(R.string.profile_recent_contributions)) }
        if (profile.recentContributions.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.profile_no_recent_contributions),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(profile.recentContributions) { contribution ->
                RecentContributionRow(contribution)
            }
        }
    }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RecentContributionRow(contribution: RecentContribution) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                statusLabel(contribution.kind),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                listOf(statusLabel(contribution.status), formatDate(contribution.reviewedAt))
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

package com.compatchecker.app.ui.guides

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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.compatchecker.app.R
import com.compatchecker.app.domain.model.GuideStep
import com.compatchecker.app.domain.repository.GuideRepository
import com.compatchecker.app.ui.compatViewModel
import com.compatchecker.app.ui.components.DateText
import com.compatchecker.app.ui.components.ErrorPane
import com.compatchecker.app.ui.components.LoadingPane
import com.compatchecker.app.ui.components.OfflineBanner
import com.compatchecker.app.ui.components.SectionTitle
import com.compatchecker.app.ui.difficultyLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideDetailScreen(
    guideId: String,
    guidesRepository: GuideRepository,
    onBack: () -> Unit,
) {
    val viewModel: GuideDetailViewModel = compatViewModel {
        GuideDetailViewModel(guideId, guidesRepository)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_guides)) },
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
            state.guide == null -> Text(
                stringResource(R.string.guide_not_found),
                modifier = Modifier.padding(padding).padding(24.dp),
            )
            else -> {
                val guide = state.guide!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (guide.offline) {
                        item { OfflineBanner() }
                    }
                    item {
                        Text(guide.guide.title, style = MaterialTheme.typography.headlineSmall)
                        if (!guide.guide.summary.isNullOrBlank()) {
                            Text(guide.guide.summary, style = MaterialTheme.typography.bodyLarge)
                        }
                        Text(
                            listOf(
                                difficultyLabel(guide.guide.difficulty),
                                stringResource(R.string.guide_author, guide.guide.authorName),
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        DateText(guide.guide.publishedAt)
                    }
                    item {
                        SectionTitle(stringResource(R.string.section_overview))
                        MarkdownText(guide.guide.bodyMarkdown)
                    }
                    item { SectionTitle(stringResource(R.string.guide_steps)) }
                    if (guide.steps.isEmpty()) {
                        item {
                            Text(
                                stringResource(R.string.guide_no_steps),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(guide.steps, key = { it.id }) { step ->
                        StepCard(step)
                    }
                    item {
                        Text(
                            stringResource(R.string.comments_count, guide.commentCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(step: GuideStep) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${step.position}.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text(step.title, style = MaterialTheme.typography.titleMedium)
            }
            if (step.imageUrl != null) {
                AsyncImage(
                    model = step.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    contentScale = ContentScale.Crop,
                )
            }
            Text(step.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

package com.example.compatchecker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.compatchecker.domain.model.CompatibilityStatus
import com.example.compatchecker.domain.model.ContributionStatus
import com.example.compatchecker.domain.model.ProductCategory
import com.example.compatchecker.domain.model.RequestStatus
import com.example.compatchecker.ui.components.CompatibilityBadge
import com.example.compatchecker.ui.components.DataSourceBanner
import com.example.compatchecker.ui.theme.CompatGreen
import com.example.compatchecker.ui.theme.TechCyan
import com.example.compatchecker.ui.viewmodels.CommunityViewModel

@Composable
fun CommunityHubScreen(
    viewModel: CommunityViewModel,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }

    var showRequestDialog by remember { mutableStateOf(false) }
    var reqTargetDevice by remember { mutableStateOf("") }
    var reqModelNumber by remember { mutableStateOf("") }
    var reqNotes by remember { mutableStateOf("") }

    var showContributionDialog by remember { mutableStateOf(false) }
    var contribTitle by remember { mutableStateOf("") }
    var contribSource by remember { mutableStateOf("") }
    var contribTarget by remember { mutableStateOf("") }
    var contribEvidenceUrl by remember { mutableStateOf("") }
    var contribNotes by remember { mutableStateOf("") }
    var contribEmail by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("community_hub_screen")
    ) {
        DataSourceBanner(
            sourceType = state.dataSourceType,
            onConfigureClick = onNavigateToSettings
        )

        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Product Requests (${state.requests.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Contributions (${state.contributions.size})") }
            )
        }

        if (state.submitSuccessNotice != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(CompatGreen.copy(alpha = 0.2f))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = state.submitSuccessNotice!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = CompatGreen,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { viewModel.clearNotice() }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = CompatGreen)
                    }
                }
            }
        }

        if (selectedTab == 0) {
            // Tab 0: Requests & Voting
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Vote on Needed Verifications",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showRequestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TechCyan, contentColor = MaterialTheme.colorScheme.background),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("submit_request_dialog_btn")
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Request")
                        }
                    }
                }

                items(state.requests) { req ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (req.hasUserVoted) TechCyan.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.voteRequest(req) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("vote_btn_${req.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = "Vote",
                                    tint = if (req.hasUserVoted) TechCyan else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = req.votesCount.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (req.hasUserVoted) TechCyan else MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = req.targetDeviceName,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = req.status.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TechCyan
                                        )
                                    }
                                }

                                Text(
                                    text = "Model: ${req.targetModelNumber} • ${req.componentType.displayName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = req.notes,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Tab 1: Community Contributions
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Technician Evidence Submissions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(
                            onClick = { showContributionDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TechCyan, contentColor = MaterialTheme.colorScheme.background),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Science, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Submit Proof")
                        }
                    }
                }

                items(state.contributions) { c ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = c.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (c.status == ContributionStatus.ACCEPTED) CompatGreen.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = c.status.label,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (c.status == ContributionStatus.ACCEPTED) CompatGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = "${c.sourceProductName} ↔ ${c.targetProductName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = TechCyan
                            )

                            Text(
                                text = c.technicalNotes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Text(
                                text = "Evidence URL: ${c.evidenceUrl}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog for New Product Request
    if (showRequestDialog) {
        AlertDialog(
            onDismissRequest = { showRequestDialog = false },
            title = { Text("Request Compatibility Research") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = reqTargetDevice,
                        onValueChange = { reqTargetDevice = it },
                        label = { Text("Target Device / Phone Model") },
                        placeholder = { Text("e.g. Nothing Phone (2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reqModelNumber,
                        onValueChange = { reqModelNumber = it },
                        label = { Text("Model Number / Variant") },
                        placeholder = { Text("e.g. A065") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reqNotes,
                        onValueChange = { reqNotes = it },
                        label = { Text("What component or fitment should be tested?") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (reqTargetDevice.isNotBlank()) {
                            viewModel.submitNewRequest(
                                targetDevice = reqTargetDevice,
                                modelNumber = reqModelNumber,
                                category = ProductCategory.BATTERY,
                                notes = reqNotes
                            )
                            showRequestDialog = false
                            reqTargetDevice = ""
                            reqModelNumber = ""
                            reqNotes = ""
                        }
                    }
                ) {
                    Text("Submit Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRequestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Dialog for New Contribution
    if (showContributionDialog) {
        AlertDialog(
            onDismissRequest = { showContributionDialog = false },
            title = { Text("Submit Technical Evidence") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = contribTitle,
                        onValueChange = { contribTitle = it },
                        label = { Text("Finding Summary Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contribSource,
                        onValueChange = { contribSource = it },
                        label = { Text("Component A (e.g. Replacement Panel)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contribTarget,
                        onValueChange = { contribTarget = it },
                        label = { Text("Component B (e.g. Phone Chassis)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contribEvidenceUrl,
                        onValueChange = { contribEvidenceUrl = it },
                        label = { Text("Evidence URL (Teardown, Datasheet, GitHub)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = contribNotes,
                        onValueChange = { contribNotes = it },
                        label = { Text("Technical Pinout / Fitment Notes") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    OutlinedTextField(
                        value = contribEmail,
                        onValueChange = { contribEmail = it },
                        label = { Text("Your Email / Lab Handle") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (contribTitle.isNotBlank() && contribSource.isNotBlank()) {
                            viewModel.submitNewContribution(
                                title = contribTitle,
                                sourceProduct = contribSource,
                                targetProduct = contribTarget,
                                status = CompatibilityStatus.COMPATIBLE,
                                evidenceUrl = contribEvidenceUrl,
                                technicalNotes = contribNotes,
                                submitterEmail = contribEmail
                            )
                            showContributionDialog = false
                            contribTitle = ""
                            contribSource = ""
                            contribTarget = ""
                            contribEvidenceUrl = ""
                            contribNotes = ""
                        }
                    }
                ) {
                    Text("Submit for Verification")
                }
            },
            dismissButton = {
                TextButton(onClick = { showContributionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

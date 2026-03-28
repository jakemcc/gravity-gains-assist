package com.jakemccrary.gravitygainsassist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.website.GripGainsLoginWebViewFactory
import com.jakemccrary.gravitygainsassist.website.GripGainsSession
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import com.jakemccrary.gravitygainsassist.website.GripGainsWebSignInSessionCapture

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    gripGainsWebSignInSessionCapture: GripGainsWebSignInSessionCapture,
    gripGainsLoginWebViewFactory: GripGainsLoginWebViewFactory,
    onGrantPermissions: () -> Unit,
    onSetAutoSyncEnabled: (Boolean) -> Unit,
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    var isShowingGripGainsSignIn by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        MainScreenContent(
            screenState = screenState,
            onGrantPermissions = onGrantPermissions,
            onStartGripGainsSignIn = { isShowingGripGainsSignIn = true },
            onClearGripGainsSession = viewModel::clearGripGainsSession,
            onSetAutoSyncEnabled = onSetAutoSyncEnabled,
            onReadLatestWeight = viewModel::readLatestWeightNow,
            onRunSyncNow = viewModel::runSyncNow,
            innerPadding = innerPadding,
        )
    }

    if (isShowingGripGainsSignIn) {
        GripGainsSignInDialog(
            gripGainsWebSignInSessionCapture = gripGainsWebSignInSessionCapture,
            gripGainsLoginWebViewFactory = gripGainsLoginWebViewFactory,
            onDismiss = { isShowingGripGainsSignIn = false },
            onSessionCaptured = { session ->
                isShowingGripGainsSignIn = false
                viewModel.completeGripGainsSignIn(session)
            },
        )
    }
}

@Composable
private fun MainScreenContent(
    screenState: MainScreenState,
    onGrantPermissions: () -> Unit,
    onStartGripGainsSignIn: () -> Unit,
    onClearGripGainsSession: () -> Unit,
    onSetAutoSyncEnabled: (Boolean) -> Unit,
    onReadLatestWeight: () -> Unit,
    onRunSyncNow: () -> Unit,
    innerPadding: PaddingValues,
) {
    val dashboardModel = screenState.toDashboardUiModel()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.surface,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            WeightHeroCard(dashboardModel)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DashboardStatusCard(
                    summary = dashboardModel.healthSummary,
                    modifier = Modifier.weight(1f),
                )
                DashboardStatusCard(
                    summary = dashboardModel.sessionSummary,
                    modifier = Modifier.weight(1f),
                )
            }

            SyncSummaryCard(dashboardModel.syncSummary)
            AutoSyncStatusCard(screenState)

            dashboardModel.statusMessage?.let { message ->
                InlineNoteCard(message)
            }

            Button(
                onClick = onRunSyncNow,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text(
                    text = "Run sync now",
                    modifier = Modifier.padding(vertical = 4.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            dashboardModel.setupAction?.let { action ->
                FilledTonalButton(
                    onClick = {
                        when (action.type) {
                            DashboardActionType.GRANT_PERMISSIONS -> onGrantPermissions()
                            DashboardActionType.SIGN_IN -> onStartGripGainsSignIn()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    enabled = action.type != DashboardActionType.GRANT_PERMISSIONS ||
                        screenState.healthStatus?.availability == HealthConnectAvailability.AVAILABLE,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                ) {
                    Text(
                        text = action.label,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }

            UtilityActionsCard(
                screenState = screenState,
                onStartGripGainsSignIn = onStartGripGainsSignIn,
                onClearGripGainsSession = onClearGripGainsSession,
                onSetAutoSyncEnabled = onSetAutoSyncEnabled,
                onReadLatestWeight = onReadLatestWeight,
            )
        }
    }
}

@Composable
private fun WeightHeroCard(dashboardModel: DashboardUiModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        ),
        shape = RoundedCornerShape(28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            dashboardModel.contextLine?.let { line ->
                ContextPill(line)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = dashboardModel.weightValue,
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = dashboardModel.weightUnit,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }

            Text(
                text = dashboardModel.weightMeta,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            dashboardModel.weightSupport?.let { support ->
                Text(
                    text = support,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                )
            }
        }
    }
}

@Composable
private fun ContextPill(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
        contentColor = MaterialTheme.colorScheme.primary,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun DashboardStatusCard(
    summary: DashboardStatusSummary,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = summary.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = summary.status,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = summary.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SyncSummaryCard(summary: DashboardSyncSummary) {
    val accentColor = when (summary.tone) {
        SyncSummaryTone.NEUTRAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        SyncSummaryTone.SUCCESS -> MaterialTheme.colorScheme.primary
        SyncSummaryTone.WARNING -> Color(0xFFF2C879)
        SyncSummaryTone.ERROR -> MaterialTheme.colorScheme.error
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = accentColor.copy(alpha = 0.35f),
                shape = RoundedCornerShape(24.dp),
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .background(
                        color = accentColor,
                        shape = RoundedCornerShape(999.dp),
                    )
                    .heightIn(min = 56.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor,
                )
                Text(
                    text = summary.detail,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                summary.supportingText?.let { supportingText ->
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AutoSyncStatusCard(screenState: MainScreenState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Auto-sync status",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AutoSyncStatusRow("Auto sync", if (screenState.autoSyncEnabled) "On" else "Off")
            AutoSyncStatusRow(
                "Background Health Connect",
                when {
                    screenState.healthStatus == null -> "Unknown"
                    screenState.healthStatus.isBackgroundReadFeatureAvailable -> "Available"
                    else -> "Unavailable"
                },
            )
            AutoSyncStatusRow(
                "Background permission",
                when {
                    screenState.healthStatus == null -> "Unknown"
                    screenState.healthStatus.isBackgroundReadPermissionGranted -> "Granted"
                    else -> "Not granted"
                },
            )
            AutoSyncStatusRow(
                "Today's sync",
                screenState.todaySyncStatusText ?: "Unknown",
            )
            AutoSyncStatusRow(
                "Next check",
                screenState.nextAutoSyncCheckAt?.displayDateTime() ?: "Not scheduled",
            )
            AutoSyncStatusRow(
                "Last attempt",
                screenState.lastSyncAttemptAt?.displayDateTime() ?: "Never",
            )
            AutoSyncStatusRow(
                "Last success",
                screenState.lastSyncSuccessAt?.displayDateTime() ?: "Never",
            )
            AutoSyncStatusRow(
                "Grip Gains session",
                when (screenState.sessionState.status) {
                    GripGainsSessionState.Status.NO_TOKEN -> "Missing"
                    GripGainsSessionState.Status.INVALID_SESSION -> "Invalid"
                    GripGainsSessionState.Status.TOKEN_PRESENT -> "Valid"
                },
            )
            AutoSyncStatusRow(
                "Most recent error",
                screenState.lastSyncFailureMessage ?: screenState.lastSyncSkippedReasonText ?: "None",
            )
        }
    }
}

@Composable
private fun AutoSyncStatusRow(
    label: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun InlineNoteCard(message: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
        ),
        shape = RoundedCornerShape(22.dp),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun UtilityActionsCard(
    screenState: MainScreenState,
    onStartGripGainsSignIn: () -> Unit,
    onClearGripGainsSession: () -> Unit,
    onSetAutoSyncEnabled: (Boolean) -> Unit,
    onReadLatestWeight: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        ),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Auto-sync",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Keep checking in the background and stop once today's weight is synced.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Switch(
                    checked = screenState.autoSyncEnabled,
                    onCheckedChange = onSetAutoSyncEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                )
            }

            Text(
                text = "More actions",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            )

            TextButton(
                onClick = onReadLatestWeight,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("Read latest weight")
                }
            }

            if (screenState.sessionState.status == GripGainsSessionState.Status.TOKEN_PRESENT) {
                TextButton(
                    onClick = onClearGripGainsSession,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Clear saved sign-in")
                    }
                }
            } else {
                TextButton(
                    onClick = onStartGripGainsSignIn,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text("Sign in to Grip Gains")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GripGainsSignInDialog(
    gripGainsWebSignInSessionCapture: GripGainsWebSignInSessionCapture,
    gripGainsLoginWebViewFactory: GripGainsLoginWebViewFactory,
    onDismiss: () -> Unit,
    onSessionCaptured: (GripGainsSession) -> Unit,
) {
    val context = LocalContext.current
    val latestSessionCapture = rememberUpdatedState(gripGainsWebSignInSessionCapture)
    val latestOnSessionCaptured = rememberUpdatedState(onSessionCaptured)
    val webView = remember {
        gripGainsLoginWebViewFactory.create(context) {
            latestSessionCapture.value.capture()?.let { latestOnSessionCaptured.value(it) }
        }
    }

    DisposableEffect(webView) {
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "Grip Gains sign-in",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Button(onClick = onDismiss) {
                        Text("Close")
                    }
                }
                Text(
                    text = "Sign in once. The app will save your session automatically.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                AndroidView(
                    factory = { webView },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 480.dp),
                )
            }
        }
    }
}

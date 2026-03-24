package com.jakemccrary.gravitygainsassist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.website.GripGainsLoginWebViewFactory
import com.jakemccrary.gravitygainsassist.website.GripGainsSession
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import com.jakemccrary.gravitygainsassist.website.GripGainsWebSignInSessionCapture
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    gripGainsWebSignInSessionCapture: GripGainsWebSignInSessionCapture,
    gripGainsLoginWebViewFactory: GripGainsLoginWebViewFactory,
    onGrantPermissions: () -> Unit,
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    var isShowingGripGainsSignIn by rememberSaveable { mutableStateOf(false) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        MainScreenContent(
            screenState = screenState,
            onGrantPermissions = onGrantPermissions,
            onStartGripGainsSignIn = { isShowingGripGainsSignIn = true },
            onClearGripGainsSession = viewModel::clearGripGainsSession,
            onReadLatestWeight = viewModel::readLatestWeightNow,
            onScheduleDailySync = viewModel::scheduleDailySync,
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
    onReadLatestWeight: () -> Unit,
    onScheduleDailySync: () -> Unit,
    onRunSyncNow: () -> Unit,
    innerPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Gravity Gains Assist",
            style = MaterialTheme.typography.headlineMedium,
        )
        StatusRow("Health Connect", screenState.healthStatus.availabilityText())
        StatusRow("Weight permission", screenState.healthStatus.permissionText())
        StatusRow(
            "Background-read feature",
            screenState.healthStatus.backgroundAvailabilityText(),
        )
        StatusRow("Grip Gains session", screenState.sessionState.status.displayText())
        StatusRow("Last weight", screenState.lastWeight.displayText())
        StatusRow("Last submitted weight", screenState.lastSubmittedWeight.displayText())
        StatusRow("Last sync attempt", screenState.lastSyncAttemptAt.displayText())
        StatusRow("Last successful sync", screenState.lastSyncSuccessAt.displayText())
        StatusRow("Last sync skip", screenState.lastSyncSkippedReasonText ?: "none")
        StatusRow("Last sync failure", screenState.lastSyncFailureMessage ?: "none")

        HorizontalDivider(modifier = Modifier.fillMaxWidth())

        Button(onClick = onStartGripGainsSignIn) {
            Text("Sign in to Grip Gains")
        }
        Button(onClick = onClearGripGainsSession) {
            Text("Clear Grip Gains sign-in")
        }
        Button(
            onClick = onGrantPermissions,
            enabled = screenState.healthStatus?.availability == HealthConnectAvailability.AVAILABLE,
        ) {
            Text("Grant permissions")
        }
        Button(
            onClick = onReadLatestWeight,
            enabled = screenState.healthStatus?.availability == HealthConnectAvailability.AVAILABLE,
        ) {
            Text("Read latest weight now")
        }
        Button(onClick = onScheduleDailySync) {
            Text("Schedule daily sync")
        }
        Button(onClick = onRunSyncNow) {
            Text("Run sync now")
        }

        screenState.statusMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun HealthConnectStatus?.availabilityText(): String {
    return when (this?.availability) {
        HealthConnectAvailability.AVAILABLE -> "Available"
        HealthConnectAvailability.UPDATE_REQUIRED -> "Provider update required"
        HealthConnectAvailability.UNAVAILABLE -> "Unavailable"
        null -> "Checking..."
    }
}

private fun HealthConnectStatus?.permissionText(): String {
    return if (this == null) {
        "Checking..."
    } else if (isWeightPermissionGranted) {
        "Granted"
    } else {
        "Not granted"
    }
}

private fun HealthConnectStatus?.backgroundAvailabilityText(): String {
    return when {
        this == null -> "Checking..."
        isBackgroundReadFeatureAvailable -> "Available"
        else -> "Unavailable"
    }
}

private fun WeightReading?.displayText(): String {
    return if (this == null) {
        "none"
    } else {
        "${formatKilograms(kilograms)} kg at ${recordedAt.displayText()}"
    }
}

private fun SubmittedWeight?.displayText(): String {
    return if (this == null) {
        "none"
    } else {
        "${formatPounds(weightLbs)} lbs on $date"
    }
}

private fun Instant?.displayText(): String {
    return this?.let { instant ->
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.US)
            .withZone(ZoneId.systemDefault())
            .format(instant)
    } ?: "none"
}

private fun formatKilograms(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun formatPounds(value: Double): String {
    return String.format(Locale.US, "%.1f", value)
}

private fun GripGainsSessionState.Status.displayText(): String {
    return when (this) {
        GripGainsSessionState.Status.NO_TOKEN -> "Not signed in"
        GripGainsSessionState.Status.TOKEN_PRESENT -> "Signed in"
        GripGainsSessionState.Status.INVALID_SESSION -> "Invalid session"
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

package com.jakemccrary.gravitygainsassist.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.model.HealthConnectStatus
import com.jakemccrary.gravitygainsassist.model.SubmittedWeight
import com.jakemccrary.gravitygainsassist.model.WeightReading
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onGrantPermissions: () -> Unit,
) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        MainScreenContent(
            screenState = screenState,
            onGrantPermissions = onGrantPermissions,
            onTokenChanged = viewModel::onTokenChanged,
            onSaveToken = viewModel::saveToken,
            onClearToken = viewModel::clearToken,
            onReadLatestWeight = viewModel::readLatestWeightNow,
            onScheduleDailySync = viewModel::scheduleDailySync,
            onRunSyncNow = viewModel::runSyncNow,
            innerPadding = innerPadding,
        )
    }
}

@Composable
private fun MainScreenContent(
    screenState: MainScreenState,
    onGrantPermissions: () -> Unit,
    onTokenChanged: (String) -> Unit,
    onSaveToken: () -> Unit,
    onClearToken: () -> Unit,
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

        OutlinedTextField(
            value = screenState.tokenDraft,
            onValueChange = onTokenChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Grip Gains token") },
            singleLine = true,
        )
        Button(onClick = onSaveToken) {
            Text("Save token")
        }
        Button(onClick = onClearToken) {
            Text("Clear token")
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
        GripGainsSessionState.Status.NO_TOKEN -> "No token"
        GripGainsSessionState.Status.TOKEN_PRESENT -> "Token present"
        GripGainsSessionState.Status.INVALID_SESSION -> "Invalid session"
    }
}

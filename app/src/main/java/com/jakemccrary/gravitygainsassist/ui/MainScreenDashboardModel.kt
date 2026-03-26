package com.jakemccrary.gravitygainsassist.ui

import com.jakemccrary.gravitygainsassist.model.HealthConnectAvailability
import com.jakemccrary.gravitygainsassist.website.GripGainsSessionState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

data class DashboardUiModel(
    val contextLine: String?,
    val weightValue: String,
    val weightUnit: String,
    val weightMeta: String,
    val weightSupport: String?,
    val healthSummary: DashboardStatusSummary,
    val sessionSummary: DashboardStatusSummary,
    val syncSummary: DashboardSyncSummary,
    val setupAction: DashboardAction?,
    val statusMessage: String?,
)

data class DashboardStatusSummary(
    val title: String,
    val status: String,
    val detail: String,
)

data class DashboardSyncSummary(
    val title: String,
    val detail: String,
    val tone: SyncSummaryTone,
    val supportingText: String? = null,
)

data class DashboardAction(
    val label: String,
    val type: DashboardActionType,
)

enum class DashboardActionType {
    GRANT_PERMISSIONS,
    SIGN_IN,
}

enum class SyncSummaryTone {
    NEUTRAL,
    SUCCESS,
    WARNING,
    ERROR,
}

fun MainScreenState.toDashboardUiModel(): DashboardUiModel {
    val setupAction = preferredSetupAction()
    return DashboardUiModel(
        contextLine = contextLine(setupAction),
        weightValue = lastWeight?.kilograms?.formatSingleDecimal() ?: "--",
        weightUnit = "kg",
        weightMeta = lastWeight?.let {
            "Last captured: ${it.recordedAt.displayDate()}"
        } ?: "No weight captured yet",
        weightSupport = lastSubmittedWeight?.let {
            "Last submitted: ${it.weightLbs.formatSingleDecimal()} lb on ${it.date.displayDate()}"
        },
        healthSummary = healthSummary(),
        sessionSummary = sessionSummary(),
        syncSummary = syncSummary(),
        setupAction = setupAction,
        statusMessage = statusMessage,
    )
}

private fun MainScreenState.preferredSetupAction(): DashboardAction? {
    return when {
        healthStatus?.availability == HealthConnectAvailability.AVAILABLE &&
            healthStatus?.isWeightPermissionGranted == false -> {
            DashboardAction(
                label = "Grant permissions",
                type = DashboardActionType.GRANT_PERMISSIONS,
            )
        }

        sessionState.status != GripGainsSessionState.Status.TOKEN_PRESENT -> {
            DashboardAction(
                label = "Sign in to Grip Gains",
                type = DashboardActionType.SIGN_IN,
            )
        }

        else -> null
    }
}

private fun MainScreenState.contextLine(setupAction: DashboardAction?): String? {
    return when {
        lastSyncFailureMessage != null -> "Sync issue"
        lastSyncSkippedReasonText != null -> "Sync paused"
        setupAction?.type == DashboardActionType.GRANT_PERMISSIONS -> "Permissions needed"
        setupAction?.type == DashboardActionType.SIGN_IN -> "Sign in needed"
        healthStatus?.availability == HealthConnectAvailability.UPDATE_REQUIRED -> "Health Connect update needed"
        healthStatus?.availability == HealthConnectAvailability.UNAVAILABLE -> "Health Connect unavailable"
        else -> null
    }
}

private fun MainScreenState.healthSummary(): DashboardStatusSummary {
    val currentHealthStatus = healthStatus
    return when {
        currentHealthStatus == null -> DashboardStatusSummary(
            title = "Health Connect",
            status = "Checking",
            detail = "Reading connection status.",
        )

        currentHealthStatus.availability == HealthConnectAvailability.UPDATE_REQUIRED -> {
            DashboardStatusSummary(
                title = "Health Connect",
                status = "Update needed",
                detail = "Update Health Connect before syncing.",
            )
        }

        currentHealthStatus.availability == HealthConnectAvailability.UNAVAILABLE -> {
            DashboardStatusSummary(
                title = "Health Connect",
                status = "Unavailable",
                detail = "This device cannot provide weight data.",
            )
        }

        !currentHealthStatus.isWeightPermissionGranted -> {
            DashboardStatusSummary(
                title = "Health Connect",
                status = "Permissions missing",
                detail = "Grant weight access to read your latest entry.",
            )
        }

        !currentHealthStatus.isBackgroundReadFeatureAvailable -> {
            DashboardStatusSummary(
                title = "Health Connect",
                status = "Linked",
                detail = "Ready to read weight in the app. Background reads are unavailable.",
            )
        }

        !currentHealthStatus.isBackgroundReadPermissionGranted -> {
            DashboardStatusSummary(
                title = "Health Connect",
                status = "Linked",
                detail = "App reads are ready. Background read permission is still off.",
            )
        }

        else -> DashboardStatusSummary(
            title = "Health Connect",
            status = "Linked",
            detail = "Ready to read new weight entries automatically.",
        )
    }
}

private fun MainScreenState.sessionSummary(): DashboardStatusSummary {
    return when (sessionState.status) {
        GripGainsSessionState.Status.NO_TOKEN -> DashboardStatusSummary(
            title = "Grip Gains",
            status = "Needs sign-in",
            detail = "Save a session before the app can submit a sync.",
        )

        GripGainsSessionState.Status.INVALID_SESSION -> DashboardStatusSummary(
            title = "Grip Gains",
            status = "Sign in again",
            detail = "Sign in again to restore syncing.",
        )

        GripGainsSessionState.Status.TOKEN_PRESENT -> DashboardStatusSummary(
            title = "Grip Gains",
            status = "Signed in",
            detail = "Ready to send your latest weight.",
        )
    }
}

private fun MainScreenState.syncSummary(): DashboardSyncSummary {
    return when {
        lastSyncFailureMessage != null -> DashboardSyncSummary(
            title = "Sync issue",
            detail = lastSyncFailureMessage,
            tone = SyncSummaryTone.ERROR,
            supportingText = lastSyncAttemptAt?.let { "Last attempt: ${it.displayDateTime()}" },
        )

        lastSyncSkippedReasonText != null -> DashboardSyncSummary(
            title = "Sync paused",
            detail = lastSyncSkippedReasonText,
            tone = SyncSummaryTone.WARNING,
            supportingText = lastSyncAttemptAt?.let { "Last attempt: ${it.displayDateTime()}" },
        )

        lastSyncSuccessAt != null -> DashboardSyncSummary(
            title = "Last successful sync",
            detail = lastSyncSuccessAt.displayDateTime(),
            tone = SyncSummaryTone.SUCCESS,
            supportingText = lastSubmittedWeight?.let {
                "Submitted ${it.weightLbs.formatSingleDecimal()} lb on ${it.date.displayDate()}"
            },
        )

        lastSyncAttemptAt != null -> DashboardSyncSummary(
            title = "Last sync attempt",
            detail = lastSyncAttemptAt.displayDateTime(),
            tone = SyncSummaryTone.NEUTRAL,
            supportingText = "No successful sync has been recorded yet.",
        )

        else -> DashboardSyncSummary(
            title = "Sync not run yet",
            detail = "Run a sync once Health Connect and Grip Gains are ready.",
            tone = SyncSummaryTone.NEUTRAL,
        )
    }
}

internal fun Instant.displayDateTime(): String {
    return DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(this)
}

private fun Instant.displayDate(): String {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.US)
        .withZone(ZoneId.systemDefault())
        .format(this)
}

private fun java.time.LocalDate.displayDate(): String {
    return DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        .withLocale(Locale.US)
        .format(this)
}

internal fun Double.formatSingleDecimal(): String {
    return String.format(Locale.US, "%.1f", this)
}

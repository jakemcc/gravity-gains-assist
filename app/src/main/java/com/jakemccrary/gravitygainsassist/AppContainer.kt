package com.jakemccrary.gravitygainsassist

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.jakemccrary.gravitygainsassist.data.AppStateRepository
import com.jakemccrary.gravitygainsassist.data.AppStateStore
import com.jakemccrary.gravitygainsassist.data.DefaultAppStateRepository
import com.jakemccrary.gravitygainsassist.data.PreferenceAppStateStore
import com.jakemccrary.gravitygainsassist.health.AndroidHealthConnectManager
import com.jakemccrary.gravitygainsassist.health.DefaultHealthConnectRepository
import com.jakemccrary.gravitygainsassist.health.HealthConnectManager
import com.jakemccrary.gravitygainsassist.health.HealthConnectRepository
import com.jakemccrary.gravitygainsassist.health.HealthPermissionGateway
import com.jakemccrary.gravitygainsassist.sync.AppWorkerFactory
import com.jakemccrary.gravitygainsassist.sync.DefaultSyncCoordinator
import com.jakemccrary.gravitygainsassist.sync.SyncCoordinator
import com.jakemccrary.gravitygainsassist.sync.SyncScheduler
import com.jakemccrary.gravitygainsassist.sync.WorkManagerSyncScheduler
import com.jakemccrary.gravitygainsassist.website.AuthRepository
import com.jakemccrary.gravitygainsassist.website.DefaultAuthRepository
import com.jakemccrary.gravitygainsassist.website.GripGainsPayloadMapper
import com.jakemccrary.gravitygainsassist.website.GripGainsRequestFactory
import com.jakemccrary.gravitygainsassist.website.GripGainsWebsiteSubmissionRepository
import com.jakemccrary.gravitygainsassist.website.HttpUrlConnectionGripGainsApi
import com.jakemccrary.gravitygainsassist.website.SecurePreferencesSessionStore
import com.jakemccrary.gravitygainsassist.website.WebsiteSubmissionRepository
import java.io.File
import java.time.Clock
import java.time.ZoneId

class AppContainer private constructor(
    val healthPermissionGateway: HealthPermissionGateway,
    val appStateRepository: AppStateRepository,
    val healthConnectRepository: HealthConnectRepository,
    val authRepository: AuthRepository,
    val syncScheduler: SyncScheduler,
    val syncCoordinator: SyncCoordinator,
    val workerFactory: AppWorkerFactory,
) {
    companion object {
        fun create(context: Context): AppContainer {
            val applicationContext = context.applicationContext
            val clock = Clock.systemUTC()
            val zoneId = ZoneId.systemDefault()
            val healthPermissionGateway = HealthPermissionGateway()
            val appStateStore: AppStateStore = PreferenceAppStateStore(
                dataStore = PreferenceDataStoreFactory.create(
                    produceFile = {
                        File(applicationContext.filesDir, "datastore/app_state.preferences_pb")
                    },
                ),
            )
            val appStateRepository = DefaultAppStateRepository(appStateStore)
            val healthConnectManager: HealthConnectManager = AndroidHealthConnectManager(
                context = applicationContext,
                clock = clock,
            )
            val healthConnectRepository: HealthConnectRepository = DefaultHealthConnectRepository(
                healthConnectManager = healthConnectManager,
                healthPermissionGateway = healthPermissionGateway,
            )
            val authRepository: AuthRepository = DefaultAuthRepository(
                sessionStore = SecurePreferencesSessionStore(applicationContext),
            )
            val websiteSubmissionRepository: WebsiteSubmissionRepository =
                GripGainsWebsiteSubmissionRepository(
                    authRepository = authRepository,
                    requestFactory = GripGainsRequestFactory(
                        payloadMapper = GripGainsPayloadMapper(zoneId),
                    ),
                    gripGainsApi = HttpUrlConnectionGripGainsApi(),
                )
            val syncCoordinator: SyncCoordinator = DefaultSyncCoordinator(
                healthConnectRepository = healthConnectRepository,
                appStateRepository = appStateRepository,
                websiteSubmissionRepository = websiteSubmissionRepository,
                clock = clock,
            )
            val syncScheduler: SyncScheduler = WorkManagerSyncScheduler(applicationContext)
            val workerFactory = AppWorkerFactory(syncCoordinator)

            return AppContainer(
                healthPermissionGateway = healthPermissionGateway,
                appStateRepository = appStateRepository,
                healthConnectRepository = healthConnectRepository,
                authRepository = authRepository,
                syncScheduler = syncScheduler,
                syncCoordinator = syncCoordinator,
                workerFactory = workerFactory,
            )
        }
    }
}

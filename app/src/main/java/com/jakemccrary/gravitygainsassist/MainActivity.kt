package com.jakemccrary.gravitygainsassist

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.health.connect.client.PermissionController
import com.jakemccrary.gravitygainsassist.ui.MainScreen
import com.jakemccrary.gravitygainsassist.ui.MainViewModel
import com.jakemccrary.gravitygainsassist.ui.AutoSyncPolicy
import com.jakemccrary.gravitygainsassist.ui.theme.GravityGainsAssistTheme
import java.time.Clock
import java.time.ZoneId

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            healthConnectRepository = appContainer.healthConnectRepository,
            appStateRepository = appContainer.appStateRepository,
            authRepository = appContainer.authRepository,
            syncScheduler = appContainer.syncScheduler,
            healthPermissionGateway = appContainer.healthPermissionGateway,
            autoSyncPolicy = AutoSyncPolicy(Clock.systemUTC(), ZoneId.systemDefault()),
        )
    }

    private val appContainer: AppContainer
        get() = (application as GravityGainsApp).appContainer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val permissionLauncher = registerForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) { grantedPermissions ->
            viewModel.onPermissionsResult(grantedPermissions)
        }
        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { _ -> }

        setContent {
            GravityGainsAssistTheme {
                MainScreen(
                    viewModel = viewModel,
                    gripGainsWebSignInSessionCapture = appContainer.gripGainsWebSignInSessionCapture,
                    gripGainsLoginWebViewFactory = appContainer.gripGainsLoginWebViewFactory,
                    onGrantPermissions = {
                        permissionLauncher.launch(viewModel.permissionsToRequest())
                    },
                    onSetAutoSyncEnabled = { enabled ->
                        if (enabled && !hasNotificationPermission()) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        viewModel.setAutoSyncEnabled(enabled)
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    private fun hasNotificationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }
}

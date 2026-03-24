package com.jakemccrary.gravitygainsassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.health.connect.client.PermissionController
import com.jakemccrary.gravitygainsassist.ui.MainScreen
import com.jakemccrary.gravitygainsassist.ui.MainViewModel
import com.jakemccrary.gravitygainsassist.ui.theme.GravityGainsAssistTheme

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(
            healthConnectRepository = appContainer.healthConnectRepository,
            appStateRepository = appContainer.appStateRepository,
            authRepository = appContainer.authRepository,
            syncScheduler = appContainer.syncScheduler,
            healthPermissionGateway = appContainer.healthPermissionGateway,
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

        setContent {
            GravityGainsAssistTheme {
                MainScreen(
                    viewModel = viewModel,
                    onGrantPermissions = {
                        permissionLauncher.launch(viewModel.permissionsToRequest())
                    },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }
}

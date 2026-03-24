package com.jakemccrary.gravitygainsassist

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jakemccrary.gravitygainsassist.ui.theme.GravityGainsAssistTheme

class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GravityGainsAssistTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                    ) {
                        Text(
                            text = getString(R.string.health_permissions_rationale_title),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                        Text(
                            text = getString(R.string.health_permissions_rationale_body),
                            modifier = Modifier.padding(top = 12.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Button(
                            onClick = ::finish,
                            modifier = Modifier.padding(top = 24.dp),
                        ) {
                            Text(text = getString(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

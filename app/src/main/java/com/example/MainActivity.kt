package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.model.ServerStatus
import com.example.ui.MainViewModel
import com.example.ui.ScriptBridgeScreen
import com.example.ui.theme.ScriptBridgeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val uiState by viewModel.uiState.collectAsStateWithLifecycle()

            val luaPickerLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) {
                    viewModel.onScriptUriSelected(uri)
                }
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
            ) { _ ->
                viewModel.toggleServer()
            }

            LaunchedEffect(Unit) {
                viewModel.refreshBatteryOptimizationStatus()
                viewModel.refreshNetworkEndpoints()
            }

            ScriptBridgeTheme {
                ScriptBridgeScreen(
                    uiState = uiState,
                    onToggleServer = {
                        val isStopped = uiState.status == ServerStatus.STOPPED || uiState.status == ServerStatus.ERROR
                        if (isStopped && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val granted = ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) == PackageManager.PERMISSION_GRANTED
                            if (!granted) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                return@ScriptBridgeScreen
                            }
                        }
                        viewModel.toggleServer()
                    },
                    onPortChange = viewModel::onPortInputChange,
                    onTailscaleUrlChange = viewModel::onTailscaleFunnelUrlChange,
                    onRefreshNetwork = viewModel::refreshNetworkEndpoints,
                    onOpenBatterySettings = ::openBatteryOptimizationSettings,
                    onPickLuaFile = {
                        luaPickerLauncher.launch(arrayOf("*/*"))
                    },
                    onReloadLuaFile = viewModel::reloadCurrentScript,
                    onLoadSampleScript = viewModel::loadSampleScript,
                    onAutoReloadToggle = viewModel::setAutoReload,
                    onSendWsCommand = viewModel::sendCommandOrScript,
                    onPushScriptToClients = viewModel::pushLoadedScriptToClients,
                    onClearLogs = viewModel::clearLogs,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshBatteryOptimizationStatus()
        viewModel.refreshNetworkEndpoints()
    }

    private fun openBatteryOptimizationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        }
        runCatching { startActivity(intent) }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", packageName, null),
            )
            runCatching { startActivity(fallback) }
        }
    }
}

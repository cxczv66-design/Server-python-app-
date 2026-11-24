package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "script_bridge_prefs")

data class PersistedBridgeConfig(
    val tailscaleFunnelUrl: String = "",
    val serverPort: Int = 8080,
    val scriptUri: String? = null,
    val scriptName: String? = null,
    val autoReloadScript: Boolean = true,
)

class BridgePreferences(private val context: Context) {

    private object Keys {
        val TAILSCALE_FUNNEL_URL = stringPreferencesKey("tailscale_funnel_url")
        val SERVER_PORT = intPreferencesKey("server_port")
        val SCRIPT_URI = stringPreferencesKey("script_uri")
        val SCRIPT_NAME = stringPreferencesKey("script_name")
        val AUTO_RELOAD_SCRIPT = booleanPreferencesKey("auto_reload_script")
    }

    val configFlow: Flow<PersistedBridgeConfig> = context.dataStore.data.map { prefs ->
        PersistedBridgeConfig(
            tailscaleFunnelUrl = prefs[Keys.TAILSCALE_FUNNEL_URL].orEmpty(),
            serverPort = prefs[Keys.SERVER_PORT] ?: 8080,
            scriptUri = prefs[Keys.SCRIPT_URI],
            scriptName = prefs[Keys.SCRIPT_NAME],
            autoReloadScript = prefs[Keys.AUTO_RELOAD_SCRIPT] ?: true,
        )
    }

    suspend fun saveTailscaleFunnelUrl(url: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TAILSCALE_FUNNEL_URL] = url.trim()
        }
    }

    suspend fun saveServerPort(port: Int) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SERVER_PORT] = port
        }
    }

    suspend fun saveScriptSelection(uri: String, fileName: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SCRIPT_URI] = uri
            prefs[Keys.SCRIPT_NAME] = fileName
        }
    }

    suspend fun saveAutoReload(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AUTO_RELOAD_SCRIPT] = enabled
        }
    }
}

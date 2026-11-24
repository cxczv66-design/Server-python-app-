package com.example.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.example.model.LoadedScript
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object LocalScriptLoader {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.US)

    suspend fun loadFromUri(context: Context, uri: Uri): Result<LoadedScript> = withContext(Dispatchers.IO) {
        runCatching {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }

            val fileName = resolveDisplayName(context, uri)
            val content = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader(Charsets.UTF_8).readText()
            } ?: error("Unable to open input stream for selected file")

            LoadedScript(
                uriString = uri.toString(),
                fileName = fileName,
                content = content,
                byteSize = content.toByteArray(Charsets.UTF_8).size,
                lastLoadedTime = currentTimeString(),
            )
        }
    }

    fun createSampleScript(): LoadedScript {
        val sample = """
            -- ScriptBridge Sample Client Check (script.lua)
            local HttpService = game:GetService("HttpService")
            local BASE_URL = "http://127.0.0.1:8080"

            print("[ScriptBridge] Connected to local server!")

            -- Example: Report status back to the Android log console
            pcall(function()
                request({
                    Url = BASE_URL .. "/report",
                    Method = "POST",
                    Headers = { ["Content-Type"] = "text/plain" },
                    Body = "Client heartbeat OK from Lua"
                })
            end)
        """.trimIndent()

        return LoadedScript(
            uriString = null,
            fileName = "sample_script.lua",
            content = sample,
            byteSize = sample.toByteArray(Charsets.UTF_8).size,
            lastLoadedTime = currentTimeString(),
        )
    }

    private fun resolveDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        val name = cursor.getString(idx)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "script.lua"
    }

    private fun currentTimeString(): String = synchronized(timeFormat) {
        timeFormat.format(Date())
    }
}

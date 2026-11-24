package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.LoadedScript
import com.example.server.BridgeHttpServer
import com.example.server.BridgeStateHolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("ScriptBridge", appName)
    }

    @Test
    fun `bridgeHttpServer serves health script and poll without watchService errors`() {
        val freePort = ServerSocket(0).use { it.localPort }
        val scriptContent = "print('test lua script')"
        BridgeStateHolder.setLoadedScript(
            LoadedScript(
                uriString = null,
                fileName = "test.lua",
                content = scriptContent,
                byteSize = scriptContent.toByteArray().size,
                lastLoadedTime = "12:00:00",
            ),
        )
        BridgeStateHolder.clearPollQueue()
        BridgeStateHolder.enqueuePollCommand("CMD_1")

        val server = BridgeHttpServer(freePort)
        server.start()
        try {
            val healthConn = URL("http://127.0.0.1:$freePort/health").openConnection() as HttpURLConnection
            assertEquals(200, healthConn.responseCode)
            val healthBody = healthConn.inputStream.bufferedReader().readText()
            assertTrue(healthBody.contains("\"status\":\"ok\""))

            val scriptConn = URL("http://127.0.0.1:$freePort/script.lua").openConnection() as HttpURLConnection
            assertEquals(200, scriptConn.responseCode)
            assertEquals(scriptContent, scriptConn.inputStream.bufferedReader().readText())

            val pollConn = URL("http://127.0.0.1:$freePort/poll").openConnection() as HttpURLConnection
            assertEquals(200, pollConn.responseCode)
            val pollBody = pollConn.inputStream.bufferedReader().readText()
            assertTrue(pollBody.contains("CMD_1"))
        } finally {
            server.stop()
            BridgeStateHolder.setLoadedScript(null)
            BridgeStateHolder.clearPollQueue()
        }
    }
}

package com.hermes.portal

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation test designed to act as a background server on the device.
 * It uses Ktor to expose HTTP endpoints, allowing UI automation and inspection
 * without requiring accessibility service permissions on the car device.
 */
@RunWith(AndroidJUnit4::class)
class HermesPortalServerTest {

    companion object {
        private const val TAG = "AutomatorServerTest"
    }

    @Test
    fun runAutomatorServer() {
        val args = InstrumentationRegistry.getArguments()
        
        Log.d(TAG, "Starting HermesPortalServerTest")
        
        // Pull port from instrumentation arguments, default to 9081
        val portStr = args.getString("port", "9081")
        val port = portStr.toIntOrNull() ?: 9081
        
        Log.d(TAG, "Using port: $port")

        var service: HermesPortalService? = null
        
        try {
            service = HermesPortalService(port)
            service.start()
            
            Log.d(TAG, "Server started, awaiting stop signal...")
            
            // Block the test thread until /stop is called
            service.awaitStop()
            
            Log.d(TAG, "Stop signal received, shutting down server...")
        } catch (e: Exception) {
            Log.e(TAG, "Error in HermesPortalServerTest", e)
            throw e
        } finally {
            // Gracefully stop the server, test completes when it exits.
            service?.let {
                Log.d(TAG, "Stopping service in finally block")
                it.stop()
            }
            Log.d(TAG, "HermesPortalServerTest completed")
        }
    }
}

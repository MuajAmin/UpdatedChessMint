package com.example.updatedchessmint.bridge

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.example.updatedchessmint.engine.EngineProcess
import org.json.JSONObject

/**
 * JavaScript interface bridge between WebView (Mint.js) and the native engine process.
 *
 * Methods annotated with @JavascriptInterface are callable from JavaScript
 * via window.ChessMintAndroid.methodName()
 */
class ChessMintBridge(
    private val webView: WebView,
    private val engineProcess: EngineProcess,
    private val onSettingsRequested: () -> Unit = {}
) {
    companion object {
        private const val TAG = "ChessMintBridge"
        const val BRIDGE_NAME = "ChessMintAndroid"
    }

    /**
     * Called from JavaScript to send a UCI command to the engine.
     */
    @JavascriptInterface
    fun sendToEngine(command: String) {
        Log.d(TAG, "JS -> Engine: $command")
        engineProcess.sendCommand(command)
    }

    /**
     * Called from JavaScript to check if engine is running.
     */
    @JavascriptInterface
    fun isEngineRunning(): Boolean {
        return engineProcess.isRunning.value
    }

    /**
     * Called from JavaScript to get the engine name.
     */
    @JavascriptInterface
    fun getEngineName(): String {
        return engineProcess.engineName.value
    }

    /**
     * Called from JavaScript to open the settings panel.
     */
    @JavascriptInterface
    fun openSettings() {
        webView.post { onSettingsRequested() }
    }

    /**
     * Called from JavaScript to log messages to Android Logcat.
     */
    @JavascriptInterface
    fun logMessage(level: String, message: String) {
        when (level) {
            "error" -> Log.e(TAG, "JS: $message")
            "warn" -> Log.w(TAG, "JS: $message")
            "info" -> Log.i(TAG, "JS: $message")
            else -> Log.d(TAG, "JS: $message")
        }
    }

    /**
     * Sends an engine response line from native back into JavaScript.
     * Must be called on the main thread.
     */
    fun sendToJavaScript(line: String) {
        val quotedLine = JSONObject.quote(line)
        webView.post {
            webView.evaluateJavascript(
                "if(window.onEngineResponse){window.onEngineResponse($quotedLine);}",
                null
            )
        }
    }
}

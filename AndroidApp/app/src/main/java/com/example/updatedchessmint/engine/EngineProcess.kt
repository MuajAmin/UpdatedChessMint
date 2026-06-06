package com.example.updatedchessmint.engine

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * Manages a UCI chess engine subprocess.
 * Handles stdin/stdout communication and exposes engine output as a Flow.
 */
class EngineProcess(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "EngineProcess"
    }

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var readerJob: Job? = null

    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _engineName = MutableStateFlow("No Engine")
    val engineName: StateFlow<String> = _engineName.asStateFlow()

    /**
     * Starts the engine process from the given executable file.
     */
    fun start(engineFile: File, name: String = "Engine") {
        stop() // Stop any existing process

        try {
            _engineName.value = name
            val processBuilder = ProcessBuilder(engineFile.absolutePath)
            processBuilder.redirectErrorStream(true)
            processBuilder.directory(engineFile.parentFile)

            process = processBuilder.start()
            writer = OutputStreamWriter(process!!.outputStream)
            _isRunning.value = true

            // Start reading stdout in a coroutine
            readerJob = scope.launch(Dispatchers.IO) {
                try {
                    val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                    var line: String?
                    while (isActive) {
                        line = reader.readLine()
                        if (line == null) break
                        Log.d(TAG, "Engine: $line")
                        _outputFlow.emit(line)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading engine output", e)
                } finally {
                    _isRunning.value = false
                }
            }

            Log.i(TAG, "Engine process started: ${engineFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start engine", e)
            _isRunning.value = false
        }
    }

    /**
     * Sends a UCI command to the engine.
     */
    fun sendCommand(command: String) {
        try {
            writer?.let { w ->
                w.write("$command\n")
                w.flush()
                Log.d(TAG, "Sent: $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: $command", e)
        }
    }

    /**
     * Stops the engine process and cleans up resources.
     */
    fun stop() {
        readerJob?.cancel()
        readerJob = null

        try {
            writer?.close()
        } catch (_: Exception) {}
        writer = null

        try {
            process?.destroy()
        } catch (_: Exception) {}
        process = null

        _isRunning.value = false
        _engineName.value = "No Engine"
    }
}

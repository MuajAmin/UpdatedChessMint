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
        private const val MAX_PENDING_COMMANDS = 128
    }

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var readerJob: Job? = null
    private val lock = Any()
    private val pendingCommands = ArrayDeque<String>()

    private val _outputFlow = MutableSharedFlow<String>(extraBufferCapacity = 256)
    val outputFlow: SharedFlow<String> = _outputFlow.asSharedFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _engineName = MutableStateFlow("No Engine")
    val engineName: StateFlow<String> = _engineName.asStateFlow()

    /**
     * Starts the engine process from the given executable file.
     */
    fun start(engineFile: File, name: String = "Engine"): Boolean {
        stop(clearPendingCommands = false) // Stop any existing process

        return try {
            _engineName.value = name
            val processBuilder = ProcessBuilder(engineFile.absolutePath)
            processBuilder.redirectErrorStream(true)
            processBuilder.directory(engineFile.parentFile)

            val startedProcess = processBuilder.start()
            process = startedProcess
            writer = OutputStreamWriter(startedProcess.outputStream)
            _isRunning.value = true

            // Start reading stdout in a coroutine
            readerJob = scope.launch(Dispatchers.IO) {
                try {
                    BufferedReader(InputStreamReader(startedProcess.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            Log.d(TAG, "Engine: $line")
                            _outputFlow.emit(line)
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Error reading engine output", e)
                    }
                } finally {
                    _isRunning.value = false
                }
            }

            flushPendingCommands()
            Log.i(TAG, "Engine process started: ${engineFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start engine", e)
            _isRunning.value = false
            _engineName.value = "No Engine"
            false
        }
    }

    /**
     * Sends a UCI command to the engine.
     */
    fun sendCommand(command: String) {
        val targetWriter = synchronized(lock) {
            val activeWriter = writer
            if (activeWriter == null || !_isRunning.value) {
                enqueuePendingCommand(command)
                null
            } else {
                activeWriter
            }
        }

        try {
            targetWriter?.write("$command\n")
            targetWriter?.flush()
            if (targetWriter != null) Log.d(TAG, "Sent: $command")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending command: $command", e)
        }
    }

    private fun enqueuePendingCommand(command: String) {
        if (pendingCommands.size == MAX_PENDING_COMMANDS) {
            pendingCommands.removeFirst()
        }
        pendingCommands.addLast(command)
        Log.d(TAG, "Queued until engine starts: $command")
    }

    private fun flushPendingCommands() {
        val commands = synchronized(lock) {
            val copy = pendingCommands.toList()
            pendingCommands.clear()
            copy
        }
        commands.forEach { sendCommand(it) }
    }

    /**
     * Stops the engine process and cleans up resources.
     */
    fun stop(clearPendingCommands: Boolean = true) {
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

        if (clearPendingCommands) {
            synchronized(lock) { pendingCommands.clear() }
        }
        _isRunning.value = false
        _engineName.value = "No Engine"
    }
}

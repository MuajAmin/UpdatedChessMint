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

        /**
         * Returns true if the engine output line should be forwarded to the
         * JavaScript bridge.  Mint.js only processes a handful of UCI line
         * types; everything else (currmove, nodes, nps, hashfull, tbhits …)
         * is pure noise that chokes the main-thread evaluateJavascript path.
         *
         * Allowed lines:
         *  • "uciok"
         *  • "readyok"
         *  • "bestmove …"
         *  • "option name …"     (engine capability discovery)
         *  • "info … score … pv …" (actual PV evaluation data)
         *  • "Load eval file success: 1"  (NNUE load confirmation)
         */
        private fun shouldForwardLine(line: String): Boolean {
            // Fast exact matches first
            if (line == "uciok" || line == "readyok") return true
            if (line.startsWith("bestmove ")) return true
            if (line.startsWith("option name ")) return true
            if (line == "Load eval file success: 1") return true

            // For "info" lines only forward those containing a PV with a score.
            // Lines like "info depth 5 currmove e2e4" lack "pv" and are noise.
            if (line.startsWith("info ")) {
                return line.contains(" pv ") && line.contains(" score ")
            }

            // Unknown/unexpected lines — forward to be safe
            return true
        }
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
            val startedWriter = OutputStreamWriter(startedProcess.outputStream)
            synchronized(lock) {
                process = startedProcess
                writer = startedWriter
                _isRunning.value = true
            }

            // Start reading stdout in a coroutine
            readerJob = scope.launch(Dispatchers.IO) {
                try {
                    BufferedReader(InputStreamReader(startedProcess.inputStream)).use { reader ->
                        while (isActive) {
                            val line = reader.readLine() ?: break
                            // Only forward lines the JS bridge actually processes;
                            // noisy intermediate info lines are discarded here to
                            // avoid flooding the main-thread evaluateJavascript.
                            if (shouldForwardLine(line)) {
                                Log.d(TAG, "Engine: $line")
                                _outputFlow.emit(line)
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (isActive) {
                        Log.e(TAG, "Error reading engine output", e)
                    }
                } finally {
                    synchronized(lock) {
                        if (process === startedProcess) {
                            process = null
                            writer = null
                            _isRunning.value = false
                            _engineName.value = "No Engine"
                        }
                    }
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

        val (writerToClose, processToDestroy) = synchronized(lock) {
            val currentWriter = writer
            val currentProcess = process
            writer = null
            process = null

            if (clearPendingCommands) {
                pendingCommands.clear()
            }

            currentWriter to currentProcess
        }

        try {
            writerToClose?.close()
        } catch (_: Exception) {}

        try {
            processToDestroy?.destroy()
        } catch (_: Exception) {}

        _isRunning.value = false
        _engineName.value = "No Engine"
    }
}

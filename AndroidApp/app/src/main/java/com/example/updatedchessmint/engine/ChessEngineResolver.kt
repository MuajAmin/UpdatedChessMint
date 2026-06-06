package com.example.updatedchessmint.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileOutputStream

/**
 * Data class representing a discovered OEX chess engine.
 */
data class ChessEngine(
    val name: String,
    val packageName: String,
    val authority: String,
    val fileName: String
)

/**
 * Discovers chess engines installed via Open Exchange (OEX) protocol.
 *
 * Scans for apps that respond to the "intent.chess.provider.ENGINE" action
 * and queries their ContentProvider for the engine binary matching the
 * device's CPU architecture.
 */
class ChessEngineResolver(private val context: Context) {

    companion object {
        private const val OEX_ACTION = "intent.chess.provider.ENGINE"
        private const val META_AUTHORITY = "chess.provider.engine.authority"
    }

    /**
     * Finds all installed OEX engine apps.
     */
    fun resolveEngines(): List<ChessEngine> {
        val engines = mutableListOf<ChessEngine>()
        val intent = Intent(OEX_ACTION)

        val resolveInfoList: List<ResolveInfo> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        }

        for (resolveInfo in resolveInfoList) {
            val activityInfo = resolveInfo.activityInfo ?: continue
            val metaData = activityInfo.metaData ?: continue
            val authority = metaData.getString(META_AUTHORITY) ?: continue
            val packageName = activityInfo.packageName
            val appName = activityInfo.loadLabel(context.packageManager).toString()

            engines.add(
                ChessEngine(
                    name = appName,
                    packageName = packageName,
                    authority = authority,
                    fileName = ""
                )
            )
        }
        return engines
    }

    /**
     * Copies the engine binary from the OEX provider to the app's private files directory.
     * Returns the File pointing to the executable, or null if copy failed.
     */
    fun copyEngineToFiles(engine: ChessEngine): File? {
        return try {
            val abi = getCurrentAbi()
            val contentUri = Uri.parse("content://${engine.authority}/$abi")

            val inputStream = context.contentResolver.openInputStream(contentUri) ?: return null
            val engineFile = File(context.filesDir, "engine_${engine.packageName.replace(".", "_")}")

            FileOutputStream(engineFile).use { output ->
                inputStream.copyTo(output)
            }
            inputStream.close()

            // Make the binary executable
            engineFile.setExecutable(true, true)
            engineFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Returns the device's primary ABI string.
     */
    private fun getCurrentAbi(): String {
        val abis = Build.SUPPORTED_ABIS
        return when {
            abis.contains("arm64-v8a") -> "arm64-v8a"
            abis.contains("armeabi-v7a") -> "armeabi-v7a"
            abis.contains("x86_64") -> "x86_64"
            abis.contains("x86") -> "x86"
            else -> abis.firstOrNull() ?: "armeabi-v7a"
        }
    }
}

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.Q
        ) {
            throw UnsupportedOperationException(
                "Android 10+ blocks executing OEX engines copied into app storage for this target SDK. Bundle a native engine in the APK or use an engine service."
            )
        }

        return try {
            val abi = getCurrentAbi()
            val contentUri = Uri.parse("content://${engine.authority}/$abi")

            val engineFile = File(context.filesDir, "engine_${engine.packageName.replace(".", "_")}")

            context.contentResolver.openInputStream(contentUri)?.use { input ->
                FileOutputStream(engineFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            if (!engineFile.setExecutable(true, true)) {
                return null
            }

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

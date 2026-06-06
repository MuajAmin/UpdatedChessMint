package com.example.updatedchessmint.engine

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.XmlResourceParser
import android.net.Uri
import android.os.Build
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Data class representing a discovered OEX chess engine.
 */
data class ChessEngine(
    val name: String,
    val packageName: String,
    val authority: String,
    val fileName: String,
    val targetAbis: Set<String> = emptySet(),
    val source: EngineSource = EngineSource.OEX
)

enum class EngineSource {
    Bundled,
    OEX
}

/**
 * Discovers chess engines installed via Open Exchange (OEX) protocol.
 *
 * Scans for apps that respond to the "intent.chess.provider.ENGINE" action
 * and queries their ContentProvider for the engine binary matching the
 * device's CPU architecture.
 */
class ChessEngineResolver(private val context: Context) {

    companion object {
        private const val TAG = "ChessEngineResolver"
        private const val OEX_ACTION = "intent.chess.provider.ENGINE"
        private const val META_AUTHORITY = "chess.provider.engine.authority"
        private const val ENGINE_LIST_XML = "enginelist"
        private const val ENGINE_TAG = "engine"
    }

    /**
     * Finds all installed OEX engine apps.
     */
    fun resolveEngines(): List<ChessEngine> {
        val engines = linkedMapOf<String, ChessEngine>()

        findBundledEngine()?.let { bundledEngine ->
            engines[engineKey(bundledEngine)] = bundledEngine
        }

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
            resolveEnginesForProvider(resolveInfo).forEach { engine ->
                engines[engineKey(engine)] = engine
            }
        }
        return engines.values.toList()
    }

    /**
     * Copies the engine binary from the OEX provider to the app's private files directory.
     * Returns the File pointing to the executable, or null if copy failed.
     */
    fun copyEngineToFiles(engine: ChessEngine): File? {
        if (engine.source == EngineSource.Bundled) {
            return File(engine.fileName).takeIf { it.exists() && it.canExecute() }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            context.applicationInfo.targetSdkVersion >= Build.VERSION_CODES.Q
        ) {
            throw UnsupportedOperationException(
                "Android 10+ blocks executing OEX engines copied into app storage for this target SDK. Bundle a native engine in the APK or use an engine service."
            )
        }

        return try {
            if (engine.authority.isBlank()) return null

            val engineFile = File(
                context.filesDir,
                "engine_${sanitizeFileName(engine.packageName)}_${sanitizeFileName(engine.fileName.ifBlank { getCurrentAbi() })}"
            )

            val copied = contentUrisFor(engine).any { contentUri ->
                try {
                    context.contentResolver.openInputStream(contentUri)?.use { input ->
                        FileOutputStream(engineFile).use { output ->
                            input.copyTo(output)
                        }
                    } ?: return@any false
                    engineFile.length() > 0L
                } catch (e: Exception) {
                    Log.w(TAG, "Could not copy OEX engine from $contentUri", e)
                    false
                }
            }

            if (!copied) return null

            if (!engineFile.setExecutable(true, true)) {
                return null
            }

            engineFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun resolveEnginesForProvider(resolveInfo: ResolveInfo): List<ChessEngine> {
        val activityInfo = resolveInfo.activityInfo ?: return emptyList()
        val metaData = activityInfo.metaData ?: return emptyList()
        val authority = metaData.getString(META_AUTHORITY) ?: return emptyList()
        val packageName = activityInfo.packageName ?: return emptyList()
        val appName = activityInfo.loadLabel(context.packageManager).toString()

        val engines = parseEngineListXml(packageName, authority, appName)
        if (engines.isNotEmpty()) return engines

        return listOf(
            ChessEngine(
                name = appName,
                packageName = packageName,
                authority = authority,
                fileName = ""
            )
        )
    }

    private fun parseEngineListXml(
        packageName: String,
        authority: String,
        appName: String
    ): List<ChessEngine> {
        return try {
            val resources = context.packageManager.getResourcesForApplication(packageName)
            val resId = resources.getIdentifier(ENGINE_LIST_XML, "xml", packageName)
            if (resId == 0) return emptyList()

            val parser = resources.getXml(resId)
            try {
                parseEngineListXml(parser, packageName, authority, appName)
            } finally {
                parser.close()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not parse OEX engine list for $packageName", e)
            emptyList()
        }
    }

    private fun parseEngineListXml(
        parser: XmlResourceParser,
        packageName: String,
        authority: String,
        appName: String
    ): List<ChessEngine> {
        val result = mutableListOf<ChessEngine>()
        var eventType = parser.eventType

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == ENGINE_TAG) {
                val fileName = parser.getAttributeValue(null, "filename")
                    ?: parser.getAttributeValue(null, "fileName")
                    ?: parser.getAttributeValue(null, "file")
                val targetAbis = parseTargetAbis(parser.getAttributeValue(null, "target"))

                if (!fileName.isNullOrBlank() && isTargetCompatible(targetAbis)) {
                    val engineName = parser.getAttributeValue(null, "name")
                        ?.takeIf { it.isNotBlank() }
                        ?: appName
                    result.add(
                        ChessEngine(
                            name = engineName,
                            packageName = packageName,
                            authority = authority,
                            fileName = fileName,
                            targetAbis = targetAbis
                        )
                    )
                }
            }
            eventType = parser.next()
        }

        return result
    }

    private fun contentUrisFor(engine: ChessEngine): List<Uri> {
        val paths = mutableListOf<String>()
        if (engine.fileName.isNotBlank()) {
            paths.add(engine.fileName)
        }
        paths.add(getCurrentAbi())

        return paths
            .distinct()
            .map { path ->
                Uri.Builder()
                    .scheme("content")
                    .authority(engine.authority)
                    .appendPath(path)
                    .build()
            }
    }

    private fun parseTargetAbis(target: String?): Set<String> {
        if (target.isNullOrBlank()) return emptySet()
        return target
            .split('|', ',', ';', ' ', '\n', '\t')
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { normalizeAbi(it) }
            .toSet()
    }

    private fun isTargetCompatible(targetAbis: Set<String>): Boolean {
        if (targetAbis.isEmpty() || "*" in targetAbis || "all" in targetAbis) {
            return true
        }
        val supportedAbis = Build.SUPPORTED_ABIS.map { normalizeAbi(it) }.toSet()
        return targetAbis.any { it in supportedAbis }
    }

    private fun findBundledEngine(): ChessEngine? {
        val nativeDir = File(context.applicationInfo.nativeLibraryDir ?: return null)
        val candidates = listOf(
            "libstockfish.so",
            "libstockfish_android.so",
            "libengine.so",
            "stockfish"
        )
        val engineFile = candidates
            .asSequence()
            .map { File(nativeDir, it) }
            .firstOrNull { it.exists() && it.canExecute() }
            ?: nativeDir.listFiles()
                ?.firstOrNull { file ->
                    file.isFile &&
                        file.canExecute() &&
                        file.name.contains("stockfish", ignoreCase = true)
                }

        return engineFile?.let {
            ChessEngine(
                name = "Bundled Stockfish",
                packageName = context.packageName,
                authority = "",
                fileName = it.absolutePath,
                targetAbis = emptySet(),
                source = EngineSource.Bundled
            )
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

    private fun normalizeAbi(abi: String): String {
        return when (abi.lowercase(Locale.US)) {
            "arm", "armv7", "armv7a", "armeabi" -> "armeabi-v7a"
            "arm64", "armv8", "armv8a", "aarch64" -> "arm64-v8a"
            "x64", "amd64" -> "x86_64"
            else -> abi
        }
    }

    private fun engineKey(engine: ChessEngine): String {
        return listOf(engine.source.name, engine.packageName, engine.authority, engine.fileName)
            .joinToString("|")
    }

    private fun sanitizeFileName(value: String): String {
        return value
            .ifBlank { "unknown" }
            .map { char ->
                if (char.isLetterOrDigit() || char == '_' || char == '-' || char == '.') char else '_'
            }
            .joinToString("")
    }
}

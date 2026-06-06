package com.example.updatedchessmint

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.updatedchessmint.bridge.ChessMintBridge
import com.example.updatedchessmint.engine.ChessEngine
import com.example.updatedchessmint.engine.ChessEngineResolver
import com.example.updatedchessmint.engine.EngineProcess
import com.example.updatedchessmint.theme.*
import androidx.compose.animation.core.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

class MainActivity : ComponentActivity() {

    private var engineProcess: EngineProcess? = null
    private var bridge: ChessMintBridge? = null
    private var cachedCombinedScript: String? = null
    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Set dark status bar and navigation bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.setSystemBarsAppearance(
                0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            )
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        )

        setContent {
            UpdatedChessMintTheme(dynamicColor = false) {
                ChessMintApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engineProcess?.stop()
        engineScope.cancel()
    }

    @SuppressLint("SetJavaScriptEnabled")
    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChessMintApp() {
        val context = LocalContext.current
        val engine = remember { EngineProcess(engineScope) }
        var showControlPanel by remember { mutableStateOf(false) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        var engineRunning by remember { mutableStateOf(false) }
        var currentEngineName by remember { mutableStateOf("No Engine") }
        var availableEngines by remember { mutableStateOf<List<ChessEngine>>(emptyList()) }
        var consoleLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var pageLoaded by remember { mutableStateOf(false) }
        val lowPowerMode = remember { isLowPowerAndroidDevice() }

        // Settings state
        var depth by remember { mutableIntStateOf(defaultSearchDepth()) }
        var multiPV by remember { mutableIntStateOf(defaultMultiPv()) }
        var showHints by remember { mutableStateOf(true) }
        var showEvalBar by remember { mutableStateOf(true) }
        var showDepthBar by remember { mutableStateOf(true) }
        var moveAnalysis by remember { mutableStateOf(true) }
        var autoMove by remember { mutableStateOf(false) }

        DisposableEffect(engine) {
            engineProcess = engine
            onDispose {
                engine.stop()
                if (engineProcess === engine) {
                    engineProcess = null
                }
            }
        }

        // Discover engines
        LaunchedEffect(Unit) {
            val engines = withContext(Dispatchers.IO) {
                val resolver = ChessEngineResolver(context)
                resolver.resolveEngines()
            }
            availableEngines = engines
        }

        // Collect engine state
        LaunchedEffect(engine) {
            launch {
                engine.isRunning.collectLatest { running ->
                    engineRunning = running
                }
            }
            launch {
                engine.engineName.collectLatest { name ->
                    currentEngineName = name
                }
            }
            launch {
                engine.outputFlow.collectLatest { line ->
                    // Send engine output to the WebView
                    bridge?.sendToJavaScript(line)
                    // Add to console log (keep last 50 lines)
                    consoleLog = (consoleLog + line).takeLast(50)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // WebView fills the entire screen
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = false
                            allowContentAccess = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            databaseEnabled = true
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                            textZoom = 100
                            mediaPlaybackRequiresUserGesture = false
                            javaScriptCanOpenWindowsAutomatically = false
                            userAgentString = settings.userAgentString.replace(
                                "; wv", ""
                            ) // Remove WebView indicator from UA
                            cacheMode = WebSettings.LOAD_DEFAULT
                            setSupportMultipleWindows(false)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                safeBrowsingEnabled = false
                                offscreenPreRaster = !isLowPowerAndroidDevice()
                            }
                            disableWebViewDarkening(this)
                        }

                        // The default layer avoids black WebView surfaces on some Android GPU/WebView builds.
                        setLayerType(View.LAYER_TYPE_NONE, null)
                        setBackgroundColor(android.graphics.Color.WHITE)

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        // Create the bridge
                        val b = ChessMintBridge(this, engine) { showControlPanel = true }
                        bridge = b
                        addJavascriptInterface(b, ChessMintBridge.BRIDGE_NAME)

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                consoleMessage?.let {
                                    Log.d("WebView", "${it.messageLevel()}: ${it.message()}")
                                }
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return shouldBlockTopLevelNavigation(url)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                if (isWebUrl(url)) {
                                    pageLoaded = false
                                }
                            }

                            override fun onPageCommitVisible(view: WebView?, url: String?) {
                                super.onPageCommitVisible(view, url)
                                if (isWebUrl(url)) {
                                    pageLoaded = true
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (isWebUrl(url)) {
                                    pageLoaded = true
                                }
                                if (isAllowedChessUrl(url)) {
                                    injectScripts(view, ctx)
                                }
                            }
                        }

                        webView = this
                        loadUrl("https://www.chess.com")
                    }
                }
            )

            // Loading overlay
            if (!pageLoaded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = AccentPurple,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Loading Chess.com...",
                            color = TextPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Preparing assistant engine",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            // Floating Action Button - Control Panel Toggle
            if (pageLoaded) {
                val infiniteTransition = rememberInfiniteTransition(label = "fabPulse")
                val pulseScale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = if (engineRunning) 1.25f else 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                val pulseAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.5f,
                    targetValue = 0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1400, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(54.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Pulsing Glow Ring behind FAB
                    if (engineRunning) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .scale(pulseScale)
                                .alpha(pulseAlpha)
                                .clip(CircleShape)
                                .background(AccentGreen)
                        )
                    }

                    // Main FAB
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (engineRunning) {
                                    Brush.verticalGradient(
                                        colors = listOf(AccentPurple, Color(0xFF673AB7))
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(Color(0xFF27272A), Color(0xFF18181B))
                                    )
                                }
                            )
                            .border(
                                width = 1.dp,
                                color = if (engineRunning) AccentGreen.copy(alpha = 0.6f) else GlassBorder,
                                shape = CircleShape
                            )
                            .clickable { showControlPanel = !showControlPanel },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (showControlPanel) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "Toggle Control Panel",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Integrated status badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 1.dp)
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (engineRunning) AccentGreen else AccentRed)
                            .border(1.dp, DarkBackground, CircleShape)
                    )
                }
            }

            // Bottom Sheet Control Panel
            AnimatedVisibility(
                visible = showControlPanel,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ControlPanel(
                    engineRunning = engineRunning,
                    currentEngineName = currentEngineName,
                    availableEngines = availableEngines,
                    consoleLog = consoleLog,
                    depth = depth,
                    multiPV = multiPV,
                    showHints = showHints,
                    showEvalBar = showEvalBar,
                    showDepthBar = showDepthBar,
                    moveAnalysis = moveAnalysis,
                    autoMove = autoMove,
                    lowPowerMode = lowPowerMode,
                    onDepthChange = { d ->
                        val clampedDepth = clampDepthForDevice(d)
                        depth = clampedDepth
                        updateOption(webView, "option-depth", clampedDepth)
                    },
                    onMultiPVChange = { m ->
                        val clampedMultiPv = clampMultiPvForDevice(m)
                        multiPV = clampedMultiPv
                        updateOption(webView, "option-multipv", clampedMultiPv)
                    },
                    onShowHintsChange = { v ->
                        showHints = v
                        updateOption(webView, "option-show-hints", v)
                    },
                    onShowEvalBarChange = { v ->
                        showEvalBar = v
                        updateOption(webView, "option-evaluation-bar", v)
                    },
                    onShowDepthBarChange = { v ->
                        showDepthBar = v
                        updateOption(webView, "option-depth-bar", v)
                    },
                    onMoveAnalysisChange = { v ->
                        moveAnalysis = v
                        updateOption(webView, "option-move-analysis", v)
                    },
                    onAutoMoveChange = { v ->
                        autoMove = v
                        updateOption(webView, "option-legit-auto-move", v)
                    },
                    onSelectEngine = { engine ->
                        engineScope.launch {
                            startEngine(context, engine, webView)
                        }
                    },
                    onStopEngine = {
                        engineProcess?.stop()
                    },
                    onDismiss = { showControlPanel = false }
                )
            }
        }
    }

    private fun updateOption(webView: WebView?, key: String, value: Any) {
        val jsKey = JSONObject.quote(key)
        val jsValue = when (value) {
            is Boolean -> value.toString()
            is Int -> value.toString()
            is String -> JSONObject.quote(value)
            else -> value.toString()
        }
        webView?.post {
            webView.evaluateJavascript(
                """
                (function() {
                    var opts = window.getChessMintOptions ? window.getChessMintOptions() : {};
                    opts[$jsKey] = $jsValue;
                    if (window.updateChessMintOptions) window.updateChessMintOptions(opts);
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private suspend fun startEngine(context: Context, engine: ChessEngine, webView: WebView?) {
        withContext(Dispatchers.IO) {
            try {
                val resolver = ChessEngineResolver(context)
                val engineFile = resolver.copyEngineToFiles(engine)
                if (engineFile != null && engineFile.exists()) {
                    val started = engineProcess?.start(engineFile, engine.name) == true
                    withContext(Dispatchers.Main) {
                        if (started) {
                            restartEngineHandshake(webView)
                        }
                        val message = if (started) {
                            "Engine started: ${engine.name}"
                        } else {
                            "Failed to start engine process"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to copy engine binary", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to start engine", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun restartEngineHandshake(webView: WebView?) {
        webView?.evaluateJavascript(
            "if(window.restartChessMintEngineHandshake){window.restartChessMintEngineHandshake();}",
            null
        )
    }

    private fun isAllowedChessUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val parsed = Uri.parse(url)
            val scheme = parsed.scheme?.lowercase(Locale.US)
            val host = parsed.host?.lowercase(Locale.US) ?: return false
            (scheme == "https" || scheme == "http") && (host == "chess.com" || host.endsWith(".chess.com"))
        } catch (_: Exception) {
            false
        }
    }

    private fun isWebUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = Uri.parse(url).scheme?.lowercase(Locale.US)
        return scheme == "https" || scheme == "http"
    }

    private fun shouldBlockTopLevelNavigation(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val scheme = Uri.parse(url).scheme?.lowercase(Locale.US)
        return when (scheme) {
            "http", "https", "about", "blob", "data" -> false
            else -> true
        }
    }

    private fun isLowPowerAndroidDevice(): Boolean {
        val runtime = Runtime.getRuntime()
        val is32BitOnly = Build.SUPPORTED_64_BIT_ABIS.isEmpty() || !android.os.Process.is64Bit()
        val lowHeap = runtime.maxMemory() <= 192L * 1024L * 1024L
        val lowCpu = runtime.availableProcessors() <= 2
        return is32BitOnly || lowHeap || lowCpu
    }

    private fun defaultSearchDepth(): Int = if (isLowPowerAndroidDevice()) 2 else 3

    private fun defaultMultiPv(): Int = if (isLowPowerAndroidDevice()) 1 else 3

    private fun clampDepthForDevice(value: Int): Int {
        return value.coerceIn(1, if (isLowPowerAndroidDevice()) 8 else 30)
    }

    private fun clampMultiPvForDevice(value: Int): Int {
        return value.coerceIn(1, if (isLowPowerAndroidDevice()) 3 else 10)
    }

    @Suppress("DEPRECATION")
    private fun disableWebViewDarkening(settings: WebSettings) {
        when {
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) -> {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
            }
            WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) -> {
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_OFF)
            }
        }
    }

    // Cached combined script - built once, reused on every page load
    private fun getCombinedScript(context: Context): String {
        return cachedCombinedScript ?: buildString {
            append("(function(){'use strict';")
            append("if(window.__UpdatedChessMintAndroidInjected){console.log('[UpdatedChessMint Android] Scripts already injected');return;}")
            append("window.__UpdatedChessMintAndroidInjected=true;try{\n")
            append(loadAsset(context, "js/MintJsAdapter.js"))
            append(";\n")
            val depthBarCss = loadAsset(context, "css/depthbar.css").escapeForJs()
            val evalBarCss = loadAsset(context, "css/evalbar.css").escapeForJs()
            val materialIconCss = loadAsset(context, "css/material-icon.css").escapeForJs()
            append("(function(){function addStyle(c){var s=document.createElement('style');s.textContent=c;document.head.appendChild(s);}addStyle('")
            append(depthBarCss)
            append("');addStyle('")
            append(evalBarCss)
            append("');addStyle('")
            append(materialIconCss)
            append("');})();\n")
            append(loadAsset(context, "js/Mint.js"))
            append("\n}catch(e){window.__UpdatedChessMintAndroidInjected=false;console.error('[UpdatedChessMint Android] Injection failed', e);}})();")
        }.also { cachedCombinedScript = it }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectScripts(webView: WebView?, context: Context) {
        webView ?: return
        webView.evaluateJavascript(getCombinedScript(context), null)
        Log.i("MainActivity", "All scripts injected in single call")
    }

    private fun loadAsset(context: Context, path: String): String {
        return try {
            val reader = BufferedReader(InputStreamReader(context.assets.open(path)))
            val content = reader.readText()
            reader.close()
            content
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to load asset: $path", e)
            ""
        }
    }

    private fun String.escapeForJs(): String {
        return this
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}

// ===========================================================================
// Control Panel Composable
// ===========================================================================

@Composable
fun ControlPanel(
    engineRunning: Boolean,
    currentEngineName: String,
    availableEngines: List<ChessEngine>,
    consoleLog: List<String>,
    depth: Int,
    multiPV: Int,
    showHints: Boolean,
    showEvalBar: Boolean,
    showDepthBar: Boolean,
    moveAnalysis: Boolean,
    autoMove: Boolean,
    lowPowerMode: Boolean,
    onDepthChange: (Int) -> Unit,
    onMultiPVChange: (Int) -> Unit,
    onShowHintsChange: (Boolean) -> Unit,
    onShowEvalBarChange: (Boolean) -> Unit,
    onShowDepthBarChange: (Boolean) -> Unit,
    onMoveAnalysisChange: (Boolean) -> Unit,
    onAutoMoveChange: (Boolean) -> Unit,
    onSelectEngine: (ChessEngine) -> Unit,
    onStopEngine: () -> Unit,
    onDismiss: () -> Unit
) {
    val maxDepth = if (lowPowerMode) 8f else 30f
    val maxMultiPv = if (lowPowerMode) 3f else 10f
    val panelGradient = Brush.verticalGradient(
        colors = listOf(
            DarkSurface.copy(alpha = 0.95f),
            DarkBackground.copy(alpha = 0.98f)
        )
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.55f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(panelGradient)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Drag handle bar
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                    }
                }

                // Title Header
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "CHESS",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(AccentPurple.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "MINT",
                                    color = AccentPurple,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(32.dp)
                                .background(Color.White.copy(alpha = 0.04f), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Engine status hero card
                item {
                    GlassCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val infiniteTransition = rememberInfiniteTransition(label = "statusPulse")
                                val pulseAlpha by infiniteTransition.animateFloat(
                                    initialValue = 0.4f,
                                    targetValue = 1f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = LinearEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "statusAlpha"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(if (engineRunning) pulseAlpha else 1.0f)
                                        .clip(CircleShape)
                                        .background(if (engineRunning) AccentGreen else AccentRed)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        currentEngineName,
                                        color = TextPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        if (engineRunning) "Engine Active - OEX" else "Engine Stopped - Inactive",
                                        color = if (engineRunning) AccentGreen.copy(alpha = 0.8f) else TextSecondary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            if (engineRunning) {
                                Button(
                                    onClick = onStopEngine,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = AccentRed.copy(alpha = 0.15f),
                                        contentColor = AccentRed
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(32.dp)
                                        .border(0.5.dp, AccentRed.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                ) {
                                    Text("Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // Available Engines list
                if (availableEngines.isNotEmpty()) {
                    item {
                        Text(
                            "AVAILABLE ENGINES",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }

                    items(availableEngines) { engine ->
                        GlassCard(
                            modifier = Modifier.clickable { onSelectEngine(engine) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentPurple.copy(alpha = 0.10f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = "Start Engine",
                                        tint = AccentPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        engine.name,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        engine.packageName,
                                        color = TextSecondary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    item {
                        GlassCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "No OEX Engines Found",
                                    color = TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Install a chess engine app (e.g. Stockfish OEX) from the Play Store",
                                    color = TextSecondary,
                                    fontSize = 11.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        }
                    }
                }

                // Settings Section
                item {
                    Text(
                        "ENGINE SETTINGS",
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Sliders card
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Depth slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "Depth",
                                            tint = AccentTeal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Search Depth", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentTeal.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "$depth",
                                            color = AccentTeal,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = depth.toFloat(),
                                    onValueChange = { onDepthChange(it.toInt()) },
                                    valueRange = 1f..maxDepth,
                                    steps = (maxDepth.toInt() - 2).coerceAtLeast(0),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = AccentTeal,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.06f),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            Spacer(modifier = Modifier.height(14.dp))

                            // MultiPV slider
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.List,
                                            contentDescription = "MultiPV",
                                            tint = AccentPurple,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Parallel Lines (MultiPV)", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(AccentPurple.copy(alpha = 0.1f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            "$multiPV",
                                            color = AccentPurple,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Slider(
                                    value = multiPV.toFloat(),
                                    onValueChange = { onMultiPVChange(it.toInt()) },
                                    valueRange = 1f..maxMultiPv,
                                    steps = (maxMultiPv.toInt() - 2).coerceAtLeast(0),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Color.White,
                                        activeTrackColor = AccentPurple,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.06f),
                                        activeTickColor = Color.Transparent,
                                        inactiveTickColor = Color.Transparent
                                    ),
                                    modifier = Modifier.height(32.dp)
                                )
                            }
                        }
                    }
                }

                // Toggle switches
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                            SettingsToggle("Show Hints", Icons.Default.Info, AccentTeal, showHints, onShowHintsChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Evaluation Bar", Icons.Default.Star, AccentPurple, showEvalBar, onShowEvalBarChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Depth Bar", Icons.Default.PlayArrow, AccentTeal, showDepthBar, onShowDepthBarChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Move Analysis", Icons.Default.Build, AccentPurple, moveAnalysis, onMoveAnalysisChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Auto Move", Icons.Default.CheckCircle, AccentGreen, autoMove, onAutoMoveChange)
                        }
                    }
                }

                // Console log
                if (consoleLog.isNotEmpty()) {
                    item {
                        Text(
                            "CONSOLE MONITOR",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0E))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 0.5.dp,
                                        color = AccentGreen.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    // Monitor header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(AccentGreen)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "LIVE FEED",
                                                color = AccentGreen,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 1.sp
                                            )
                                        }
                                        Text(
                                            "UCI LOG PORT",
                                            color = TextSecondary,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    HorizontalDivider(color = AccentGreen.copy(alpha = 0.1f), modifier = Modifier.padding(bottom = 8.dp))

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 110.dp)
                                    ) {
                                        consoleLog.takeLast(7).forEach { line ->
                                            Text(
                                                line,
                                                color = AccentGreen.copy(alpha = 0.85f),
                                                fontSize = 9.sp,
                                                maxLines = 1,
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                                modifier = Modifier.padding(vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Bottom spacing
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun SettingsToggle(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(iconColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(label, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = AccentPurple,
                uncheckedThumbColor = Color.White.copy(alpha = 0.4f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.08f),
                checkedBorderColor = Color.Transparent,
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = GlassWhite
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.5.dp,
                    color = GlassBorder,
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            content()
        }
    }
}

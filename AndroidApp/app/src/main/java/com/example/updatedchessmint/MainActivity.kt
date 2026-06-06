package com.example.updatedchessmint

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.webkit.ConsoleMessage
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.updatedchessmint.bridge.ChessMintBridge
import com.example.updatedchessmint.engine.ChessEngine
import com.example.updatedchessmint.engine.ChessEngineResolver
import com.example.updatedchessmint.engine.EngineProcess
import com.example.updatedchessmint.theme.UpdatedChessMintTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {

    private var engineProcess: EngineProcess? = null
    private var bridge: ChessMintBridge? = null
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
        var showControlPanel by remember { mutableStateOf(false) }
        var webView by remember { mutableStateOf<WebView?>(null) }
        var engineRunning by remember { mutableStateOf(false) }
        var currentEngineName by remember { mutableStateOf("No Engine") }
        var availableEngines by remember { mutableStateOf<List<ChessEngine>>(emptyList()) }
        var showEngineSelector by remember { mutableStateOf(false) }
        var consoleLog by remember { mutableStateOf<List<String>>(emptyList()) }
        var pageLoaded by remember { mutableStateOf(false) }

        // Settings state
        var depth by remember { mutableIntStateOf(3) }
        var multiPV by remember { mutableIntStateOf(3) }
        var showHints by remember { mutableStateOf(true) }
        var showEvalBar by remember { mutableStateOf(true) }
        var showDepthBar by remember { mutableStateOf(true) }
        var moveAnalysis by remember { mutableStateOf(true) }
        var autoMove by remember { mutableStateOf(false) }

        // Discover engines
        LaunchedEffect(Unit) {
            withContext(Dispatchers.IO) {
                val resolver = ChessEngineResolver(context)
                val engines = resolver.resolveEngines()
                availableEngines = engines
            }
        }

        // Collect engine state
        LaunchedEffect(engineProcess) {
            engineProcess?.let { ep ->
                launch {
                    ep.isRunning.collectLatest { running ->
                        engineRunning = running
                    }
                }
                launch {
                    ep.engineName.collectLatest { name ->
                        currentEngineName = name
                    }
                }
                launch {
                    ep.outputFlow.collectLatest { line ->
                        // Send engine output to the WebView
                        bridge?.sendToJavaScript(line)
                        // Add to console log (keep last 50 lines)
                        consoleLog = (consoleLog + line).takeLast(50)
                    }
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
                            allowFileAccess = true
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            builtInZoomControls = true
                            displayZoomControls = false
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            userAgentString = settings.userAgentString.replace(
                                "; wv", ""
                            ) // Remove WebView indicator from UA
                            cacheMode = WebSettings.LOAD_DEFAULT
                            setSupportMultipleWindows(false)
                        }

                        // Create the engine process
                        val ep = EngineProcess(engineScope)
                        engineProcess = ep

                        // Create the bridge
                        val b = ChessMintBridge(this, ep) { showControlPanel = true }
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
                                // Keep chess.com navigation inside the WebView
                                return !url.contains("chess.com")
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                if (url?.contains("chess.com") == true) {
                                    pageLoaded = true
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
                        .background(Color(0xFF0D0D0D)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = Color(0xFF7C4DFF),
                            strokeWidth = 3.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Loading Chess.com...",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Floating Action Button - Control Panel Toggle
            if (pageLoaded) {
                FloatingActionButton(
                    onClick = { showControlPanel = !showControlPanel },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .size(48.dp),
                    containerColor = if (engineRunning) Color(0xFF7C4DFF) else Color(0xFF2C2C2E),
                    contentColor = Color.White,
                    shape = CircleShape,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = if (showControlPanel) Icons.Default.Close else Icons.Default.Settings,
                        contentDescription = "Toggle Control Panel",
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Engine status indicator
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 68.dp)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(
                            if (engineRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                        )
                )
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
                    onDepthChange = { d ->
                        depth = d
                        updateOption(webView, "option-depth", d)
                    },
                    onMultiPVChange = { m ->
                        multiPV = m
                        updateOption(webView, "option-multipv", m)
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
                            startEngine(context, engine)
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
        val jsValue = when (value) {
            is Boolean -> value.toString()
            is Int -> value.toString()
            is String -> "'$value'"
            else -> value.toString()
        }
        webView?.post {
            webView.evaluateJavascript(
                """
                (function() {
                    var opts = window.getChessMintOptions ? window.getChessMintOptions() : {};
                    opts['$key'] = $jsValue;
                    if (window.updateChessMintOptions) window.updateChessMintOptions(opts);
                })();
                """.trimIndent(),
                null
            )
        }
    }

    private suspend fun startEngine(context: Context, engine: ChessEngine) {
        withContext(Dispatchers.IO) {
            try {
                val resolver = ChessEngineResolver(context)
                val engineFile = resolver.copyEngineToFiles(engine)
                if (engineFile != null && engineFile.exists()) {
                    engineProcess?.start(engineFile, engine.name)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Engine started: ${engine.name}", Toast.LENGTH_SHORT).show()
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectScripts(webView: WebView?, context: Context) {
        webView ?: return

        // Read and inject MintJsAdapter.js first (sets up mocks)
        val adapterJs = loadAsset(context, "js/MintJsAdapter.js")
        webView.evaluateJavascript(adapterJs, null)

        // Inject CSS styles
        val depthBarCss = loadAsset(context, "css/depthbar.css").escapeForJs()
        val evalBarCss = loadAsset(context, "css/evalbar.css").escapeForJs()
        val materialIconCss = loadAsset(context, "css/material-icon.css").escapeForJs()

        val injectCssJs = """
            (function() {
                function addStyle(css) {
                    var s = document.createElement('style');
                    s.textContent = css;
                    document.head.appendChild(s);
                }
                addStyle('$depthBarCss');
                addStyle('$evalBarCss');
                addStyle('$materialIconCss');
            })();
        """.trimIndent()
        webView.evaluateJavascript(injectCssJs, null)

        // Inject Mint.js
        val mintJs = loadAsset(context, "js/Mint.js")
        webView.evaluateJavascript(mintJs, null)

        Log.i("MainActivity", "All scripts injected into Chess.com")
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
    val panelGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0xE6161618),
            Color(0xF20D0D0F)
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
                // Handle bar
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
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                }

                // Title
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "♟ UpdatedChessMint",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // Engine Status
                item {
                    GlassCard {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (engineRunning) Color(0xFF00E676) else Color(0xFFFF5252)
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        currentEngineName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        if (engineRunning) "Active" else "Inactive",
                                        color = Color.White.copy(alpha = 0.5f),
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            if (engineRunning) {
                                TextButton(
                                    onClick = onStopEngine,
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = Color(0xFFFF5252)
                                    )
                                ) {
                                    Text("Stop", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Engine Selector
                if (availableEngines.isNotEmpty()) {
                    item {
                        Text(
                            "Available Engines",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp)
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
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = "Start",
                                    tint = Color(0xFF7C4DFF),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        engine.name,
                                        color = Color.White,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        engine.packageName,
                                        color = Color.White.copy(alpha = 0.4f),
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
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Install a chess engine app (e.g., Stockfish OEX) from the Play Store",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                // Settings Section
                item {
                    Text(
                        "Settings",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }

                // Depth slider
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Depth", color = Color.White, fontSize = 13.sp)
                                Text(
                                    "$depth",
                                    color = Color(0xFF7C4DFF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = depth.toFloat(),
                                onValueChange = { onDepthChange(it.toInt()) },
                                valueRange = 1f..30f,
                                steps = 28,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7C4DFF),
                                    activeTrackColor = Color(0xFF7C4DFF),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }

                // MultiPV slider
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Multi PV", color = Color.White, fontSize = 13.sp)
                                Text(
                                    "$multiPV",
                                    color = Color(0xFF7C4DFF),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = multiPV.toFloat(),
                                onValueChange = { onMultiPVChange(it.toInt()) },
                                valueRange = 1f..10f,
                                steps = 8,
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFF7C4DFF),
                                    activeTrackColor = Color(0xFF7C4DFF),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        }
                    }
                }

                // Toggle switches
                item {
                    GlassCard {
                        Column(modifier = Modifier.padding(14.dp)) {
                            SettingsToggle("Show Hints", showHints, onShowHintsChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Evaluation Bar", showEvalBar, onShowEvalBarChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Depth Bar", showDepthBar, onShowDepthBarChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Move Analysis", moveAnalysis, onMoveAnalysisChange)
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                            SettingsToggle("Auto Move", autoMove, onAutoMoveChange)
                        }
                    }
                }

                // Console log
                if (consoleLog.isNotEmpty()) {
                    item {
                        Text(
                            "Engine Console",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                        )
                    }
                    item {
                        GlassCard {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 120.dp)
                                    .padding(10.dp)
                            ) {
                                consoleLog.takeLast(8).forEach { line ->
                                    Text(
                                        line,
                                        color = Color(0xFF00E676).copy(alpha = 0.8f),
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                                    )
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
fun SettingsToggle(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Color.White, fontSize = 13.sp)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF7C4DFF),
                uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
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
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White.copy(alpha = 0.06f)
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 0.5.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(14.dp)
                )
        ) {
            content()
        }
    }
}

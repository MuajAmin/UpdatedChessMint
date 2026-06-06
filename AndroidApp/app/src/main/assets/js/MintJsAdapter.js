/**
 * MintJsAdapter.js
 * 
 * This script is injected BEFORE Mint.js into the Chess.com WebView.
 * It replaces the Chrome Extension APIs (chrome.storage, chrome.runtime)
 * and the WebSocket connection with the native Android bridge.
 *
 * The native bridge is available as window.ChessMintAndroid
 */
(function() {
    'use strict';

    window.UpdatedChessMintConfig = window.UpdatedChessMintConfig || {
        pathToEcoJson: "data:application/json,[]",
        threadedEnginePaths: {
            stockfish: {
                multiThreaded: { loader: "", engine: "" },
                singleThreaded: { loader: "", engine: "" }
            }
        }
    };

    // ========================================================================
    // Default Options (ported from loader.js / options.js)
    // ========================================================================
    var defaultOptions = {
        "option-url-api-stockfish": "native://engine",
        "option-api-stockfish": true,
        "option-num-cores": 1,
        "option-hashtable-ram": 1024,
        "option-depth": 3,
        "option-mate-finder-value": 5,
        "option-multipv": 3,
        "option-highmatechance": false,
        "option-auto-move-time": 0,
        "option-auto-move-time-random": 10000,
        "option-auto-move-time-random-div": 10,
        "option-auto-move-time-random-multi": 1000,
        "option-max-legit-auto-move-depth": 10,
        "option-legit-auto-move": false,
        "option-max-premoves": 3,
        "option-premove-enabled": false,
        "option-premove-time": 1000,
        "option-premove-time-random": 500,
        "option-premove-time-random-div": 100,
        "option-premove-time-random-multi": 1,
        "option-best-move-chance": 30,
        "option-random-best-move": false,
        "option-show-hints": true,
        "option-text-to-speech": false,
        "option-move-analysis": true,
        "option-depth-bar": true,
        "option-evaluation-bar": true
    };

    // Load saved options from localStorage, or use defaults
    var savedOptions = {};
    try {
        var stored = localStorage.getItem('updatedchessmint_options');
        if (stored) {
            savedOptions = JSON.parse(stored);
        }
    } catch(e) {}

    var currentOptions = Object.assign({}, defaultOptions, savedOptions);

    // ========================================================================
    // Mock Chrome Extension APIs
    // ========================================================================
    
    // Provide a minimal chrome.storage.sync mock
    if (typeof window.chrome === 'undefined') window.chrome = {};
    if (typeof window.chrome.storage === 'undefined') window.chrome.storage = {};
    if (typeof window.chrome.runtime === 'undefined') window.chrome.runtime = {};

    window.chrome.storage.sync = {
        get: function(defaults, callback) {
            var result = Object.assign({}, defaults, currentOptions);
            if (callback) callback(result);
        },
        set: function(data, callback) {
            Object.assign(currentOptions, data);
            try {
                localStorage.setItem('updatedchessmint_options', JSON.stringify(currentOptions));
            } catch(e) {}
            if (callback) callback();
        }
    };

    window.chrome.storage.local = {
        get: function(keys) {
            return new Promise(function(resolve) {
                try {
                    var result = {};
                    if (Array.isArray(keys)) {
                        keys.forEach(function(k) {
                            var v = localStorage.getItem('ucm_local_' + k);
                            if (v !== null) result[k] = JSON.parse(v);
                        });
                    }
                    resolve(result);
                } catch(e) {
                    resolve({});
                }
            });
        },
        set: function(data, callback) {
            try {
                Object.keys(data).forEach(function(k) {
                    localStorage.setItem('ucm_local_' + k, JSON.stringify(data[k]));
                });
            } catch(e) {}
            if (callback) callback();
        }
    };

    window.chrome.runtime.getURL = function(path) {
        return path;
    };

    window.chrome.runtime.onMessage = {
        addListener: function() {}
    };

    window.chrome.tabs = {
        query: function(q, cb) { if (cb) cb([]); },
        sendMessage: function() {}
    };

    // ========================================================================
    // Options Event System (replacing Chrome Extension messaging)
    // ========================================================================

    // Listen for UpdatedChessMintGetOptions events from Mint.js
    window.addEventListener('UpdatedChessMintGetOptions', function(evt) {
        var request = evt.detail;
        var response = {
            requestId: request.id,
            data: Object.assign({}, currentOptions)
        };
        window.dispatchEvent(new CustomEvent('UpdatedChessMintSendOptions', { detail: response }));
    });

    // Function to update options from the settings panel
    window.updateChessMintOptions = function(newOptions) {
        Object.assign(currentOptions, newOptions);
        try {
            localStorage.setItem('updatedchessmint_options', JSON.stringify(currentOptions));
        } catch(e) {}
        window.dispatchEvent(new CustomEvent('UpdatedChessMintUpdateOptions', { detail: currentOptions }));
    };

    // Function to get current options
    window.getChessMintOptions = function() {
        return Object.assign({}, currentOptions);
    };

    // ========================================================================
    // Native Engine Bridge (replacing WebSocket)
    // ========================================================================

    // This function is called by the native Kotlin code when the engine sends a response
    window.onEngineResponse = function(line) {
        if (window._engineMessageHandler) {
            window._engineMessageHandler(line);
        }
    };

    // Override WebSocket to intercept engine connections
    var OriginalWebSocket = window.WebSocket;
    window.WebSocket = function(url, protocols) {
        // Check if this is a chess engine connection
        if (url && (url.indexOf('localhost:8000') !== -1 || url.indexOf('native://') !== -1)) {
            console.log('[UpdatedChessMint Android] Intercepting WebSocket -> Native Engine Bridge');
            return new NativeEngineBridge();
        }
        // For all other WebSocket connections, use the original
        return new OriginalWebSocket(url, protocols);
    };
    // Copy static properties
    window.WebSocket.CONNECTING = 0;
    window.WebSocket.OPEN = 1;
    window.WebSocket.CLOSING = 2;
    window.WebSocket.CLOSED = 3;

    /**
     * NativeEngineBridge - A fake WebSocket that routes to the native Android engine.
     */
    function NativeEngineBridge() {
        this.readyState = WebSocket.CONNECTING;
        this.onopen = null;
        this.onmessage = null;
        this.onclose = null;
        this.onerror = null;
        this._listeners = {};

        var self = this;

        // Register the message handler for engine responses
        window._engineMessageHandler = function(line) {
            if (self.readyState !== WebSocket.OPEN) return;
            
            var event = { data: line };
            
            // Call event listeners
            if (self.onmessage) self.onmessage(event);
            if (self._listeners['message']) {
                self._listeners['message'].forEach(function(fn) { fn(event); });
            }
        };

        // Simulate connection opening (async to match WebSocket behavior)
        setTimeout(function() {
            self.readyState = WebSocket.OPEN;
            var openEvent = {};
            if (self.onopen) self.onopen(openEvent);
            if (self._listeners['open']) {
                self._listeners['open'].forEach(function(fn) { fn(openEvent); });
            }
            console.log('[UpdatedChessMint Android] Native engine bridge connected');
        }, 50);
    }

    NativeEngineBridge.prototype.send = function(data) {
        if (this.readyState !== WebSocket.OPEN) {
            console.warn('[UpdatedChessMint Android] Cannot send, bridge not open');
            return;
        }
        if (window.ChessMintAndroid) {
            window.ChessMintAndroid.sendToEngine(data);
        } else {
            console.error('[UpdatedChessMint Android] Native bridge not available');
        }
    };

    NativeEngineBridge.prototype.close = function() {
        this.readyState = WebSocket.CLOSED;
        var event = {};
        if (this.onclose) this.onclose(event);
        if (this._listeners['close']) {
            this._listeners['close'].forEach(function(fn) { fn(event); });
        }
    };

    NativeEngineBridge.prototype.addEventListener = function(type, fn) {
        if (!this._listeners[type]) this._listeners[type] = [];
        this._listeners[type].push(fn);
    };

    NativeEngineBridge.prototype.removeEventListener = function(type, fn) {
        if (!this._listeners[type]) return;
        this._listeners[type] = this._listeners[type].filter(function(f) { return f !== fn; });
    };

    console.log('[UpdatedChessMint Android] Adapter loaded successfully');
})();

<p align="center">
  <img width="600" src="https://i.ibb.co/6Pz1c3B/1-background.png">
  <br><br>
  <a href="https://discord.com/servers/updatedchessmint-development-1098267851732815932"><img alt="Discord" src="https://img.shields.io/badge/Discord-UpdatedChessMint-5865F2?style=for-the-badge&logo=discord&logoColor=white"></a>
  <a href="https://github.com/MuajAmin/UpdatedChessMint/releases/"><img alt="Downloads" src="https://img.shields.io/github/downloads/MuajAmin/UpdatedChessMint/total?color=31c754&label=Downloads&style=for-the-badge&logo=github"></a>
  <a href="https://chromewebstore.google.com/detail/mint-v2/hjpjlhjhmbnpokpgdhpncefmchlonmhj"><img alt="Chrome Web Store" src="https://img.shields.io/badge/Chrome-Web%20Store-4285F4?style=for-the-badge&logo=googlechrome&logoColor=white"></a>
</p>

<h1 align="center">♟️ UpdatedChessMint V2</h1>
<p align="center"><b>A powerful Chess.com analysis companion — available as a Chrome Extension and an Android App.</b></p>

---

## 📦 Project Structure

| Directory | Description |
|---|---|
| **`UpdatedChessMint/`** | Chrome Extension — injects analysis UI directly into Chess.com |
| **`AndroidApp/`** | Native Android app (Kotlin + Jetpack Compose) — wraps Chess.com in a WebView with a built-in engine bridge |
| **`EngineWS/`** | Local WebSocket server (Python) — bridges a UCI chess engine to the Chrome Extension |
| **`BetterCheems/`** | Legacy GMCheems extension (bundled, archived) |

---

## ✨ Features

- **Real-time move analysis** — arrows and highlights for the top engine lines directly on the board
- **Evaluation bar** — live centipawn and mate score visualization
- **Depth progress bar** — see how deep the engine is searching
- **Move classification** — Brilliant, Best Move, Blunder, etc. for every move played
- **Multi-PV support** — view multiple principal variations simultaneously
- **Universal UCI engine support** — use Stockfish or any UCI-compatible engine
- **Android native integration** — discovers and runs OEX-protocol engines on-device

---

## 🚀 Getting Started

### Option A: Chrome Extension (Desktop)

#### Prerequisites
- [Python 3.12+](https://www.python.org/downloads/) (add to PATH during install)
- [Git](https://git-scm.com/downloads)
- A UCI chess engine binary (e.g. [Stockfish](https://stockfishchess.org/download/))

#### Installation

1. **Download** the [latest release](https://github.com/MuajAmin/UpdatedChessMint/releases/) or install from the [Chrome Web Store](https://chromewebstore.google.com/detail/mint-v2/hjpjlhjhmbnpokpgdhpncefmchlonmhj).

2. **Start the engine server:**
   ```
   cd EngineWS
   run.bat
   ```
   Select your UCI engine `.exe` when prompted.

3. **Load the extension** (if not using Chrome Web Store):
   - Open `chrome://extensions`
   - Enable **Developer Mode** (top-right toggle)
   - Click **Load unpacked** → select the `UpdatedChessMint/` folder

4. **Play on Chess.com** — the analysis overlay activates automatically on any game board.

---

### Option B: Android App

#### Prerequisites
- Android device running Android 6.0+ (API 23)
- An OEX-compatible chess engine app (e.g. Stockfish from Play Store)

#### Installation

1. **Download** the latest APK from [Releases](https://github.com/MuajAmin/UpdatedChessMint/releases/) or build from source:
   ```
   cd AndroidApp
   ./gradlew assembleDebug
   ```
   The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

2. **Install** the APK on your device.

3. **Install an OEX engine** — the app auto-discovers installed engines via the Open Exchange protocol.

4. **Open the app** → Chess.com loads in the built-in browser. Tap the ⚙️ FAB to open the control panel, select your engine, and start analyzing.

---

## ⚙️ Configuration Options

| Option | Default | Description |
|---|---|---|
| Search Depth | 3 | Engine search depth (1–30) |
| MultiPV | 3 | Number of parallel lines (1–10) |
| Show Hints | ✅ | Draw arrows and highlights for top moves |
| Evaluation Bar | ✅ | Show the vertical eval bar |
| Depth Bar | ✅ | Show the depth progress bar |
| Move Analysis | ✅ | Classify each move (Brilliant, Blunder, etc.) |

---

## 🛠️ Building from Source

### Chrome Extension
No build step required — load the `UpdatedChessMint/` directory directly as an unpacked extension.

### Engine WebSocket Server
```bash
cd EngineWS
pip install -r requirements.txt
python main.py
```

### Android App
```bash
cd AndroidApp
./gradlew assembleDebug
```
Requires JDK 17 and Android SDK (compileSdk 36).

---

## 📜 Credits

This project is a collaborative effort made possible by:

| Contributor | Role |
|---|---|
| [thedemons](https://github.com/sakiodre) | Original Creator |
| [Webcubed](https://github.com/webcubed) | Development |
| [HotaVN](https://github.com/hotamago) | Development & Improvements |
| [UpdatedChessMint](https://github.com/MuajAmin) | Lead Development & Maintenance |
| [ProtonDev](https://github.com/ProtonDev-sys) | API Docs & Public API Host |
| [GMCheems](https://github.com/gmcheems) | GMCheems Extension |
| [lucaskvasirr](https://github.com/lucaskvasirr) | Premove Code Snippet |

---

## 📄 License

This project is licensed under the terms included in the [LICENSE](LICENSE) file.

---

<p align="center"><i>UpdatedChessMint is a learning and analysis tool. True chess mastery comes from practice and dedication.</i></p>

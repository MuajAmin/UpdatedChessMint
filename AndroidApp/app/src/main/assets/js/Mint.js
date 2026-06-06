"use strict";
var __awaiter =
  (this && this.__awaiter) ||
  function (thisArg, _arguments, P, generator) {
    function adopt(value) {
      return value instanceof P
        ? value
        : new P(function (resolve) {
            resolve(value);
          });
    }
    return new (P || (P = Promise))(function (resolve, reject) {
      function fulfilled(value) {
        try {
          step(generator.next(value));
        } catch (e) {
          reject(e);
        }
      }
      function rejected(value) {
        try {
          step(generator["throw"](value));
        } catch (e) {
          reject(e);
        }
      }
      function step(result) {
        result.done
          ? resolve(result.value)
          : adopt(result.value).then(fulfilled, rejected);
      }
      step((generator = generator.apply(thisArg, _arguments || [])).next());
    });
  };

var ChromeRequest = (function () {
  // Options listener and sender
  var requestId = 0;
  function getData(data) {
    var id = requestId++;
    return new Promise(function (resolve, reject) {
      var listener = function (evt) {
        if (evt.detail.requestId == id) {
          // Deregister self
          window.removeEventListener("UpdatedChessMintSendOptions", listener);
          resolve(evt.detail.data);
        }
      };
      window.addEventListener("UpdatedChessMintSendOptions", listener);
      var payload = {
        data: data,
        id: id,
      };
      window.dispatchEvent(
        new CustomEvent("UpdatedChessMintGetOptions", { detail: payload })
      );
    });
  }
  return { getData: getData };
})();

function getGradientColor(start_color, end_color, percent) {
  percent = clampNumber(percent, 0, 1, 0);
  // strip the leading # if it's there
  start_color = start_color.replace(/^\s*#|\s*$/g, "");
  end_color = end_color.replace(/^\s*#|\s*$/g, "");

  // convert 3 char codes --> 6, e.g. `E0F` --> `EE00FF`
  if (start_color.length == 3) {
    start_color = start_color.replace(/(.)/g, "$1$1");
  }

  if (end_color.length == 3) {
    end_color = end_color.replace(/(.)/g, "$1$1");
  }

  // get colors
  var start_red = parseInt(start_color.substr(0, 2), 16),
    start_green = parseInt(start_color.substr(2, 2), 16),
    start_blue = parseInt(start_color.substr(4, 2), 16);

  var end_red = parseInt(end_color.substr(0, 2), 16),
    end_green = parseInt(end_color.substr(2, 2), 16),
    end_blue = parseInt(end_color.substr(4, 2), 16);

  start_red = clampNumber(start_red, 0, 255, 0);
  start_green = clampNumber(start_green, 0, 255, 0);
  start_blue = clampNumber(start_blue, 0, 255, 0);
  end_red = clampNumber(end_red, 0, 255, 0);
  end_green = clampNumber(end_green, 0, 255, 0);
  end_blue = clampNumber(end_blue, 0, 255, 0);

  // calculate new color
  var diff_red = end_red - start_red;
  var diff_green = end_green - start_green;
  var diff_blue = end_blue - start_blue;

  diff_red = Math.round(diff_red * percent + start_red).toString(16);
  diff_green = Math.round(diff_green * percent + start_green).toString(16);
  diff_blue = Math.round(diff_blue * percent + start_blue).toString(16);

  // ensure 2 digits by color
  if (diff_red.length == 1) diff_red = "0" + diff_red;
  if (diff_green.length == 1) diff_green = "0" + diff_green;
  if (diff_blue.length == 1) diff_blue = "0" + diff_blue;

  return "#" + diff_red + diff_green + diff_blue;
}

var enumOptions = {
  UrlApiStockfish: "option-url-api-stockfish",
  ApiStockfish: "option-api-stockfish",
  NumCores: "option-num-cores",
  HashtableRam: "option-hashtable-ram",
  Depth: "option-depth",
  MateFinderValue: "option-mate-finder-value",
  MultiPV: "option-multipv",
  HighMateChance: "option-highmatechance",
  AutoMoveTime: "option-auto-move-time",
  AutoMoveTimeRandom: "option-auto-move-time-random",
  AutoMoveTimeRandomDiv: "option-auto-move-time-random-div",
  AutoMoveTimeRandomMulti: "option-auto-move-time-random-multi",
  Premove: "option-premove-enabled",
  MaxPreMoves: "option-max-premoves",
  PreMoveTime: "option-premove-time",
  PreMoveTimeRandom: "option-premove-time-random",
  PreMoveTimeRandomDiv: "option-premove-time-random-div",
  PreMoveTimeRandomMulti: "option-premove-time-random-multi",
  LegitAutoMove: "option-legit-auto-move",
  BestMoveChance: "option-best-move-chance",
  RandomBestMove: "option-random-best-move",
  ShowHints: "option-show-hints",
  TextToSpeech: "option-text-to-speech",
  MoveAnalysis: "option-move-analysis",
  DepthBar: "option-depth-bar",
  EvaluationBar: "option-evaluation-bar",
};

var UpdatedChessMintmaster;
var Config =
  (typeof window !== "undefined" && window.UpdatedChessMintConfig) || {
    pathToEcoJson: "data:application/json,[]",
    threadedEnginePaths: {
      stockfish: {
        multiThreaded: { loader: "", engine: "" },
        singleThreaded: { loader: "", engine: "" },
      },
    },
  };
var context = undefined;
var eTable = null;

function isLowPowerMintDevice() {
  var profile =
    (typeof window !== "undefined" && window.__UpdatedChessMintDeviceProfile) ||
    {};
  return (
    profile.lowPower === true ||
    profile.is64Bit === false ||
    (Number(profile.maxMemoryMb) > 0 && Number(profile.maxMemoryMb) <= 192)
  );
}

function getDefaultDepth() {
  return isLowPowerMintDevice() ? 2 : 3;
}

function getDepthLimit() {
  return isLowPowerMintDevice() ? 8 : 99;
}

function getDefaultMultiPV() {
  return isLowPowerMintDevice() ? 1 : 3;
}

var defaultOptionValues = {};
defaultOptionValues[enumOptions.UrlApiStockfish] = "native://engine";
defaultOptionValues[enumOptions.ApiStockfish] = true;
defaultOptionValues[enumOptions.NumCores] = 1;
defaultOptionValues[enumOptions.HashtableRam] = isLowPowerMintDevice() ? 64 : 256;
defaultOptionValues[enumOptions.Depth] = getDefaultDepth();
defaultOptionValues[enumOptions.MateFinderValue] = 5;
defaultOptionValues[enumOptions.MultiPV] = getDefaultMultiPV();
defaultOptionValues[enumOptions.HighMateChance] = false;
defaultOptionValues[enumOptions.AutoMoveTime] = 0;
defaultOptionValues[enumOptions.AutoMoveTimeRandom] = 10000;
defaultOptionValues[enumOptions.AutoMoveTimeRandomDiv] = 10;
defaultOptionValues[enumOptions.AutoMoveTimeRandomMulti] = 1000;
defaultOptionValues[enumOptions.Premove] = false;
defaultOptionValues[enumOptions.MaxPreMoves] = 3;
defaultOptionValues[enumOptions.PreMoveTime] = 1000;
defaultOptionValues[enumOptions.PreMoveTimeRandom] = 500;
defaultOptionValues[enumOptions.PreMoveTimeRandomDiv] = 100;
defaultOptionValues[enumOptions.PreMoveTimeRandomMulti] = 1;
defaultOptionValues[enumOptions.LegitAutoMove] = false;
defaultOptionValues[enumOptions.BestMoveChance] = 30;
defaultOptionValues[enumOptions.RandomBestMove] = false;
defaultOptionValues[enumOptions.ShowHints] = true;
defaultOptionValues[enumOptions.TextToSpeech] = false;
defaultOptionValues[enumOptions.MoveAnalysis] = true;
defaultOptionValues[enumOptions.DepthBar] = true;
defaultOptionValues[enumOptions.EvaluationBar] = true;

var tempOptions = {};
ChromeRequest.getData().then(function (options) {
  tempOptions = options || {};
}).catch(function (error) {
  reportMint("warn", "Could not read options, using safe defaults.", error);
});
function getValueConfig(key) {
  var options =
    UpdatedChessMintmaster == undefined
      ? tempOptions
      : UpdatedChessMintmaster.options;
  if (
    options &&
    Object.prototype.hasOwnProperty.call(options, key) &&
    options[key] !== undefined &&
    options[key] !== null
  ) {
    return options[key];
  }
  return defaultOptionValues[key];
}

function clampNumber(value, min, max, fallback) {
  var number = Number(value);
  if (!Number.isFinite(number)) number = fallback;
  if (!Number.isFinite(number)) number = min;
  if (number < min) return min;
  if (number > max) return max;
  return number;
}

function getNumberConfig(key, fallback, min, max) {
  return clampNumber(getValueConfig(key), min, max, fallback);
}

function getBooleanConfig(key, fallback) {
  var value = getValueConfig(key);
  if (value === undefined || value === null) return fallback;
  if (typeof value === "string") return value.toLowerCase() === "true";
  return !!value;
}

function getIntegerConfig(key, fallback, min, max) {
  return Math.round(getNumberConfig(key, fallback, min, max));
}

function getRandomizedDelay(baseKey, randomKey, divKey, multiKey, fallback) {
  var base = getNumberConfig(baseKey, fallback, 0, 600000);
  var random = getNumberConfig(randomKey, 0, 0, 600000);
  var div = getNumberConfig(divKey, 1, 1, 600000);
  var multi = getNumberConfig(multiKey, 1, 0, 600000);
  var delay = base + (Math.floor(Math.random() * random) % div) * multi;
  return Math.round(clampNumber(delay, 0, 600000, fallback));
}

function reportMint(level, message, error) {
  var logger = console[level] || console.log;
  logger.call(console, "[UpdatedChessMint] " + message, error || "");
  try {
    if (window.ChessMintAndroid && window.ChessMintAndroid.logMessage) {
      window.ChessMintAndroid.logMessage(level, message);
    }
  } catch (_) {}
}

function showMintToast(id, content, options) {
  options = options || {};
  if (window.toaster && typeof window.toaster.add === "function") {
    window.toaster.add({
      id: id,
      duration: options.duration || 2000,
      icon: options.icon || "circle-info",
      content: content,
      style: Object.assign(
        {
          position: "fixed",
          bottom: options.bottom || "90px",
          right: "30px",
          backgroundColor: options.backgroundColor || "#1f2937",
          color: "white",
        },
        options.style || {}
      ),
    });
  } else {
    reportMint("info", content);
  }
}

function getMultiPVLimit() {
  return getIntegerConfig(
    enumOptions.MultiPV,
    getDefaultMultiPV(),
    1,
    isLowPowerMintDevice() ? 3 : 20
  );
}

function isMoveAutomationAllowed() {
  return false;
}

class TopMove {
  constructor(line, depth, cp, mate, multipv) {
    line = typeof line === "string" ? line.trim() : "";
    this.line = line.length > 0 ? line.split(/\s+/) : [];
    this.move = this.line[0];
    this.promotion = this.move && this.move.length > 4 ? this.move.substring(4, 5) : null;
    this.from = this.move ? this.move.substring(0, 2) : null;
    this.to = this.move ? this.move.substring(2, 4) : null;
    this.cp = cp;
    this.mate = mate;
    this.mateIn = mate;
    this.depth = depth;
    this.multipv = multipv || 1;
  }
}

class GameController {
  constructor(UpdatedChessMintmaster, chessboard) {
    this.UpdatedChessMintmaster = UpdatedChessMintmaster;
    this.chessboard = chessboard;
    this.controller = chessboard.game;
    this.options = this.controller.getOptions() || {};
    this.depthBar = null;
    this.evalBar = null;
    this.evalBarFill = null;
    this.evalScore = null;
    this.evalScoreAbbreviated = null;
    this.currentMarkings = [];
    this.analysisToolsInterval = null;
    this.disposed = false;
    this.controller.on("Move", (event) => {
      try {
        if (this.disposed) return;
        reportMint("info", "Move event received.");

        const currentFEN = this.controller.getFEN();
        if (
          currentFEN &&
          currentFEN.startsWith("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR")
        ) {
          if (
            this.UpdatedChessMintmaster.engine.moveCounter === 0 &&
            currentFEN.indexOf(" w ") !== -1
          ) {
            this.UpdatedChessMintmaster.engine.isPreMoveSequence = true;
          }
        }
        this.UpdateEngine(false);
      } catch (error) {
        reportMint("error", "Move handling failed.", error);
      }
    });
    // check if a new game has started
    if (this.evalBar == null && getBooleanConfig(enumOptions.EvaluationBar, true)) {
      this.CreateAnalysisTools();
    }
    this.controller.on('ModeChanged', (event) => {
      if (this.disposed) return;
      if (event && event.data === "playing") {
        this.ResetGame();
        this.RefreshEvaluationBar();
        this.UpdatedChessMintmaster.engine.resetMoveCounters();
      }
    });
    let checkEventOne = false;
    this.controller.on("RendererSet", (event) => {
      if (this.disposed) return;
      this.ResetGame();
      this.RefreshEvaluationBar();
      checkEventOne = true;
    });
    setTimeout(() => {
        if(!checkEventOne){
            this.controller.on("ResetGame", (event) => {
              if (this.disposed) return;
              this.ResetGame();
              this.RefreshEvaluationBar();
            });
        }
    }, 1100);
    // this.controller.onAll((event) => {
    //   console.log("OnAll", event);
    // });

    this.controller.on("UpdateOptions", (event) => {
      if (this.disposed) return;
      this.options = this.controller.getOptions() || {};
      if (event && event.data && event.data.flipped != undefined && this.evalBar != null) {
        if (event.data.flipped)
          this.evalBar.classList.add("evaluation-bar-flipped");
        else this.evalBar.classList.remove("evaluation-bar-flipped");
      }
    });
  }
  UpdateExtensionOptions() {
    if (this.disposed) return;
    if (getBooleanConfig(enumOptions.EvaluationBar, true) && this.evalBar == null)
      this.CreateAnalysisTools();
    else if (
      !getBooleanConfig(enumOptions.EvaluationBar, true) &&
      this.evalBar != null
    ) {
      this.evalBar.remove();
      this.evalBar = null;
      this.evalBarFill = null;
      this.evalScore = null;
      this.evalScoreAbbreviated = null;
    }
    if (getBooleanConfig(enumOptions.DepthBar, true) && this.depthBar == null)
      this.CreateAnalysisTools();
    else if (!getBooleanConfig(enumOptions.DepthBar, true) && this.depthBar != null) {
      if (this.depthBar.parentElement) this.depthBar.parentElement.remove();
      this.depthBar = null;
    }
    if (!getBooleanConfig(enumOptions.ShowHints, true)) {
      this.RemoveCurrentMarkings();
    }
    if (!getBooleanConfig(enumOptions.MoveAnalysis, true)) {
      let lastMove = this.controller.getLastMove();
      if (lastMove) {
        this.controller.markings.removeOne(`effect|${lastMove.to}`);
      }
    }
  }
  CreateAnalysisTools() {
    if (this.disposed) return;
    // we must wait for a little bit because at this point the chessboard has not
    // been added to chessboard layout (#board-layout-main)
    if (this.analysisToolsInterval != null) return;
    let attempts = 0;
    this.analysisToolsInterval = setInterval(() => {
      if (this.disposed) {
        this.clearAnalysisToolsInterval();
        return;
      }
      attempts++;
      let layoutChessboard = this.chessboard.parentElement;
      if (layoutChessboard == null) {
        if (attempts > 200) this.clearAnalysisToolsInterval();
        return;
      }
      let layoutMain = layoutChessboard.parentElement;
      if (layoutMain == null) {
        if (attempts > 200) this.clearAnalysisToolsInterval();
        return;
      }

      this.clearAnalysisToolsInterval();

      if (getBooleanConfig(enumOptions.DepthBar, true) && this.depthBar == null) {
        // create depth bar
        let depthBar = document.createElement("div");
        depthBar.classList.add("depthBarLayoutt");
        depthBar.innerHTML = `<div class="depthBarr"><span class="depthBarProgress"></span></div>`;
        layoutMain.insertBefore(depthBar, layoutChessboard.nextSibling);
        this.depthBar = depthBar.querySelector(".depthBarProgress");
      }
      if (getBooleanConfig(enumOptions.EvaluationBar, true) && this.evalBar == null) {
        // create eval bar
        let evalBar = document.createElement("div");
        evalBar.style.flex = "1 1 auto";
        evalBar.innerHTML = `
                <div class="evaluation-bar-bar">
                    <span class="evaluation-bar-scoreAbbreviated evaluation-bar-dark">0.0</span>
                    <span class="evaluation-bar-score evaluation-bar-dark ">+0.00</span>
                    <div class="evaluation-bar-fill">
                    <div class="evaluation-bar-color evaluation-bar-black"></div>
                    <div class="evaluation-bar-color evaluation-bar-draw"></div>
                    <div class="evaluation-bar-color evaluation-bar-white" style="transform: translate3d(0px, 50%, 0px);"></div>
                    </div>
                </div>`;
        let layoutEvaluation = layoutChessboard.querySelector(
          "#board-layout-evaluation, .board-layout-evaluation"
        );
        if (layoutEvaluation == null) {
          layoutEvaluation = document.createElement("div");
          layoutEvaluation.id = "board-layout-evaluation";
          layoutEvaluation.classList.add("board-layout-evaluation");
          layoutChessboard.insertBefore(
            layoutEvaluation,
            layoutChessboard.firstElementChild
          );
        }
        layoutEvaluation.innerHTML = "";
        layoutEvaluation.appendChild(evalBar);
        this.evalBar = layoutEvaluation.querySelector(".evaluation-bar-bar");
        this.evalBarFill = layoutEvaluation.querySelector(
          ".evaluation-bar-white"
        );
        this.evalScore = layoutEvaluation.querySelector(
          ".evaluation-bar-score"
        );
        this.evalScoreAbbreviated = layoutEvaluation.querySelector(
          ".evaluation-bar-scoreAbbreviated"
        );
        if (!this.options.isWhiteOnBottom && this.options.flipped)
          this.evalBar.classList.add("evaluation-bar-flipped");
      }
    }, 10);
  }
  clearAnalysisToolsInterval() {
    if (this.analysisToolsInterval != null) {
      clearInterval(this.analysisToolsInterval);
      this.analysisToolsInterval = null;
    }
  }
  RefreshEvalutionBar(){
    // Rest evaluation bar
    this.RefreshEvaluationBar();
  }
  RefreshEvaluationBar(){
    if (this.disposed) return;
    if(getBooleanConfig(enumOptions.EvaluationBar, true)){
      if (this.evalBar == null){
        this.CreateAnalysisTools();
      } else if (this.evalBar != null) {
        this.evalBar.remove();
        this.evalBar = null;
        this.evalBarFill = null;
        this.evalScore = null;
        this.evalScoreAbbreviated = null;
        this.CreateAnalysisTools();
      }
    }
  }
  UpdateEngine(isNewGame) {
    if (this.disposed) return;
    try {
      if (!this.UpdatedChessMintmaster.engine) return;
      let FENs = this.controller.getFEN();
      if (!FENs) return;
      this.UpdatedChessMintmaster.engine.UpdatePosition(FENs, isNewGame);
      this.SetCurrentDepth(0);
    } catch (error) {
      reportMint("error", "Could not update engine position.", error);
    }
  }
  ResetGame() {
    if (this.disposed) return;
    this.UpdateEngine(true);
    this.RefreshEvaluationBar();
  }
  RemoveCurrentMarkings() {
    if (this.disposed) return;
    this.currentMarkings.forEach((marking) => {
      if (!marking || !marking.data || !this.controller.markings) return;
      let key = marking.type + "|";
      if (marking.data.square != null) key += marking.data.square;
      else key += `${marking.data.from}${marking.data.to}`;
      if (typeof this.controller.markings.removeOne === "function") {
        this.controller.markings.removeOne(key);
      }
    });
    this.currentMarkings = [];
  }
  dispose() {
    this.clearAnalysisToolsInterval();
    this.RemoveCurrentMarkings();

    let depthNode = this.depthBar;
    while (
      depthNode &&
      depthNode.classList &&
      !depthNode.classList.contains("depthBarLayoutt")
    ) {
      depthNode = depthNode.parentElement;
    }
    if (depthNode && depthNode.parentElement) {
      depthNode.parentElement.removeChild(depthNode);
    }

    if (this.evalBar && this.evalBar.parentElement) {
      this.evalBar.parentElement.removeChild(this.evalBar);
    }

    this.depthBar = null;
    this.evalBar = null;
    this.evalBarFill = null;
    this.evalScore = null;
    this.evalScoreAbbreviated = null;
    this.disposed = true;
  }
  HintMoves(topMoves, lastTopMoves, isBestMove) {
    if (this.disposed) return;
    if (!Array.isArray(topMoves) || topMoves.length === 0) return;
    topMoves = topMoves.filter((move) => move && move.from && move.to);
    if (topMoves.length === 0) return;
    let bestMove = topMoves[0];
    if (getBooleanConfig(enumOptions.ShowHints, true) && this.controller.markings) {
      this.RemoveCurrentMarkings();
      topMoves.forEach((move, idx) => {
        // isBestMove means final evaluation, don't include the moves that has less
        // depth than the best move
        if (isBestMove && move.depth != bestMove.depth) return;

        // Add fast check evalution
        if (idx != 0 && move.cp != null && move.mate == null) {
          let hlColor = getGradientColor(
            "#ff0000",
            "#0000ff",
            clampNumber(((move.cp + 250) / 500) ** 4, 0, 1, 0.5)
          );
          this.currentMarkings.push({
            data: {
              opacity: 0.4,
              color: hlColor,
              square: move.to,
            },
            node: true,
            persistent: true,
            type: "highlight",
          });
        }

        // Draw arror
        let arrowColors = this.options.arrowColors || {};
        let color =
          idx == 0
            ? arrowColors.alt
            : idx >= 1 && idx <= 2
            ? arrowColors.shift
            : idx >= 3 && idx <= 5
            ? arrowColors.default
            : arrowColors.ctrl;
        if (!color) color = "#10b981";
        this.currentMarkings.push({
          data: {
            from: move.from,
            color: color,
            opacity: 0.8,
            to: move.to,
          },
          node: true,
          persistent: true,
          type: "arrow",
        });
        if (move.mate != null) {
          this.currentMarkings.push({
            data: {
              square: move.to,
              type: move.mate < 0 ? "ResignWhite" : "WinnerWhite",
            },
            node: true,
            persistent: true,
            type: "effect",
          });
        }
      });
      // reverse the markings to make the best move arrow appear on top
      this.currentMarkings.reverse();
      if (typeof this.controller.markings.addMany === "function") {
        this.controller.markings.addMany(this.currentMarkings);
      }
    }
    if (getBooleanConfig(enumOptions.DepthBar, true)) {
      let depthPercent =
        ((isBestMove ? bestMove.depth : bestMove.depth - 1) /
          getNumberConfig(enumOptions.Depth, 3, 1, 99)) *
        100;
      this.SetCurrentDepth(depthPercent);
    }
    if (getBooleanConfig(enumOptions.EvaluationBar, true)) {
      let score = bestMove.mate != null ? bestMove.mate : bestMove.cp;
      if (!Number.isFinite(Number(score))) return;
      if (this.controller.getTurn() == 2) score *= -1;
      this.SetEvaluation(score, bestMove.mate != null);
    }
  }
  SetCurrentDepth(percentage) {
    if (this.depthBar == null) return;
    percentage = clampNumber(percentage, 0, 100, 0);
    let style = this.depthBar.style;
    if (percentage <= 0) {
      this.depthBar.classList.add("disable-transition");
      style.width = `0%`;
      this.depthBar.classList.remove("disable-transition");
    } else {
      if (percentage > 100) percentage = 100;
      style.width = `${percentage}%`;
    }
  }
  SetEvaluation(score, isMate) {
    score = Number(score);
    if (
      this.evalBar == null ||
      this.evalBarFill == null ||
      this.evalScore == null ||
      this.evalScoreAbbreviated == null ||
      !Number.isFinite(score)
    ) return;
    var percentage, textNumber, textScoreAbb;
    if (!isMate) {
      let eval_max = 500;
      let eval_min = -500;
      let smallScore = score / 100;
      percentage =
        90 - ((score - eval_min) / (eval_max - eval_min)) * (95 - 5) + 5;
      if (percentage < 5) percentage = 5;
      else if (percentage > 95) percentage = 95;
      textNumber = (score >= 0 ? "+" : "") + smallScore.toFixed(2);
      textScoreAbb = Math.abs(smallScore).toFixed(1);
    } else {
      percentage = score < 0 ? 100 : 0;
      textNumber = "M" + Math.abs(score).toString();
      textScoreAbb = textNumber;
    }
    this.evalBarFill.style.transform = `translate3d(0px, ${percentage}%, 0px)`;
    this.evalScore.innerText = textNumber;
    this.evalScoreAbbreviated.innerText = textScoreAbb;
    let classSideAdd =
      score >= 0 ? "evaluation-bar-dark" : "evaluation-bar-light";
    let classSideRemove =
      score >= 0 ? "evaluation-bar-light" : "evaluation-bar-dark";
    this.evalScore.classList.remove(classSideRemove);
    this.evalScoreAbbreviated.classList.remove(classSideRemove);
    this.evalScore.classList.add(classSideAdd);
    this.evalScoreAbbreviated.classList.add(classSideAdd);
  }
  getPlayingAs() {
    // Return 2 if player chose black
    return this.options.isPlayerBlack ? 2 : 1;
  }
}

class StockfishEngine {
  constructor(UpdatedChessMintmaster) {
    let stockfishJsURL;
    this.UpdatedChessMintmaster = UpdatedChessMintmaster;
    this.loaded = false;
    this.stopInFlight = false;
    this.ready = false;
    this.isEvaluating = false;
    this.isRequestedStop = false;
    this.isGameStarted = false; // New state to track if a game has started
    this.readyCallbacks = [];
    this.goDoneCallbacks = [];
    this.topMoves = [];
    this.lastTopMoves = [];
    this.moveCounter = 0;
    this.maxAutoMoves = 5;
    this.isPreMoveSequence = false;
    this.hasShownLimitMessage = false;
    this.isInTheory = false;
    this.lastMoveScore = null;
    this.stopTimeout = null;
    this.reconnectTimer = null;
    this.disposed = false;
    this.resetEngineOptionDiscovery();
    this.reconnectDelay = 500;
    this.maxReconnectDelay = 3000;
    this.reconnectAttempts = 0;
    this.maxReconnectAttempts = 5;
    this.depth = getIntegerConfig(
      enumOptions.Depth,
      getDefaultDepth(),
      1,
      getDepthLimit()
    );
    this.options = {
      "MultiPV": getMultiPVLimit(),
    };

    // Initialize Stockfish
    if (!getBooleanConfig(enumOptions.ApiStockfish, true)) {
      let stockfishPathConfig = Config.threadedEnginePaths.stockfish;
      try {
        new SharedArrayBuffer(
          getIntegerConfig(
            enumOptions.HashtableRam,
            isLowPowerMintDevice() ? 64 : 256,
            1,
            isLowPowerMintDevice() ? 128 : 1048576
          )
        );
        stockfishJsURL = `${stockfishPathConfig.multiThreaded.loader}#${stockfishPathConfig.multiThreaded.engine}`;
      } catch (e) {
        stockfishJsURL = `${stockfishPathConfig.singleThreaded.loader}#${stockfishPathConfig.singleThreaded.engine}`;
      }
      this.initializeWorker(stockfishJsURL);
    } else {
      this.initializeWebSocket(getValueConfig(enumOptions.UrlApiStockfish));
    }
  }

  initializeWorker(stockfishJsURL) {
    if (!stockfishJsURL || stockfishJsURL === "#") {
      reportMint("error", "Stockfish worker path is missing.");
      return;
    }
    try {
      this.stockfish = new Worker(stockfishJsURL);
      this.stockfish.onmessage = (e) => {
        this.ProcessMessage(e);
      };
      this.beginUciHandshake();
    } catch (e) {
      this.lastError = e;
      reportMint("error", "Failed to load Stockfish worker.", e);
      showMintToast("UpdatedChessMint-engine-error", "Engine worker failed to load.", {
        backgroundColor: "#b33430",
      });
    }
  }

  initializeWebSocket(url) {
    if (!url) {
      reportMint("error", "Engine socket URL is missing.");
      return;
    }
    try {
      this.stockfish = new WebSocket(url);
      this.stockfish.addEventListener("open", () => {
        reportMint("info", "Engine connection opened.");
        this.reconnectAttempts = 0;
        this.beginUciHandshake();
      });

      this.stockfish.addEventListener("message", (event) => {
        this.ProcessMessage(event.data);
      });

      this.stockfish.addEventListener("close", () => {
        reportMint("warn", "Engine connection closed.");
        this.handleDisconnect();
      });

      this.stockfish.addEventListener("error", (error) => {
        reportMint("error", "Engine connection error.", error);
        this.handleDisconnect();
      });
    } catch (e) {
      this.lastError = e;
      reportMint("error", "Failed to create engine connection.", e);
      showMintToast("UpdatedChessMint-engine-error", "Engine connection failed.", {
        backgroundColor: "#b33430",
      });
    }
  }

  send(cmd) {
    try {
      if (!this.stockfish) return false;
      if (!getBooleanConfig(enumOptions.ApiStockfish, true)) {
        this.stockfish.postMessage(cmd);
        return true;
      }
      if (this.isWebSocketOpen()) {
        this.stockfish.send(cmd);
        return true;
      } else {
        reportMint("warn", "Attempted to send command while engine socket is not open.");
        return false;
      }
    } catch (error) {
      reportMint("error", "Failed to send command to engine.", error);
      return false;
    }
  }

  isWebSocketOpen() {
    return this.stockfish && this.stockfish.readyState === WebSocket.OPEN;
  }

  go() {
    this.onReady(() => {
      this.stopEvaluation(() => {
        // Prevent overlapping evaluations
        if (this.isEvaluating) return;
        this.isEvaluating = true;
        this.send(`go depth ${this.depth}`);
      });
    });
  }

  handleDisconnect() {
    if (this.disposed) return;
    this.ready = false;
    this.loaded = false;
    this.isEvaluating = false; // Reset evaluation state
    if (getBooleanConfig(enumOptions.ApiStockfish, true)) {
      this.attemptReconnect();
    }
  }

  attemptReconnect() {
    if (this.reconnectAttempts < this.maxReconnectAttempts) {
      this.reconnectAttempts++;
      const delay = Math.min(this.reconnectDelay * this.reconnectAttempts, this.maxReconnectDelay);
      reportMint("info", `Attempting engine reconnect in ${delay / 1000} seconds.`);
      clearTimeout(this.reconnectTimer);
      this.reconnectTimer = setTimeout(() => {
        this.reconnectTimer = null;
        if (this.disposed) return;
        this.initializeWebSocket(getValueConfig(enumOptions.UrlApiStockfish));
      }, delay);
    } else {
      reportMint("error", "Max engine reconnect attempts reached.");
      showMintToast("UpdatedChessMint-engine-offline", "Engine offline. Start or reconnect the engine.", {
        backgroundColor: "#b33430",
        duration: 3000,
      });
    }
  }

  onReady(callback) {
    if (this.disposed) return;
    if (this.ready) {
      callback();
    } else {
      if (typeof callback === "function") this.readyCallbacks.push(callback);
      if (!this.send("isready")) {
        reportMint("warn", "Engine is not ready yet.");
      }
    }
  }

  stopEvaluation(callback) {
    if (this.isEvaluating) {
      if (typeof callback === "function") {
        this.goDoneCallbacks.push(callback);
      }
      if (this.stopInFlight) return;
      this.stopInFlight = true;
      this.isRequestedStop = true;
      this.send("stop");
      clearTimeout(this.stopTimeout);
      this.stopTimeout = setTimeout(() => {
        this.finishStop();
      }, 1500);
    } else {
      if (typeof callback === "function") callback();
    }
  }

  finishStop() {
    clearTimeout(this.stopTimeout);
    this.stopTimeout = null;
    this.isEvaluating = false;
    this.isRequestedStop = false;
    this.stopInFlight = false;
    this.executeCallbacks();
  }
  
  onStockfishResponse() {
    if (this.isRequestedStop) {
      this.finishStop();
    }
  }

  executeCallbacks() {
    while (this.goDoneCallbacks.length) {
      const callback = this.goDoneCallbacks.shift();
      if (typeof callback === "function") callback();
    }
  }

  UpdatePosition(FENs = null, isNewGame = true) {
    if (!FENs) return;
    this.onReady(() => {
      this.stopEvaluation(() => {
        if (isNewGame) {
          this.resetMoveCounters();
          reportMint("info", "New game detected; engine state reset.");
        }
        this.MoveAndGo(FENs, isNewGame);
      });
    });
  }

  resetMoveCounters() {
    this.moveCounter = 0;
    this.hasShownLimitMessage = false;
    this.isPreMoveSequence = true;
  }

  restartGame() {
    this.stopEvaluation(() => {
      this.isGameStarted = false;
      this.moveCounter = 0;
      this.isPreMoveSequence = false;
      this.send("ucinewgame");
      this.isGameStarted = true;
      this.go();
    });
  }

  UpdateExtensionOptions(options) {
    if (this.disposed) return;
    // Handle both null/undefined cases
    if (options == null) options = {};
    this.depth = getIntegerConfig(
      enumOptions.Depth,
      getDefaultDepth(),
      1,
      getDepthLimit()
    );
    this.options["MultiPV"] = getMultiPVLimit();
    this.options["Threads"] = getIntegerConfig(enumOptions.NumCores, 1, 1, 64);
    this.options["Hash"] = getIntegerConfig(
      enumOptions.HashtableRam,
      isLowPowerMintDevice() ? 64 : 256,
      1,
      isLowPowerMintDevice() ? 128 : 1048576
    );
    this.UpdateOptions(this.options);
    if (this.topMoves.length > 0) this.onTopMoves(null, !this.isEvaluating);
  }

  UpdateOptions(options = null) {
    if (options === null) options = this.options;
    Object.keys(options).forEach((key) => {
      this.sendEngineOption(key, options[key]);
    });
  }
  resetEngineOptionDiscovery() {
    this.supportedEngineOptions = new Set();
    this.hasReceivedEngineOptions = false;
    this.reportedUnsupportedEngineOptions = new Set();
  }
  beginUciHandshake() {
    if (this.disposed) return;
    this.resetEngineOptionDiscovery();
    this.ready = false;
    this.loaded = false;
    this.send("uci");
  }
  normalizeEngineOptionName(name) {
    return String(name || "").trim().toLowerCase();
  }
  registerEngineOption(line) {
    let optionMatch = line.match(/^option name\s+(.+?)\s+type\s+/i);
    if (!optionMatch) return false;
    this.hasReceivedEngineOptions = true;
    this.supportedEngineOptions.add(
      this.normalizeEngineOptionName(optionMatch[1])
    );
    return true;
  }
  isEngineOptionSupported(key) {
    if (!this.hasReceivedEngineOptions) return true;
    return this.supportedEngineOptions.has(this.normalizeEngineOptionName(key));
  }
  sendEngineOption(key, value) {
    if (value === undefined || value === null || value === "") return;
    if (!this.isEngineOptionSupported(key)) {
      let normalized = this.normalizeEngineOptionName(key);
      if (!this.reportedUnsupportedEngineOptions.has(normalized)) {
        this.reportedUnsupportedEngineOptions.add(normalized);
        reportMint("info", `Skipped unsupported engine option: ${key}.`);
      }
      return;
    }
    this.send(`setoption name ${key} value ${value}`);
  }
  ProcessMessage(event) {
    if (this.disposed) return;
    this.ready = false;
    let line = event && typeof event === "object" ? event.data : event;
    if (typeof line !== "string" || line.length === 0) return;

    if (this.registerEngineOption(line)) {
      return;
    }

    if (line === "uciok") {
      this.loaded = true;
      this.UpdateOptions();
      this.send("ucinewgame");
      this.send("isready");
      this.UpdatedChessMintmaster.onEngineLoaded();
    } else if (line === "readyok") {
      this.ready = true;
      this.executeReadyCallbacks();
    } else if (this.isEvaluating && line === "Load eval file success: 1") {
      this.finishStop();
    } else {
      let depthMatch = line.match(/^info .*?depth (\d+)/);
      let seldepthMatch = line.match(/^info .*?seldepth (\d+)/);
      let timeMatch = line.match(/^info .*?time (\d+)/);
      let scoreMatch = line.match(/^info .*?score (\w+) (-?\d+)/);
      let pvMatch = line.match(/^info .*?pv ([a-h][1-8][a-h][1-8][qrbn]?(?: [a-h][1-8][a-h][1-8][qrbn]?)*)(?: .*)?/);
      let multipvMatch = line.match(/^info .*?multipv (\d+)/);
      let bestMoveMatch = line.match(/^bestmove ([a-h][1-8][a-h][1-8][qrbn]?)(?: ponder ([a-h][1-8][a-h][1-8][qrbn]?))?/);

      if (depthMatch && scoreMatch && pvMatch) {
        let depth = parseInt(depthMatch[1]);
        let seldepth = seldepthMatch ? parseInt(seldepthMatch[1]) : null;
        let time = timeMatch ? parseInt(timeMatch[1]) : null;
        let scoreType = scoreMatch[1];
        let score = parseInt(scoreMatch[2]);
        let multipv = multipvMatch ? parseInt(multipvMatch[1]) : 1;
        let pv = pvMatch[1];

        let cpScore = scoreType === "cp" ? score : null;
        let mateScore = scoreType === "mate" ? score : null;

        if (!this.isRequestedStop) {
          let move = new TopMove(pv, depth, cpScore, mateScore, multipv);
          this.onTopMoves(move, false);
        }
      } else if (bestMoveMatch) {
        this.isEvaluating = false;
        if (this.stopInFlight || this.isRequestedStop) {
          this.finishStop();
          return;
        }
        if (!this.isRequestedStop && bestMoveMatch[1] !== undefined) {
          const bestMove = bestMoveMatch[1];
          const ponderMove = bestMoveMatch[2];
          const index = this.topMoves.findIndex((object) => object.move === bestMove);

          if (index < 0) {
            reportMint("warn", `The engine returned best move "${bestMove}" outside the PV list.`);
            let bestMoveOnTop = new TopMove(
              bestMove,
              getIntegerConfig(enumOptions.Depth, 3, 1, 99),
              100,
              null
            );
            this.onTopMoves(bestMoveOnTop, true);
          } else {
            this.onTopMoves(this.topMoves[index], true);
          }
        }
        this.isRequestedStop = false;
      }
    }
  }  

  executeReadyCallbacks() {
    while (this.readyCallbacks.length > 0) {
      const callback = this.readyCallbacks.shift();
      if (typeof callback === "function") callback();
    }
  }

  dispose() {
    this.disposed = true;
    this.ready = false;
    this.loaded = false;
    this.isEvaluating = false;
    this.isRequestedStop = false;
    this.stopInFlight = false;
    this.readyCallbacks = [];
    this.goDoneCallbacks = [];
    clearTimeout(this.stopTimeout);
    clearTimeout(this.reconnectTimer);
    this.stopTimeout = null;
    this.reconnectTimer = null;
    try {
      if (this.stockfish && typeof this.stockfish.close === "function") {
        this.stockfish.close();
      } else if (this.stockfish && typeof this.stockfish.terminate === "function") {
        this.stockfish.terminate();
      }
    } catch (error) {
      reportMint("warn", "Could not dispose engine connection cleanly.", error);
    }
    this.stockfish = null;
  }
  MoveAndGo(FENs = null, isNewGame = true) {
    // let it go, let it gooo
    let go = () => {
      this.lastTopMoves = isNewGame ? [] : this.topMoves;
      this.lastMoveScore = null;
      this.topMoves = [];
      if (isNewGame) this.isInTheory = eTable != null;
      if (this.isInTheory) {
        let shortFen = this.UpdatedChessMintmaster.game.controller
          .getFEN()
          .split(" ")
          .slice(0, 3)
          .join(" ");
        if (eTable.get(shortFen) !== true) this.isInTheory = false;
      }
      if (FENs != null) this.send(`position fen ${FENs}`);
      this.go();
    };
    this.onReady(() => {
      if (isNewGame) {
        this.send("ucinewgame");
        this.onReady(go);
      } else {
        go();
      }
    });
  }
  AnalyzeLastMove() {
    this.lastMoveScore = null;
    let lastMove = this.UpdatedChessMintmaster.game.controller.getLastMove();
    if (lastMove === undefined) return;
    if (this.isInTheory) {
      this.lastMoveScore = "Book";
    } else if (this.lastTopMoves.length > 0) {
      let lastBestMove = this.lastTopMoves[0];
      if (!lastBestMove) return;
      // check if last move is the best move
      if (
        lastBestMove.from === lastMove.from &&
        lastBestMove.to === lastMove.to
      ) {
        this.lastMoveScore = "BestMove";
      } else {
        let bestMove = this.topMoves[0];
        if (!bestMove) return;
        if (lastBestMove.mate != null) {
          // if last move is losing mate, this move just escapes a mate
          // if last move is winning mate, this move is a missed win
          if (bestMove.mate == null) {
            this.lastMoveScore =
              lastBestMove.mate > 0 ? "MissedWin" : "Brilliant";
          } else {
            // both move are mate
            this.lastMoveScore =
              lastBestMove.mate > 0 ? "Excellent" : "ResignWhite";
          }
        } else if (bestMove.mate != null) {
          // brilliant if it found a mate, blunder if it moved into a mate
          this.lastMoveScore = bestMove.mate < 0 ? "Brilliant" : "Blunder";
        } else if (bestMove.cp != null && lastBestMove.cp != null) {
          let evalDiff = -(bestMove.cp + lastBestMove.cp);
          if (evalDiff > 100) this.lastMoveScore = "Brilliant";
          else if (evalDiff > 0) this.lastMoveScore = "GreatFind";
          else if (evalDiff > -10) this.lastMoveScore = "BestMove";
          else if (evalDiff > -25) this.lastMoveScore = "Excellent";
          else if (evalDiff > -50) this.lastMoveScore = "Good";
          else if (evalDiff > -100) this.lastMoveScore = "Inaccuracy";
          else if (evalDiff > -250) this.lastMoveScore = "Mistake";
          else this.lastMoveScore = "Blunder";
        } else {
          reportMint("warn", "Skipped last move analysis because score data was incomplete.");
        }
      }
    }
    // add highlight and effect
    if (this.lastMoveScore != null) {
      const highlightColors = {
        Brilliant: "#1baca6",
        GreatFind: "#5c8bb0",
        BestMove: "#9eba5a",
        Excellent: "#96bc4b",
        Good: "#96af8b",
        Book: "#a88865",
        Inaccuracy: "#f0c15c",
        Mistake: "#e6912c",
        Blunder: "#b33430",
        MissedWin: "#dbac16",
      };
      let hlColor = highlightColors[this.lastMoveScore];
      if (hlColor != null) {
        if (
          this.UpdatedChessMintmaster.game.controller.markings &&
          typeof this.UpdatedChessMintmaster.game.controller.markings.addOne === "function"
        ) this.UpdatedChessMintmaster.game.controller.markings.addOne({
          data: {
            opacity: 0.5,
            color: hlColor,
            square: lastMove.to,
          },
          node: true,
          persistent: true,
          type: "highlight",
        });
      }
      // this.UpdatedChessMintmaster.game.controller.markings.removeOne(`effect|${lastMove.to}`);
      if (
        this.UpdatedChessMintmaster.game.controller.markings &&
        typeof this.UpdatedChessMintmaster.game.controller.markings.addOne === "function"
      ) this.UpdatedChessMintmaster.game.controller.markings.addOne({
        data: {
          square: lastMove.to,
          type: this.lastMoveScore,
        },
        node: true,
        persistent: true,
        type: "effect",
      });
    }
  }

  onTopMoves(move = null, isBestMove = false) {
    var top_pv_moves = [];
    window.top_pv_moves = top_pv_moves;
    var bestMoveSelected = false;
    if (move != null) {
        if (!move.move || !move.from || !move.to) return;
        const index = this.topMoves.findIndex(
            (object) => object.move === move.move
        );
        if (isBestMove) {
            // Basically, engine just finished evaluation
            bestMoveSelected = true; // A best move has been selected
            if (index === -1) {
                this.topMoves.push(move);
                this.SortTopMoves();
            }
        } else {
            if (index === -1) {
                // If move not found, just push it to topMoves
                this.topMoves.push(move);
                this.SortTopMoves();
            } else {
                // If move found, compare depths and update if necessary
                if (move.depth >= this.topMoves[index].depth) {
                    this.topMoves[index] = move;
                    this.SortTopMoves();
                }
            }
        }
    }
    if (this.topMoves.length === 0) return;
    if (
      bestMoveSelected &&
      getBooleanConfig(enumOptions.LegitAutoMove, false) &&
      !isMoveAutomationAllowed()
    ) {
      showMintToast(
        "UpdatedChessMint-automation-disabled",
        "Auto move is disabled. Analysis and hints are still active.",
        { backgroundColor: "#374151", duration: 2500 }
      );
    }
    if (bestMoveSelected && this.topMoves.length > 0) {
      const bestMove = this.topMoves[0];

      if (isMoveAutomationAllowed() && getBooleanConfig(enumOptions.Premove, false) && getBooleanConfig(enumOptions.LegitAutoMove, false)) {
        const currentFEN = this.UpdatedChessMintmaster.game.controller.getFEN();
        const currentTurn = currentFEN.split(" ")[1]; // 'w' or 'b'
        const playingAs = this.UpdatedChessMintmaster.game.getPlayingAs();
        // [FIX] Execute pre-moves if:
        // - It's player's turn AND
        // - Haven't reached move limit
        if (
          ((playingAs === 1 && currentTurn === 'w') ||
           (playingAs === 2 && currentTurn === 'b')) &&
          this.moveCounter < getIntegerConfig(enumOptions.MaxPreMoves, 3, 0, 20) &&
          !this.hasShownLimitMessage
        ) {
          const legalMoves = this.UpdatedChessMintmaster.game.controller.getLegalMoves();
          const moveData = legalMoves.find(
            move => move.from === bestMove.from && move.to === bestMove.to
          );
    
          if (moveData) {
            moveData.userGenerated = true;
    
            if (bestMove.promotion !== null) {
              moveData.promotion = bestMove.promotion;
            }
    
            this.moveCounter++; // Increment move counter
    
            // Calculate pre-move execution time
            let pre_move_time = getRandomizedDelay(
              enumOptions.PreMoveTime,
              enumOptions.PreMoveTimeRandom,
              enumOptions.PreMoveTimeRandomDiv,
              enumOptions.PreMoveTimeRandomMulti,
              1000
            );
    
            setTimeout(() => {
              this.UpdatedChessMintmaster.game.controller.move(moveData);
    
              if (window.toaster) {
                window.toaster.add({
                  id: "auto-move-counter",
                  duration: 2000,
                  icon: "circle-info",
                  content: `Pre-move ${this.moveCounter}/${getIntegerConfig(enumOptions.MaxPreMoves, 3, 0, 20)} executed!`,
                  style: {
                    position: "fixed",
                    bottom: "120px",
                    right: "30px",
                    backgroundColor: "#2ecc71",
                    color: "white"
                  }
                });
              }
    
              if (this.moveCounter >= getIntegerConfig(enumOptions.MaxPreMoves, 3, 0, 20)) {
                if (window.toaster) {
                  window.toaster.add({
                    id: "auto-move-limit",
                    duration: 2000, // Reduced from 3000
                    icon: "circle-checkmark",
                    content: "Maximum pre-moves reached!",
                    style: {
                      position: "fixed",
                      bottom: "120px",
                      right: "30px",
                      backgroundColor: "#e67e22",
                      color: "white"
                    }
                  });
                }
                this.hasShownLimitMessage = true;
              }
            }, pre_move_time); // Execute with calculated delay
          }
        }
    
        // Check for mate in 3 or less - MOVED INSIDE THE PREMOVE CHECK
        if (bestMove.mate !== null && bestMove.mate > 0 && bestMove.mate <= getIntegerConfig(enumOptions.MateFinderValue, 5, 1, 20)) {
          const legalMoves = this.UpdatedChessMintmaster.game.controller.getLegalMoves();
          const moveData = legalMoves.find(
            move => move.from === bestMove.from && move.to === bestMove.to
          );
    
          if (moveData) {
            moveData.userGenerated = true;
    
            if (bestMove.promotion !== null) {
              moveData.promotion = bestMove.promotion;
            }
    
            if (window.toaster) {
              window.toaster.add({
                id: "premove-mate",
                duration: 2000,
                icon: "circle-checkmark",
                content: `UpdatedChessMint: Mate in ${bestMove.mate} move(s)! Executing premove...`,
                style: {
                  position: "fixed",
                  bottom: "120px",
                  right: "30px",
                  backgroundColor: "#1baca6",
                  color: "white",
                  fontWeight: "bold",
                },
              });
            }
    
            this.UpdatedChessMintmaster.game.controller.move(moveData);
          }
        }
      }
    }
    
    if (
      getBooleanConfig(enumOptions.TextToSpeech, false) &&
      this.topMoves[0] &&
      typeof SpeechSynthesisUtterance !== "undefined" &&
      window.speechSynthesis
    ) {
      const topMove = this.topMoves[0]; // Select the top move from the PV list
      const msg = new SpeechSynthesisUtterance(topMove.move); // Use topMove.move for the spoken text
      const voices = window.speechSynthesis.getVoices();
      const femaleVoices = voices.filter((voice) =>
        voice.voiceURI.includes("Google UK English Female")
      );
      if (femaleVoices.length > 0) {
        msg.voice = femaleVoices[0];
      }
      msg.volume = 0.75; // Set the volume to 75%
      msg.rate = 1;
      window.speechSynthesis.cancel(); // Stop any previous text-to-speech
      window.speechSynthesis.speak(msg);
    }

    if (bestMoveSelected) {
      // If a best move has been selected, consider all moves in topMoves
      top_pv_moves = this.topMoves.slice(0, getMultiPVLimit());
      window.top_pv_moves = top_pv_moves;
      // sort by rank in multipv
      this.UpdatedChessMintmaster.game.HintMoves(
        top_pv_moves,
        this.lastTopMoves,
        isBestMove
      );

      if (getBooleanConfig(enumOptions.MoveAnalysis, true)) {
        this.AnalyzeLastMove();
      }
    } else {
      // if da best move aint been selected yet
      if (isMoveAutomationAllowed() && getBooleanConfig(enumOptions.LegitAutoMove, false)) {
        // legit move stuff, ignore
        const movesWithAccuracy = this.topMoves.filter(
          (move) => move.accuracy !== undefined
        );

        if (movesWithAccuracy.length > 0) {
          // Sort the moves by accuracy in descending order
          movesWithAccuracy.sort((a, b) => b.accuracy - a.accuracy);

          // Calculate the total accuracy
          const totalAccuracy = movesWithAccuracy.reduce(
            (sum, move) => sum + move.accuracy,
            0
          );

          // Calculate the cumulative probabilities
          const cumulativeProbabilities = movesWithAccuracy.reduce(
            (arr, move) => {
              const lastProbability = arr.length > 0 ? arr[arr.length - 1] : 0;
              const probability = move.accuracy / totalAccuracy;
              arr.push(lastProbability + probability);
              return arr;
            },
            []
          );

          // Generate a random number between 0 and 1
          const random = Math.random();

          // Select a move based on the cumulative probabilities
          let selectedMove;
          for (let i = 0; i < cumulativeProbabilities.length; i++) {
            if (random <= cumulativeProbabilities[i]) {
              selectedMove = movesWithAccuracy[i];
              break;
            }
          }

          // Move the selected move to the front of the PV moves
          top_pv_moves = [
            selectedMove,
            ...this.topMoves.filter((move) => move !== selectedMove),
          ];
        } else {
          // If no moves have accuracy information, use the normal PV moves
          top_pv_moves = this.topMoves.slice(0, getMultiPVLimit());
        }
      } // end ignore
      if (isMoveAutomationAllowed() && getBooleanConfig(enumOptions.LegitAutoMove, false) && top_pv_moves.length > 0) {
        // random crap with auto move
        const randomMoveIndex = Math.floor(Math.random() * top_pv_moves.length);
        const randomMove = top_pv_moves[randomMoveIndex];
        top_pv_moves = [
          randomMove,
          ...top_pv_moves.filter((move) => move !== randomMove),
        ]; // Move the random move to the front of the PV moves
      } else {
        // if no auto move and engine aint even done, idfk what this is doing
        top_pv_moves = this.topMoves.slice(0, getMultiPVLimit());
      }
      window.top_pv_moves = top_pv_moves;
    }

    const bestMoveChance = getNumberConfig(enumOptions.BestMoveChance, 30, 0, 100);
    if (
      isMoveAutomationAllowed() &&
      Math.random() * 100 < bestMoveChance &&
      getBooleanConfig(enumOptions.LegitAutoMove, false) &&
      top_pv_moves.length > 0
    ) {
      top_pv_moves = [top_pv_moves[0]]; // Only consider the top move
      window.top_pv_moves = top_pv_moves;
    } else {
      // const randomMoveIndex = Math.floor(Math.random() * top_pv_moves.length);
      // const randomMove = top_pv_moves[randomMoveIndex];
      // top_pv_moves = [randomMove, ...top_pv_moves.filter(move => move !== randomMove)]; // Move the random move to the front of the PV moves
    }
    if (
      isMoveAutomationAllowed() &&
      bestMoveSelected &&
      getBooleanConfig(enumOptions.LegitAutoMove, false) &&
      top_pv_moves.length > 0 &&
      this.UpdatedChessMintmaster.game.getPlayingAs() ===
        this.UpdatedChessMintmaster.game.controller.getTurn()
    ) {
      let bestMove;
      if (getBooleanConfig(enumOptions.RandomBestMove, false)) {
        const random_best_move_index = Math.floor(
          Math.random() * top_pv_moves.length
        );
        bestMove = top_pv_moves[random_best_move_index];
      } else {
        bestMove = top_pv_moves[0];
      }
      if (!bestMove) return;
      const legalMoves = this.UpdatedChessMintmaster.game.controller.getLegalMoves();
      const index = legalMoves.findIndex(
        (move) => move.from === bestMove.from && move.to === bestMove.to
      );
      if (index < 0) {
        reportMint("warn", "Skipped move because it was not legal in the current position.");
        return;
      }
      const moveData = legalMoves[index];
      moveData.userGenerated = true;
      if (bestMove.promotion !== null) {
        moveData.promotion = bestMove.promotion;
      }
      if (getBooleanConfig(enumOptions.HighMateChance, false)) {
        const sortedMoves = this.topMoves.sort((a, b) => {
          if (a.mateIn !== null && b.mateIn === null) {
            return -1;
          } else if (a.mateIn === null && b.mateIn !== null) {
            return 1;
          } else if (a.mateIn !== null && b.mateIn !== null) {
            if (
              a.mateIn <= getIntegerConfig(enumOptions.MateFinderValue, 5, 1, 20) &&
              b.mateIn <= getIntegerConfig(enumOptions.MateFinderValue, 5, 1, 20)
            ) {
              return a.mateIn - b.mateIn;
            } else {
              return 0;
            }
          } else {
            return 0;
          }
        });
        top_pv_moves = sortedMoves.slice(0, Math.min(getMultiPVLimit(), this.topMoves.length));
        window.top_pv_moves = top_pv_moves;
        const mateMoves = top_pv_moves.filter((move) => move.mateIn !== null);
        if (mateMoves.length > 0) {
          const fastestMateMove = mateMoves.reduce((a, b) =>
            a.mateIn < b.mateIn ? a : b
          );
          top_pv_moves = [fastestMateMove];
        }
      }
      let auto_move_time = getRandomizedDelay(
        enumOptions.AutoMoveTime,
        enumOptions.AutoMoveTimeRandom,
        enumOptions.AutoMoveTimeRandomDiv,
        enumOptions.AutoMoveTimeRandomMulti,
        100
      );
      const secondsTillAutoMove = (auto_move_time / 1000).toFixed(1);
      if (window.toaster) {
        window.toaster.add({
          id: "chess.com",
          duration: (parseFloat(secondsTillAutoMove) + 1) * 1000,
          icon: "circle-info",
          content: `UpdatedChessMint: Auto move in ${secondsTillAutoMove} seconds`,
          // autoClose: 3000,
          style: {
            position: "fixed",
            bottom: "60px",
            right: "30px",
            backgroundColor: "black",
            color: "white",
          },
        });
      }
      setTimeout(() => {
        this.UpdatedChessMintmaster.game.controller.move(moveData);
      }, auto_move_time);
    }
  }
  SortTopMoves() {
    // sort the top move list to bring the best moves on top (index 0)
    this.topMoves.sort(function (a, b) {
      if (a.mate !== null && b.mate === null) {
        return a.mate < 0 ? 1 : -1;
      }
      if (a.mate === null && b.mate !== null) {
        return b.mate > 0 ? 1 : -1;
      }
      // both moves has no mate, compare the depth first than centipawn
      if (a.mate === null && b.mate === null) {
        if (a.depth === b.depth) {
          if (a.cp === b.cp) return 0;
          return a.cp > b.cp ? -1 : 1;
        }
        return a.depth > b.depth ? -1 : 1;
      }
      // If both are check mate

      if (a.mate < 0 && b.mate < 0) {
        if (a.line.length === b.line.length) return 0;
        return a.line.length < b.line.length ? 1 : -1;
      }
      if (a.mate > 0 && b.mate > 0) {
        if (a.line.length === b.line.length) return 0;
        return a.line.length > b.line.length ? 1 : -1;
      }

      return a.mate < b.mate ? 1 : -1;
    });
  }
}

class UpdatedChessMint {
  constructor(chessboard, options) {
    this.options = Object.assign({}, defaultOptionValues, options || {});
    this.game = new GameController(this, chessboard);
    this.engine = new StockfishEngine(this);
    this.optionsListener = (event) => {
        this.options = Object.assign({}, defaultOptionValues, event.detail || {});
        this.game.UpdateExtensionOptions();
        // Pass the updated options explicitly
        this.engine.UpdateExtensionOptions(this.options);

        // show a notification when the settings is updated, but only if the previous
        // notification has gone
        if (
          window.toaster &&
          Array.isArray(window.toaster.notifications) &&
          window.toaster.notifications.findIndex(
            (noti) => noti.id == "UpdatedChessMint-settings-updated"
          ) == -1
        ) {
          window.toaster.add({
            id: "UpdatedChessMint-settings-updated",
            duration: 2000,
            icon: "circle-gearwheel",
            content: `Settings updated!`,
          });
        }
      };
    window.addEventListener(
      "UpdatedChessMintUpdateOptions",
      this.optionsListener,
      false
    );
  }
  onEngineLoaded() {
    showMintToast("UpdatedChessMint-enabled", "UpdatedChessMint analysis is enabled.", {
      duration: 3000,
    });
  }
  resetPreMoveCounter() {
    this.engine.resetMoveCounters();
  }
  dispose() {
    if (this.optionsListener) {
      window.removeEventListener(
        "UpdatedChessMintUpdateOptions",
        this.optionsListener,
        false
      );
      this.optionsListener = null;
    }
    if (this.game && typeof this.game.dispose === "function") {
      this.game.dispose();
    }
    if (this.engine && typeof this.engine.dispose === "function") {
      this.engine.dispose();
    }
  }
}

/* The above code defines a JavaScript module named `ChromeRequest` that exports a single function
`getData`. This function takes a `data` parameter and returns a Promise that resolves with the data
received from a custom event dispatched on the `window` object. The custom event is named
"UpdatedChessMintGetOptions" and is expected to be handled by an event listener that will send a response
event named "UpdatedChessMintSendOptions" with the requested data. The `requestId` variable is used to
uniquely identify each request and match the response to the correct request. */

function InitUpdatedChessMint(chessboard) {
  // Fetch the ECO table
  if (Config.pathToEcoJson) {
    fetch(Config.pathToEcoJson).then(function (response) {
      if (!response.ok) throw new Error("Could not load ECO table");
      return __awaiter(this, void 0, void 0, function* () {
        let table = yield response.json();
        eTable = new Map((Array.isArray(table) ? table : []).map((data) => [data.f, true]));
      });
    }).catch(function () {
      eTable = new Map();
    });
  } else {
    eTable = new Map();
  }

  // Get the extension options
  ChromeRequest.getData().then(function (options) {
    try {
      if (
        UpdatedChessMintmaster &&
        UpdatedChessMintmaster.game &&
        UpdatedChessMintmaster.game.chessboard !== chessboard &&
        typeof UpdatedChessMintmaster.dispose === "function"
      ) {
        UpdatedChessMintmaster.dispose();
      }
      UpdatedChessMintmaster = new UpdatedChessMint(chessboard, options);

      // Add hotkeys
      installUpdatedChessMintHotkeys();
    } catch (e) {
      if (updatedChessMintActiveBoard === chessboard) {
        updatedChessMintActiveBoard = null;
        updatedChessMintInitStarted = false;
      }
      reportMint("error", "UpdatedChessMint failed to initialize.", e);
      showMintToast("UpdatedChessMint-init-error", "UpdatedChessMint could not start on this board.", {
        backgroundColor: "#b33430",
        duration: 3000,
      });
    }
  }).catch(function (error) {
    if (updatedChessMintActiveBoard === chessboard) {
      updatedChessMintActiveBoard = null;
      updatedChessMintInitStarted = false;
    }
    reportMint("error", "Could not read startup options.", error);
  });
}

var updatedChessMintInitStarted = false;
var updatedChessMintActiveBoard = null;
var updatedChessMintBoardObserver = null;
var updatedChessMintHotkeysInstalled = false;

function installUpdatedChessMintHotkeys() {
  if (updatedChessMintHotkeysInstalled) return;
  updatedChessMintHotkeysInstalled = true;
  document.addEventListener("keypress", function (e) {
    if (!UpdatedChessMintmaster || !UpdatedChessMintmaster.game) return;
    if (e.ctrlKey || e.metaKey || e.altKey) return;
    if (
      e.target &&
      ["INPUT", "TEXTAREA", "SELECT"].indexOf(e.target.tagName) !== -1
    ) return;
    if (e.key === "q") {
      if (typeof UpdatedChessMintmaster.game.controller.moveBackward === "function")
        UpdatedChessMintmaster.game.controller.moveBackward();
    }
    if (e.key === "e") {
      if (typeof UpdatedChessMintmaster.game.controller.moveForward === "function")
        UpdatedChessMintmaster.game.controller.moveForward();
    }
    if (e.key === "r") {
      if (typeof UpdatedChessMintmaster.game.controller.resetGame === "function")
        UpdatedChessMintmaster.game.controller.resetGame();
    }
    if (e.key === "w") {
      UpdatedChessMintmaster.game.ResetGame();
      UpdatedChessMintmaster.game.RefreshEvaluationBar();
    }
  });
}

function isUpdatedChessMintBoard(node) {
  if (!node || node.nodeType !== Node.ELEMENT_NODE || !node.tagName) return false;
  var tagName = node.tagName.toUpperCase();
  return tagName === "WC-CHESS-BOARD" || tagName === "CHESS-BOARD";
}

function hasChessComGameController(board) {
  return !!(
    board &&
    board.game &&
    typeof board.game.on === "function" &&
    typeof board.game.getFEN === "function" &&
    typeof board.game.getOptions === "function"
  );
}

function tryInitUpdatedChessMint(board, attemptsLeft) {
  if (!board) return;
  if (updatedChessMintActiveBoard === board && updatedChessMintInitStarted) return;

  if (hasChessComGameController(board)) {
    updatedChessMintInitStarted = true;
    updatedChessMintActiveBoard = board;
    InitUpdatedChessMint(board);
    return;
  }

  if (attemptsLeft > 0) {
    setTimeout(function () {
      tryInitUpdatedChessMint(board, attemptsLeft - 1);
    }, 250);
  }
}

function findUpdatedChessMintBoards(root) {
  var boards = [];
  if (!root) return boards;

  if (isUpdatedChessMintBoard(root)) {
    boards.push(root);
  }

  if (root.querySelectorAll) {
    root.querySelectorAll("wc-chess-board, chess-board").forEach(function (board) {
      boards.push(board);
    });
  }

  return boards;
}

function scanForUpdatedChessMintBoard(root) {
  findUpdatedChessMintBoards(root).some(function (board) {
    tryInitUpdatedChessMint(board, 40);
    return updatedChessMintActiveBoard === board;
  });
}

scanForUpdatedChessMintBoard(document);

if (typeof MutationObserver !== "undefined") {
  updatedChessMintBoardObserver = new MutationObserver(function (mutations) {
    mutations.forEach(function (mutation) {
      mutation.addedNodes.forEach(function (node) {
        if (node.nodeType === Node.ELEMENT_NODE) {
          scanForUpdatedChessMintBoard(node);
        }
      });
    });
  });

  updatedChessMintBoardObserver.observe(document.documentElement || document, {
    childList: true,
    subtree: true
  });
}


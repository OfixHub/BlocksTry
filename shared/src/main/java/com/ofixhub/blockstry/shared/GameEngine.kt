package com.ofixhub.blockstry.shared

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.LinkedList
import java.util.Queue
import kotlin.math.max

const val BOARD_WIDTH = 10
const val BOARD_HEIGHT = 20
const val INITIAL_SPEED_MS = 500L
const val FAST_DROP_SPEED_MS = 30L
const val MIN_SPEED_MS = 150L
const val POINTS_PER_SPEED_INCREASE = 100
const val SPEED_DECREASE_PER_INCREASE = 40L
const val MAX_SPEED_SCORE = 1000

data class Point(val x: Int, val y: Int)
enum class PieceType { I, O, T, J, L, S, Z }
data class Piece(val shape: List<Point>, val color: Color, val type: PieceType)
data class GameState(
    val board: List<List<PieceType?>>,
    val currentPiece: Piece?,
    val piecePosition: Point,
    val nextPiece: Piece?,
    val score: Int = 0,
    val isGameOver: Boolean = false,
    val ghostPiecePosition: Point? = null,
    val totalLinesCleared: Int = 0,
    val isPaused: Boolean = false,
    val linesToAnimate: List<Int> = emptyList()
)

object Pieces {
    private val I = Piece(listOf(Point(0, 0), Point(1, 0), Point(2, 0), Point(3, 0)), Color.Cyan, PieceType.I)
    private val O = Piece(listOf(Point(0, 0), Point(1, 0), Point(0, 1), Point(1, 1)), Color.Yellow, PieceType.O)
    private val T = Piece(listOf(Point(1, 0), Point(0, 1), Point(1, 1), Point(2, 1)), Color.Magenta, PieceType.T)
    private val J = Piece(listOf(Point(0, 0), Point(0, 1), Point(1, 1), Point(2, 1)), Color.Blue, PieceType.J)
    private val L = Piece(listOf(Point(2, 0), Point(0, 1), Point(1, 1), Point(2, 1)), Color(0xFFFFA500), PieceType.L)
    private val S = Piece(listOf(Point(1, 0), Point(2, 0), Point(0, 1), Point(1, 1)), Color.Green, PieceType.S)
    private val Z = Piece(listOf(Point(0, 0), Point(1, 0), Point(1, 1), Point(2, 1)), Color.Red, PieceType.Z)

    fun getPiecesForTheme(theme: ColorTheme): List<Piece> {
        val defaultPieces = listOf(I, O, T, J, L, S, Z)
        return defaultPieces.mapIndexed { index, piece ->
            piece.copy(color = theme.pieceColors.getOrElse(index) { Color.White })
        }
    }
}

class GameViewModel : ViewModel() {
    private val _gameState = MutableStateFlow(createInitialGameState())
    val gameState: StateFlow<GameState> = _gameState.asStateFlow()

    private var gameLoopJob: Job? = null
    private var currentSpeed = INITIAL_SPEED_MS
    private val pieceQueue: Queue<Piece> = LinkedList()
    private var pieceBag = mutableListOf<Piece>()

    init {
        reset()
    }

    private fun addPieceToQueueFromBag() {
        if (pieceBag.isEmpty()) {
            pieceBag.addAll(Pieces.getPiecesForTheme(SettingsManager.currentTheme).shuffled())
        }
        pieceQueue.add(pieceBag.removeAt(0))
    }

    fun reset() {
        pieceBag.clear()
        pieceQueue.clear()
        addPieceToQueueFromBag()
        addPieceToQueueFromBag()
        currentSpeed = INITIAL_SPEED_MS
        _gameState.value = createInitialGameState()
        startGameLoop()
    }

    private fun createInitialGameState(): GameState {
        val emptyBoard = List(BOARD_HEIGHT) { List(BOARD_WIDTH) { null as PieceType? } }
        return GameState(board = emptyBoard, currentPiece = null, piecePosition = Point(0, 0), nextPiece = null, isGameOver = false, score = 0, ghostPiecePosition = null)
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            if (_gameState.value.currentPiece == null) {
                spawnNewPiece()
            }
            while (!gameState.value.isGameOver && !gameState.value.isPaused) {
                val tickStart = System.currentTimeMillis()
                delay(currentSpeed)
                if (!gameState.value.isGameOver && !gameState.value.isPaused) {
                    moveDown()
                    // Compensate for time spent in moveDown() so speed stays accurate
                    val elapsed = System.currentTimeMillis() - tickStart
                    val drift = elapsed - currentSpeed
                    if (drift > 0 && drift < currentSpeed) {
                        currentSpeed = maxOf(MIN_SPEED_MS, currentSpeed - drift)
                    }
                }
            }
        }
    }

    fun togglePause() {
        val currentState = _gameState.value
        if (currentState.isGameOver) return
        
        _gameState.update { it.copy(isPaused = !it.isPaused) }
        
        if (!_gameState.value.isPaused) {
            // Reanudar
            startGameLoop()
        } else {
            // Pausar
            gameLoopJob?.cancel()
        }
    }

    private fun spawnNewPiece() {
        if (gameState.value.isGameOver) return

        val newPiece = pieceQueue.poll() ?: return
        addPieceToQueueFromBag()

        val nextPieceInQueue = pieceQueue.peek()
        val newPosition = Point(BOARD_WIDTH / 2 - 1, 0)

        if (isValidPosition(newPiece, newPosition)) {
            _gameState.update { it.copy(currentPiece = newPiece, piecePosition = newPosition, nextPiece = nextPieceInQueue) }
            updateGhostPiece()
        } else {
            val finalScore = _gameState.value.score
            _gameState.update { it.copy(isGameOver = true) }
            SettingsManager.submitScore(finalScore, "Tetris")
            gameLoopJob?.cancel()
        }
    }

    private fun moveDown() {
        if (gameState.value.isGameOver) return
        val currentState = gameState.value
        val piece = currentState.currentPiece ?: return
        val newPosition = currentState.piecePosition.copy(y = currentState.piecePosition.y + 1)

        if (isValidPosition(piece, newPosition)) {
            _gameState.update { it.copy(piecePosition = newPosition) }
        } else {
            lockPiece()
            viewModelScope.launch {
                clearLines()
                spawnNewPiece()
            }
        }
    }

    fun moveLeft() = move(-1)
    fun moveRight() = move(1)

    private fun move(dx: Int) {
        if (gameState.value.isGameOver) return
        val currentState = gameState.value
        val piece = currentState.currentPiece ?: return
        val newPosition = currentState.piecePosition.copy(x = currentState.piecePosition.x + dx)
        if (isValidPosition(piece, newPosition)) {
            _gameState.update { it.copy(piecePosition = newPosition) }
            updateGhostPiece()
        }
    }

    fun rotate() {
        if (gameState.value.isGameOver) return
        val currentState = gameState.value
        val piece = currentState.currentPiece ?: return
        // O-piece (square) does not rotate — check by type, not color
        if (piece.type == PieceType.O) return

        val rotatedShape = piece.shape.map { p -> Point(-p.y, p.x) }
        val rotatedPiece = piece.copy(shape = rotatedShape)

        var newPosition = currentState.piecePosition

        // Wall kick logic
        val testOffsets = listOf(Point(0, 0), Point(-1, 0), Point(1, 0), Point(0, -1), Point(-2, 0), Point(2, 0))
        for (offset in testOffsets) {
            val testPos = Point(currentState.piecePosition.x + offset.x, currentState.piecePosition.y + offset.y)
            if (isValidPosition(rotatedPiece, testPos)) {
                newPosition = testPos
                break
            }
        }

        if (isValidPosition(rotatedPiece, newPosition)) {
            _gameState.update { it.copy(currentPiece = rotatedPiece, piecePosition = newPosition) }
            updateGhostPiece()
        }
    }

    private fun updateGhostPiece() {
        val currentState = _gameState.value
        if (!SettingsManager.isGhostPieceEnabled || currentState.currentPiece == null) {
            if (currentState.ghostPiecePosition != null) {
                _gameState.update { it.copy(ghostPiecePosition = null) }
            }
            return
        }

        val piece = currentState.currentPiece
        var ghostPosition = currentState.piecePosition
        while (isValidPosition(piece, ghostPosition.copy(y = ghostPosition.y + 1))) {
            ghostPosition = ghostPosition.copy(y = ghostPosition.y + 1)
        }

        if (ghostPosition != currentState.ghostPiecePosition) {
            _gameState.update { it.copy(ghostPiecePosition = ghostPosition) }
        }
    }

    fun setDropSpeed(isFast: Boolean) {
        if (gameState.value.isGameOver || gameState.value.isPaused) return
        
        val baseSpeed = calculateSpeedFromScore(gameState.value.score)
        currentSpeed = if (isFast) FAST_DROP_SPEED_MS else baseSpeed
        
        gameLoopJob?.cancel()
        startGameLoop()
    }

    private fun calculateSpeedFromScore(score: Int): Long {
        if (SettingsManager.isConstantSpeedEnabled) {
            return INITIAL_SPEED_MS
        }
        
        val speedIncreases = (score / POINTS_PER_SPEED_INCREASE).coerceAtMost(MAX_SPEED_SCORE / POINTS_PER_SPEED_INCREASE)
        return maxOf(MIN_SPEED_MS, INITIAL_SPEED_MS - (speedIncreases * SPEED_DECREASE_PER_INCREASE))
    }

    private fun isValidPosition(piece: Piece, position: Point): Boolean {
        return piece.shape.all { p ->
            val newX = position.x + p.x
            val newY = position.y + p.y
            newX in 0 until BOARD_WIDTH && newY < BOARD_HEIGHT &&
                    (newY < 0 || _gameState.value.board.getOrNull(newY)?.getOrNull(newX) == null)
        }
    }

    private fun lockPiece() {
        val currentState = gameState.value
        val piece = currentState.currentPiece ?: return
        val newBoard = currentState.board.map { it.toMutableList<PieceType?>() }

        piece.shape.forEach { p ->
            val x = currentState.piecePosition.x + p.x
            val y = currentState.piecePosition.y + p.y
            if (y >= 0 && y < BOARD_HEIGHT && x >= 0 && x < BOARD_WIDTH) {
                newBoard[y][x] = piece.type
            }
        }
        _gameState.update { it.copy(currentPiece = null, board = newBoard) }
    }

    private suspend fun clearLines() {
        val board = _gameState.value.board
        val fullLineIndices = board.mapIndexedNotNull { index, row ->
            if (row.all { it != null }) index else null
        }
        
        if (fullLineIndices.isNotEmpty()) {
            // Marcar líneas para animación
            _gameState.update { it.copy(linesToAnimate = fullLineIndices) }
            
            // Delay para animación
            delay(150) // Reducido para mayor fluidez
            
            _gameState.update { state ->
                // Limpiar líneas de forma atómica
                val currentBoard = state.board
                val remainingLines = currentBoard.filterIndexed { index, _ -> index !in fullLineIndices }
                val linesCleared = fullLineIndices.size
                val newLines = List(linesCleared) { List(BOARD_WIDTH) { null as PieceType? } }
                val nextBoard = newLines + remainingLines
                
                val newTotalLines = state.totalLinesCleared + linesCleared
                val scoreToAdd = when (linesCleared) {
                    1 -> 40
                    2 -> 100
                    3 -> 300
                    4 -> 1200
                    else -> 0
                }
                val newScore = state.score + scoreToAdd
                
                // Actualizar velocidad basada en score
                if (!SettingsManager.isConstantSpeedEnabled) {
                    currentSpeed = calculateSpeedFromScore(newScore)
                }
                
                state.copy(
                    board = nextBoard,
                    score = newScore,
                    totalLinesCleared = newTotalLines,
                    linesToAnimate = emptyList()
                )
            }
            
            if (_gameState.value.score > SettingsManager.highScore) {
                SettingsManager.updateHighScore(_gameState.value.score)
            }
        }
    }
}

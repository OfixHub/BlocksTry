package com.ofixhub.blockstry

import android.os.Bundle
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.input.key.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.ScalingLazyColumn
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ofixhub.blockstry.shared.SettingsManager
import com.ofixhub.blockstry.shared.ColorTheme
import android.graphics.BitmapFactory
import java.io.IOException
import java.io.File
import com.ofixhub.blockstry.shared.PieceType
import com.ofixhub.blockstry.shared.GameViewModel
import com.ofixhub.blockstry.shared.GameState
import com.ofixhub.blockstry.shared.Piece
import com.ofixhub.blockstry.shared.BOARD_WIDTH
import com.ofixhub.blockstry.shared.BOARD_HEIGHT
import com.ofixhub.blockstry.shared.GameInputHandler
import com.ofixhub.blockstry.shared.SnakeViewModel
import com.ofixhub.blockstry.shared.ThemeRepository
import kotlin.math.abs

class GameActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(applicationContext)
        setContent {
            WearApp(gameViewModel = gameViewModel, activity = this)
        }
    }
}

@Composable
fun WearApp(gameViewModel: GameViewModel, activity: ComponentActivity) {
    val theme = SettingsManager.currentTheme
    val context = LocalContext.current
    
    var pieceBitmaps by remember { mutableStateOf<Map<PieceType, ImageBitmap>>(emptyMap()) }
    var borderBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var backgroundBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var buttonBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(SettingsManager.currentTheme) {
        backgroundBitmap = null
        pieceBitmaps = emptyMap()
        borderBitmap = null
        buttonBitmap = null

        val localThemeDir = theme.localPath?.let { File(it) } ?: File(context.filesDir, "themes/${theme.id}")
        val assetsRoot = if (localThemeDir.exists()) localThemeDir else null

        if (theme.hasCustomAssets || assetsRoot != null) {
            try {
                fun loadBitmapFromFile(path: String): ImageBitmap? {
                    return try {
                        BitmapFactory.decodeFile(path)?.asImageBitmap()
                    } catch (e: Exception) {
                        null
                    }
                }

                backgroundBitmap = assetsRoot?.let { loadBitmapFromFile(File(it, "background.png").absolutePath) }
                borderBitmap = assetsRoot?.let { loadBitmapFromFile(File(it, "border.png").absolutePath) }
                buttonBitmap = assetsRoot?.let { loadBitmapFromFile(File(it, "button.png").absolutePath) }

                val newPieceBitmaps = mutableMapOf<PieceType, ImageBitmap>()
                val pieceFiles = mapOf(
                    PieceType.I to "i.png", PieceType.O to "o.png", PieceType.T to "t.png",
                    PieceType.J to "j.png", PieceType.L to "l.png", PieceType.S to "s.png", PieceType.Z to "z.png"
                )
                pieceFiles.forEach { (type, filename) ->
                    val bitmap = assetsRoot?.let { loadBitmapFromFile(File(it, filename).absolutePath) }
                    if (bitmap != null) newPieceBitmaps[type] = bitmap
                }
                pieceBitmaps = newPieceBitmaps
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    MaterialTheme(colors = MaterialTheme.colors.copy(background = theme.backgroundColor)) {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "game"
        ) {
            composable("game") {
                if (SettingsManager.currentGame == "Tetris") {
                    val gameState by gameViewModel.gameState.collectAsState()
                    val tetrisInputHandler = object : GameInputHandler {
                        override fun onMoveLeft() = gameViewModel.moveLeft()
                        override fun onMoveRight() = gameViewModel.moveRight()
                        override fun onMoveUp() {}
                        override fun onMoveDown() {}
                        override fun onRotate() = gameViewModel.rotate()
                        override fun onActionPrimary() = gameViewModel.rotate()
                        override fun onTogglePause() = gameViewModel.togglePause()
                        override fun onSetFastDrop(enabled: Boolean) = gameViewModel.setDropSpeed(enabled)
                    }

                    UniversalGameContainer(
                        inputHandler = tetrisInputHandler,
                        isPaused = gameState.isPaused,
                        isGameOver = gameState.isGameOver,
                        onRestart = gameViewModel::reset,
                        onExit = { activity.finish() },
                        onNavigateToSettings = { navController.navigate("settings") },
                        backgroundBitmap = backgroundBitmap,
                        borderBitmap = borderBitmap,
                        buttonBitmap = buttonBitmap,
                        onRotary = { if (it > 0) gameViewModel.moveRight() else gameViewModel.moveLeft() }
                    ) {
                        BlocksTryGameScreen(
                            gameState = gameState,
                            pieceBitmaps = pieceBitmaps,
                            borderBitmap = borderBitmap,
                            backgroundBitmap = backgroundBitmap,
                            buttonBitmap = buttonBitmap
                        )
                    }
                } else {
                    val snakeViewModel: SnakeViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                    val state by snakeViewModel.state.collectAsState()
                    val snakeInputHandler = object : GameInputHandler {
                        override fun onMoveLeft() = snakeViewModel.setDirection(-1, 0)
                        override fun onMoveRight() = snakeViewModel.setDirection(1, 0)
                        override fun onMoveUp() = snakeViewModel.setDirection(0, -1)
                        override fun onMoveDown() = snakeViewModel.setDirection(0, 1)
                        override fun onRotate() {}
                        override fun onActionPrimary() {}
                        override fun onTogglePause() = snakeViewModel.togglePause()
                    }

                    UniversalGameContainer(
                        inputHandler = snakeInputHandler,
                        isPaused = state.isPaused,
                        isGameOver = state.isGameOver,
                        onRestart = snakeViewModel::reset,
                        onExit = { activity.finish() },
                        onNavigateToSettings = { navController.navigate("settings") },
                        backgroundBitmap = backgroundBitmap,
                        borderBitmap = borderBitmap,
                        buttonBitmap = buttonBitmap,
                        onRotary = { if (it > 0) snakeInputHandler.onMoveRight() else snakeInputHandler.onMoveLeft() }
                    ) {
                        SnakeGameScreen(
                            state = state,
                            pieceBitmaps = pieceBitmaps,
                            borderBitmap = borderBitmap,
                            backgroundBitmap = backgroundBitmap,
                            buttonBitmap = buttonBitmap
                        )
                    }
                }
            }
            composable("settings") {
                SettingsScreen()
            }
        }
    }
}

@Composable
fun UniversalGameContainer(
    inputHandler: GameInputHandler,
    isPaused: Boolean,
    isGameOver: Boolean,
    onRestart: () -> Unit,
    onExit: () -> Unit,
    onNavigateToSettings: () -> Unit,
    backgroundBitmap: ImageBitmap?,
    borderBitmap: ImageBitmap?,
    buttonBitmap: ImageBitmap?,
    onRotary: (Float) -> Unit = {},
    content: @Composable () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    val textMeasurer = rememberTextMeasurer()

    var retryButtonRect by remember { mutableStateOf(Rect.Zero) }
    var settingsButtonRect by remember { mutableStateOf(Rect.Zero) }
    var exitButtonRect by remember { mutableStateOf(Rect.Zero) }
    var pauseResumeButtonRect by remember { mutableStateOf(Rect.Zero) }
    var pauseExitButtonRect by remember { mutableStateOf(Rect.Zero) }
    var pauseSettingsButtonRect by remember { mutableStateOf(Rect.Zero) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingsManager.currentTheme.backgroundColor)
            .focusRequester(focusRequester)
            .onRotaryScrollEvent {
                if (!isPaused && !isGameOver) {
                    onRotary(it.verticalScrollPixels)
                    true
                } else false
            }
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (!isPaused && !isGameOver) {
                        when (event.key) {
                            Key.DirectionLeft -> { inputHandler.onMoveLeft(); true }
                            Key.DirectionRight -> { inputHandler.onMoveRight(); true }
                            Key.DirectionUp -> { inputHandler.onMoveUp(); true }
                            Key.DirectionDown -> { inputHandler.onMoveDown(); inputHandler.onSetFastDrop(true); true }
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> { inputHandler.onRotate(); true }
                            Key.ButtonA -> { inputHandler.onActionPrimary(); true }
                            Key.Back -> { inputHandler.onTogglePause(); true }
                            else -> false
                        }
                    } else if (isPaused) {
                         if (event.key == Key.Back) { onExit(); true } else false
                    } else false
                } else if (event.type == KeyEventType.KeyUp) {
                     if (event.key == Key.DirectionDown) { inputHandler.onSetFastDrop(false); true } else false
                } else false
            }
            .focusable()
            .pointerInput(isGameOver, isPaused) {
                if (isPaused) {
                    detectTapGestures { offset ->
                        if (pauseResumeButtonRect.contains(offset)) inputHandler.onTogglePause()
                        else if (pauseSettingsButtonRect.contains(offset)) onNavigateToSettings()
                        else if (pauseExitButtonRect.contains(offset)) onExit()
                    }
                } else if (isGameOver) {
                    detectTapGestures { offset ->
                        if (retryButtonRect.contains(offset)) onRestart()
                        else if (settingsButtonRect.contains(offset)) onNavigateToSettings()
                        else if (exitButtonRect.contains(offset)) onExit()
                    }
                } else {
                    var lastTapTime = 0L
                    var fastDropJob: Job? = null
                    var pauseJob: Job? = null
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val downTime = System.currentTimeMillis()
                        val downPosition = down.position
                        val timeSinceLastTap = downTime - lastTapTime
                        if (timeSinceLastTap < 300) {
                            inputHandler.onRotate()
                            lastTapTime = 0L
                            fastDropJob?.cancel()
                            pauseJob?.cancel()
                            return@awaitEachGesture
                        }
                        fastDropJob = scope.launch { delay(300); inputHandler.onSetFastDrop(true) }
                        var pauseTriggered = false
                        pauseJob = scope.launch {
                            delay(900)
                            fastDropJob?.cancel()
                            inputHandler.onSetFastDrop(false)
                            pauseTriggered = true
                            inputHandler.onTogglePause()
                        }
                        var lastPosition = downPosition
                        var hasDragged = false
                        var totalDragX = 0f
                        var totalDragY = 0f
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                fastDropJob?.cancel()
                                pauseJob?.cancel()
                                inputHandler.onSetFastDrop(false)
                                if (!pauseTriggered && !hasDragged) lastTapTime = downTime
                                break
                            }
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    val currentPosition = change.position
                                    val dragDelta = currentPosition - lastPosition
                                    totalDragX += dragDelta.x
                                    totalDragY += dragDelta.y
                                    val threshold = 25f
                                    if (abs(totalDragX) > threshold || abs(totalDragY) > threshold) {
                                        change.consume()
                                        hasDragged = true
                                        pauseJob?.cancel() // cancelar pausa si el usuario arrastra
                                        if (abs(totalDragX) > abs(totalDragY)) {
                                            if (totalDragX > threshold) { inputHandler.onMoveRight(); totalDragX = 0f }
                                            else if (totalDragX < -threshold) { inputHandler.onMoveLeft(); totalDragX = 0f }
                                        } else {
                                            if (totalDragY > threshold) { inputHandler.onMoveDown(); totalDragY = 0f }
                                            else if (totalDragY < -threshold) { inputHandler.onMoveUp(); totalDragY = 0f }
                                        }
                                        lastPosition = currentPosition
                                        fastDropJob?.cancel()
                                        inputHandler.onSetFastDrop(false)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        content()
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (isPaused) {
                drawPauseOverlay(textMeasurer, buttonBitmap) { resumeRect, settingsRect, exitRect ->
                    pauseResumeButtonRect = resumeRect
                    pauseSettingsButtonRect = settingsRect
                    pauseExitButtonRect = exitRect
                }
            } else if (isGameOver) {
                drawGameOver(textMeasurer, buttonBitmap) { retryRect, settingsRect, exitRect ->
                    retryButtonRect = retryRect
                    settingsButtonRect = settingsRect
                    exitButtonRect = exitRect
                }
            }
        }
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun BlocksTryGameScreen(
    gameState: GameState,
    pieceBitmaps: Map<PieceType, ImageBitmap> = emptyMap(),
    borderBitmap: ImageBitmap? = null,
    backgroundBitmap: ImageBitmap? = null,
    buttonBitmap: ImageBitmap? = null
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val screenHeight = size.height
        val boardPixelHeight = screenHeight * 0.9f
        val cellPixelSize = boardPixelHeight / BOARD_HEIGHT
        val boardPixelWidth = cellPixelSize * BOARD_WIDTH
        val boardTopOffset = (screenHeight - boardPixelHeight) / 2
        val boardLeftOffset = (size.width - boardPixelWidth) / 2
        drawBoardGeneric(boardLeftOffset, boardTopOffset, BOARD_WIDTH, BOARD_HEIGHT, cellPixelSize, borderBitmap, backgroundBitmap)
        gameState.board.forEachIndexed { y, row ->
            row.forEachIndexed { x, pieceType ->
                if (pieceType != null) {
                    val pieceIndex = PieceType.values().indexOf(pieceType)
                    val color = SettingsManager.currentTheme.pieceColors.getOrElse(pieceIndex) { SettingsManager.currentTheme.gridColor }
                    drawCell(boardLeftOffset + x * cellPixelSize, boardTopOffset + y * cellPixelSize, cellPixelSize, color, pieceBitmaps[pieceType])
                }
            }
        }
        if (SettingsManager.isGhostPieceEnabled) {
            gameState.currentPiece?.let { piece ->
                gameState.ghostPiecePosition?.let { ghostPos ->
                    piece.shape.forEach { p ->
                        drawRect(color = piece.color.copy(alpha = 0.2f), topLeft = Offset(boardLeftOffset + (ghostPos.x + p.x) * cellPixelSize, boardTopOffset + (ghostPos.y + p.y) * cellPixelSize), size = Size(cellPixelSize, cellPixelSize))
                    }
                }
            }
        }
        if (gameState.linesToAnimate.isNotEmpty()) {
            gameState.linesToAnimate.forEach { lineIndex ->
                drawRect(color = Color.White.copy(alpha = 0.6f), topLeft = Offset(boardLeftOffset, boardTopOffset + lineIndex * cellPixelSize), size = Size(BOARD_WIDTH * cellPixelSize, cellPixelSize))
            }
        }
        gameState.currentPiece?.let { piece ->
            piece.shape.forEach { p ->
                drawCell(boardLeftOffset + (gameState.piecePosition.x + p.x) * cellPixelSize, boardTopOffset + (gameState.piecePosition.y + p.y) * cellPixelSize, cellPixelSize, piece.color, pieceBitmaps[piece.type])
            }
        }
        drawScore(gameState.score, gameState.totalLinesCleared, textMeasurer)
        drawNextPiece(gameState.nextPiece, cellPixelSize, pieceBitmaps[gameState.nextPiece?.type])
    }
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun SnakeGameScreen(
    state: com.ofixhub.blockstry.shared.SnakeState,
    pieceBitmaps: Map<PieceType, ImageBitmap> = emptyMap(),
    borderBitmap: ImageBitmap? = null,
    backgroundBitmap: ImageBitmap? = null,
    buttonBitmap: ImageBitmap? = null
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = Modifier.fillMaxSize()) {
        val boardWidth = BOARD_WIDTH * 2
        val boardHeight = BOARD_HEIGHT
        val cellPixelSize = size.height * 0.8f / boardHeight
        val boardPixelWidth = cellPixelSize * boardWidth
        val boardLeftOffset = (size.width - boardPixelWidth) / 2
        drawBoardGeneric(boardLeftOffset, (size.height - size.height * 0.8f) / 2, boardWidth, boardHeight, cellPixelSize, borderBitmap, backgroundBitmap)
        drawCell(boardLeftOffset + state.food.x * cellPixelSize, (size.height - size.height * 0.8f) / 2 + state.food.y * cellPixelSize, cellPixelSize, Color.Red, pieceBitmaps[PieceType.O])
        state.snake.forEach { point ->
            drawCell(boardLeftOffset + point.x * cellPixelSize, (size.height - size.height * 0.8f) / 2 + point.y * cellPixelSize, cellPixelSize, Color.Green, pieceBitmaps[PieceType.I])
        }
        drawScore(state.score, 0, textMeasurer)
    }
}

private fun DrawScope.drawBoardGeneric(left: Float, top: Float, width: Int, height: Int, cellSize: Float, borderBitmap: ImageBitmap?, backgroundBitmap: ImageBitmap?) {
    val theme = SettingsManager.currentTheme
    if (backgroundBitmap != null) {
        val scale = maxOf(size.width / backgroundBitmap.width.toFloat(), size.height / backgroundBitmap.height.toFloat())
        val scaledWidth = backgroundBitmap.width * scale
        val scaledHeight = backgroundBitmap.height * scale
        drawImage(image = backgroundBitmap, dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - scaledWidth) / 2).toInt(), ((size.height - scaledHeight) / 2).toInt()), dstSize = androidx.compose.ui.unit.IntSize(scaledWidth.toInt(), scaledHeight.toInt()))
    }
    if (borderBitmap != null) {
         val borderThickness = 12f 
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height), androidx.compose.ui.unit.IntOffset((left - borderThickness).toInt(), (top - borderThickness).toInt()), androidx.compose.ui.unit.IntSize((width * cellSize + borderThickness * 2).toInt(), borderThickness.toInt()), androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt()))
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height), androidx.compose.ui.unit.IntOffset((left - borderThickness).toInt(), (top + height * cellSize).toInt()), androidx.compose.ui.unit.IntSize((width * cellSize + borderThickness * 2).toInt(), borderThickness.toInt()), androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt()))
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height), androidx.compose.ui.unit.IntOffset((left - borderThickness).toInt(), top.toInt()), androidx.compose.ui.unit.IntSize(borderThickness.toInt(), (height * cellSize).toInt()), androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt()))
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height), androidx.compose.ui.unit.IntOffset((left + width * cellSize).toInt(), top.toInt()), androidx.compose.ui.unit.IntSize(borderThickness.toInt(), (height * cellSize).toInt()), androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt()))
    } else {
        drawRect(color = theme.gridColor, topLeft = Offset(left, top), size = Size(width * cellSize, height * cellSize), style = Stroke(width = 2f))
    }
    if (SettingsManager.isGridEnabled) {
        for (i in 0..width) drawLine(color = theme.gridColor, start = Offset(left + i * cellSize, top), end = Offset(left + i * cellSize, top + height * cellSize), strokeWidth = 1f)
        for (i in 0..height) drawLine(color = theme.gridColor, start = Offset(left, top + i * cellSize), end = Offset(left + width * cellSize, top + i * cellSize), strokeWidth = 1f)
    }
}

private fun DrawScope.drawCell(x: Float, y: Float, size: Float, color: Color, bitmap: ImageBitmap? = null) {
    if (bitmap != null) {
        drawImage(image = bitmap, dstOffset = androidx.compose.ui.unit.IntOffset(x.roundToInt(), y.roundToInt()), dstSize = androidx.compose.ui.unit.IntSize(size.roundToInt(), size.roundToInt()))
    } else {
        val newSize = size - 1f
        drawRect(color = color, topLeft = Offset(x, y), size = Size(newSize, newSize))
        val darkColor = Color(color.red * 0.8f, color.green * 0.8f, color.blue * 0.8f)
        val lightColor = Color.White.copy(alpha = 0.3f)
        drawLine(darkColor, Offset(x, y + newSize), Offset(x + newSize, y + newSize), strokeWidth = 1f)
        drawLine(darkColor, Offset(x + newSize, y), Offset(x + newSize, y + newSize), strokeWidth = 1f)
        drawLine(lightColor, Offset(x, y), Offset(x + newSize, y), strokeWidth = 1f)
        drawLine(lightColor, Offset(x, y), Offset(x, y + newSize), strokeWidth = 1f)
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawScore(score: Int, totalLines: Int, textMeasurer: TextMeasurer) {
    val scoreText = if (totalLines > 0) "L: $totalLines\nP: $score" else "P: $score"
    val res = textMeasurer.measure(AnnotatedString(scoreText), TextStyle(color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
    withTransform({ rotate(90f, Offset(size.width - 25f, size.height / 2)) }) {
        drawText(res, color = Color.White, topLeft = Offset(size.width - 25f - res.size.width / 2, size.height / 2 - res.size.height / 2))
    }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNextPiece(nextPiece: Piece?, cellSize: Float, pieceBitmap: ImageBitmap? = null) {
    nextPiece ?: return
    val boxSize = cellSize * 4.5f
    val boxLeft = 20f
    val pieceOffsetX = boxLeft + (boxSize - nextPiece.shape.maxOf { it.x + 1 } * cellSize) / 2
    val pieceOffsetY = center.y - boxSize / 2 + (boxSize - nextPiece.shape.maxOf { it.y + 1 } * cellSize) / 2
    nextPiece.shape.forEach { p -> drawCell(pieceOffsetX + p.x * cellSize, pieceOffsetY + p.y * cellSize, cellSize, nextPiece.color, pieceBitmap) }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawGameOver(textMeasurer: TextMeasurer, buttonBitmap: ImageBitmap? = null, onButtonRectsMeasured: (Rect, Rect, Rect) -> Unit) {
    val theme = SettingsManager.currentTheme
    drawRect(color = Color.Black.copy(alpha = 0.7f))
    val gameOverText = textMeasurer.measure(AnnotatedString("GAME OVER"), TextStyle(fontSize = 18.sp, color = Color.White, textAlign = TextAlign.Center))
    val highScoreText = textMeasurer.measure(AnnotatedString("HS: ${SettingsManager.highScore}"), TextStyle(fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center))
    val btnStyle = TextStyle(fontSize = 10.sp, color = theme.buttonTextColor, textAlign = TextAlign.Center)
    val buttonHeight = 30f
    val buttonWidth = 100f
    val totalHeight = gameOverText.size.height + 4f + highScoreText.size.height + 10f + (buttonHeight * 3) + 12f
    var currentY = (size.height - totalHeight) / 2
    drawText(gameOverText, color = Color.White, topLeft = Offset(center.x - gameOverText.size.width / 2, currentY))
    currentY += gameOverText.size.height + 4f
    drawText(highScoreText, color = Color.White, topLeft = Offset(center.x - highScoreText.size.width / 2, currentY))
    currentY += highScoreText.size.height + 10f
    val retryRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, retryRect, textMeasurer.measure(AnnotatedString("Reintentar"), btnStyle), theme.buttonColor)
    currentY += buttonHeight + 6f
    val settingsRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, settingsRect, textMeasurer.measure(AnnotatedString("Ajustes"), btnStyle), theme.buttonColor)
    currentY += buttonHeight + 6f
    val exitRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, exitRect, textMeasurer.measure(AnnotatedString("Salir"), btnStyle), theme.buttonColor)
    onButtonRectsMeasured(retryRect, settingsRect, exitRect)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPauseOverlay(textMeasurer: TextMeasurer, buttonBitmap: ImageBitmap? = null, onButtonRectsMeasured: (Rect, Rect, Rect) -> Unit) {
    val theme = SettingsManager.currentTheme
    drawRect(color = Color.Black.copy(alpha = 0.8f))
    val pauseText = textMeasurer.measure(AnnotatedString("PAUSA"), TextStyle(fontSize = 20.sp, color = Color.White, textAlign = TextAlign.Center))
    drawText(pauseText, color = Color.White, topLeft = Offset(center.x - pauseText.size.width / 2, center.y - pauseText.size.height * 3.5f))
    val btnStyle = TextStyle(fontSize = 12.sp, color = theme.buttonTextColor, textAlign = TextAlign.Center)
    val buttonHeight = 40f
    val buttonWidth = 150f
    val startY = (size.height - ((buttonHeight * 3) + 20f)) / 2 + 10f
    val resumeRect = Rect(center.x - buttonWidth / 2, startY, center.x + buttonWidth / 2, startY + buttonHeight)
    drawButton(buttonBitmap, resumeRect, textMeasurer.measure(AnnotatedString("Reanudar"), btnStyle), theme.buttonColor)
    val settingsRect = Rect(center.x - buttonWidth / 2, resumeRect.bottom + 10f, center.x + buttonWidth / 2, resumeRect.bottom + 10f + buttonHeight)
    drawButton(buttonBitmap, settingsRect, textMeasurer.measure(AnnotatedString("Ajustes"), btnStyle), theme.buttonColor)
    val exitRect = Rect(center.x - buttonWidth / 2, settingsRect.bottom + 10f, center.x + buttonWidth / 2, settingsRect.bottom + 10f + buttonHeight)
    drawButton(buttonBitmap, exitRect, textMeasurer.measure(AnnotatedString("Salir"), btnStyle), theme.buttonColor)
    onButtonRectsMeasured(resumeRect, settingsRect, exitRect)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawButton(bitmap: ImageBitmap?, rect: Rect, textLayout: androidx.compose.ui.text.TextLayoutResult, color: Color) {
    if (bitmap != null) {
        drawTiledBitmap(bitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height), androidx.compose.ui.unit.IntOffset(rect.left.toInt(), rect.top.toInt()), androidx.compose.ui.unit.IntSize(rect.width.toInt(), rect.height.toInt()))
    } else drawRect(color = color, topLeft = rect.topLeft, size = rect.size)
    drawText(textLayout, color = Color.White, topLeft = Offset(rect.center.x - textLayout.size.width / 2, rect.center.y - textLayout.size.height / 2))
}

private fun DrawScope.drawTiledBitmap(bitmap: ImageBitmap, srcOffset: androidx.compose.ui.unit.IntOffset, srcSize: androidx.compose.ui.unit.IntSize, dstOffset: androidx.compose.ui.unit.IntOffset, dstSize: androidx.compose.ui.unit.IntSize, scaledTileSize: androidx.compose.ui.unit.IntSize? = null) {
    if (dstSize.width <= 0 || dstSize.height <= 0) return
    val tileWidth = scaledTileSize?.width ?: srcSize.width
    val tileHeight = scaledTileSize?.height ?: srcSize.height
    if (tileWidth <= 0 || tileHeight <= 0) return
    drawContext.canvas.save()
    drawContext.transform.translate(dstOffset.x.toFloat(), dstOffset.y.toFloat())
    drawContext.canvas.clipRect(androidx.compose.ui.geometry.Rect(0f, 0f, dstSize.width.toFloat(), dstSize.height.toFloat()))
    for (y in 0 until (kotlin.math.ceil(dstSize.height.toFloat() / tileHeight).toInt())) {
        for (x in 0 until (kotlin.math.ceil(dstSize.width.toFloat() / tileWidth).toInt())) {
            drawImage(bitmap, srcOffset, srcSize, androidx.compose.ui.unit.IntOffset(x * tileWidth, y * tileHeight), androidx.compose.ui.unit.IntSize(tileWidth, tileHeight))
        }
    }
    drawContext.canvas.restore()
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ThemeRepository(context) }
    
    var ghostPieceEnabled by remember { mutableStateOf(SettingsManager.isGhostPieceEnabled) }
    var gridEnabled by remember { mutableStateOf(SettingsManager.isGridEnabled) }
    var currentThemeName by remember { mutableStateOf(SettingsManager.currentThemeName) }
    var constantSpeedEnabled by remember { mutableStateOf(SettingsManager.isConstantSpeedEnabled) }
    var currentGame by remember { mutableStateOf(SettingsManager.currentGame) }
    var isDownloading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        repository.getAvailableThemes().forEach { cloudTheme ->
            val localPath = repository.getDownloadedThemeDir(cloudTheme.id).let { if (it.exists()) it.absolutePath else null }
            SettingsManager.addTheme(cloudTheme.toColorTheme(localPath))
        }
    }

    ScalingLazyColumn(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Ajustes", style = MaterialTheme.typography.title1) }
        item { Chip(onClick = { val next = if (currentGame == "Tetris") "Snake" else "Tetris"; currentGame = next; SettingsManager.updateCurrentGame(next) }, label = { Text("Juego: $currentGame") }, modifier = Modifier.fillMaxWidth()) }
        item {
            val themeNames = SettingsManager.themes.keys.toList()
            Chip(
                onClick = {
                    if (isDownloading) return@Chip
                    val nextName = themeNames[(themeNames.indexOf(currentThemeName) + 1) % themeNames.size]
                    val nextTheme = SettingsManager.themes[nextName]!!
                    val manifest = nextTheme.remoteManifest
                    if (!nextTheme.isDownloaded && manifest != null) {
                        scope.launch {
                            isDownloading = true
                            if (repository.downloadThemeAssets(manifest)) {
                                SettingsManager.addTheme(manifest.toColorTheme(repository.getDownloadedThemeDir(nextTheme.id).absolutePath))
                                currentThemeName = nextName
                                SettingsManager.updateCurrentThemeName(nextName)
                            }
                            isDownloading = false
                        }
                    } else {
                        currentThemeName = nextName
                        SettingsManager.updateCurrentThemeName(nextName)
                    }
                },
                label = { Text(if (isDownloading) "Descargando..." else "Tema: $currentThemeName") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDownloading
            )
        }
        item { ToggleChip(checked = ghostPieceEnabled, onCheckedChange = { ghostPieceEnabled = it; SettingsManager.setIsGhostPieceEnabled(it) }, label = { Text("Pieza Fantasma") }, modifier = Modifier.fillMaxWidth(), toggleControl = { Switch(checked = ghostPieceEnabled) }) }
        item { ToggleChip(checked = gridEnabled, onCheckedChange = { gridEnabled = it; SettingsManager.setIsGridEnabled(it) }, label = { Text("Cuadrícula") }, modifier = Modifier.fillMaxWidth(), toggleControl = { Switch(checked = gridEnabled) }) }
        item { ToggleChip(checked = constantSpeedEnabled, onCheckedChange = { constantSpeedEnabled = it; SettingsManager.setIsConstantSpeedEnabled(it) }, label = { Text("Velocidad Constante") }, modifier = Modifier.fillMaxWidth(), toggleControl = { Switch(checked = constantSpeedEnabled) }) }
        item { Chip(onClick = { SettingsManager.resetHighScore() }, label = { Text("Resetear HS") }, colors = ChipDefaults.chipColors(backgroundColor = Color.Red), modifier = Modifier.fillMaxWidth()) }
    }
}

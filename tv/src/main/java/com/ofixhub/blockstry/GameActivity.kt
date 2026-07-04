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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.darkColorScheme
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
            TVApp(gameViewModel = gameViewModel, activity = this)
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TVApp(gameViewModel: GameViewModel, activity: ComponentActivity) {
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
    
    val tvColorScheme = darkColorScheme(
        primary = theme.buttonColor,
        onPrimary = theme.buttonTextColor,
        background = theme.backgroundColor,
        onBackground = theme.buttonTextColor,
        surface = theme.backgroundColor,
        onSurface = theme.buttonTextColor,
        surfaceVariant = theme.gridColor,
        onSurfaceVariant = theme.buttonTextColor
    )

    MaterialTheme(colorScheme = tvColorScheme) { 
        Surface(modifier = Modifier.fillMaxSize()) { 
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
                            buttonBitmap = buttonBitmap
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
                            override fun onTogglePause() {}
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
                            buttonBitmap = buttonBitmap
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
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    if (!isPaused && !isGameOver) {
                        when (event.key) {
                            Key.DirectionLeft -> { inputHandler.onMoveLeft(); true }
                            Key.DirectionRight -> { inputHandler.onMoveRight(); true }
                            Key.DirectionUp -> { inputHandler.onMoveUp(); true }
                            Key.DirectionDown -> { 
                                inputHandler.onMoveDown()
                                inputHandler.onSetFastDrop(true)
                                true 
                            }
                            Key.DirectionCenter, Key.Enter, Key.Spacebar -> { inputHandler.onRotate(); true }
                            Key.ButtonA -> { inputHandler.onActionPrimary(); true }
                            Key.Back -> { inputHandler.onTogglePause(); true }
                            else -> false
                        }
                    } else if (isPaused) {
                         if (event.key == Key.Back) { onExit(); true } else false
                    } else false
                } else if (event.type == KeyEventType.KeyUp) {
                     if (event.key == Key.DirectionDown) {
                         inputHandler.onSetFastDrop(false)
                         true
                     } else false
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
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val downTime = System.currentTimeMillis()
                        val downPosition = down.position
                        if (downPosition.y < size.height * 0.2f && downPosition.x > size.width * 0.3f && downPosition.x < size.width * 0.7f) {
                            inputHandler.onTogglePause()
                            return@awaitEachGesture
                        }
                        val timeSinceLastTap = downTime - lastTapTime
                        if (timeSinceLastTap < 300) {
                            inputHandler.onRotate()
                            lastTapTime = 0L
                            fastDropJob?.cancel()
                            return@awaitEachGesture
                        }
                        fastDropJob = scope.launch { delay(300); inputHandler.onSetFastDrop(true) }
                        var lastPosition = downPosition
                        var hasDragged = false
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                fastDropJob?.cancel()
                                inputHandler.onSetFastDrop(false)
                                if (!hasDragged) lastTapTime = downTime
                                break
                            }
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    val currentPosition = change.position
                                    val dragAmount = currentPosition - lastPosition
                                    if (abs(dragAmount.x) > 15) {
                                        change.consume()
                                        hasDragged = true
                                        if (dragAmount.x > 0) inputHandler.onMoveRight() else inputHandler.onMoveLeft()
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
        val screenWidth = size.width
        val screenHeight = size.height
        val sidebarWidth = screenWidth * 0.30f
        val gameAreaWidth = screenWidth * 0.70f
        val cellPixelSize = screenHeight / BOARD_HEIGHT
        val boardPixelWidth = cellPixelSize * BOARD_WIDTH
        val boardLeftOffset = sidebarWidth + (gameAreaWidth - boardPixelWidth) / 2
        val boardTopOffset = 0f
        drawBoardGeneric(boardLeftOffset, boardTopOffset, BOARD_WIDTH, BOARD_HEIGHT, cellPixelSize, borderBitmap, backgroundBitmap)
        gameState.board.forEachIndexed { y, row ->
            row.forEachIndexed { x, color ->
                if (color != SettingsManager.currentTheme.backgroundColor) {
                    val index = SettingsManager.currentTheme.pieceColors.indexOf(color)
                    val pieceType = if (index >= 0) PieceType.values().getOrNull(index) else null
                    drawCell(boardLeftOffset + x * cellPixelSize, boardTopOffset + y * cellPixelSize, cellPixelSize, color, if (pieceType != null) pieceBitmaps[pieceType] else null)
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
        val sidebarCenterX = sidebarWidth / 2
        drawScore(gameState.score, gameState.totalLinesCleared, textMeasurer, sidebarCenterX, screenHeight * 0.5f)
        drawNextPiece(gameState.nextPiece, cellPixelSize * 1.2f, sidebarCenterX, screenHeight * 0.15f, pieceBitmaps[gameState.nextPiece?.type])
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
        val cellPixelSize = size.height / boardHeight
        val boardPixelWidth = cellPixelSize * boardWidth
        val boardLeftOffset = (size.width - boardPixelWidth) / 2
        drawBoardGeneric(boardLeftOffset, 0f, boardWidth, boardHeight, cellPixelSize, borderBitmap, backgroundBitmap)
        drawCell(boardLeftOffset + state.food.x * cellPixelSize, state.food.y * cellPixelSize, cellPixelSize, Color.Red, pieceBitmaps[PieceType.O])
        state.snake.forEach { point ->
            drawCell(boardLeftOffset + point.x * cellPixelSize, point.y * cellPixelSize, cellPixelSize, Color.Green, pieceBitmaps[PieceType.I])
        }
        drawScore(state.score, 0, textMeasurer, 50f, size.height / 2)
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
private fun DrawScope.drawScore(score: Int, totalLines: Int, textMeasurer: TextMeasurer, centerX: Float, topY: Float) {
    val scoreText = if (totalLines > 0) "Puntaje: $score\nLíneas: $totalLines" else "Puntaje: $score"
    val style = TextStyle(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    val res = textMeasurer.measure(AnnotatedString(scoreText), style)
    drawText(textLayoutResult = res, color = Color.White, topLeft = Offset(x = centerX - res.size.width / 2, y = topY))
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNextPiece(nextPiece: Piece?, cellSize: Float, centerX: Float, topY: Float, pieceBitmap: ImageBitmap? = null) {
    nextPiece ?: return
    val boxSize = cellSize * 4.5f
    val boxLeft = centerX - boxSize / 2
    val pieceOffsetX = boxLeft + (boxSize - nextPiece.shape.maxOf { it.x + 1 } * cellSize) / 2
    val pieceOffsetY = topY + (boxSize - nextPiece.shape.maxOf { it.y + 1 } * cellSize) / 2
    nextPiece.shape.forEach { p -> drawCell(pieceOffsetX + p.x * cellSize, pieceOffsetY + p.y * cellSize, cellSize, nextPiece.color, pieceBitmap) }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawGameOver(textMeasurer: TextMeasurer, buttonBitmap: ImageBitmap? = null, onButtonRectsMeasured: (Rect, Rect, Rect) -> Unit) {
    val theme = SettingsManager.currentTheme
    drawRect(color = Color.Black.copy(alpha = 0.7f))
    val gameOverText = textMeasurer.measure(AnnotatedString("GAME OVER"), TextStyle(fontSize = 60.sp, color = Color.White, textAlign = TextAlign.Center))
    val highScoreText = textMeasurer.measure(AnnotatedString("High Score: ${SettingsManager.highScore}"), TextStyle(fontSize = 32.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center))
    val buttonTextStyle = TextStyle(fontSize = 28.sp, color = theme.buttonTextColor, textAlign = TextAlign.Center)
    val retryText = textMeasurer.measure(AnnotatedString("Reintentar"), buttonTextStyle)
    val settingsText = textMeasurer.measure(AnnotatedString("Configuración"), buttonTextStyle)
    val exitText = textMeasurer.measure(AnnotatedString("Salir"), buttonTextStyle)
    val buttonHeight = 120f
    val buttonWidth = size.width * 0.4f
    val totalHeight = gameOverText.size.height + 24f + highScoreText.size.height + 60f + (buttonHeight * 3) + 80f
    var currentY = (size.height - totalHeight) / 2
    drawText(gameOverText, color = Color.White, topLeft = Offset(center.x - gameOverText.size.width / 2, currentY))
    currentY += gameOverText.size.height + 24f
    drawText(highScoreText, color = Color.White, topLeft = Offset(center.x - highScoreText.size.width / 2, currentY))
    currentY += highScoreText.size.height + 60f
    val retryButtonRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, retryButtonRect, retryText, theme.buttonColor)
    currentY += buttonHeight + 40f
    val settingsButtonRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, settingsButtonRect, settingsText, theme.buttonColor)
    currentY += buttonHeight + 40f
    val exitButtonRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, exitButtonRect, exitText, theme.buttonColor)
    onButtonRectsMeasured(retryButtonRect, settingsButtonRect, exitButtonRect)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPauseOverlay(textMeasurer: TextMeasurer, buttonBitmap: ImageBitmap? = null, onButtonRectsMeasured: (Rect, Rect, Rect) -> Unit) {
    val theme = SettingsManager.currentTheme
    drawRect(color = Color.Black.copy(alpha = 0.8f))
    val pauseText = textMeasurer.measure(AnnotatedString("PAUSA"), TextStyle(fontSize = 60.sp, color = Color.White, textAlign = TextAlign.Center))
    drawText(pauseText, color = Color.White, topLeft = Offset(center.x - pauseText.size.width / 2, center.y - pauseText.size.height * 2.5f))
    val buttonTextStyle = TextStyle(fontSize = 28.sp, color = theme.buttonTextColor, textAlign = TextAlign.Center)
    val buttonHeight = 120f
    val buttonWidth = size.width * 0.4f
    val startY = (size.height - ((buttonHeight * 3) + 60f)) / 2 + 60f 
    val resumeButtonRect = Rect(center.x - buttonWidth / 2, startY, center.x + buttonWidth / 2, startY + buttonHeight)
    drawButton(buttonBitmap, resumeButtonRect, textMeasurer.measure(AnnotatedString("Reanudar"), buttonTextStyle), theme.buttonColor)
    val settingsButtonRect = Rect(center.x - buttonWidth / 2, resumeButtonRect.bottom + 30f, center.x + buttonWidth / 2, resumeButtonRect.bottom + 30f + buttonHeight)
    drawButton(buttonBitmap, settingsButtonRect, textMeasurer.measure(AnnotatedString("Configuración"), buttonTextStyle), theme.buttonColor)
    val exitButtonRect = Rect(center.x - buttonWidth / 2, settingsButtonRect.bottom + 30f, center.x + buttonWidth / 2, settingsButtonRect.bottom + 30f + buttonHeight)
    drawButton(buttonBitmap, exitButtonRect, textMeasurer.measure(AnnotatedString("Salir"), buttonTextStyle), theme.buttonColor)
    onButtonRectsMeasured(resumeButtonRect, settingsButtonRect, exitButtonRect)
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

@OptIn(ExperimentalTvMaterial3Api::class)
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

    LazyColumn(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Ajustes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 24.dp)) }
        item {
            Button(onClick = {
                val nextGame = if (currentGame == "Tetris") "Snake" else "Tetris"
                currentGame = nextGame
                SettingsManager.updateCurrentGame(nextGame)
            }, modifier = Modifier.fillMaxWidth()) { Text("Juego Actual: $currentGame") }
        }
        item {
            Button(
                onClick = {
                    if (isDownloading) return@Button
                    val names = SettingsManager.themes.keys.toList()
                    val nextName = names[(names.indexOf(currentThemeName) + 1) % names.size]
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
                modifier = Modifier.fillMaxWidth(),
                enabled = !isDownloading
            ) { Text(if (isDownloading) "Descargando..." else "Tema: $currentThemeName") }
        }
        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().clickable { ghostPieceEnabled = !ghostPieceEnabled; SettingsManager.setIsGhostPieceEnabled(ghostPieceEnabled) }.padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Pieza Fantasma", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = ghostPieceEnabled, onCheckedChange = { ghostPieceEnabled = it; SettingsManager.setIsGhostPieceEnabled(it) })
                }
            }
        }
        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().clickable { gridEnabled = !gridEnabled; SettingsManager.setIsGridEnabled(gridEnabled) }.padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Cuadrícula", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = gridEnabled, onCheckedChange = { gridEnabled = it; SettingsManager.setIsGridEnabled(it) })
                }
            }
        }
        item {
            Card(onClick = {}, modifier = Modifier.fillMaxWidth()) {
                androidx.compose.foundation.layout.Row(modifier = Modifier.fillMaxWidth().clickable { constantSpeedEnabled = !constantSpeedEnabled; SettingsManager.setIsConstantSpeedEnabled(constantSpeedEnabled) }.padding(24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Velocidad Constante", style = MaterialTheme.typography.bodyLarge)
                    Switch(checked = constantSpeedEnabled, onCheckedChange = { constantSpeedEnabled = it; SettingsManager.setIsConstantSpeedEnabled(it) })
                }
            }
        }
        item { Button(onClick = { SettingsManager.resetHighScore() }, modifier = Modifier.fillMaxWidth()) { Text("Resetear High Score") } }
    }
}

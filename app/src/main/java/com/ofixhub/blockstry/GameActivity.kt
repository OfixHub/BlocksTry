package com.ofixhub.blockstry

import android.os.Bundle
import kotlin.math.roundToInt
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.ofixhub.blockstry.shared.GameViewModel
import com.ofixhub.blockstry.shared.GameState
import com.ofixhub.blockstry.shared.Piece
import com.ofixhub.blockstry.shared.BOARD_WIDTH
import com.ofixhub.blockstry.shared.BOARD_HEIGHT
import com.ofixhub.blockstry.shared.SettingsManager
import com.ofixhub.blockstry.shared.ColorTheme
import android.graphics.BitmapFactory
import java.io.IOException
import java.io.File
import com.ofixhub.blockstry.shared.LeaderboardRepository
import com.ofixhub.blockstry.shared.SnakeViewModel
import com.ofixhub.blockstry.shared.Point
import com.ofixhub.blockstry.shared.ThemeRepository
import com.ofixhub.blockstry.shared.CloudTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import com.ofixhub.blockstry.shared.PieceType
import com.ofixhub.blockstry.shared.GameInputHandler
import kotlin.math.abs

class GameActivity : ComponentActivity() {
    private val gameViewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(applicationContext)
        
        val windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            MobileApp(gameViewModel = gameViewModel, activity = this)
        }
    }
}

@Composable
fun MobileApp(gameViewModel: GameViewModel, activity: ComponentActivity) {
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
                    PieceType.I to "i.png",
                    PieceType.O to "o.png",
                    PieceType.T to "t.png",
                    PieceType.J to "j.png",
                    PieceType.L to "l.png",
                    PieceType.S to "s.png",
                    PieceType.Z to "z.png"
                )

                pieceFiles.forEach { (type, filename) ->
                    val bitmap = assetsRoot?.let { loadBitmapFromFile(File(it, filename).absolutePath) }
                    if (bitmap != null) {
                        newPieceBitmaps[type] = bitmap
                    }
                }
                pieceBitmaps = newPieceBitmaps
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    MaterialTheme {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = "splash"
        ) {
            composable("splash") {
                SplashScreen(onFinished = {
                    navController.navigate("game") {
                        popUpTo("splash") { inclusive = true }
                    }
                })
            }
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
                SettingsScreen(
                    onNavigateToThemeStore = { navController.navigate("theme_store") },
                    onNavigateToLeaderboard = { navController.navigate("leaderboard") }
                )
            }
            composable("leaderboard") {
                LeaderboardScreen()
            }
            composable("theme_store") {
                ThemeStoreScreen(onBack = { navController.popBackStack() })
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
                        var totalDragX = 0f
                        var totalDragY = 0f
                        
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) {
                                fastDropJob?.cancel()
                                inputHandler.onSetFastDrop(false)
                                
                                // Gesto de pausa: Deslizar hacia abajo significativamente (> 150px)
                                if (hasDragged && totalDragY > 150f && abs(totalDragY) > abs(totalDragX) * 1.5f) {
                                    inputHandler.onTogglePause()
                                }
                                
                                if (!hasDragged) lastTapTime = downTime
                                break
                            }
                            event.changes.forEach { change ->
                                if (change.pressed) {
                                    val currentPosition = change.position
                                    val dragDelta = currentPosition - lastPosition
                                    totalDragX += dragDelta.x
                                    totalDragY += dragDelta.y
                                    
                                    val threshold = 30f
                                    if (abs(totalDragX) > threshold || abs(totalDragY) > threshold) {
                                        change.consume()
                                        hasDragged = true
                                        
                                        if (abs(totalDragX) > abs(totalDragY)) {
                                            if (totalDragX > threshold) {
                                                inputHandler.onMoveRight()
                                                totalDragX = 0f
                                            } else if (totalDragX < -threshold) {
                                                inputHandler.onMoveLeft()
                                                totalDragX = 0f
                                            }
                                        } else {
                                            // Solo procesamos Up/Down si no es el gesto largo de pausa (se evalúa al soltar)
                                            // O para juegos como Snake que necesitan movimiento inmediato
                                            if (totalDragY > threshold) {
                                                inputHandler.onMoveDown()
                                                totalDragY = 0f
                                            } else if (totalDragY < -threshold) {
                                                inputHandler.onMoveUp()
                                                totalDragY = 0f
                                            }
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
        val headerHeight = screenHeight * 0.15f
        val boardMarginBottom = screenHeight * 0.05f
        val boardMaxHeight = screenHeight - headerHeight - boardMarginBottom
        
        val cellPixelSize = boardMaxHeight / BOARD_HEIGHT
        val boardPixelWidth = cellPixelSize * BOARD_WIDTH
        val boardPixelHeight = cellPixelSize * BOARD_HEIGHT

        val boardTopOffset = headerHeight
        val boardLeftOffset = (size.width - boardPixelWidth) / 2
        val previewCellSize = (headerHeight * 0.6f) / 4f 
        
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
                drawRect(color = Color.White.copy(alpha = 0.8f), topLeft = Offset(boardLeftOffset, boardTopOffset + lineIndex * cellPixelSize), size = Size(BOARD_WIDTH * cellPixelSize, cellPixelSize))
            }
        }

        gameState.currentPiece?.let { piece ->
            piece.shape.forEach { p ->
                drawCell(boardLeftOffset + (gameState.piecePosition.x + p.x) * cellPixelSize, boardTopOffset + (gameState.piecePosition.y + p.y) * cellPixelSize, cellPixelSize, piece.color, pieceBitmaps[piece.type])
            }
        }

        drawScore(gameState.score, gameState.totalLinesCleared, textMeasurer, headerHeight / 2)
        drawNextPiece(gameState.nextPiece, previewCellSize, size.width, headerHeight / 2, pieceBitmaps[gameState.nextPiece?.type])
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
        
        val screenHeight = size.height
        val headerHeight = screenHeight * 0.15f
        val boardMarginBottom = screenHeight * 0.05f
        val boardMaxHeight = screenHeight - headerHeight - boardMarginBottom
        
        val cellSize = boardMaxHeight / boardHeight
        val boardPixelWidth = cellSize * boardWidth
        val boardPixelHeight = cellSize * boardHeight

        val boardTopOffset = headerHeight
        val boardLeftOffset = (size.width - boardPixelWidth) / 2

        drawBoardGeneric(boardLeftOffset, boardTopOffset, boardWidth, boardHeight, cellSize, borderBitmap, backgroundBitmap)

        drawCell(
            boardLeftOffset + state.food.x * cellSize, 
            boardTopOffset + state.food.y * cellSize, 
            cellSize, 
            Color.Red, 
            pieceBitmaps[PieceType.O]
        )

        state.snake.forEach { point ->
            drawCell(
                boardLeftOffset + point.x * cellSize, 
                boardTopOffset + point.y * cellSize, 
                cellSize, 
                Color.Green, 
                pieceBitmaps[PieceType.I]
            )
        }
        
        drawScore(state.score, 0, textMeasurer, headerHeight / 2)
    }
}

private fun DrawScope.drawBoardGeneric(
    left: Float,
    top: Float,
    width: Int,
    height: Int,
    cellSize: Float,
    borderBitmap: ImageBitmap?,
    backgroundBitmap: ImageBitmap?
) {
    val theme = SettingsManager.currentTheme
    
    if (backgroundBitmap != null) {
        val scale = maxOf(size.width / backgroundBitmap.width.toFloat(), size.height / backgroundBitmap.height.toFloat())
        val scaledWidth = backgroundBitmap.width * scale
        val scaledHeight = backgroundBitmap.height * scale
        drawImage(
            image = backgroundBitmap,
            dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - scaledWidth) / 2).toInt(), ((size.height - scaledHeight) / 2).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(scaledWidth.toInt(), scaledHeight.toInt())
        )
    }

    if (borderBitmap != null) {
         val borderThickness = 12f 
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height),
             androidx.compose.ui.unit.IntOffset((left - borderThickness).toInt(), (top - borderThickness).toInt()),
             androidx.compose.ui.unit.IntSize((width * cellSize + borderThickness * 2).toInt(), borderThickness.toInt()),
             androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt())
         )
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height),
             androidx.compose.ui.unit.IntOffset((left - borderThickness).toInt(), (top + height * cellSize).toInt()),
             androidx.compose.ui.unit.IntSize((width * cellSize + borderThickness * 2).toInt(), borderThickness.toInt()),
             androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt())
         )
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height),
             androidx.compose.ui.unit.IntOffset((left - borderThickness).toInt(), top.toInt()),
             androidx.compose.ui.unit.IntSize(borderThickness.toInt(), (height * cellSize).toInt()),
             androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt())
         )
         drawTiledBitmap(borderBitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(borderBitmap.width, borderBitmap.height),
             androidx.compose.ui.unit.IntOffset((left + width * cellSize).toInt(), top.toInt()),
             androidx.compose.ui.unit.IntSize(borderThickness.toInt(), (height * cellSize).toInt()),
             androidx.compose.ui.unit.IntSize(borderThickness.toInt(), borderThickness.toInt())
         )
    } else {
        drawRect(color = theme.gridColor, topLeft = Offset(left, top), size = Size(width * cellSize, height * cellSize), style = Stroke(width = 2f))
    }

    if (SettingsManager.isGridEnabled) {
        for (i in 0..width) {
            drawLine(color = theme.gridColor, start = Offset(left + i * cellSize, top), end = Offset(left + i * cellSize, top + height * cellSize), strokeWidth = 1f)
        }
        for (i in 0..height) {
            drawLine(color = theme.gridColor, start = Offset(left, top + i * cellSize), end = Offset(left + width * cellSize, top + i * cellSize), strokeWidth = 1f)
        }
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
private fun DrawScope.drawScore(score: Int, totalLines: Int, textMeasurer: TextMeasurer, centerY: Float) {
    val scoreText = if (totalLines > 0) "Puntaje: $score\nLíneas: $totalLines" else "Puntaje: $score"
    val style = TextStyle(color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Left)
    val textLayoutResult = textMeasurer.measure(AnnotatedString(scoreText), style)
    drawText(textLayoutResult = textLayoutResult, color = Color.White, topLeft = Offset(x = 32f, y = centerY - textLayoutResult.size.height / 2))
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawNextPiece(nextPiece: Piece?, cellSize: Float, screenWidth: Float, centerY: Float, pieceBitmap: ImageBitmap? = null) {
    nextPiece ?: return
    val boxSize = cellSize * 4.5f
    val boxLeft = screenWidth - boxSize - 32f
    val boxTop = centerY - boxSize / 2
    val pcWidth = nextPiece.shape.maxOf { it.x + 1 } * cellSize
    val pcHeight = nextPiece.shape.maxOf { it.y + 1 } * cellSize
    val pieceOffsetX = boxLeft + (boxSize - pcWidth) / 2
    val pieceOffsetY = boxTop + (boxSize - pcHeight) / 2
    nextPiece.shape.forEach { p -> drawCell(pieceOffsetX + p.x * cellSize, pieceOffsetY + p.y * cellSize, cellSize, nextPiece.color, pieceBitmap) }
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawGameOver(textMeasurer: TextMeasurer, buttonBitmap: ImageBitmap? = null, onButtonRectsMeasured: (Rect, Rect, Rect) -> Unit) {
    val theme = SettingsManager.currentTheme
    drawRect(color = Color.Black.copy(alpha = 0.7f))
    val gameOverText = textMeasurer.measure(AnnotatedString("GAME OVER"), TextStyle(fontSize = 40.sp, color = Color.White, textAlign = TextAlign.Center))
    val highScoreText = textMeasurer.measure(AnnotatedString("High Score: ${SettingsManager.highScore}"), TextStyle(fontSize = 24.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center))
    val buttonTextStyle = TextStyle(fontSize = 24.sp, color = theme.buttonTextColor, textAlign = TextAlign.Center)
    val retryText = textMeasurer.measure(AnnotatedString("Reintentar"), buttonTextStyle)
    val settingsText = textMeasurer.measure(AnnotatedString("Configuración"), buttonTextStyle)
    val exitText = textMeasurer.measure(AnnotatedString("Salir"), buttonTextStyle)
    val buttonHeight = 120f
    val buttonWidth = size.width * 0.65f
    val buttonSpacing = 30f
    val totalHeight = gameOverText.size.height + 16f + highScoreText.size.height + 40f + (buttonHeight * 3) + (buttonSpacing * 2)
    var currentY = (size.height - totalHeight) / 2
    drawText(gameOverText, color = Color.White, topLeft = Offset(center.x - gameOverText.size.width / 2, currentY))
    currentY += gameOverText.size.height + 16f
    drawText(highScoreText, color = Color.White, topLeft = Offset(center.x - highScoreText.size.width / 2, currentY))
    currentY += highScoreText.size.height + 40f
    val retryButtonRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, retryButtonRect, retryText, theme.buttonColor)
    currentY += buttonHeight + buttonSpacing
    val settingsButtonRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, settingsButtonRect, settingsText, theme.buttonColor)
    currentY += buttonHeight + buttonSpacing
    val exitButtonRect = Rect(center.x - buttonWidth / 2, currentY, center.x + buttonWidth / 2, currentY + buttonHeight)
    drawButton(buttonBitmap, exitButtonRect, exitText, theme.buttonColor)
    onButtonRectsMeasured(retryButtonRect, settingsButtonRect, exitButtonRect)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawPauseOverlay(textMeasurer: TextMeasurer, buttonBitmap: ImageBitmap? = null, onButtonRectsMeasured: (Rect, Rect, Rect) -> Unit) {
    val theme = SettingsManager.currentTheme
    drawRect(color = Color.Black.copy(alpha = 0.8f))
    val pauseText = textMeasurer.measure(AnnotatedString("PAUSA"), TextStyle(fontSize = 40.sp, color = Color.White, textAlign = TextAlign.Center))
    drawText(pauseText, color = Color.White, topLeft = Offset(center.x - pauseText.size.width / 2, center.y - pauseText.size.height * 2.5f))
    val buttonTextStyle = TextStyle(fontSize = 24.sp, color = theme.buttonTextColor, textAlign = TextAlign.Center)
    val buttonPadding = 30f
    val buttonHeight = 120f
    val buttonWidth = size.width * 0.65f
    val resumeText = textMeasurer.measure(AnnotatedString("Reanudar"), buttonTextStyle)
    val startY = (size.height - ((buttonHeight * 3) + (buttonPadding * 2))) / 2 + 50f
    val resumeButtonRect = Rect(center.x - buttonWidth / 2, startY, center.x + buttonWidth / 2, startY + buttonHeight)
    drawButton(buttonBitmap, resumeButtonRect, resumeText, theme.buttonColor)
    val settingsText = textMeasurer.measure(AnnotatedString("Configuración"), buttonTextStyle)
    val settingsButtonRect = Rect(center.x - buttonWidth / 2, resumeButtonRect.bottom + buttonPadding, center.x + buttonWidth / 2, resumeButtonRect.bottom + buttonPadding + buttonHeight)
    drawButton(buttonBitmap, settingsButtonRect, settingsText, theme.buttonColor)
    val exitText = textMeasurer.measure(AnnotatedString("Salir"), buttonTextStyle)
    val exitButtonRect = Rect(center.x - buttonWidth / 2, settingsButtonRect.bottom + buttonPadding, center.x + buttonWidth / 2, settingsButtonRect.bottom + buttonPadding + buttonHeight)
    drawButton(buttonBitmap, exitButtonRect, exitText, theme.buttonColor)
    onButtonRectsMeasured(resumeButtonRect, settingsButtonRect, exitButtonRect)
}

@OptIn(ExperimentalTextApi::class)
private fun DrawScope.drawButton(bitmap: ImageBitmap?, rect: Rect, textLayout: androidx.compose.ui.text.TextLayoutResult, color: Color) {
    if (bitmap != null) {
        drawTiledBitmap(bitmap, androidx.compose.ui.unit.IntOffset.Zero, androidx.compose.ui.unit.IntSize(bitmap.width, bitmap.height), androidx.compose.ui.unit.IntOffset(rect.left.toInt(), rect.top.toInt()), androidx.compose.ui.unit.IntSize(rect.width.toInt(), rect.height.toInt()))
    } else {
        drawRect(color = color, topLeft = rect.topLeft, size = rect.size)
    }
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
fun LeaderboardScreen() {
    val leaderboardRepository = remember { LeaderboardRepository() }
    val scores = remember { leaderboardRepository.getTopScores() }
    val theme = SettingsManager.currentTheme

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.backgroundColor)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                "Ranking Local",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                "Top ${scores.size} partidas",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 16.dp)
            )

            if (scores.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Sin partidas registradas aún.\n¡Juega para aparecer aquí!", color = Color.White.copy(alpha = 0.5f), textAlign = TextAlign.Center)
                }
            } else {
                androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(scores.size) { index ->
                        val entry = scores[index]
                        val medalColor = when (index) {
                            0 -> Color(0xFFFFD700)
                            1 -> Color(0xFFC0C0C0)
                            2 -> Color(0xFFCD7F32)
                            else -> Color.White.copy(alpha = 0.7f)
                        }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = theme.gridColor.copy(alpha = 0.8f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}.",
                                    color = medalColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    modifier = Modifier.width(32.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(entry.playerName, color = Color.White, fontWeight = FontWeight.Bold)
                                    Text(entry.game, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                                }
                                Text(
                                    "${entry.score} pts",
                                    color = theme.buttonColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(
            onClick = { SettingsManager.clearLeaderboard() },
            modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f))
        ) {
            Text("Limpiar", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
fun SettingsScreen(
    onNavigateToThemeStore: () -> Unit = {},
    onNavigateToLeaderboard: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { ThemeRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    
    var ghostPieceEnabled by remember { mutableStateOf(SettingsManager.isGhostPieceEnabled) }
    var gridEnabled by remember { mutableStateOf(SettingsManager.isGridEnabled) }
    var currentThemeName by remember { mutableStateOf(SettingsManager.currentThemeName) }
    var constantSpeedEnabled by remember { mutableStateOf(SettingsManager.isConstantSpeedEnabled) }
    var currentGame by remember { mutableStateOf(SettingsManager.currentGame) }
    var isDownloading by remember { mutableStateOf(false) }

    // Fetch themes from GitHub on enter
    LaunchedEffect(Unit) {
        val remoteThemes = repository.getAvailableThemes()
        remoteThemes.forEach { cloudTheme ->
            val localPath = repository.getDownloadedThemeDir(cloudTheme.id).let { 
                if (it.exists()) it.absolutePath else null 
            }
            SettingsManager.addTheme(cloudTheme.toColorTheme(localPath))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Text("Ajustes", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 16.dp)) }

            // Player name
            item {
                var playerNameInput by remember { mutableStateOf(SettingsManager.playerName) }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Nombre del Jugador", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedTextField(
                            value = playerNameInput,
                            onValueChange = { v ->
                                playerNameInput = v.take(20)
                                SettingsManager.updatePlayerName(v)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            placeholder = { Text("Jugador") }
                        )
                    }
                }
            }
            
            item {
                Button(
                    onClick = {
                        val nextGame = if (currentGame == "Tetris") "Snake" else "Tetris"
                        currentGame = nextGame
                        SettingsManager.updateCurrentGame(nextGame)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) { Text("Juego Actual: $currentGame") }
            }

            item {
                val themeNames = SettingsManager.themes.keys.toList()
                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        
                        val currentIndex = themeNames.indexOf(currentThemeName)
                        val nextIndex = (currentIndex + 1) % themeNames.size
                        val nextThemeName = themeNames[nextIndex]
                        val nextTheme = SettingsManager.themes[nextThemeName]!!
                        val manifest = nextTheme.remoteManifest

                        if (!nextTheme.isDownloaded && manifest != null) {
                            scope.launch {
                                isDownloading = true
                                val success = repository.downloadThemeAssets(manifest)
                                isDownloading = false
                                if (success) {
                                    val localPath = repository.getDownloadedThemeDir(nextTheme.id).absolutePath
                                    SettingsManager.addTheme(manifest.toColorTheme(localPath))
                                    currentThemeName = nextThemeName
                                    SettingsManager.updateCurrentThemeName(nextThemeName)
                                    snackbarHostState.showSnackbar("Tema '$nextThemeName' descargado y aplicado")
                                } else {
                                    snackbarHostState.showSnackbar("No se pudo descargar el tema '$nextThemeName'")
                                }
                            }
                        } else {
                            currentThemeName = nextThemeName
                            SettingsManager.updateCurrentThemeName(nextThemeName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isDownloading
                ) {
                    Text(if (isDownloading) "Descargando..." else "Tema: $currentThemeName")
                }
            }

            item {
                Button(
                    onClick = onNavigateToThemeStore,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF6A0DAD))
                ) { Text("Tienda de Temas", color = Color.White) }
            }

            item {
                Button(
                    onClick = onNavigateToLeaderboard,
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                ) { Text("Tabla de Puntuaciones", color = Color.White) }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().clickable { ghostPieceEnabled = !ghostPieceEnabled; SettingsManager.setIsGhostPieceEnabled(ghostPieceEnabled) }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pieza Fantasma")
                        Switch(checked = ghostPieceEnabled, onCheckedChange = { ghostPieceEnabled = it; SettingsManager.setIsGhostPieceEnabled(it) })
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().clickable { gridEnabled = !gridEnabled; SettingsManager.setIsGridEnabled(gridEnabled) }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Cuadrícula")
                        Switch(checked = gridEnabled, onCheckedChange = { gridEnabled = it; SettingsManager.setIsGridEnabled(it) })
                    }
                }
            }

            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Row(
                        modifier = Modifier.fillMaxWidth().clickable { constantSpeedEnabled = !constantSpeedEnabled; SettingsManager.setIsConstantSpeedEnabled(constantSpeedEnabled) }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Velocidad Constante")
                        Switch(checked = constantSpeedEnabled, onCheckedChange = { constantSpeedEnabled = it; SettingsManager.setIsConstantSpeedEnabled(it) })
                    }
                }
            }

            item {
                Button(
                    onClick = { SettingsManager.resetHighScore() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Resetear High Score", color = Color.White) }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

// ──────────────────────────────────────────────────────────────────
// Splash Screen con animación de bloques
// ──────────────────────────────────────────────────────────────────
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.7f) }

    val infiniteTransition = rememberInfiniteTransition(label = "splash_blocks")
    val blockOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "blockOffset"
    )

    LaunchedEffect(Unit) {
        launch { alpha.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        launch { scale.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        delay(2500L)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        // Falling block animation behind the title
        Canvas(modifier = Modifier.fillMaxSize()) {
            val blockColors = listOf(Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue, Color(0xFFFFA500), Color.Green, Color.Red)
            val blockSize = 40f
            val cols = (size.width / blockSize).toInt() + 1
            val rows = (size.height / blockSize).toInt() + 2
            for (col in 0 until cols) {
                for (row in 0 until rows) {
                    val yOffset = (blockOffset * blockSize * rows + row * blockSize) % (size.height + blockSize * 2) - blockSize
                    val color = blockColors[(col + row) % blockColors.size]
                    drawRect(
                        color = color.copy(alpha = 0.06f),
                        topLeft = Offset(col * blockSize, yOffset),
                        size = androidx.compose.ui.geometry.Size(blockSize - 2f, blockSize - 2f)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alpha.value)
                .then(Modifier.padding(32.dp))
        ) {
            // Decorative block row
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf(Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue, Color(0xFFFFA500), Color.Green, Color.Red).forEach { c ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(c)
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "BlocksTry",
                color = Color.White,
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "by OfixHub",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 16.sp,
                letterSpacing = 4.sp
            )
            Spacer(Modifier.height(32.dp))
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.5f))
        }
    }
}

// ──────────────────────────────────────────────────────────────────
// Tienda de Temas
// ──────────────────────────────────────────────────────────────────
@Composable
fun ThemeStoreScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { com.ofixhub.blockstry.shared.ThemeRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoadingManifest by remember { mutableStateOf(false) }
    var downloadingId by remember { mutableStateOf<String?>(null) }

    // Load remote themes on first composition
    LaunchedEffect(Unit) {
        isLoadingManifest = true
        val remoteThemes = repository.getAvailableThemes()
        remoteThemes.forEach { cloudTheme ->
            val localPath = repository.getDownloadedThemeDir(cloudTheme.id).let {
                if (it.exists()) it.absolutePath else null
            }
            SettingsManager.addTheme(cloudTheme.toColorTheme(localPath))
        }
        isLoadingManifest = false
    }

    val currentThemeName = SettingsManager.currentThemeName
    val allThemes = SettingsManager.themes.values.toList()

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D1117))) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onBack,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                ) { Text("← Volver") }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Tienda de Temas",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (isLoadingManifest) {
                Box(Modifier.fillMaxWidth().height(60.dp), contentAlignment = Alignment.Center) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Cargando desde GitHub...", color = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(allThemes.size) { index ->
                    val theme = allThemes[index]
                    val isActive = theme.name == currentThemeName
                    val isDownloading = downloadingId == theme.id
                    val needsDownload = !theme.isDownloaded && theme.hasCustomAssets

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isActive) Color(0xFF1A237E) else Color(0xFF1C2028)
                        ),
                        border = if (isActive) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF3F51B5)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp).fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Color swatch strip
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    theme.pieceColors.take(7).forEach { c ->
                                        Box(
                                            modifier = Modifier
                                                .size(12.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(c)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(width = 90.dp, height = 28.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(theme.backgroundColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("Fondo", color = theme.gridColor, fontSize = 10.sp)
                                }
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(theme.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    if (isActive) {
                                        Spacer(Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(Color(0xFF3F51B5))
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) { Text("Activo", color = Color.White, fontSize = 10.sp) }
                                    }
                                }
                                Text(
                                    if (theme.hasCustomAssets) "Tema visual personalizado" else "Tema de colores",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                                if (needsDownload) {
                                    Text("Requiere descarga", color = Color(0xFFFFB300), fontSize = 11.sp)
                                } else if (theme.hasCustomAssets && theme.isDownloaded) {
                                    Text("Descargado ✓", color = Color(0xFF4CAF50), fontSize = 11.sp)
                                }
                            }

                            when {
                                isDownloading -> CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                                isActive -> Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF4CAF50)),
                                    contentAlignment = Alignment.Center
                                ) { Text("✓", color = Color.White, fontSize = 14.sp) }
                                needsDownload -> Button(
                                    onClick = {
                                        val manifest = theme.remoteManifest ?: return@Button
                                        scope.launch {
                                            downloadingId = theme.id
                                            val success = repository.downloadThemeAssets(manifest)
                                            downloadingId = null
                                            if (success) {
                                                val localPath = repository.getDownloadedThemeDir(theme.id).absolutePath
                                                SettingsManager.addTheme(manifest.toColorTheme(localPath))
                                                SettingsManager.updateCurrentThemeName(theme.name)
                                                snackbarHostState.showSnackbar("'${theme.name}' aplicado")
                                            } else {
                                                snackbarHostState.showSnackbar("Error al descargar '${theme.name}'")
                                            }
                                        }
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF6A0DAD))
                                ) { Text("Descargar", fontSize = 12.sp) }
                                else -> Button(
                                    onClick = {
                                        SettingsManager.updateCurrentThemeName(theme.name)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Tema '${theme.name}' aplicado")
                                        }
                                    },
                                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF1565C0))
                                ) { Text("Aplicar", fontSize = 12.sp) }
                            }
                        }
                    }
                }
            }
        }
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

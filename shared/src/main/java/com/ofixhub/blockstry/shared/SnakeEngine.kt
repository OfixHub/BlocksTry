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
import kotlin.random.Random

data class SnakeState(
    val snake: List<Point> = listOf(Point(5, 10)),
    val food: Point = Point(15, 10),
    val direction: Point = Point(1, 0),
    val score: Int = 0,
    val isGameOver: Boolean = false,
    val isPaused: Boolean = false
)

class SnakeViewModel : ViewModel() {
    private val _state = MutableStateFlow(SnakeState())
    val state: StateFlow<SnakeState> = _state.asStateFlow()

    private var gameLoopJob: Job? = null
    private val boardWidth = BOARD_WIDTH * 2 // Snake uses a denser grid
    private val boardHeight = BOARD_HEIGHT

    init {
        startGameLoop()
    }

    private fun startGameLoop() {
        gameLoopJob?.cancel()
        gameLoopJob = viewModelScope.launch {
            while (!_state.value.isGameOver && !_state.value.isPaused) {
                // Speed increases every 50 points: 200ms base, min 80ms
                val speed = maxOf(80L, 200L - (_state.value.score / 50) * 15L)
                delay(speed)
                move()
            }
        }
    }

    fun reset() {
        gameLoopJob?.cancel()
        _state.value = SnakeState()
        startGameLoop()
    }

    fun togglePause() {
        if (_state.value.isGameOver) return
        _state.update { it.copy(isPaused = !it.isPaused) }
        if (!_state.value.isPaused) {
            startGameLoop()
        } else {
            gameLoopJob?.cancel()
        }
    }

    private fun move() {
        _state.update { currentState ->
            val newHead = Point(
                (currentState.snake.first().x + currentState.direction.x + boardWidth) % boardWidth,
                (currentState.snake.first().y + currentState.direction.y + boardHeight) % boardHeight
            )

            if (currentState.snake.contains(newHead)) {
                SettingsManager.submitScore(currentState.score, "Snake")
                return@update currentState.copy(isGameOver = true)
            }

            val newSnake = mutableListOf(newHead) + currentState.snake
            if (newHead == currentState.food) {
                val newFood = generateFood(newSnake)
                currentState.copy(snake = newSnake, food = newFood, score = currentState.score + 10)
            } else {
                currentState.copy(snake = newSnake.dropLast(1))
            }
        }
    }

    private fun generateFood(snake: List<Point>): Point {
        var food = Point(Random.nextInt(boardWidth), Random.nextInt(boardHeight))
        while (snake.contains(food)) {
            food = Point(Random.nextInt(boardWidth), Random.nextInt(boardHeight))
        }
        return food
    }

    fun setDirection(dx: Int, dy: Int) {
        if (_state.value.direction.x + dx == 0 && _state.value.direction.y + dy == 0) return
        _state.update { it.copy(direction = Point(dx, dy)) }
    }
}

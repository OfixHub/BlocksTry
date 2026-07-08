package com.ofixhub.blockstry.shared

interface GameInputHandler {
    fun onMoveLeft()
    fun onMoveRight()
    fun onMoveUp()
    fun onMoveDown()
    fun onRotate()
    fun onActionPrimary() // e.g., Drop or Shoot
    fun onTogglePause()
    fun onSetFastDrop(enabled: Boolean) {}
}

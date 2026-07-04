package com.ofixhub.blockstry.shared

import android.content.Context
import androidx.compose.ui.graphics.Color
import java.io.File

/**
 * Representa un tema cargado localmente desde la carpeta 'rsc'.
 */
data class LocalTheme(
    val id: String,
    val name: String,
    val pieceColors: List<Color>,
    val backgroundColor: Color,
    val gridColor: Color,
    val buttonColor: Color,
    val buttonTextColor: Color,
    val hasCustomAssets: Boolean = false
) {
    /**
     * Convierte el tema local al formato ColorTheme utilizado por el motor del juego.
     */
    fun toColorTheme(localPath: String? = null): ColorTheme {
        return ColorTheme(
            id = id,
            name = name,
            pieceColors = pieceColors,
            backgroundColor = backgroundColor,
            gridColor = gridColor,
            buttonColor = buttonColor,
            buttonTextColor = buttonTextColor,
            hasCustomAssets = hasCustomAssets,
            localPath = localPath
        )
    }
}

class ThemeRepository(private val context: Context) {

    // Lista de temas que la aplicación reconoce localmente
    private val staticThemes = listOf(
        LocalTheme(
            id = "clasico",
            name = "Clásico",
            pieceColors = listOf(Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue, Color(0xFFFFA500), Color.Green, Color.Red),
            backgroundColor = Color.Black,
            gridColor = Color.DarkGray,
            buttonColor = Color.LightGray,
            buttonTextColor = Color.Black,
            hasCustomAssets = false
        ),
        LocalTheme(
            id = "invierno",
            name = "Invierno",
            pieceColors = listOf(Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue, Color(0xFFFFA500), Color.Green, Color.Red),
            backgroundColor = Color(0xFFE3F2FD),
            gridColor = Color(0xFF90CAF9),
            buttonColor = Color(0xFF64B5F6),
            buttonTextColor = Color.White,
            hasCustomAssets = true
        ),
        LocalTheme(
            id = "egipto",
            name = "Egipto",
            pieceColors = listOf(Color.Cyan, Color.Yellow, Color.Magenta, Color.Blue, Color(0xFFFFA500), Color.Green, Color.Red),
            backgroundColor = Color(0xFF3E2723),
            gridColor = Color(0xFF5D4037),
            buttonColor = Color(0xFFFFB300),
            buttonTextColor = Color.Black,
            hasCustomAssets = true
        )
    )

    /**
     * Obtiene la lista de temas disponibles localmente.
     */
    fun getAvailableThemes(): List<LocalTheme> = staticThemes

    /**
     * Obtiene la ruta absoluta de los recursos para un tema específico.
     * En Android, esto apuntará a la carpeta 'rsc' que el usuario debe colocar en la raíz.
     */
    fun getThemeResourcePath(themeId: String): String {
        // En una implementación real de Android, 'rsc' debería estar en el almacenamiento externo
        // o ser una carpeta vinculada. Aquí devolvemos la ruta lógica.
        return "rsc/themes/$themeId"
    }
}

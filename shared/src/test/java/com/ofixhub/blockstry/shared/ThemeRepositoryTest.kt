package com.ofixhub.blockstry.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRepositoryTest {
    @Test
    fun parsesCloudThemesFromManifestJson() {
        val json = """
            {
              "themes": [
                {
                  "id": "test-theme",
                  "name": "Tema de prueba",
                  "pieceColors": ["#FFFFFF", "#000000"],
                  "backgroundColor": "#123456",
                  "gridColor": "#654321",
                  "buttonColor": "#111111",
                  "buttonTextColor": "#EEEEEE",
                  "archiveUrl": "https://example.com/theme.zip"
                }
              ],
              "games": []
            }
        """.trimIndent()

        val themes = RemoteContentManifestParser.parseManifest(json).themes

        assertEquals(1, themes.size)
        assertEquals("test-theme", themes.first().id)
        assertEquals("Tema de prueba", themes.first().name)
        assertTrue(themes.first().archiveUrl!!.startsWith("https://"))
    }
}

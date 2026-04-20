package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FontScannerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var root: File

    @Before
    fun setup() {
        root = tempFolder.root
    }

    @Test
    fun `ttf 파일을 발견하면 맵에 포함한다`() {
        File(root, "MyFont-Regular.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("My Font Regular"))
    }

    @Test
    fun `otf 파일을 발견하면 맵에 포함한다`() {
        File(root, "NotoSans-Bold.otf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("Noto Sans Bold"))
    }

    @Test
    fun `같은 패밀리의 여러 weight는 각각 포함한다`() {
        File(root, "MyFont-Regular.ttf").createNewFile()
        File(root, "MyFont-Bold.ttf").createNewFile()
        File(root, "MyFont-Italic.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(3, result.size)
        assertTrue(result.containsKey("My Font Regular"))
        assertTrue(result.containsKey("My Font Bold"))
        assertTrue(result.containsKey("My Font Italic"))
    }

    @Test
    fun `하위 디렉토리도 재귀 스캔한다`() {
        val subDir = File(root, "Fonts").also { it.mkdirs() }
        File(subDir, "DeepFont-Regular.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertEquals(1, result.size)
        assertTrue(result.containsKey("Deep Font Regular"))
    }

    @Test
    fun `숨김 폴더는 제외한다`() {
        val hidden = File(root, ".hidden").also { it.mkdirs() }
        File(hidden, "SecretFont-Regular.ttf").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `ttf otf 외 확장자는 무시한다`() {
        File(root, "NotAFont.txt").createNewFile()
        File(root, "Image.png").createNewFile()
        val result = FontScanner.scanFontFiles(root)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `빈 디렉토리는 빈 맵을 반환한다`() {
        val result = FontScanner.scanFontFiles(root)
        assertTrue(result.isEmpty())
    }
}

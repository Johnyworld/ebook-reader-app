package com.rotein.ebookreader

import org.junit.Assert.*
import org.junit.Test
import java.text.Normalizer

class BookRecordRelinkTest {

    // 이전 폴더(Books)로 등록했을 때의 SAF 문서 URI
    private fun oldUri(fileName: String) =
        "content://com.android.externalstorage.documents/tree/primary%3ABooks/document/primary%3ABooks%2Fsub%2F" +
            encode(fileName)

    // 상위 폴더(Download)로 다시 등록했을 때의 SAF 문서 URI
    private fun newUri(fileName: String) =
        "content://com.android.externalstorage.documents/tree/primary%3ADownload/document/primary%3ADownload%2FBooks%2Fsub%2F" +
            encode(fileName)

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    private fun record(path: String, fileVersion: String = "") =
        BookReadRecord(bookPath = path, lastReadAt = 1, fileVersion = fileVersion)

    private fun book(uri: String, name: String, size: Long = 100) = BookFile(
        name = name, path = "", contentUri = uri, extension = name.substringAfterLast('.'),
        size = size, dateAdded = 0, dateModified = 0
    )

    @Test
    fun `이름과 크기가 같은 후보가 하나면 매칭된다`() {
        val result = findRelinkTargets(
            records = listOf(record(oldUri("A Book.epub"), fileVersionOf(100, 5))),
            scannedBooks = listOf(book(newUri("A Book.epub"), "A Book.epub", size = 100))
        )
        assertEquals(mapOf(oldUri("A Book.epub") to newUri("A Book.epub")), result)
    }

    @Test
    fun `대소문자와 NFC NFD 차이가 있어도 매칭된다`() {
        // 이전 URI는 NFD(맥에서 복사된 파일 등), 스캔된 이름은 NFC로 정규화되어 있다
        val nfd = Normalizer.normalize("한글책.epub", Normalizer.Form.NFD)
        val result = findRelinkTargets(
            records = listOf(record(oldUri(nfd.uppercase()))),
            scannedBooks = listOf(book(newUri("한글책.epub"), "한글책.epub"))
        )
        assertEquals(mapOf(oldUri(nfd.uppercase()) to newUri("한글책.epub")), result)
    }

    @Test
    fun `파일명에 플러스가 있어도 매칭된다`() {
        // SAF는 '+'를 %2B로 인코딩한다
        val result = findRelinkTargets(
            records = listOf(record(oldUri("C++ 입문.epub"))),
            scannedBooks = listOf(book(newUri("C++ 입문.epub"), "C++ 입문.epub"))
        )
        assertEquals(mapOf(oldUri("C++ 입문.epub") to newUri("C++ 입문.epub")), result)
    }

    @Test
    fun `저장소 루트에 있던 파일도 매칭된다`() {
        val rootUri = "content://com.android.externalstorage.documents/tree/primary%3A/document/primary%3Aa.epub"
        val result = findRelinkTargets(
            records = listOf(record(rootUri)),
            scannedBooks = listOf(book(newUri("a.epub"), "a.epub"))
        )
        assertEquals(mapOf(rootUri to newUri("a.epub")), result)
    }

    @Test
    fun `fileVersion이 있고 크기가 다르면 매칭하지 않는다`() {
        val result = findRelinkTargets(
            records = listOf(record(oldUri("a.epub"), fileVersionOf(100, 5))),
            scannedBooks = listOf(book(newUri("a.epub"), "a.epub", size = 200))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `fileVersion이 비어있으면 이름만으로 매칭한다`() {
        val result = findRelinkTargets(
            records = listOf(record(oldUri("a.epub"))),
            scannedBooks = listOf(book(newUri("a.epub"), "a.epub", size = 200))
        )
        assertEquals(mapOf(oldUri("a.epub") to newUri("a.epub")), result)
    }

    @Test
    fun `후보가 여러 개면 건너뛴다`() {
        val result = findRelinkTargets(
            records = listOf(record(oldUri("a.epub"))),
            scannedBooks = listOf(
                book(newUri("a.epub"), "a.epub"),
                // 다른 하위 폴더에 있는 같은 이름의 파일
                book(newUri("a.epub").replace("%2Fsub%2F", "%2Fother%2F"), "a.epub")
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `끊긴 기록 여러 개가 같은 후보를 가리키면 모두 건너뛴다`() {
        val other = "content://com.android.externalstorage.documents/tree/primary%3AOld/document/primary%3AOld%2Fa.epub"
        val result = findRelinkTargets(
            records = listOf(record(oldUri("a.epub")), record(other)),
            scannedBooks = listOf(book(newUri("a.epub"), "a.epub"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `이미 기록이 있는 책은 후보에서 제외된다`() {
        val result = findRelinkTargets(
            records = listOf(record(oldUri("a.epub")), record(newUri("a.epub"))),
            scannedBooks = listOf(book(newUri("a.epub"), "a.epub"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `이번 스캔에도 있는 URI의 기록은 대상이 아니다`() {
        val result = findRelinkTargets(
            records = listOf(record(oldUri("a.epub"))),
            scannedBooks = listOf(
                book(oldUri("a.epub"), "a.epub"),
                book(newUri("a.epub"), "a.epub")
            )
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `document id에 파일명이 없는 URI는 건너뛴다`() {
        val result = findRelinkTargets(
            records = listOf(record("content://com.android.providers.media.documents/document/msf%3A1234")),
            scannedBooks = listOf(book(newUri("1234"), "1234"))
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun `레거시 절대 경로 기록은 대상이 아니다`() {
        // 절대 경로 → URI 전환은 migrateBookPathsToUri가 담당한다
        val result = findRelinkTargets(
            records = listOf(record("/sdcard/Books/a.epub")),
            scannedBooks = listOf(book(newUri("a.epub"), "a.epub"))
        )
        assertTrue(result.isEmpty())
    }
}

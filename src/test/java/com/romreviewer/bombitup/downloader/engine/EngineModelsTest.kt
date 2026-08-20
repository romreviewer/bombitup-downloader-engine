package com.romreviewer.bombitup.downloader.engine

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineModelsTest {
    @Test
    fun requestBuildsUrlAndOptionsWithoutShellExpansion() {
        val request = EngineRequest("https://example.test/watch?v=1&next=2")
            .addOption("--no-playlist")
            .addOption("--format", "137+140")

        val args = request.commandArguments()

        assertEquals("https://example.test/watch?v=1&next=2", args.first())
        assertTrue(args.containsAll(listOf("--no-playlist", "--format", "137+140")))
    }

    @Test
    fun videoInfoMapsYtDlpJsonFields() {
        val json = """
            {
              "webpage_url":"https://example.test/video",
              "title":"Example",
              "duration":12,
              "extractor_key":"ExampleSite",
              "formats":[{
                "format_id":"137",
                "ext":"mp4",
                "vcodec":"avc1",
                "acodec":"none",
                "height":1080,
                "fps":30,
                "filesize_approx":12345
              }]
            }
        """.trimIndent()

        val info = Gson().fromJson(json, EngineVideoInfo::class.java)

        assertEquals("Example", info.title)
        assertEquals("137", info.formats?.single()?.formatId)
        assertEquals(12345L, info.formats?.single()?.fileSizeApproximate)
    }
}

package com.romreviewer.bombitup.downloader.engine

import com.google.gson.annotations.SerializedName

class EngineRequest(val url: String) {
    private val options = mutableListOf<Pair<String, String?>>()

    fun addOption(name: String): EngineRequest = apply { options += name to null }

    fun addOption(name: String, value: Any): EngineRequest = apply {
        options += name to value.toString()
    }

    fun hasOption(name: String): Boolean = options.any { it.first == name }

    internal fun commandArguments(): List<String> = buildList {
        add(url)
        options.forEach { (name, value) ->
            add(name)
            if (value != null) add(value)
        }
    }

    internal fun copyRequest(): EngineRequest = EngineRequest(url).also { copy ->
        options.forEach { (name, value) ->
            if (value == null) copy.addOption(name) else copy.addOption(name, value)
        }
    }
}

data class EngineResponse(
    val command: List<String>,
    val exitCode: Int,
    val elapsedMillis: Long,
    val out: String,
    val err: String
)

data class EngineVideoInfo(
    @SerializedName("webpage_url") val webpageUrl: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("uploader") val uploader: String? = null,
    @SerializedName("duration") val duration: Int = 0,
    @SerializedName("thumbnail") val thumbnail: String? = null,
    @SerializedName("extractor_key") val extractorKey: String? = null,
    @SerializedName("formats") val formats: List<EngineVideoFormat>? = null
)

data class EngineVideoFormat(
    @SerializedName("format_id") val formatId: String? = null,
    @SerializedName("ext") val ext: String? = null,
    @SerializedName("vcodec") val vcodec: String? = null,
    @SerializedName("acodec") val acodec: String? = null,
    @SerializedName("height") val height: Int = 0,
    @SerializedName("fps") val fps: Int = 0,
    @SerializedName("tbr") val tbr: Int = 0,
    @SerializedName("abr") val abr: Int = 0,
    @SerializedName("filesize") val filesize: Long = 0,
    @SerializedName("filesize_approx") val fileSizeApproximate: Long = 0
) {
    val fileSize: Long get() = filesize
}

data class EngineRuntimePaths(
    val versionCode: Long,
    val versionName: String,
    val root: java.io.File
) {
    val pythonDir = java.io.File(root, "python")
    val ffmpegDir = java.io.File(root, "ffmpeg")
    val ytdlpFile = java.io.File(root, "yt-dlp/yt-dlp")
}

class EngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
class EngineCanceledException : Exception()

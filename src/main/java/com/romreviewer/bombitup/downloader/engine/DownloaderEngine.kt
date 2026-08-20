package com.romreviewer.bombitup.downloader.engine

import android.content.Context
import com.google.gson.Gson
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

object DownloaderEngine {
    private val gson = Gson()
    private val processes = ConcurrentHashMap<String, Process>()
    private val canceledProcessIds = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var runtime: Runtime? = null

    private data class Runtime(
        val paths: EngineRuntimePaths,
        val python: File,
        val ffmpeg: File,
        val quickJs: File,
        val environment: Map<String, String>
    )

    @Synchronized
    fun initialize(context: Context, paths: EngineRuntimePaths) {
        val current = runtime
        if (
            current?.paths?.versionCode == paths.versionCode &&
            current.paths.root.canonicalFile == paths.root.canonicalFile
        ) return
        check(processes.isEmpty()) { "Cannot switch the engine while processes are running" }
        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val python = File(nativeDir, "libpython.so")
        val ffmpeg = File(nativeDir, "libffmpeg.so")
        val quickJs = File(nativeDir, "libqjs.so")
        val required = listOf(
            python,
            ffmpeg,
            quickJs,
            paths.ytdlpFile,
            File(paths.pythonDir, "usr"),
            File(paths.ffmpegDir, "usr")
        )
        val missing = required.firstOrNull { !it.exists() }
        if (missing != null) throw EngineException("Engine file missing: ${missing.name}")

        val ldLibraryPath = listOf(
            File(paths.pythonDir, "usr/lib").absolutePath,
            File(paths.ffmpegDir, "usr/lib").absolutePath
        ).joinToString(":")
        val pythonHome = File(paths.pythonDir, "usr").absolutePath
        runtime = Runtime(
            paths = paths,
            python = python,
            ffmpeg = ffmpeg,
            quickJs = quickJs,
            environment = mapOf(
                "LD_LIBRARY_PATH" to ldLibraryPath,
                "SSL_CERT_FILE" to File(paths.pythonDir, "usr/etc/tls/cert.pem").absolutePath,
                "PYTHONHOME" to pythonHome,
                "HOME" to pythonHome,
                "TMPDIR" to context.cacheDir.absolutePath,
                "PATH" to (System.getenv("PATH").orEmpty() + ":" + nativeDir.absolutePath)
            )
        )
    }

    @Synchronized
    fun reset() {
        check(processes.isEmpty()) { "Cannot reset the engine while processes are running" }
        runtime = null
    }

    fun versionName(): String? = runtime?.paths?.versionName

    fun getInfo(request: EngineRequest): EngineVideoInfo {
        val infoRequest = request.copyRequest().addOption("--dump-json")
        val response = execute(infoRequest)
        return runCatching { gson.fromJson(response.out, EngineVideoInfo::class.java) }
            .getOrElse { throw EngineException("Unable to parse video information", it) }
            ?: throw EngineException("Failed to fetch video information")
    }

    fun execute(
        request: EngineRequest,
        processId: String? = null,
        callback: ((Float, Long, String) -> Unit)? = null
    ): EngineResponse {
        val current = runtime ?: throw EngineException("Downloader engine is not installed")
        if (processId != null && processes.containsKey(processId)) {
            throw EngineException("Process ID already exists")
        }

        val prepared = request.copyRequest()
        if (!prepared.hasOption("--cache-dir")) prepared.addOption("--no-cache-dir")
        prepared.addOption("--js-runtimes", "quickjs:${current.quickJs.absolutePath}")
        prepared.addOption("--ffmpeg-location", current.ffmpeg.absolutePath)

        val command = buildList {
            add(current.python.absolutePath)
            add(current.paths.ytdlpFile.absolutePath)
            addAll(prepared.commandArguments())
        }
        val builder = ProcessBuilder(command).redirectErrorStream(false)
        builder.environment().putAll(current.environment)
        val startedAt = System.currentTimeMillis()
        val process = try {
            builder.start()
        } catch (e: Exception) {
            throw EngineException("Unable to start downloader engine", e)
        }
        if (processId != null) processes[processId] = process

        val stdout = StringBuffer()
        val stderr = StringBuffer()
        val outputThread = streamReader(process.inputStream, stdout, callback)
        val errorThread = streamReader(process.errorStream, stderr, null)
        val exitCode = try {
            outputThread.join()
            errorThread.join()
            process.waitFor()
        } catch (e: InterruptedException) {
            process.destroy()
            throw e
        } finally {
            if (processId != null) processes.remove(processId, process)
        }

        if (exitCode != 0) {
            if (processId != null && canceledProcessIds.remove(processId)) {
                throw EngineCanceledException()
            }
            throw EngineException(stderr.toString().ifBlank { "Engine exited with $exitCode" })
        }
        if (processId != null) canceledProcessIds.remove(processId)
        if (processId != null) processes.remove(processId)
        return EngineResponse(
            command = Collections.unmodifiableList(command),
            exitCode = exitCode,
            elapsedMillis = System.currentTimeMillis() - startedAt,
            out = stdout.toString(),
            err = stderr.toString()
        )
    }

    fun cancel(processId: String): Boolean {
        val process = processes.remove(processId) ?: return false
        canceledProcessIds.add(processId)
        process.destroy()
        return true
    }

    fun selfTest(context: Context, paths: EngineRuntimePaths) {
        initialize(context, paths)
        val current = runtime ?: throw EngineException("Engine failed to initialize")
        runSelfTest(
            listOf(current.python.absolutePath, paths.ytdlpFile.absolutePath, "--version"),
            current.environment
        )
        runSelfTest(listOf(current.ffmpeg.absolutePath, "-version"), current.environment)
    }

    private fun runSelfTest(command: List<String>, environment: Map<String, String>) {
        val process = ProcessBuilder(command).redirectErrorStream(true).apply {
            this.environment().putAll(environment)
        }.start()
        val output = process.inputStream.bufferedReader().use(BufferedReader::readText)
        val code = process.waitFor()
        if (code != 0) throw EngineException("Engine self-test failed: ${output.take(160)}")
    }

    private fun streamReader(
        input: InputStream,
        output: StringBuffer,
        callback: ((Float, Long, String) -> Unit)?
    ) = thread(start = true, name = "downloader-engine-output") {
        InputStreamReader(input, Charsets.UTF_8).buffered().use { reader ->
            val line = StringBuilder()
            while (true) {
                val value = reader.read()
                if (value == -1) break
                val char = value.toChar()
                output.append(char)
                if (char == '\r' || char == '\n') {
                    if (line.isNotEmpty()) callback?.invoke(
                        progress(line.toString()), etaSeconds(line.toString()), line.toString()
                    )
                    line.clear()
                } else {
                    line.append(char)
                }
            }
        }
    }

    private val downloadProgress = Regex("""\[download]\s+(\d+(?:\.\d+)?)%""")
    private val eta = Regex("""ETA\s+(\d+):(\d+)""")

    private fun progress(line: String): Float = when {
        line.startsWith("size=") -> 99f
        else -> downloadProgress.find(line)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
    }

    private fun etaSeconds(line: String): Long {
        val match = eta.find(line) ?: return -1
        return match.groupValues[1].toLong() * 60 + match.groupValues[2].toLong()
    }
}

package jp.titze.intellij.helix.shell

import com.intellij.util.EnvironmentUtil
import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

data class HelixShellResult(val exitCode: Int, val stdout: String, val stderr: String)

object HelixShellExecutor {

    const val DEFAULT_TIMEOUT_MS = 5000L

    @Volatile
    var shellRunner: ((command: String, input: String?, workingDir: String?) -> HelixShellResult)? = null

    fun reset() {
        shellRunner = null
    }

    fun execute(
        command: String,
        input: String? = null,
        workingDir: String? = null,
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): HelixShellResult {
        val runner = shellRunner
        if (runner != null) {
            return runner(command, input, workingDir)
        }
        return executeProcess(command, input, workingDir, timeoutMs)
    }

    private fun buildCommandArray(command: String): Array<String> {
        val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
        return if (isWindows) {
            arrayOf("cmd.exe", "/c", command)
        } else {
            val shell = System.getenv("SHELL")?.takeIf { it.isNotEmpty() } ?: "/bin/sh"
            arrayOf(shell, "-c", command)
        }
    }

    private fun executeProcess(
        command: String,
        input: String?,
        workingDir: String?,
        timeoutMs: Long,
    ): HelixShellResult = try {
        val pb = ProcessBuilder(*buildCommandArray(command))
        applyEnvironment(pb)
        applyWorkingDirectory(pb, workingDir)

        val process = pb.start()

        val stdoutFuture = CompletableFuture.supplyAsync {
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).readText()
        }
        val stderrFuture = CompletableFuture.supplyAsync {
            process.errorStream.bufferedReader(StandardCharsets.UTF_8).readText()
        }

        val stdinFuture = CompletableFuture.runAsync {
            writeProcessInput(process, input)
        }

        val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
        if (!finished) {
            process.destroyForcibly()
            stdinFuture.cancel(true)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            HelixShellResult(-1, "", "Command timed out after ${timeoutMs}ms")
        } else {
            try {
                stdinFuture.get(1, TimeUnit.SECONDS)
            } catch (_: Exception) {
                // Ignore stdin write errors if process finished
            }
            val stdout = stdoutFuture.get(1, TimeUnit.SECONDS)
            val stderr = stderrFuture.get(1, TimeUnit.SECONDS)
            HelixShellResult(process.exitValue(), stdout, stderr)
        }
    } catch (e: IOException) {
        HelixShellResult(-1, "", e.message ?: "Process execution failed")
    } catch (e: InterruptedException) {
        Thread.currentThread().interrupt()
        HelixShellResult(-1, "", "Command execution interrupted")
    } catch (_: java.util.concurrent.ExecutionException) {
        HelixShellResult(-1, "", "Stream reading failed")
    }

    private fun applyEnvironment(pb: ProcessBuilder) {
        try {
            val env = EnvironmentUtil.getEnvironmentMap()
            if (env.isNotEmpty()) {
                pb.environment().putAll(env)
            }
        } catch (_: Throwable) {
            // Fallback to inherited environment if EnvironmentUtil fails
        }
    }

    private fun applyWorkingDirectory(pb: ProcessBuilder, workingDir: String?) {
        if (!workingDir.isNullOrBlank()) {
            val dir = File(workingDir)
            if (dir.exists() && dir.isDirectory) {
                pb.directory(dir)
            }
        }
    }

    private fun writeProcessInput(process: java.lang.Process, input: String?) {
        try {
            if (input != null) {
                process.outputStream.bufferedWriter(StandardCharsets.UTF_8).use { writer ->
                    writer.write(input)
                    writer.flush()
                }
            } else {
                process.outputStream.close()
            }
        } catch (_: IOException) {
            // Child process closed pipe early (e.g. head, grep -m)
        }
    }
}

package moe.shizuku.manager.manage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.shizuku.manager.utils.newShellProcess
import kotlin.math.abs

internal class MockCoordinates private constructor(val latitude: Double, val longitude: Double) {
    companion object {
        private val decimal = Regex("""[+-]?(?:\d+(?:[.,]\d*)?|[.,]\d+)""")

        fun parse(latitude: String, longitude: String): MockCoordinates? {
            val lat = parseAxis(latitude, 90) ?: return null
            val lon = parseAxis(longitude, 180) ?: return null
            return MockCoordinates(lat, lon)
        }

        fun parseAxis(input: String, limit: Int): Double? {
            val text = input.trim()
            if (text.length > 32 || !decimal.matches(text)) return null
            return text.replace(',', '.').toDoubleOrNull()?.takeIf { abs(it) <= limit }
        }
    }
}

internal class MockLocation(
    private val run: (String) -> Command? = ::runLocationCommand,
) {
    internal data class Command(val code: Int, val output: String)

    internal sealed interface Result {
        data object ServiceUnavailable : Result
        data object AdbRequired : Result
        data object LocationDisabled : Result
        data class Applied(val point: MockCoordinates, val unverified: List<String>) : Result
        data object Restored : Result
        data class Failed(val details: String, val providersMayRemain: Boolean = false) : Result
    }

    internal data class Outcome(val result: Result, val permissionRestoreFailed: Boolean = false)

    suspend fun update(point: MockCoordinates?): Outcome = lock.withLock {
        // Finish the short transaction even if the user leaves the screen midway through it.
        withContext(Dispatchers.IO + NonCancellable) { change(point) }
    }

    private fun change(point: MockCoordinates?): Outcome {
        val uid = run("id -u") ?: return Outcome(Result.ServiceUnavailable)
        if (uid.code != 0) return Outcome(Result.Failed(details("id", uid)))
        if (uid.output != "2000") return Outcome(Result.AdbRequired)
        if (point != null) {
            val enabled = run("cmd location is-location-enabled")
            if (enabled?.code != 0 || enabled.output !in setOf("true", "false")) {
                return Outcome(Result.Failed(details("is-location-enabled", enabled)))
            }
            if (enabled.output == "false") return Outcome(Result.LocationDisabled)
        }

        val appOp = readPermission()
        val previous = appOp?.takeIf { it.code == 0 }?.let { readMode(it.output) }
            ?: return Outcome(Result.Failed(details("MOCK_LOCATION", appOp)))
        var permissionRestoreFailed: Boolean
        val result = try {
            val grant = if (previous == "allow") Command(0, "") else setMode("allow")
            val granted = if (previous != "allow" && grant?.code == 0) readPermission() else null
            when {
                grant?.code != 0 -> Result.Failed(details("MOCK_LOCATION", grant))
                previous != "allow" && (granted?.code != 0 || readMode(granted.output) != "allow") ->
                    Result.Failed(details("MOCK_LOCATION", granted))
                point != null -> apply(point)
                else -> removeProviders()?.let { Result.Failed(it, providersMayRemain = true) } ?: Result.Restored
            }
        } finally {
            permissionRestoreFailed = previous != "allow" && setMode(previous)?.code != 0
        }
        val verifiedResult = if (result is Result.Applied) verify(result.point) else result
        return Outcome(verifiedResult, permissionRestoreFailed)
    }

    private fun readPermission() = run("appops get 2000 android:mock_location")
    private fun setMode(mode: String) = run("appops set 2000 android:mock_location $mode")

    private fun apply(point: MockCoordinates): Result {
        for (provider in providers) {
            for (step in listOf(
                "add-test-provider $provider",
                "set-test-provider-enabled $provider true",
                "set-test-provider-location $provider --location ${point.latitude},${point.longitude}",
            )) {
                val response = run("cmd location providers $step")
                if (response?.code != 0) {
                    // A partly configured set can give apps conflicting locations.
                    val cleanupFailed = removeProviders() != null
                    return Result.Failed(details(step.substringBefore(' ') + " " + provider, response), cleanupFailed)
                }
            }
        }
        return Result.Applied(point, providers)
    }

    private fun verify(point: MockCoordinates): Result.Applied {
        val dump = run("dumpsys -t 5 location")?.takeIf { it.code == 0 }?.output.orEmpty()
        val verified = mockFix.findAll(dump).mapNotNull { match ->
            val (provider, lat, lon) = match.destructured
            if (abs(lat.toDouble() - point.latitude) <= 0.000001 &&
                abs(lon.toDouble() - point.longitude) <= 0.000001
            ) provider else null
        }.toSet()
        return Result.Applied(point, providers.filterNot { it in verified })
    }

    private fun removeProviders(): String? {
        val errors = providers.mapNotNull { provider ->
            val result = run("cmd location providers remove-test-provider $provider")
            if (result?.code == 0) null else details(provider, result)
        }
        return errors.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun details(step: String, command: Command?): String {
        val output = command?.output?.lineSequence()?.filter { it.isNotBlank() }?.take(3)?.joinToString("\n")
        return if (output.isNullOrEmpty()) "$step (exit ${command?.code ?: -1})" else "$step\n${output.take(420)}"
    }

    private companion object {
        val lock = Mutex()
        val providers = listOf("gps", "network", "fused")
        val modeLine = Regex("""(?m)^\s*(?:Uid mode:\s*)?MOCK_LOCATION:\s*([a-z]+)\b""")
        val modes = setOf("allow", "ignore", "deny", "default", "foreground")
        val mockFix = Regex("""last location\s*=\s*Location\[(gps|network|fused)\s+(-?\d+(?:\.\d+)?),\s*(-?\d+(?:\.\d+)?)[^\]\r\n]*\bmock\b""")

        fun readMode(output: String): String? = if (output.lineSequence().firstOrNull() == "No operations.") "default"
        else modeLine.find(output)?.groupValues?.get(1)?.takeIf { it in modes }
    }
}

private fun runLocationCommand(command: String): MockLocation.Command? {
    val process = newShellProcess("exec timeout 6 $command 2>&1") ?: return null
    return try {
        val output = process.inputStream.bufferedReader().use { it.readText().trim() }
        MockLocation.Command(process.waitFor(), output)
    } catch (_: Exception) {
        null
    } finally {
        runCatching { process.destroy() }
    }
}

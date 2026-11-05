package moe.shizuku.manager.manage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocationTest {
    private val point = checkNotNull(MockCoordinates.parse("-6.1234567", "106.8765432"))

    @Test
    fun acceptsSignedDecimalCoordinatesAndRejectsNonCoordinates() {
        val parsed = checkNotNull(MockCoordinates.parse(" -6,2 ", "106.8"))
        assertEquals(-6.2, parsed.latitude, 0.0)
        assertEquals(106.8, parsed.longitude, 0.0)
        assertNull(MockCoordinates.parse("90.1", "0"))
        assertNull(MockCoordinates.parse("0", "-180.1"))
        assertNull(MockCoordinates.parse("NaN", "0"))
        assertNull(MockCoordinates.parse("0; id", "0"))
        assertNull(MockCoordinates.parse("-6.2,106.8", ""))
    }

    @Test
    fun appliesThreeProvidersAndRestoresDefaultPermissionBeforeVerifying() = runBlocking {
        val shell = FakeShell()
        val outcome = MockLocation(shell::execute).update(point)
        val applied = outcome.result as MockLocation.Result.Applied
        assertTrue(applied.unverified.isEmpty())
        assertFalse(outcome.permissionRestoreFailed)
        assertEquals("default", shell.mode)
        assertTrue(shell.calls.indexOf("appops set 2000 android:mock_location default") < shell.calls.indexOf("dumpsys -t 5 location"))
        assertEquals(3, shell.calls.count { it.contains("set-test-provider-location") })
    }

    @Test
    fun partialApplyRemovesProvidersAndPreservesAnExistingAllowMode() = runBlocking {
        val shell = FakeShell(mode = "allow", failAt = "set-test-provider-enabled network")
        val outcome = MockLocation(shell::execute).update(point)
        val failure = outcome.result as MockLocation.Result.Failed
        assertTrue(failure.details.contains("network"))
        assertFalse(failure.providersMayRemain)
        assertEquals(3, shell.calls.count { it.contains("remove-test-provider") })
        assertFalse(shell.calls.any { it.startsWith("appops set") })
    }

    @Test
    fun restoreTriesEveryProviderEvenIfOneFails() = runBlocking {
        val shell = FakeShell(failAt = "remove-test-provider network")
        val outcome = MockLocation(shell::execute).update(null)
        assertTrue((outcome.result as MockLocation.Result.Failed).providersMayRemain)
        assertEquals(3, shell.calls.count { it.contains("remove-test-provider") })
        assertEquals("default", shell.mode)
    }

    private class FakeShell(var mode: String = "default", val failAt: String? = null) {
        val calls = mutableListOf<String>()

        fun execute(command: String): MockLocation.Command {
            calls += command
            if (failAt != null && command.contains(failAt)) return MockLocation.Command(255, "provider error")
            val output = when {
                command == "id -u" -> "2000"
                command == "cmd location is-location-enabled" -> "true"
                command.startsWith("appops get") -> if (mode == "default") "No operations.\nDefault mode: ignore" else "Uid mode: MOCK_LOCATION: $mode"
                command.startsWith("appops set") -> { mode = command.substringAfterLast(' '); "" }
                command.startsWith("dumpsys") -> listOf("gps", "network", "fused").joinToString("\n") {
                    "last location=Location[$it -6.123457,106.876543 hAcc=100.0 mock]"
                }
                else -> ""
            }
            return MockLocation.Command(0, output)
        }
    }
}

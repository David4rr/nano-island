package dev.nanoIsland

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NanoAccessibilityServiceTest {

    @Test
    fun testInitialDisconnectedState() {
        assertFalse(NanoAccessibilityService.isConnected)
    }

    @Test
    fun testLockScreenWhenDisconnected() {
        val result = NanoAccessibilityService.lockScreen()
        assertFalse("lockScreen must return false when service is not connected", result)
    }

    @Test
    fun testTakeScreenshotWhenDisconnected() {
        var errorReceived = 0
        var callbackCalled = false

        val started = NanoAccessibilityService.takeScreenshot(
            onSuccess = { _, _ -> },
            onFailure = { errorCode ->
                callbackCalled = true
                errorReceived = errorCode
            }
        )

        assertFalse("takeScreenshot must return false when service is not connected", started)
        assertEquals(true, callbackCalled)
        assertEquals(NanoAccessibilityService.ERROR_SERVICE_NOT_CONNECTED, errorReceived)
    }
}

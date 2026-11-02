package com.openlauncher.app

import com.openlauncher.app.radio.awaitRadioRoute
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RadioRouteGateTest {
    @Test fun waitsForOemSwitchInsteadOfAssumingBinderOpenIsSufficient() = runBlocking {
        val channels = listOf(3, 3, 11, 7)
        var reads = 0
        var pauses = 0
        val observed = mutableListOf<Int>()
        val actual = awaitRadioRoute(readChannel = { channels[reads++] },
            onChannel = { observed.add(it) }, pause = { pauses++ })
        assertEquals(11, actual)
        assertEquals(listOf(3, 3, 11), observed)
        assertEquals(3, reads)
        assertEquals(2, pauses)
    }

    @Test fun wrongRouteIsBoundedAndReportsTheObservedChannel() = runBlocking {
        var reads = 0
        var pauses = 0
        val failure = runCatching {
            awaitRadioRoute(readChannel = { reads++; 7 }, pause = { pauses++ }, maxReads = 3)
        }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("observé=7"))
        assertEquals(3, reads)
        assertEquals(2, pauses)
    }

    @Test fun permissionFailureDoesNotBecomeAnAssumedRadioRoute() = runBlocking {
        var pauses = 0
        val failure = runCatching {
            awaitRadioRoute(readChannel = { throw SecurityException("OEM denied") }, pause = { pauses++ })
        }.exceptionOrNull()
        assertTrue(failure is SecurityException)
        assertEquals(0, pauses)
    }

    @Test fun alreadySelectedRadioDoesNotWaitOrPollAgain() = runBlocking {
        var reads = 0
        awaitRadioRoute(readChannel = { reads++; 11 }, pause = { fail("Unexpected delay") })
        assertEquals(1, reads)
    }
}

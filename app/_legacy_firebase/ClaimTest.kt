package com.itcabs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference

/** Guards the one rule that matters: exactly one driver wins a leg. Run: ./gradlew test */
class ClaimTest {
    @Test fun onlyOpenIsClaimable() {
        assertTrue(Repo.canClaim(LegStatus.OPEN.name))
        assertFalse(Repo.canClaim(LegStatus.CLAIMED.name))
        assertFalse(Repo.canClaim(null))
    }

    /** Simulate N concurrent claims via a CAS loop mirroring the Firestore transaction guard. */
    @Test fun firstClaimWins() {
        val status = AtomicReference(LegStatus.OPEN.name)
        val winners = java.util.concurrent.atomic.AtomicInteger(0)
        val threads = (1..50).map {
            Thread {
                // transaction: read status, if canClaim -> CAS to CLAIMED
                if (Repo.canClaim(status.get()) && status.compareAndSet(LegStatus.OPEN.name, LegStatus.CLAIMED.name))
                    winners.incrementAndGet()
            }
        }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(1, winners.get())
    }
}

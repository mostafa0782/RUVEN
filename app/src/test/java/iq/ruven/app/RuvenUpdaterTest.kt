package iq.ruven.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RuvenUpdaterTest {
    @Test
    fun detectsNewerPatchVersion() {
        assertTrue(RuvenUpdater.isNewer("0.1.154", "0.1.155"))
    }

    @Test
    fun rejectsSameOrOlderVersion() {
        assertFalse(RuvenUpdater.isNewer("0.1.154", "0.1.154"))
        assertFalse(RuvenUpdater.isNewer("0.1.155", "0.1.154"))
    }

    @Test
    fun comparesNumericSegmentsInsteadOfLexicographicStrings() {
        assertTrue(RuvenUpdater.isNewer("0.1.99", "0.1.100"))
        assertTrue(RuvenUpdater.isNewer("0.9.9", "0.10.0"))
    }

    @Test
    fun ignoresBuildMetadataAfterHyphenForOrdering() {
        assertFalse(RuvenUpdater.isNewer("0.1.154-beta", "0.1.154"))
        assertTrue(RuvenUpdater.isNewer("0.1.154-beta", "0.1.155"))
    }
}

package net.askwho.scanbooks

import net.askwho.scanbooks.ui.settings.ReleaseChecklistGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseChecklistGateTest {
    @Test
    fun internalDistributionPassesWhenAttributionAndNoticesClosed() {
        assertTrue(ReleaseChecklistGate.internalDistributionAllowed)
        assertEquals("pass", ReleaseChecklistGate.internalDistributionStatus)
    }

    @Test
    fun commercialStoreBlockedUntilDocTrWrittenPermission() {
        assertFalse(ReleaseChecklistGate.commercialStoreAllowed)
        assertEquals("blocked", ReleaseChecklistGate.commercialStoreStatus)
        assertTrue(ReleaseChecklistGate.openItemIds.contains("doctr_commercial"))
    }

    @Test
    fun fingerSegFinalWeightsRemainOpen() {
        assertTrue(ReleaseChecklistGate.openItemIds.contains("fingerseg_final_weights"))
        val row = ReleaseChecklistGate.items.first { it.id == "fingerseg_final_weights" }
        assertEquals("open", row.status)
    }

    @Test
    fun closedItemsCoverNonCommercialPathAndOssNotices() {
        val closed = ReleaseChecklistGate.items.filter { it.status == "closed" }.map { it.id }
        assertTrue(closed.contains("doctr_noncommercial"))
        assertTrue(closed.contains("ort_opencv_notice"))
        assertTrue(closed.contains("attribution_ui"))
        assertTrue(closed.contains("privacy_notice"))
        assertTrue(closed.contains("ux_oss_reference_only"))
    }

    @Test
    fun checklistDocPathIsStable() {
        assertEquals("docs/RELEASE_CHECKLIST.md", ReleaseChecklistGate.documentPath)
    }
}

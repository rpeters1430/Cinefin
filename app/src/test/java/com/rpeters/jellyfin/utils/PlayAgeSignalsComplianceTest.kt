package com.rpeters.jellyfin.utils

import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayAgeSignalsComplianceTest {

    @Test
    fun `isAdultVerified returns true only for verified status`() {
        assertTrue(PlayAgeSignalsCompliance.isAdultVerified(AgeSignalsVerificationStatus.VERIFIED))
        assertFalse(PlayAgeSignalsCompliance.isAdultVerified(AgeSignalsVerificationStatus.SUPERVISED))
        assertFalse(
            PlayAgeSignalsCompliance.isAdultVerified(
                AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING,
            ),
        )
        assertFalse(
            PlayAgeSignalsCompliance.isAdultVerified(
                AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED,
            ),
        )
        assertFalse(PlayAgeSignalsCompliance.isAdultVerified(AgeSignalsVerificationStatus.UNKNOWN))
        assertFalse(PlayAgeSignalsCompliance.isAdultVerified(null))
    }

    @Test
    fun `describeStatus returns stable labels`() {
        assertEquals("VERIFIED", PlayAgeSignalsCompliance.describeStatus(AgeSignalsVerificationStatus.VERIFIED))
        assertEquals("SUPERVISED", PlayAgeSignalsCompliance.describeStatus(AgeSignalsVerificationStatus.SUPERVISED))
        assertEquals(
            "SUPERVISED_APPROVAL_PENDING",
            PlayAgeSignalsCompliance.describeStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING),
        )
        assertEquals(
            "SUPERVISED_APPROVAL_DENIED",
            PlayAgeSignalsCompliance.describeStatus(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED),
        )
        assertEquals("UNKNOWN", PlayAgeSignalsCompliance.describeStatus(AgeSignalsVerificationStatus.UNKNOWN))
        assertEquals("UNKNOWN", PlayAgeSignalsCompliance.describeStatus(9_999))
        assertEquals("UNKNOWN", PlayAgeSignalsCompliance.describeStatus(null))
    }

    @Test
    fun `isBlocked returns true for pending and denied status`() {
        assertTrue(PlayAgeSignalsCompliance.isBlocked(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING))
        assertTrue(PlayAgeSignalsCompliance.isBlocked(AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED))
        assertFalse(PlayAgeSignalsCompliance.isBlocked(AgeSignalsVerificationStatus.VERIFIED))
        assertFalse(PlayAgeSignalsCompliance.isBlocked(AgeSignalsVerificationStatus.SUPERVISED))
        assertFalse(PlayAgeSignalsCompliance.isBlocked(AgeSignalsVerificationStatus.UNKNOWN))
        assertFalse(PlayAgeSignalsCompliance.isBlocked(null))
    }
}

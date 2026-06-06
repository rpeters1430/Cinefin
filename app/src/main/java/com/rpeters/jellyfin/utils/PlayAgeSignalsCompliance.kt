package com.rpeters.jellyfin.utils

import com.google.android.play.agesignals.model.AgeSignalsVerificationStatus

object PlayAgeSignalsCompliance {
    fun isAdultVerified(userStatus: Int?): Boolean {
        return userStatus == AgeSignalsVerificationStatus.VERIFIED
    }

    fun isBlocked(userStatus: Int?): Boolean {
        return userStatus == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING ||
                userStatus == AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED
    }

    fun describeStatus(userStatus: Int?): String {
        return when (userStatus) {
            AgeSignalsVerificationStatus.VERIFIED -> "VERIFIED"
            AgeSignalsVerificationStatus.SUPERVISED -> "SUPERVISED"
            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_PENDING -> "SUPERVISED_APPROVAL_PENDING"
            AgeSignalsVerificationStatus.SUPERVISED_APPROVAL_DENIED -> "SUPERVISED_APPROVAL_DENIED"
            AgeSignalsVerificationStatus.UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN"
        }
    }
}


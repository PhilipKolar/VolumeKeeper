package com.philkolar.volumekeeper.core

/** Plain snapshot of a StatusBarNotification, so matching is testable off-device. */
data class NotificationInfo(
    val pkg: String,
    val id: Int,
    val tag: String?,
    val key: String,
    val postWall: Long,
    val channel: String?,
    val title: String?,
    val text: String?,
    val actions: List<String> = emptyList(),
)

enum class MatchKind { CSD, NEAR_MISS, OTHER }

data class MatchResult(val kind: MatchKind, val csdKind: CsdKind = CsdKind.UNKNOWN, val simulated: Boolean = false)

/**
 * Recognises SystemUI's CSD notification (CsdWarningDialogDelegate.sendNotification, Android 17):
 * package com.android.systemui, id 1007 (SystemMessage.NOTE_CSD_LOWER_AUDIO), channel "ALR",
 * title "Volume lowered to safer level". Matching is id OR (channel AND title), so one of the two
 * drifting in an update still matches; anything else from SystemUI that mentions volume/hearing/
 * headphones is reported as a near miss so a format change shows up in the log.
 */
object CsdNotificationMatcher {
    const val SYSTEMUI = "com.android.systemui"
    const val CSD_ID = 1007
    const val CSD_CHANNEL = "ALR"
    const val SIM_TAG = "vk-sim-csd"

    private val TITLE = Regex("volume lowered", RegexOption.IGNORE_CASE)
    private val NEAR = Regex("volume|hearing|headphone|sound dose", RegexOption.IGNORE_CASE)
    private val TEXT_5X = Regex("exceeded the safe limit", RegexOption.IGNORE_CASE)
    private val TEXT_1X = Regex("longer than recommended", RegexOption.IGNORE_CASE)

    fun match(n: NotificationInfo, ownPackage: String, simulationEnabled: Boolean): MatchResult {
        if (simulationEnabled && n.pkg == ownPackage && n.tag == SIM_TAG) {
            return MatchResult(MatchKind.CSD, kindOf(n), simulated = true)
        }
        if (n.pkg != SYSTEMUI) return MatchResult(MatchKind.OTHER)
        val titleMatches = n.title?.let { TITLE.containsMatchIn(it) } == true
        if (n.id == CSD_ID || (n.channel == CSD_CHANNEL && titleMatches)) {
            return MatchResult(MatchKind.CSD, kindOf(n))
        }
        val blob = listOfNotNull(n.title, n.text).joinToString(" ")
        return if (NEAR.containsMatchIn(blob)) MatchResult(MatchKind.NEAR_MISS) else MatchResult(MatchKind.OTHER)
    }

    fun kindOf(n: NotificationInfo): CsdKind {
        val text = n.text ?: return CsdKind.UNKNOWN
        return when {
            TEXT_5X.containsMatchIn(text) -> CsdKind.DOSE_5X
            TEXT_1X.containsMatchIn(text) -> CsdKind.DOSE_1X
            else -> CsdKind.UNKNOWN
        }
    }
}

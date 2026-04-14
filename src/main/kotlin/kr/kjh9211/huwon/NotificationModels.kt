package kr.kjh9211.huwon

import java.time.Instant

data class NotificationPayload(
    val appname: String? = null,
    val content: String? = null,
    val title: String? = null,
    val time: String? = null,
    val token: String? = null,
)

data class DonationMatch(
    val playerName: String,
    val amount: Long?,
    val donorName: String?,
)

enum class RequestStatus {
    ACCEPTED,
    REJECTED,
    FAILED,
}

data class DonationRecord(
    val receivedAt: Instant,
    val remoteAddress: String,
    val appName: String,
    val title: String,
    val content: String,
    val requestTime: String,
    val playerName: String?,
    val amount: Long?,
    val donorName: String?,
    val status: RequestStatus,
    val message: String,
)

data class RequestOutcome(
    val statusCode: Int,
    val status: RequestStatus,
    val message: String,
    val processed: Boolean,
    val record: DonationRecord? = null,
)

data class ReloadResult(
    val success: Boolean,
    val message: String,
)

package kr.kjh9211.huwon

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.bukkit.Bukkit
import java.net.InetSocketAddress
import java.time.Instant
import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

class DonationService(private val plugin: Huwon) {
    private val gson = Gson()
    private val lock = ReentrantLock()
    private val recentRecords = ArrayDeque<DonationRecord>()
    private var jsDonationParser = JsDonationParser(plugin)

    fun reload(): ReloadResult {
        plugin.saveDefaultConfig()
        val parser = JsDonationParser(plugin)
        val validation = parser.validateReload()
        if (!validation.success) {
            return validation
        }
        jsDonationParser = parser
        return validation
    }

    fun parsePayload(body: String): NotificationPayload? = try {
        gson.fromJson(body, NotificationPayload::class.java)
    } catch (_: JsonSyntaxException) {
        null
    }

    fun processIncoming(body: String, remoteAddress: InetSocketAddress): RequestOutcome {
        val payload = parsePayload(body)
            ?: return reject(
                remoteAddress = remoteAddress,
                payload = null,
                statusCode = 400,
                message = "JSON body is invalid.",
                status = RequestStatus.REJECTED,
            )

        val configuredToken = plugin.config.getString("auth.token").orEmpty()
        if (configuredToken.isBlank()) {
            plugin.logger.warning("auth.token is blank in config.yml, all incoming requests are blocked.")
            return reject(
                remoteAddress = remoteAddress,
                payload = payload,
                statusCode = 503,
                message = "Server token is not configured.",
                status = RequestStatus.FAILED,
            )
        }

        val requestToken = payload.token?.trim().orEmpty()
        if (requestToken.isBlank()) {
            sleepForUnauthorizedDelay()
            return reject(
                remoteAddress = remoteAddress,
                payload = payload,
                statusCode = 408,
                message = "Token is required.",
                status = RequestStatus.REJECTED,
            )
        }

        if (requestToken != configuredToken) {
            sleepForUnauthorizedDelay()
            return reject(
                remoteAddress = remoteAddress,
                payload = payload,
                statusCode = 401,
                message = "Token is invalid.",
                status = RequestStatus.REJECTED,
            )
        }

        val match = extractDonation(payload)
        if (match.playerName.isBlank()) {
            return reject(
                remoteAddress = remoteAddress,
                payload = payload,
                statusCode = 422,
                message = "Player name could not be parsed from the notification.",
                status = RequestStatus.FAILED,
            )
        }

        val record = DonationRecord(
            receivedAt = Instant.now(),
            remoteAddress = remoteAddress.address.hostAddress ?: remoteAddress.hostString,
            appName = payload.appname.orEmpty(),
            title = payload.title.orEmpty(),
            content = payload.content.orEmpty(),
            requestTime = payload.time.orEmpty(),
            playerName = match.playerName,
            amount = match.amount,
            donorName = match.donorName,
            status = RequestStatus.ACCEPTED,
            message = "Donation accepted and queued.",
        )

        remember(record)
        executeReward(record)

        return RequestOutcome(
            statusCode = 202,
            status = RequestStatus.ACCEPTED,
            message = "Donation accepted.",
            processed = true,
            record = record,
        )
    }

    fun manualProcess(playerName: String, amount: Long, donorName: String?): DonationRecord {
        val record = DonationRecord(
            receivedAt = Instant.now(),
            remoteAddress = "manual",
            appName = "manual",
            title = "manual",
            content = "manual",
            requestTime = Instant.now().toString(),
            playerName = playerName,
            amount = amount,
            donorName = donorName,
            status = RequestStatus.ACCEPTED,
            message = "Manual donation processed.",
        )
        remember(record)
        executeReward(record)
        return record
    }

    fun recentRecords(limit: Int = 10): List<DonationRecord> = lock.withLock {
        recentRecords.toList().takeLast(limit).reversed()
    }

    private fun extractDonation(payload: NotificationPayload): DonationMatch {
        return jsDonationParser.parse(payload)
    }

    private fun executeReward(record: DonationRecord) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            val commands = plugin.config.getStringList("rewards.commands")
            if (commands.isEmpty()) {
                plugin.logger.warning("No reward commands are configured. Request was accepted but no action was run.")
            }

            commands.forEach { rawCommand ->
                val command = rawCommand
                    .replace("{player}", record.playerName.orEmpty())
                    .replace("{amount}", record.amount?.toString() ?: "0")
                    .replace("{donor}", record.donorName ?: record.playerName.orEmpty())
                    .replace("{title}", record.title)
                    .replace("{content}", record.content)
                    .replace("{appname}", record.appName)
                    .replace("{time}", record.requestTime)

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)
            }

            if (plugin.config.getBoolean("logging.broadcast-to-console", true)) {
                plugin.logger.info(
                    "Processed donation: player=${record.playerName}, amount=${record.amount}, donor=${record.donorName}, source=${record.appName}"
                )
            }
        })
    }

    private fun reject(
        remoteAddress: InetSocketAddress,
        payload: NotificationPayload?,
        statusCode: Int,
        message: String,
        status: RequestStatus,
    ): RequestOutcome {
        val record = DonationRecord(
            receivedAt = Instant.now(),
            remoteAddress = remoteAddress.address.hostAddress ?: remoteAddress.hostString,
            appName = payload?.appname.orEmpty(),
            title = payload?.title.orEmpty(),
            content = payload?.content.orEmpty(),
            requestTime = payload?.time.orEmpty(),
            playerName = null,
            amount = null,
            donorName = null,
            status = status,
            message = message,
        )
        remember(record)

        return RequestOutcome(
            statusCode = statusCode,
            status = status,
            message = message,
            processed = false,
            record = record,
        )
    }

    private fun remember(record: DonationRecord) = lock.withLock {
        recentRecords.addLast(record)
        while (recentRecords.size > plugin.config.getInt("logging.max-recent-records", 30).coerceAtLeast(1)) {
            recentRecords.removeFirst()
        }
    }

    private fun sleepForUnauthorizedDelay() {
        val delay = plugin.config.getLong("auth.unauthorized-delay-ms", 3500L).coerceIn(0L, 30000L)
        if (delay > 0L) {
            Thread.sleep(delay)
        }
    }
}

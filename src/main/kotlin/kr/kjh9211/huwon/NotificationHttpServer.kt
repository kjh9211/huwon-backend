package kr.kjh9211.huwon

import com.google.gson.Gson
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

class NotificationHttpServer(
    private val plugin: Huwon,
    private val donationService: DonationService,
) {
    companion object {
        private val gson = Gson()
    }

    private var server: HttpServer? = null

    fun start() {
        if (!plugin.config.getBoolean("server.enabled", true)) {
            plugin.logger.info("Notification HTTP server is disabled by config.")
            return
        }

        val host = plugin.config.getString("server.host", "0.0.0.0").orEmpty()
        val port = plugin.config.getInt("server.port", 59999)
        val path = normalizePath(plugin.config.getString("server.path", "/noti").orEmpty())

        try {
            val created = HttpServer.create(InetSocketAddress(host, port), plugin.config.getInt("server.backlog", 0))
            created.createContext(path, NotificationHandler(donationService))
            created.executor = Executors.newFixedThreadPool(plugin.config.getInt("server.worker-threads", 4).coerceAtLeast(1))
            created.start()
            server = created
            plugin.logger.info("Listening for donation notifications at http://$host:$port$path")
        } catch (exception: Exception) {
            plugin.logger.severe("Failed to start notification server: ${exception.message}")
        }
    }

    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun normalizePath(path: String): String {
        if (path.isBlank()) {
            return "/noti"
        }
        return if (path.startsWith("/")) path else "/$path"
    }

    private class NotificationHandler(
        private val donationService: DonationService,
    ) : HttpHandler {
        override fun handle(exchange: HttpExchange) {
            try {
                if (exchange.requestMethod != "POST") {
                    sendJson(exchange, 405, mapOf("ok" to false, "message" to "POST only"))
                    return
                }

                val requestBody = exchange.requestBody.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
                val outcome = donationService.processIncoming(requestBody, exchange.remoteAddress)
                val response = linkedMapOf<String, Any>(
                    "ok" to outcome.processed,
                    "status" to outcome.status.name,
                    "message" to outcome.message,
                ).apply {
                    outcome.record?.playerName?.let { put("player", it) }
                    outcome.record?.amount?.let { put("amount", it) }
                }
                sendJson(exchange, outcome.statusCode, response)
            } finally {
                exchange.close()
            }
        }

        private fun sendJson(exchange: HttpExchange, status: Int, body: Map<String, Any>) {
            val bytes = gson.toJson(body).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { output ->
                output.write(bytes)
            }
        }
    }
}

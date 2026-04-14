package kr.kjh9211.huwon

import org.graalvm.polyglot.Context
import org.graalvm.polyglot.Value
import java.nio.charset.StandardCharsets

class JsDonationParser(
    private val plugin: Huwon,
) {
    fun validateReload(): ReloadResult {
        val script = loadScript()
        if (script.isBlank()) {
            return ReloadResult(false, "파서 스크립트가 비어 있어서 리로드를 중단했습니다.")
        }

        return try {
            execute(script, "", "", "", "", requireNonNullPlayerName = true)
            ReloadResult(true, "파서 검증에 성공했습니다.")
        } catch (exception: Exception) {
            ReloadResult(false, exception.message ?: "파서 검증에 실패했습니다.")
        }
    }

    fun parse(payload: NotificationPayload): DonationMatch {
        val script = loadScript()
        if (script.isBlank()) {
            plugin.logger.warning("Parsing script is empty, donation parsing cannot continue.")
            return DonationMatch("", null, null)
        }

        return try {
            execute(
                script = script,
                title = payload.title.orEmpty(),
                content = payload.content.orEmpty(),
                appname = payload.appname.orEmpty(),
                time = payload.time.orEmpty(),
                requireNonNullPlayerName = false,
            )
        } catch (exception: Exception) {
            plugin.logger.warning("Failed to execute parsing script: ${exception.message}")
            DonationMatch("", null, null)
        }
    }

    private fun execute(
        script: String,
        title: String,
        content: String,
        appname: String,
        time: String,
        requireNonNullPlayerName: Boolean,
    ): DonationMatch {
        Context.newBuilder("js").allowAllAccess(false).build().use { context ->
            val bindings = context.getBindings("js")
            bindings.putMember("title", title)
            bindings.putMember("content", content)
            bindings.putMember("appname", appname)
            bindings.putMember("time", time)

            context.eval("js", script)
            val result = context.eval("js", "parseDonation(title, content, appname, time)")
            return readMatch(result, requireNonNullPlayerName)
        }
    }

    private fun loadScript(): String {
        val relativePath = plugin.config.getString("parsing.script-file").orEmpty().trim()
        if (relativePath.isBlank()) {
            return ""
        }

        val scriptFile = plugin.dataFolder.resolve(relativePath)
        if (!scriptFile.exists()) {
            plugin.logger.warning("Parsing script file not found: ${scriptFile.absolutePath}")
            return ""
        }

        return scriptFile.readText(StandardCharsets.UTF_8).trim()
    }

    private fun readMatch(result: Value, requireNonNullPlayerName: Boolean): DonationMatch {
        if (!result.hasMembers()) {
            return DonationMatch("", null, null)
        }

        val playerName = readString(result, "playerName")
        if (requireNonNullPlayerName && playerName == null) {
            throw IllegalStateException("parser.js 검증 실패: parseDonation(...)이 playerName에 null을 반환했습니다.")
        }

        val donorName = readString(result, "donorName")
        val amount = when {
            result.hasMember("amount") && !result.getMember("amount").isNull -> {
                val value = result.getMember("amount")
                when {
                    value.isNumber -> value.asLong()
                    value.isString -> value.asString().replace(",", "").toLongOrNull()
                    else -> null
                }
            }

            else -> null
        }

        return DonationMatch(
            playerName = playerName?.trim().orEmpty(),
            amount = amount,
            donorName = donorName?.trim(),
        )
    }

    private fun readString(result: Value, key: String): String? {
        if (!result.hasMember(key)) {
            return null
        }

        val value = result.getMember(key)
        if (value.isNull) {
            return null
        }

        return when {
            value.isString -> value.asString()
            else -> value.toString()
        }
    }
}

package kr.kjh9211.huwon

import org.bukkit.plugin.java.JavaPlugin

class Huwon : JavaPlugin() {
    lateinit var donationService: DonationService
        private set

    private var notificationServer: NotificationHttpServer? = null

    override fun onEnable() {
        saveDefaultConfig()
        saveResourceIfMissing("parser.js")

        donationService = DonationService(this)
        registerCommands()
        restartNotificationServer()
        logger.info("Huwon plugin enabled.")
    }

    override fun onDisable() {
        notificationServer?.stop()
        notificationServer = null
    }

    fun reloadPlugin(): ReloadResult {
        reloadConfig()
        val reloadResult = donationService.reload()
        if (!reloadResult.success) {
            return reloadResult
        }
        restartNotificationServer()
        return ReloadResult(true, "설정을 다시 불러오고 HTTP 서버를 재시작했습니다.")
    }

    private fun restartNotificationServer() {
        notificationServer?.stop()
        notificationServer = NotificationHttpServer(this, donationService).also { it.start() }
    }

    private fun registerCommands() {
        val command = HuwonCommand(this)
        listOf("후원", "gndnjs", "huwonadmin", "huwonreload").forEach { name ->
            getCommand(name)?.setExecutor(command)
            getCommand(name)?.tabCompleter = command
        }
    }

    private fun saveResourceIfMissing(path: String) {
        val target = dataFolder.resolve(path)
        if (!target.exists()) {
            saveResource(path, false)
        }
    }
}

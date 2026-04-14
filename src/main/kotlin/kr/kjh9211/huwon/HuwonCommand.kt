package kr.kjh9211.huwon

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter

class HuwonCommand(
    private val plugin: Huwon,
) : CommandExecutor, TabCompleter {
    override fun onCommand(
        sender: CommandSender,
        command: Command,
        label: String,
        args: Array<out String>,
    ): Boolean {
        return when (command.name.lowercase()) {
            "후원", "gndnjs" -> handleUserCommand(sender)
            "huwonadmin" -> handleAdminCommand(sender, args)
            "huwonreload" -> handleReloadCommand(sender)
            else -> false
        }
    }

    private fun handleReloadCommand(sender: CommandSender): Boolean {
        if (!sender.hasPermission("huwon.admin")) {
            sender.sendMessage("${ChatColor.RED}관리자 권한이 없습니다.")
            return true
        }

        val result = plugin.reloadPlugin()
        val color = if (result.success) ChatColor.GREEN else ChatColor.RED
        sender.sendMessage("$color${result.message}")
        return true
    }

    private fun handleUserCommand(sender: CommandSender): Boolean {
        if (!sender.hasPermission("huwon.use")) {
            sender.sendMessage("${ChatColor.RED}권한이 없습니다.")
            return true
        }

        val recent = plugin.donationService.recentRecords(3)
        sender.sendMessage("${ChatColor.GOLD}[후원] ${ChatColor.YELLOW}후원 수신 서버가 실행 중입니다.")
        if (recent.isEmpty()) {
            sender.sendMessage("${ChatColor.GRAY}아직 처리된 후원 기록이 없습니다.")
            return true
        }

        recent.forEach { record ->
            sender.sendMessage(
                "${ChatColor.GRAY}- ${record.status.name} | player=${record.playerName ?: "?"} | amount=${record.amount ?: 0} | ${record.message}"
            )
        }
        return true
    }

    private fun handleAdminCommand(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission("huwon.admin")) {
            sender.sendMessage("${ChatColor.RED}관리자 권한이 없습니다.")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMessage("${ChatColor.YELLOW}/huwonadmin reload")
            sender.sendMessage("${ChatColor.YELLOW}/huwonadmin status")
            sender.sendMessage("${ChatColor.YELLOW}/huwonadmin recent")
            sender.sendMessage("${ChatColor.YELLOW}/huwonadmin simulate <player> <amount> [donor]")
            return true
        }

        when (args[0].lowercase()) {
            "reload" -> {
                val result = plugin.reloadPlugin()
                val color = if (result.success) ChatColor.GREEN else ChatColor.RED
                sender.sendMessage("$color${result.message}")
            }

            "status" -> {
                sender.sendMessage("${ChatColor.GOLD}[후원 상태]")
                sender.sendMessage("${ChatColor.GRAY}host=${plugin.config.getString("server.host")}")
                sender.sendMessage("${ChatColor.GRAY}port=${plugin.config.getInt("server.port")}")
                sender.sendMessage("${ChatColor.GRAY}path=${plugin.config.getString("server.path")}")
                sender.sendMessage("${ChatColor.GRAY}tokenConfigured=${plugin.config.getString("auth.token")?.isNotBlank() == true}")
            }

            "recent" -> {
                val recent = plugin.donationService.recentRecords(10)
                if (recent.isEmpty()) {
                    sender.sendMessage("${ChatColor.GRAY}최근 요청 기록이 없습니다.")
                    return true
                }

                recent.forEach { record ->
                    sender.sendMessage(
                        "${ChatColor.GRAY}- ${record.status.name} | ${record.remoteAddress} | player=${record.playerName ?: "?"} | amount=${record.amount ?: 0} | ${record.message}"
                    )
                }
            }

            "simulate" -> {
                if (args.size < 3) {
                    sender.sendMessage("${ChatColor.RED}사용법: /huwonadmin simulate <player> <amount> [donor]")
                    return true
                }

                val amount = args[2].toLongOrNull()
                if (amount == null) {
                    sender.sendMessage("${ChatColor.RED}amount는 숫자여야 합니다.")
                    return true
                }

                val donor = args.getOrNull(3)
                val record = plugin.donationService.manualProcess(args[1], amount, donor)
                sender.sendMessage(
                    "${ChatColor.GREEN}수동 후원 처리 완료: player=${record.playerName}, amount=${record.amount}, donor=${record.donorName}"
                )
            }

            else -> sender.sendMessage("${ChatColor.RED}알 수 없는 하위 명령어입니다.")
        }

        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>,
    ): MutableList<String> {
        if (command.name.lowercase() != "huwonadmin") {
            return mutableListOf()
        }

        return when (args.size) {
            1 -> listOf("reload", "status", "recent", "simulate")
                .filter { it.startsWith(args[0], ignoreCase = true) }
                .toMutableList()

            else -> mutableListOf()
        }
    }
}

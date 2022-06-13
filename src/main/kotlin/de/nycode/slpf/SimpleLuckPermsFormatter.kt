package de.nycode.slpf

import io.papermc.paper.event.player.AsyncChatEvent
import java.util.function.Consumer
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.JoinConfiguration
import net.kyori.adventure.text.format.NamedTextColor
import net.luckperms.api.LuckPerms
import net.luckperms.api.event.group.GroupCreateEvent
import net.luckperms.api.event.group.GroupDataRecalculateEvent
import net.luckperms.api.event.group.GroupDeleteEvent
import net.luckperms.api.event.user.UserDataRecalculateEvent
import net.luckperms.api.model.group.Group
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin

class SimpleLuckPermsFormatter : JavaPlugin(), Listener {

    private lateinit var luckPerms: LuckPerms

    override fun onEnable() {
        luckPerms = loadLuckPerms() ?: error("Unable to load the LuckPerms API")
        Bukkit.getPluginManager()
            .registerEvents(this, this)

        val update = Consumer<Any> {
            Bukkit.getOnlinePlayers().forEach {
                it.updateScoreboard()
                it.updatePrefixes()
            }
        }
        luckPerms.eventBus.subscribe(this, GroupDataRecalculateEvent::class.java, update)
        luckPerms.eventBus.subscribe(this, GroupCreateEvent::class.java, update)
        luckPerms.eventBus.subscribe(this, GroupDeleteEvent::class.java, update)
        luckPerms.eventBus.subscribe(this, UserDataRecalculateEvent::class.java, update)

        saveDefaultConfig()
        reloadConfig()
    }

    private fun Player.updateScoreboard() {
        scoreboard.teams.forEach {
            it.unregister()
        }
        luckPerms.groupManager.loadedGroups
            .sortedByDescending { it.weight.orElse(0) }
            .forEachIndexed { index, group ->
                val team = scoreboard.registerNewTeam(getFormattedWeight(group.weight.orElse(index)) + group.name)
                team.prefix(
                    Component.text(
                        ChatColor
                            .translateAlternateColorCodes(
                                '&', group.cachedData.metaData.prefix ?: ""
                            )
                    )
                )
                team.suffix(
                    Component.text(
                        ChatColor
                            .translateAlternateColorCodes(
                                '&', group.cachedData.metaData.suffix ?: ""
                            )
                    )
                )
                team.color(
                    NamedTextColor.NAMES.value(group.cachedData.metaData.getMetaValue("color")?.lowercase() ?: "gray")
                )
            }
    }

    private fun loadLuckPerms(): LuckPerms? {
        val provider = Bukkit.getServicesManager()
            .getRegistration(LuckPerms::class.java)
        return provider?.provider
    }

    private fun getFormattedWeight(weight: Int): String {
        var weightString = weight.toString()
        while (weightString.length <= 5) {
            weightString = "0$weightString"
        }
        return weightString
    }

    private fun Player.updatePrefixes() {
        Bukkit.getOnlinePlayers().forEach {
            val group = it.getLuckPermsGroup() ?: return
            val index = luckPerms.groupManager.loadedGroups.sortedByDescending { it.weight.orElse(0) }
                .indexOf(group)
            val team = this.scoreboard.getTeam(getFormattedWeight(group.weight.orElse(index)) + group.name)
            team?.addEntry(it.name)
        }
    }

    private fun Player.getLuckPermsGroup(): Group? {
        val groupName = luckPerms.userManager.getUser(uniqueId)?.primaryGroup ?: return null
        return luckPerms.groupManager.getGroup(groupName)
    }

    @EventHandler
    private fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        player.scoreboard = Bukkit.getScoreboardManager().newScoreboard
        Bukkit.getOnlinePlayers().forEach {
            it.updateScoreboard()
            it.updatePrefixes()
        }
    }

    @EventHandler
    private fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val group = player.getLuckPermsGroup()
        event.renderer { source, _, message, _ ->
            Component.join(
                JoinConfiguration.separator(
                    Component.text(": ").color(NamedTextColor.DARK_GRAY)
                ),
                source.name().color(
                    NamedTextColor.NAMES
                        .value(group?.cachedData?.metaData?.getMetaValue("color")?.lowercase() ?: "gray")
                ),
                message.color(NamedTextColor.WHITE)
            )
        }
    }

}

package com.rpserver.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LinkCommand implements CommandExecutor {

    private final RPServerPlugin plugin;

    public LinkCommand(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        LinkManager manager = plugin.getLinkManager();

        if (manager.isLinked(player.getUniqueId()) || manager.isBypassed(player)) {
            player.sendMessage("§aYour account is already linked or you have a manual bypass. No need to link again.");
            return true;
        }

        String code = manager.generateCode(player.getUniqueId());
        int expiry = plugin.getConfig().getInt("code-expiry-seconds", 300);

        player.sendMessage("§e§l=== Discord Linking ===");
        player.sendMessage("§7Your temporary verification code is: §a§l" + code);
        player.sendMessage("§7Go to the Discord server and use the command:");
        player.sendMessage("§b/verify " + code);
        player.sendMessage("§7You must have the §dServer Booster §7role.");
        player.sendMessage("§7This code expires in §c" + (expiry / 60) + " minutes§7.");
        player.sendMessage("§e§l======================");

        return true;
    }
}

package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class ForceVerifyCommand implements CommandExecutor {

    private final RPServerPlugin plugin;

    public ForceVerifyCommand(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rpserver.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage("§cUsage: /forceverify <player>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online to force-verify.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        LinkManager manager = plugin.getLinkManager();

        if (manager.isLinked(uuid) || manager.isBypassed(target)) {
            sender.sendMessage("§e" + target.getName() + " is already linked or has a manual bypass.");
            return true;
        }

        manager.linkAccount(uuid, "admin-forced:" + sender.getName());
        manager.setRestricted(uuid, false);

        sender.sendMessage("§aSuccessfully force-verified §e" + target.getName() + "§a.");
        target.sendMessage("§a§lAn admin has manually verified your account. You can now play freely!");

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("Force Verify",
                    "**Admin:** " + sender.getName()
                            + "\n**Player:** " + target.getName() + " (`" + uuid + "`)");
        }

        return true;
    }
}

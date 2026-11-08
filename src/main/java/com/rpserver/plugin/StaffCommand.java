package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public class StaffCommand implements CommandExecutor {

    private final RPServerPlugin plugin;

    public StaffCommand(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rpserver.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        LinkManager manager = plugin.getLinkManager();

        if (args.length == 0) {
            sender.sendMessage("§eUsage: §a/staff <add|remove|list> [player|UUID]");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("list")) {
            Set<String> list = manager.getBypassList();
            if (list.isEmpty()) {
                sender.sendMessage("§eNo manual bypass / staff entries.");
            } else {
                sender.sendMessage("§aManual bypass / staff entries (§e" + list.size() + "§a):");
                for (String entry : list) {
                    sender.sendMessage(" §7- §f" + entry);
                }
            }
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§eUsage: §a/staff <add|remove> <player|UUID>");
            return true;
        }

        String target = args[1];

        if (sub.equals("add")) {
            boolean added = manager.addBypass(target);
            if (added) {
                sender.sendMessage("§aAdded §e" + target + " §ato the staff/bypass list.");
                if (plugin.getAuditLogger() != null) {
                    plugin.getAuditLogger().log("Staff Added",
                            "**By:** " + sender.getName() + "\n**Target:** " + target);
                }
            } else {
                sender.sendMessage("§e" + target + " is already on the bypass list.");
            }
            return true;
        }

        if (sub.equals("remove")) {
            boolean removed = manager.removeBypass(target);
            if (removed) {
                sender.sendMessage("§aRemoved §e" + target + " §afrom the staff/bypass list.");
                if (plugin.getAuditLogger() != null) {
                    plugin.getAuditLogger().log("Staff Removed",
                            "**By:** " + sender.getName() + "\n**Target:** " + target);
                }
            } else {
                sender.sendMessage("§e" + target + " was not on the bypass list.");
            }
            return true;
        }

        sender.sendMessage("§eUsage: §a/staff <add|remove|list> [player|UUID]");
        return true;
    }
}

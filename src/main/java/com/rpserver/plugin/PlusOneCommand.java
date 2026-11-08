package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlusOneCommand implements CommandExecutor {

    private final RPServerPlugin plugin;

    public PlusOneCommand(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        LinkManager manager = plugin.getLinkManager();

        if (!manager.isLinked(player.getUniqueId()) && !manager.isBypassed(player)) {
            player.sendMessage("§cYou must be a verified player to use the plus-one system.");
            return true;
        }

        if (args.length == 0) {
            UUID current = manager.getPlusOne(player.getUniqueId());
            if (current == null) {
                player.sendMessage("§eYou have no plus-one. Usage: §a/plusone <player> §eor §a/plusone remove");
            } else {
                String name = Bukkit.getOfflinePlayer(current).getName();
                player.sendMessage("§aYour current plus-one is: §e" + (name != null ? name : current.toString()));
                player.sendMessage("§7Use §a/plusone remove §7to revoke their access.");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("clear")) {
            if (!manager.hasPlusOne(player.getUniqueId())) {
                player.sendMessage("§eYou do not have a plus-one to remove.");
                return true;
            }
            manager.removePlusOne(player.getUniqueId());
            player.sendMessage("§aYour plus-one has been removed.");
            return true;
        }

        // Invite a player
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            player.sendMessage("§cPlayer must be online to be invited as plus-one.");
            return true;
        }

        if (target.getUniqueId().equals(player.getUniqueId())) {
            player.sendMessage("§cYou cannot invite yourself.");
            return true;
        }

        String error = manager.setPlusOne(player.getUniqueId(), target.getUniqueId());
        if (error != null) {
            player.sendMessage("§c" + error);
            return true;
        }

        // Unrestrict the guest immediately
        manager.setRestricted(target.getUniqueId(), false);

        player.sendMessage("§aYou invited §e" + target.getName() + " §aas your plus-one!");
        target.sendMessage("§a§lYou have been invited as a plus-one by §e" + player.getName() + "§a!");
        target.sendMessage("§7You can play as long as they keep their Discord role / boost status.");

        return true;
    }
}

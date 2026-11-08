package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class StaffLinkCommand implements CommandExecutor {

    private final RPServerPlugin plugin;

    public StaffLinkCommand(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("rpserver.admin")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage("§eUsage: §a/stafflink <player> <discord_id>");
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer must be online.");
            return true;
        }

        String discordId = args[1].trim();
        if (!discordId.matches("\\d{17,20}")) {
            sender.sendMessage("§cInvalid Discord ID. It should be a long number (17-20 digits).");
            return true;
        }

        UUID uuid = target.getUniqueId();
        LinkManager manager = plugin.getLinkManager();

        manager.linkAccount(uuid, discordId);
        manager.addStaffLink(uuid, discordId);
        manager.setRestricted(uuid, false);

        sender.sendMessage("§aLinked §e" + target.getName() + " §ato Discord ID §e" + discordId + "§a.");
        target.sendMessage("§a§lYour account was linked by staff. You can now play freely!");

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("Staff Link",
                    "**Admin:** " + sender.getName()
                            + "\n**Player:** " + target.getName() + " (`" + uuid + "`)"
                            + "\n**Discord ID:** " + discordId);
        }

        return true;
    }
}

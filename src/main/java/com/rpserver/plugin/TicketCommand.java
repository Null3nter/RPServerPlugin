package com.rpserver.plugin;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class TicketCommand implements CommandExecutor {

    private final RPServerPlugin plugin;

    public TicketCommand(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return true;
        }

        if (args.length == 0) {
            player.sendMessage("§eUsage: §a/ticket <message>");
            return true;
        }

        String message = String.join(" ", args);
        DiscordBot bot = plugin.getDiscordBot();

        if (bot == null) {
            player.sendMessage("§cDiscord bot is not available. Please try again later.");
            return true;
        }

        player.sendMessage("§aSending your support ticket...");
        bot.sendTicketEmbed(player, message);
        return true;
    }
}

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
            player.sendMessage("§eUsage: §a/ticket create <reason> §eor §a/ticket close");
            return true;
        }

        String sub = args[0].toLowerCase();
        TicketManager tickets = plugin.getTicketManager();
        DiscordBot bot = plugin.getDiscordBot();

        if (sub.equals("close")) {
            String key = player.getUniqueId().toString();
            String channelId = tickets.getChannelForCreator(key);
            if (channelId == null) {
                // Also try discord id if linked
                String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());
                if (discordId != null) {
                    channelId = tickets.getChannelForCreator(discordId);
                }
            }
            if (channelId == null) {
                player.sendMessage("§cYou do not have an open ticket.");
                return true;
            }
            if (bot != null) {
                bot.closeTicket(channelId, player.getName() + " (in-game)");
                player.sendMessage("§aYour ticket has been closed.");
            } else {
                player.sendMessage("§cDiscord bot is not available.");
            }
            return true;
        }

        if (sub.equals("create") || args.length >= 1) {
            String reason;
            if (sub.equals("create")) {
                if (args.length < 2) {
                    player.sendMessage("§eUsage: §a/ticket create <reason>");
                    return true;
                }
                reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            } else {
                // /ticket <reason> shorthand
                reason = String.join(" ", args);
            }

            String key = player.getUniqueId().toString();
            if (tickets.hasOpenTicket(key)) {
                player.sendMessage("§cYou already have an open ticket.");
                return true;
            }

            if (bot == null) {
                player.sendMessage("§cDiscord bot is not available. Please try again later.");
                return true;
            }

            player.sendMessage("§aCreating your support ticket...");
            bot.createTicketFromMinecraft(player, reason);
            return true;
        }

        player.sendMessage("§eUsage: §a/ticket create <reason> §eor §a/ticket close");
        return true;
    }
}

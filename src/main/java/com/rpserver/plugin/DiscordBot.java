package com.rpserver.plugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class DiscordBot extends ListenerAdapter {

    private final RPServerPlugin plugin;
    private JDA jda;
    private String guildId;
    private Set<String> allowedRoleIds = new HashSet<>();
    private Set<String> staffRoleIds = new HashSet<>();

    public DiscordBot(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws Exception {
        String token = plugin.getConfig().getString("bot-token", "");
        this.guildId = plugin.getConfig().getString("guild-id", "");

        allowedRoleIds.clear();
        List<String> roles = plugin.getConfig().getStringList("allowed-role-ids");
        if (roles != null) {
            for (String r : roles) {
                if (r != null && !r.isBlank() && !r.contains("YOUR_")) {
                    allowedRoleIds.add(r.trim());
                }
            }
        }

        staffRoleIds.clear();
        List<String> staffRoles = plugin.getConfig().getStringList("staff-role-ids");
        if (staffRoles != null) {
            for (String r : staffRoles) {
                if (r != null && !r.isBlank() && !r.contains("YOUR_")) {
                    staffRoleIds.add(r.trim());
                }
            }
        }

        if (token == null || token.isBlank() || token.contains("YOUR_DISCORD")) {
            plugin.getLogger().warning("Discord bot-token is not configured. Discord features disabled.");
            return;
        }

        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this)
                .build();

        jda.awaitReady();

        Guild guild = resolveGuild();

        var commandList = List.of(
                Commands.slash("verify", "Link your Minecraft account using the code from /link")
                        .addOption(OptionType.STRING, "code", "The 4-digit code shown in Minecraft", true),
                Commands.slash("ticket", "Create or close a support ticket")
                        .addSubcommands(
                                new SubcommandData("create", "Create a support ticket")
                                        .addOption(OptionType.STRING, "reason", "Why are you opening a ticket?", true),
                                new SubcommandData("close", "Close your current support ticket")
                        ),
                Commands.slash("manual-link", "Staff: manually link a Minecraft account to a Discord user")
                        .addOption(OptionType.STRING, "minecraft", "Minecraft username", true)
                        .addOption(OptionType.USER, "discord", "Discord user to link", true)
        );

        if (guild != null) {
            guild.updateCommands().addCommands(commandList).queue();
            plugin.getLogger().info("Registered Discord slash commands in guild " + guild.getName());
        } else {
            jda.updateCommands().addCommands(commandList).queue();
            plugin.getLogger().info("Registered Discord slash commands globally.");
        }

        plugin.getLogger().info("Loaded " + allowedRoleIds.size() + " allowed role(s) and "
                + staffRoleIds.size() + " staff role(s).");
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
        }
    }

    private Guild resolveGuild() {
        if (guildId != null && !guildId.isBlank() && !guildId.contains("YOUR_")) {
            try {
                return jda.getGuildById(guildId);
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    public boolean stillHasAllowedRole(String discordId) {
        if (jda == null || discordId == null || discordId.isBlank()) return false;
        if (discordId.startsWith("admin-forced:")) return true;

        try {
            Guild guild = resolveGuild();
            if (guild == null) return false;

            Member member = guild.getMemberById(discordId);
            if (member == null) {
                User user = jda.retrieveUserById(discordId).complete();
                if (user != null) {
                    member = guild.retrieveMember(user).complete();
                }
            }
            if (member == null) return false;

            if (allowedRoleIds.isEmpty()) {
                return member.isBoosting();
            }
            for (Role role : member.getRoles()) {
                if (allowedRoleIds.contains(role.getId())) return true;
            }
            return false;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to check role for Discord ID " + discordId + ": " + e.getMessage());
            return false;
        }
    }

    private boolean isStaff(Member member) {
        if (member == null) return false;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        for (Role role : member.getRoles()) {
            if (staffRoleIds.contains(role.getId())) return true;
        }
        return false;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String name = event.getName();

        if (name.equals("verify")) {
            handleVerify(event);
        } else if (name.equals("ticket")) {
            handleTicket(event);
        } else if (name.equals("manual-link")) {
            handleManualLink(event);
        }
    }

    private void handleVerify(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        String code = event.getOption("code") != null ? event.getOption("code").getAsString().trim() : null;
        if (code == null || code.length() != 4) {
            event.getHook().sendMessage("❌ Invalid code. Please provide a 4-digit code from Minecraft `/link`.").queue();
            return;
        }

        LinkManager manager = plugin.getLinkManager();
        LinkManager.PendingCode pending = manager.getPendingCode(code);

        if (pending == null) {
            event.getHook().sendMessage("❌ Code not found or expired. Run `/link` again in Minecraft.").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null && event.getGuild() != null) {
            member = event.getGuild().retrieveMember(event.getUser()).complete();
        }
        if (member == null) {
            event.getHook().sendMessage("❌ Could not retrieve your member information.").queue();
            return;
        }

        boolean hasAllowedRole = false;
        if (!allowedRoleIds.isEmpty()) {
            for (Role role : member.getRoles()) {
                if (allowedRoleIds.contains(role.getId())) {
                    hasAllowedRole = true;
                    break;
                }
            }
        } else {
            hasAllowedRole = member.isBoosting();
        }

        if (!hasAllowedRole) {
            event.getHook().sendMessage("❌ You do not have any of the required roles to verify and play on this server.").queue();
            return;
        }

        UUID mcUuid = pending.playerUuid;
        String discordId = event.getUser().getId();

        manager.consumeCode(code);
        manager.linkAccount(mcUuid, discordId);

        event.getHook().sendMessage("✅ Successfully linked! Your Minecraft account is now verified.").queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.setRestricted(mcUuid, false);
            var player = Bukkit.getPlayer(mcUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a§lVerification successful! §aYou are now fully linked via Discord.");
            }
        });

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("Account Linked",
                    "**Minecraft UUID:** " + mcUuid + "\n**Discord:** <@" + discordId + ">");
        }
    }

    private void handleTicket(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) return;

        if (sub.equals("create")) {
            event.deferReply(true).queue();
            String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason provided";
            String creatorKey = event.getUser().getId();

            TicketManager tickets = plugin.getTicketManager();
            if (tickets.hasOpenTicket(creatorKey)) {
                event.getHook().sendMessage("❌ You already have an open ticket.").queue();
                return;
            }

            Guild guild = event.getGuild();
            if (guild == null) {
                event.getHook().sendMessage("❌ This command must be used in a server.").queue();
                return;
            }

            createTicketChannel(guild, event.getUser(), creatorKey, event.getUser().getName(), reason, false)
                    .thenAccept(channel -> {
                        if (channel != null) {
                            event.getHook().sendMessage("✅ Ticket created: " + channel.getAsMention()).queue();
                        } else {
                            event.getHook().sendMessage("❌ Failed to create ticket channel.").queue();
                        }
                    });
        } else if (sub.equals("close")) {
            event.deferReply(true).queue();
            String creatorKey = event.getUser().getId();
            TicketManager tickets = plugin.getTicketManager();
            String channelId = tickets.getChannelForCreator(creatorKey);

            // Staff can close any ticket if used inside the ticket channel
            if (channelId == null && event.getChannel() != null) {
                TicketManager.TicketInfo info = tickets.getTicket(event.getChannel().getId());
                if (info != null && isStaff(event.getMember())) {
                    channelId = event.getChannel().getId();
                }
            }

            if (channelId == null) {
                event.getHook().sendMessage("❌ You do not have an open ticket.").queue();
                return;
            }

            closeTicket(channelId, event.getUser().getName());
            event.getHook().sendMessage("✅ Ticket closed.").queue();
        }
    }

    private void handleManualLink(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();

        if (!isStaff(event.getMember())) {
            event.getHook().sendMessage("❌ You need a staff role to use this command.").queue();
            return;
        }

        String mcName = event.getOption("minecraft") != null ? event.getOption("minecraft").getAsString().trim() : null;
        User discordUser = event.getOption("discord") != null ? event.getOption("discord").getAsUser() : null;

        if (mcName == null || discordUser == null) {
            event.getHook().sendMessage("❌ Missing arguments.").queue();
            return;
        }

        LinkManager manager = plugin.getLinkManager();
        UUID mcUuid = manager.findMinecraftUuidByName(mcName);
        if (mcUuid == null) {
            // Try online exact
            Player online = Bukkit.getPlayerExact(mcName);
            if (online != null) mcUuid = online.getUniqueId();
        }

        if (mcUuid == null) {
            event.getHook().sendMessage("❌ Could not find Minecraft player `" + mcName + "`. They may need to have joined the server at least once.").queue();
            return;
        }

        final UUID finalUuid = mcUuid;
        manager.linkAccount(finalUuid, discordUser.getId());

        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.setRestricted(finalUuid, false);
            Player p = Bukkit.getPlayer(finalUuid);
            if (p != null && p.isOnline()) {
                p.sendMessage("§aYour account was manually linked by staff. You can now play freely.");
            }
        });

        event.getHook().sendMessage("✅ Linked **" + mcName + "** to " + discordUser.getAsMention() + ".").queue();

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("Manual Link (Discord)",
                    "**Staff:** " + event.getUser().getAsTag()
                            + "\n**Minecraft:** " + mcName + " (`" + finalUuid + "`)"
                            + "\n**Discord:** " + discordUser.getAsMention());
        }
    }

    public void createTicketFromMinecraft(Player player, String reason) {
        if (jda == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cDiscord bot is not available."));
            return;
        }

        Guild guild = resolveGuild();
        if (guild == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cDiscord guild is not configured."));
            return;
        }

        String key = player.getUniqueId().toString();
        createTicketChannel(guild, null, key, player.getName(), reason, true)
                .thenAccept(channel -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (channel != null) {
                            player.sendMessage("§aSupport ticket created! Staff have been notified on Discord.");
                        } else {
                            player.sendMessage("§cFailed to create ticket. Please try again or contact staff.");
                        }
                    });
                });
    }

    private CompletableFuture<TextChannel> createTicketChannel(Guild guild, User discordUser,
                                                               String creatorKey, String creatorName,
                                                               String reason, boolean fromMinecraft) {
        CompletableFuture<TextChannel> future = new CompletableFuture<>();

        String channelName = ("ticket-" + creatorName).toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-");
        if (channelName.length() > 90) channelName = channelName.substring(0, 90);

        var action = guild.createTextChannel(channelName);

        String categoryId = plugin.getConfig().getString("ticket-category-id", "");
        if (categoryId != null && !categoryId.isBlank()) {
            Category cat = guild.getCategoryById(categoryId);
            if (cat != null) action = action.setParent(cat);
        }

        // Deny @everyone, allow staff roles + creator
        action = action.addPermissionOverride(guild.getPublicRole(),
                Collections.emptyList(),
                EnumSet.of(Permission.VIEW_CHANNEL));

        for (String roleId : staffRoleIds) {
            Role role = guild.getRoleById(roleId);
            if (role != null) {
                action = action.addPermissionOverride(role,
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                        Collections.emptyList());
            }
        }

        if (discordUser != null) {
            Member member = guild.getMember(discordUser);
            if (member != null) {
                action = action.addPermissionOverride(member,
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                        Collections.emptyList());
            }
        }

        String finalChannelName = channelName;
        action.queue(channel -> {
            plugin.getTicketManager().registerTicket(
                    channel.getId(), creatorKey, creatorName, reason, fromMinecraft);

            String source = fromMinecraft ? "Minecraft" : "Discord";
            channel.sendMessage("**Support Ticket**\n"
                    + "**Created by:** " + creatorName + " (" + source + ")\n"
                    + "**Reason:** " + reason + "\n\n"
                    + "Staff will assist you shortly. Use `/ticket close` to close this ticket.").queue();

            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Ticket Created",
                        "**Creator:** " + creatorName
                                + "\n**Source:** " + source
                                + "\n**Reason:** " + reason
                                + "\n**Channel:** " + channel.getAsMention());
            }

            future.complete(channel);
        }, err -> {
            plugin.getLogger().warning("Failed to create ticket channel: " + err.getMessage());
            future.complete(null);
        });

        return future;
    }

    public void closeTicket(String channelId, String closedBy) {
        TicketManager.TicketInfo info = plugin.getTicketManager().closeTicket(channelId);
        if (info == null) return;

        if (jda == null) return;
        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        // Build a simple transcript from recent messages
        channel.getHistory().retrievePast(50).queue(messages -> {
            StringBuilder transcript = new StringBuilder();
            transcript.append("Ticket Transcript – ").append(info.creatorName).append("\n");
            transcript.append("Reason: ").append(info.reason).append("\n");
            transcript.append("Closed by: ").append(closedBy).append("\n\n");

            // Reverse to chronological
            List<net.dv8tion.jda.api.entities.Message> reversed = new ArrayList<>(messages);
            Collections.reverse(reversed);
            for (var msg : reversed) {
                transcript.append("[").append(msg.getTimeCreated()).append("] ")
                        .append(msg.getAuthor().getName()).append(": ")
                        .append(msg.getContentDisplay()).append("\n");
            }

            String transcriptChannelId = plugin.getConfig().getString("ticket-transcript-channel-id", "");
            if (transcriptChannelId != null && !transcriptChannelId.isBlank()) {
                TextChannel logChannel = jda.getTextChannelById(transcriptChannelId);
                if (logChannel != null) {
                    String content = transcript.toString();
                    if (content.length() > 1900) content = content.substring(0, 1900) + "\n...(truncated)";
                    logChannel.sendMessage("```\n" + content + "\n```").queue();
                }
            }

            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Ticket Closed",
                        "**Creator:** " + info.creatorName
                                + "\n**Closed by:** " + closedBy
                                + "\n**Reason was:** " + info.reason);
            }

            channel.sendMessage("Ticket closed by **" + closedBy + "**. Channel will be deleted in 5 seconds.").queue();
            channel.delete().queueAfter(5, java.util.concurrent.TimeUnit.SECONDS);
        }, err -> {
            channel.delete().queue();
        });
    }
}

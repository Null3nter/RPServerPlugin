package com.rpserver.plugin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DiscordBot extends ListenerAdapter {

    public static final String BTN_TICKET_PUBLIC = "rps_ticket_public";
    public static final String BTN_TICKET_SMP = "rps_ticket_smp";
    public static final String BTN_TICKET_CLOSE = "rps_ticket_close";

    private final RPServerPlugin plugin;
    private JDA jda;
    private String guildId;
    private final Set<String> allowedRoleIds = new HashSet<>();
    private final Set<String> staffRoleIds = new HashSet<>();

    public DiscordBot(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws Exception {
        String token = plugin.getConfig().getString("bot-token", "");
        this.guildId = plugin.getConfig().getString("guild-id", "");

        allowedRoleIds.clear();
        for (String r : plugin.getConfig().getStringList("allowed-role-ids")) {
            if (r != null && !r.isBlank() && !r.contains("YOUR_")) allowedRoleIds.add(r.trim());
        }
        staffRoleIds.clear();
        for (String r : plugin.getConfig().getStringList("staff-role-ids")) {
            if (r != null && !r.isBlank() && !r.contains("YOUR_")) staffRoleIds.add(r.trim());
        }

        if (token == null || token.isBlank() || token.contains("YOUR_DISCORD")) {
            plugin.getLogger().warning("Discord bot-token is not configured. Discord features disabled.");
            return;
        }

        jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .addEventListeners(this)
                .build();

        jda.awaitReady();
        registerSlashCommands();
        plugin.getLogger().info("Discord bot ready. Allowed roles: " + allowedRoleIds.size()
                + ", staff roles: " + staffRoleIds.size());
    }

    private void registerSlashCommands() {
        List<SlashCommandData> commands = List.of(
                Commands.slash("verify", "Link your Minecraft account using the code from /link")
                        .addOption(OptionType.STRING, "code", "The 4-digit code from Minecraft", true),

                Commands.slash("ticket", "Support ticket commands")
                        .addSubcommands(
                                new SubcommandData("create", "Create a support ticket")
                                        .addOption(OptionType.STRING, "type", "public or smp", true)
                                        .addOption(OptionType.STRING, "reason", "Why are you opening a ticket?", true),
                                new SubcommandData("close", "Close the current ticket channel"),
                                new SubcommandData("help", "How the dual ticket system works")
                        ),

                Commands.slash("ticket-panel", "Post a ticket panel embed with buttons (staff)")
                        .addSubcommands(
                                new SubcommandData("public", "Post the public (non-SMP) ticket panel"),
                                new SubcommandData("smp", "Post the SMP-member ticket panel"),
                                new SubcommandData("both", "Post both panels in their configured channels")
                        ),

                Commands.slash("management-owner", "Manage co-owners (Guild Owner only)")
                        .addSubcommands(
                                new SubcommandData("list", "List co-owners"),
                                new SubcommandData("add", "Add a co-owner")
                                        .addOption(OptionType.USER, "user", "Discord user", true),
                                new SubcommandData("remove", "Remove a co-owner")
                                        .addOption(OptionType.USER, "user", "Discord user", true)
                        ),

                Commands.slash("management-admin", "Manage staff roles (Owner / Co-Owners)")
                        .addSubcommands(
                                new SubcommandData("list", "List staff role IDs"),
                                new SubcommandData("add", "Add a staff role")
                                        .addOption(OptionType.ROLE, "role", "Discord role", true),
                                new SubcommandData("remove", "Remove a staff role")
                                        .addOption(OptionType.ROLE, "role", "Discord role", true)
                        ),

                Commands.slash("management-owner-admin", "Manage bot-admin roles (Owner / Co-Owners)")
                        .addSubcommands(
                                new SubcommandData("list", "List bot-admin role IDs"),
                                new SubcommandData("add", "Add a bot-admin role")
                                        .addOption(OptionType.ROLE, "role", "Discord role", true),
                                new SubcommandData("remove", "Remove a bot-admin role")
                                        .addOption(OptionType.ROLE, "role", "Discord role", true)
                        )
        );

        Guild guild = resolveGuild();
        if (guild != null) {
            guild.updateCommands().addCommands(commands).queue(
                    ok -> plugin.getLogger().info("Registered " + commands.size() + " slash commands on RP Hub: " + guild.getName()),
                    err -> plugin.getLogger().severe("Failed to register guild commands: " + err.getMessage())
            );
        } else {
            plugin.getLogger().warning("guild-id missing/invalid – registering globally (slow). Set guild-id for instant RP Hub commands.");
            jda.updateCommands().addCommands(commands).queue();
        }
    }

    public void shutdown() {
        if (jda != null) jda.shutdownNow();
    }

    private Guild resolveGuild() {
        if (jda == null) return null;
        if (guildId != null && !guildId.isBlank() && !guildId.contains("YOUR_")) {
            try {
                Guild g = jda.getGuildById(guildId.trim());
                if (g != null) return g;
                plugin.getLogger().warning("Bot is not in guild " + guildId + ". Invite it to the RP Hub.");
            } catch (Exception e) {
                plugin.getLogger().warning("Invalid guild-id: " + e.getMessage());
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
                var user = jda.retrieveUserById(discordId).complete();
                if (user != null) member = guild.retrieveMember(user).complete();
            }
            if (member == null) return false;
            if (allowedRoleIds.isEmpty()) return member.isBoosting();
            return member.getRoles().stream().anyMatch(r -> allowedRoleIds.contains(r.getId()));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean memberHasAllowedRole(Member member) {
        if (member == null) return false;
        if (allowedRoleIds.isEmpty()) return member.isBoosting();
        return member.getRoles().stream().anyMatch(r -> allowedRoleIds.contains(r.getId()));
    }

    private boolean isStaff(Member member) {
        if (member == null) return false;
        if (member.hasPermission(Permission.ADMINISTRATOR)) return true;
        if (isOwnerOrCoOwner(member.getId())) return true;
        return member.getRoles().stream().anyMatch(r -> staffRoleIds.contains(r.getId()));
    }

    private boolean isGuildOwner(String userId) {
        String ownerId = plugin.getConfig().getString("owner-id", "");
        return ownerId != null && ownerId.equals(userId);
    }

    private boolean isCoOwner(String userId) {
        return plugin.getConfig().getStringList("co-owner-ids").contains(userId);
    }

    private boolean isOwnerOrCoOwner(String userId) {
        return isGuildOwner(userId) || isCoOwner(userId);
    }

    private void saveConfigList(String key, List<String> list) {
        plugin.getConfig().set(key, list);
        plugin.saveConfig();
    }

    // ─── Interactions ─────────────────────────────────────────────

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "verify" -> handleVerify(event);
            case "ticket" -> handleTicketSlash(event);
            case "ticket-panel" -> handleTicketPanel(event);
            case "management-owner" -> handleManagementOwner(event);
            case "management-admin" -> handleManagementRoles(event, "staff-role-ids", "Staff roles");
            case "management-owner-admin" -> handleManagementRoles(event, "bot-admin-role-ids", "Bot-admin roles");
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        if (BTN_TICKET_PUBLIC.equals(id)) {
            event.deferReply(true).queue();
            openTicket(event.getGuild(), event.getMember(), event.getUser().getId(),
                    event.getUser().getName(), TicketManager.TicketType.PUBLIC, "Opened via panel")
                    .thenAccept(ch -> {
                        if (ch != null) event.getHook().sendMessage("✅ Public ticket created: " + ch.getAsMention()).queue();
                        else event.getHook().sendMessage("❌ Could not create ticket (maybe you already have one open).").queue();
                    });
        } else if (BTN_TICKET_SMP.equals(id)) {
            event.deferReply(true).queue();
            if (!memberHasAllowedRole(event.getMember())) {
                event.getHook().sendMessage("❌ This panel is for **SMP members** only (you need an allowed role).\nUse the **Public Support** panel instead.").queue();
                return;
            }
            openTicket(event.getGuild(), event.getMember(), event.getUser().getId(),
                    event.getUser().getName(), TicketManager.TicketType.SMP, "Opened via SMP panel")
                    .thenAccept(ch -> {
                        if (ch != null) event.getHook().sendMessage("✅ SMP ticket created: " + ch.getAsMention()).queue();
                        else event.getHook().sendMessage("❌ Could not create ticket (maybe you already have one open).").queue();
                    });
        } else if (BTN_TICKET_CLOSE.equals(id)) {
            event.deferReply(true).queue();
            String channelId = event.getChannel().getId();
            TicketManager.TicketInfo info = plugin.getTicketManager().getTicket(channelId);
            if (info == null) {
                event.getHook().sendMessage("❌ This is not an active ticket channel.").queue();
                return;
            }
            boolean allowed = info.creatorDiscordId.equals(event.getUser().getId()) || isStaff(event.getMember());
            if (!allowed) {
                event.getHook().sendMessage("❌ Only the ticket creator or staff can close this.").queue();
                return;
            }
            closeTicketChannel(channelId, event.getUser().getName());
            event.getHook().sendMessage("✅ Ticket closed.").queue();
        }
    }

    // ─── Ticket core ──────────────────────────────────────────────

    private void handleTicketSlash(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) return;

        if ("help".equals(sub)) {
            event.reply("**RP Hub Ticket System**\n\n"
                    + "**Two systems:**\n"
                    + "• **Public** – for non-SMP members / general support\n"
                    + "• **SMP** – for verified SMP members (requires an allowed role)\n\n"
                    + "**How to open:**\n"
                    + "• Click a button on a ticket panel, or\n"
                    + "• `/ticket create type:<public|smp> reason:<text>`\n\n"
                    + "**Close:** `/ticket close` or the **Close Ticket** button inside the channel.\n"
                    + "Each ticket gets its own private channel."
            ).setEphemeral(true).queue();
            return;
        }

        if ("close".equals(sub)) {
            event.deferReply(true).queue();
            String channelId = event.getChannel().getId();
            TicketManager.TicketInfo info = plugin.getTicketManager().getTicket(channelId);
            if (info == null) {
                // try by creator
                channelId = plugin.getTicketManager().getChannelForCreator(event.getUser().getId());
                info = channelId != null ? plugin.getTicketManager().getTicket(channelId) : null;
            }
            if (info == null) {
                event.getHook().sendMessage("❌ No open ticket found.").queue();
                return;
            }
            boolean allowed = info.creatorDiscordId.equals(event.getUser().getId()) || isStaff(event.getMember());
            if (!allowed) {
                event.getHook().sendMessage("❌ Only the ticket creator or staff can close this.").queue();
                return;
            }
            closeTicketChannel(info.channelId, event.getUser().getName());
            event.getHook().sendMessage("✅ Ticket closed.").queue();
            return;
        }

        if ("create".equals(sub)) {
            event.deferReply(true).queue();
            String typeStr = event.getOption("type") != null ? event.getOption("type").getAsString().trim().toLowerCase() : "public";
            String reason = event.getOption("reason") != null ? event.getOption("reason").getAsString() : "No reason";

            TicketManager.TicketType type;
            if (typeStr.startsWith("smp")) {
                type = TicketManager.TicketType.SMP;
                if (!memberHasAllowedRole(event.getMember())) {
                    event.getHook().sendMessage("❌ SMP tickets require an allowed (SMP) role. Use `type: public` instead.").queue();
                    return;
                }
            } else {
                type = TicketManager.TicketType.PUBLIC;
            }

            openTicket(event.getGuild(), event.getMember(), event.getUser().getId(),
                    event.getUser().getName(), type, reason)
                    .thenAccept(ch -> {
                        if (ch != null) event.getHook().sendMessage("✅ Ticket created: " + ch.getAsMention()).queue();
                        else event.getHook().sendMessage("❌ Failed – you may already have an open ticket.").queue();
                    });
        }
    }

    private void handleTicketPanel(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        if (!isStaff(event.getMember()) && !isOwnerOrCoOwner(event.getUser().getId())) {
            event.getHook().sendMessage("❌ Only staff / owners can post ticket panels.").queue();
            return;
        }

        String sub = event.getSubcommandName();
        Guild guild = event.getGuild() != null ? event.getGuild() : resolveGuild();
        if (guild == null) {
            event.getHook().sendMessage("❌ Guild not available.").queue();
            return;
        }

        int posted = 0;
        if ("public".equals(sub) || "both".equals(sub)) {
            if (postPanel(guild, TicketManager.TicketType.PUBLIC)) posted++;
        }
        if ("smp".equals(sub) || "both".equals(sub)) {
            if (postPanel(guild, TicketManager.TicketType.SMP)) posted++;
        }

        event.getHook().sendMessage(posted > 0
                ? "✅ Posted " + posted + " ticket panel(s)."
                : "❌ Could not post panels. Check `ticket-panel-channel-public` / `ticket-panel-channel-smp` in config.").queue();
    }

    private boolean postPanel(Guild guild, TicketManager.TicketType type) {
        String key = type == TicketManager.TicketType.SMP
                ? "ticket-panel-channel-smp" : "ticket-panel-channel-public";
        String channelId = plugin.getConfig().getString(key, "");
        if (channelId == null || channelId.isBlank()) return false;

        TextChannel channel = guild.getTextChannelById(channelId);
        if (channel == null) return false;

        if (type == TicketManager.TicketType.PUBLIC) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Public Support – RP Hub")
                    .setDescription("Need help but you're **not** an SMP member yet?\n\n"
                            + "Click the button below to open a **private ticket channel** with staff.\n"
                            + "Please describe your issue clearly once the channel opens.")
                    .setColor(Color.CYAN)
                    .setFooter("Non-SMP / General Support");

            channel.sendMessageEmbeds(embed.build())
                    .setActionRow(Button.primary(BTN_TICKET_PUBLIC, "Open Public Ticket"))
                    .queue();
        } else {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("SMP Member Support – RP Hub")
                    .setDescription("Verified **SMP members** can open priority support here.\n\n"
                            + "You must have an allowed SMP role to use this panel.\n"
                            + "Click below to open a private ticket with staff.")
                    .setColor(new Color(88, 101, 242))
                    .setFooter("SMP Members Only");

            channel.sendMessageEmbeds(embed.build())
                    .setActionRow(Button.success(BTN_TICKET_SMP, "Open SMP Ticket"))
                    .queue();
        }
        return true;
    }

    private CompletableFuture<TextChannel> openTicket(Guild guild, Member member, String creatorId,
                                                      String creatorName, TicketManager.TicketType type,
                                                      String reason) {
        CompletableFuture<TextChannel> future = new CompletableFuture<>();
        if (guild == null) {
            future.complete(null);
            return future;
        }

        TicketManager tickets = plugin.getTicketManager();
        if (tickets.hasOpenTicket(creatorId)) {
            future.complete(null);
            return future;
        }

        String categoryKey = type == TicketManager.TicketType.SMP
                ? "ticket-category-smp" : "ticket-category-public";
        String categoryId = plugin.getConfig().getString(categoryKey, "");

        String prefix = type == TicketManager.TicketType.SMP ? "smp" : "public";
        String channelName = (prefix + "-" + creatorName).toLowerCase()
                .replaceAll("[^a-z0-9-]", "-")
                .replaceAll("-+", "-");
        if (channelName.length() > 90) channelName = channelName.substring(0, 90);

        var action = guild.createTextChannel(channelName);

        if (categoryId != null && !categoryId.isBlank()) {
            Category cat = guild.getCategoryById(categoryId);
            if (cat != null) action = action.setParent(cat);
        }

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

        if (member != null) {
            action = action.addPermissionOverride(member,
                    EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY),
                    Collections.emptyList());
        }

        String typeLabel = type == TicketManager.TicketType.SMP ? "SMP" : "Public";
        action.queue(channel -> {
            tickets.registerTicket(channel.getId(), creatorId, creatorName, type, reason);

            EmbedBuilder welcome = new EmbedBuilder()
                    .setTitle(typeLabel + " Support Ticket")
                    .setColor(type == TicketManager.TicketType.SMP ? new Color(88, 101, 242) : Color.CYAN)
                    .addField("Opened by", creatorName + " (<@" + creatorId + ">)", true)
                    .addField("Type", typeLabel, true)
                    .addField("Reason", reason, false)
                    .setDescription("Staff will be with you shortly.\nClick **Close Ticket** when your issue is resolved.")
                    .setTimestamp(java.time.Instant.now());

            channel.sendMessageEmbeds(welcome.build())
                    .setActionRow(Button.danger(BTN_TICKET_CLOSE, "Close Ticket"))
                    .queue();

            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Ticket Opened (" + typeLabel + ")",
                        "**User:** " + creatorName + " (`" + creatorId + "`)\n**Reason:** " + reason
                                + "\n**Channel:** " + channel.getAsMention());
            }

            future.complete(channel);
        }, err -> {
            plugin.getLogger().warning("Ticket create failed: " + err.getMessage());
            future.complete(null);
        });

        return future;
    }

    public void closeTicketChannel(String channelId, String closedBy) {
        TicketManager.TicketInfo info = plugin.getTicketManager().closeTicket(channelId);
        if (info == null || jda == null) return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        channel.getHistory().retrievePast(40).queue(messages -> {
            StringBuilder transcript = new StringBuilder();
            transcript.append("Ticket Transcript – ").append(info.creatorName)
                    .append(" [").append(info.type).append("]\n");
            transcript.append("Reason: ").append(info.reason).append("\n");
            transcript.append("Closed by: ").append(closedBy).append("\n\n");

            List<net.dv8tion.jda.api.entities.Message> reversed = new ArrayList<>(messages);
            Collections.reverse(reversed);
            for (var msg : reversed) {
                transcript.append("[").append(msg.getTimeCreated()).append("] ")
                        .append(msg.getAuthor().getName()).append(": ")
                        .append(msg.getContentDisplay()).append("\n");
            }

            String transcriptChannelId = plugin.getConfig().getString("ticket-transcript-channel-id", "");
            if (transcriptChannelId != null && !transcriptChannelId.isBlank()) {
                TextChannel log = jda.getTextChannelById(transcriptChannelId);
                if (log != null) {
                    String content = transcript.toString();
                    if (content.length() > 1900) content = content.substring(0, 1900) + "\n...(truncated)";
                    log.sendMessage("```\n" + content + "\n```").queue();
                }
            }

            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Ticket Closed",
                        "**Creator:** " + info.creatorName
                                + "\n**Type:** " + info.type
                                + "\n**Closed by:** " + closedBy);
            }

            channel.sendMessage("Ticket closed by **" + closedBy + "**. Deleting in 5 seconds...").queue();
            channel.delete().queueAfter(5, TimeUnit.SECONDS);
        }, err -> channel.delete().queue());
    }

    /** In-game ticket → opens a PUBLIC Discord ticket if the player is linked, else posts a simple notice. */
    public void sendTicketEmbed(Player player, String message) {
        if (jda == null) {
            Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage("§cDiscord bot is not available."));
            return;
        }

        Guild guild = resolveGuild();
        String discordId = plugin.getLinkManager().getDiscordId(player.getUniqueId());

        if (guild != null && discordId != null && !discordId.startsWith("admin-forced:")) {
            Member member = guild.getMemberById(discordId);
            TicketManager.TicketType type = memberHasAllowedRole(member)
                    ? TicketManager.TicketType.SMP : TicketManager.TicketType.PUBLIC;

            openTicket(guild, member, discordId, player.getName(), type, message)
                    .thenAccept(ch -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (ch != null) player.sendMessage("§aTicket opened on Discord: check your DMs/server channels.");
                        else player.sendMessage("§cCould not open ticket (you may already have one open).");
                    }));
            return;
        }

        // Fallback: post embed-style notice if no linked Discord / no guild
        Bukkit.getScheduler().runTask(plugin, () ->
                player.sendMessage("§eTicket note sent. Link your Discord (`/link`) for a full private ticket channel."));

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("In-Game Ticket (unlinked)",
                    "**Player:** " + player.getName() + "\n**Message:** " + message);
        }
    }

    // ─── Verify + hierarchy (unchanged logic) ─────────────────────

    private void handleVerify(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        String code = event.getOption("code") != null ? event.getOption("code").getAsString().trim() : null;
        if (code == null || code.length() != 4) {
            event.getHook().sendMessage("❌ Invalid code.").queue();
            return;
        }

        LinkManager manager = plugin.getLinkManager();
        LinkManager.PendingCode pending = manager.getPendingCode(code);
        if (pending == null) {
            event.getHook().sendMessage("❌ Code not found or expired.").queue();
            return;
        }

        Member member = event.getMember();
        if (member == null && event.getGuild() != null) {
            member = event.getGuild().retrieveMember(event.getUser()).complete();
        }
        if (member == null) {
            event.getHook().sendMessage("❌ Could not retrieve member info.").queue();
            return;
        }

        if (!memberHasAllowedRole(member)) {
            event.getHook().sendMessage("❌ You do not have any of the required roles.").queue();
            return;
        }

        UUID mcUuid = pending.playerUuid;
        String discordId = event.getUser().getId();
        manager.consumeCode(code);
        manager.linkAccount(mcUuid, discordId);
        event.getHook().sendMessage("✅ Successfully linked!").queue();

        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.setRestricted(mcUuid, false);
            Player player = Bukkit.getPlayer(mcUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a§lVerification successful! §aYou are now fully linked via Discord.");
            }
        });

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("Account Linked",
                    "**Minecraft UUID:** " + mcUuid + "\n**Discord:** <@" + discordId + ">");
        }
    }

    private void handleManagementOwner(SlashCommandInteractionEvent event) {
        event.deferReply(true).queue();
        if (!isGuildOwner(event.getUser().getId())) {
            event.getHook().sendMessage("❌ Only the configured guild owner can use this.").queue();
            return;
        }
        String sub = event.getSubcommandName();
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("co-owner-ids"));
        if ("list".equals(sub)) {
            if (list.isEmpty()) event.getHook().sendMessage("No co-owners.").queue();
            else {
                StringBuilder sb = new StringBuilder("**Co-owners:**\n");
                for (String id : list) sb.append("- <@").append(id).append(">\n");
                event.getHook().sendMessage(sb.toString()).queue();
            }
            return;
        }
        var userOpt = event.getOption("user");
        if (userOpt == null) { event.getHook().sendMessage("❌ Missing user.").queue(); return; }
        String targetId = userOpt.getAsUser().getId();
        if ("add".equals(sub)) {
            if (list.contains(targetId)) { event.getHook().sendMessage("Already co-owner.").queue(); return; }
            list.add(targetId);
            saveConfigList("co-owner-ids", list);
            event.getHook().sendMessage("✅ Added <@" + targetId + ">.").queue();
        } else if ("remove".equals(sub)) {
            if (!list.remove(targetId)) { event.getHook().sendMessage("Not a co-owner.").queue(); return; }
            saveConfigList("co-owner-ids", list);
            event.getHook().sendMessage("✅ Removed <@" + targetId + ">.").queue();
        }
    }

    private void handleManagementRoles(SlashCommandInteractionEvent event, String configKey, String label) {
        event.deferReply(true).queue();
        if (!isOwnerOrCoOwner(event.getUser().getId())) {
            event.getHook().sendMessage("❌ Owner / co-owners only.").queue();
            return;
        }
        String sub = event.getSubcommandName();
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(configKey));
        if ("list".equals(sub)) {
            if (list.isEmpty()) event.getHook().sendMessage("None configured.").queue();
            else {
                StringBuilder sb = new StringBuilder("**" + label + ":**\n");
                for (String id : list) sb.append("- <@&").append(id).append(">\n");
                event.getHook().sendMessage(sb.toString()).queue();
            }
            return;
        }
        var roleOpt = event.getOption("role");
        if (roleOpt == null) { event.getHook().sendMessage("❌ Missing role.").queue(); return; }
        String roleId = roleOpt.getAsRole().getId();
        if ("add".equals(sub)) {
            if (list.contains(roleId)) { event.getHook().sendMessage("Already listed.").queue(); return; }
            list.add(roleId);
            saveConfigList(configKey, list);
            event.getHook().sendMessage("✅ Added <@&" + roleId + ">.").queue();
        } else if ("remove".equals(sub)) {
            if (!list.remove(roleId)) { event.getHook().sendMessage("Not listed.").queue(); return; }
            saveConfigList(configKey, list);
            event.getHook().sendMessage("✅ Removed <@&" + roleId + ">.").queue();
        }
    }
}

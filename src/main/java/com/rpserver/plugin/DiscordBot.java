package com.rpserver.plugin;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.*;

public class DiscordBot extends ListenerAdapter {

    private final RPServerPlugin plugin;
    private JDA jda;
    private String guildId;
    private Set<String> allowedRoleIds = new HashSet<>();

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
        plugin.getLogger().info("Loaded " + allowedRoleIds.size() + " allowed role(s).");
    }

    private void registerSlashCommands() {
        List<SlashCommandData> commands = List.of(
                Commands.slash("verify", "Link your Minecraft account using the code from /link")
                        .addOption(OptionType.STRING, "code", "The 4-digit code from Minecraft", true),

                Commands.slash("ticket", "Open a support ticket with the RP staff team")
                        .addSubcommands(
                                new SubcommandData("create", "Create a support ticket")
                                        .addOption(OptionType.STRING, "message", "Describe your issue", true),
                                new SubcommandData("help", "How the ticket system works")
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
            // Guild commands appear instantly in the RP hub server
            guild.updateCommands().addCommands(commands).queue(
                    success -> plugin.getLogger().info("Registered " + commands.size()
                            + " slash commands on guild: " + guild.getName() + " (" + guild.getId() + ")"),
                    error -> plugin.getLogger().severe("Failed to register guild commands: " + error.getMessage())
            );
        } else {
            plugin.getLogger().warning("guild-id is missing or invalid – registering commands GLOBALLY (can take up to 1 hour to appear).");
            plugin.getLogger().warning("Set a valid guild-id in config.yml so commands show up immediately on your RP hub.");
            jda.updateCommands().addCommands(commands).queue(
                    success -> plugin.getLogger().info("Registered slash commands globally."),
                    error -> plugin.getLogger().severe("Failed to register global commands: " + error.getMessage())
            );
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
                plugin.getLogger().warning("Bot is online but not in guild " + guildId + ". Invite the bot to your RP hub server.");
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
            for (Role role : member.getRoles()) {
                if (allowedRoleIds.contains(role.getId())) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
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

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "verify" -> handleVerify(event);
            case "ticket" -> handleTicket(event);
            case "management-owner" -> handleManagementOwner(event);
            case "management-admin" -> handleManagementRoles(event, "staff-role-ids", "Staff roles");
            case "management-owner-admin" -> handleManagementRoles(event, "bot-admin-role-ids", "Bot-admin roles");
        }
    }

    private void handleTicket(SlashCommandInteractionEvent event) {
        String sub = event.getSubcommandName();
        if (sub == null) return;

        if ("help".equals(sub)) {
            event.reply("**Support Tickets (RP Hub)**\n\n"
                    + "• `/ticket create <message>` – Open a ticket from Discord\n"
                    + "• In Minecraft: `/ticket <message>`\n\n"
                    + "Tickets are posted to the staff ticket channel for the team to handle."
            ).setEphemeral(true).queue();
            return;
        }

        if ("create".equals(sub)) {
            event.deferReply(true).queue();
            String message = event.getOption("message") != null
                    ? event.getOption("message").getAsString()
                    : "No message provided";

            String channelId = plugin.getConfig().getString("ticket-channel-id", "");
            if (channelId == null || channelId.isBlank() || channelId.contains("DISCORD_")) {
                event.getHook().sendMessage("❌ Ticket channel is not configured on the server.").queue();
                return;
            }

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                event.getHook().sendMessage("❌ Could not find the ticket channel. Check `ticket-channel-id`.").queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Support Ticket (Discord)")
                    .setColor(Color.ORANGE)
                    .addField("Discord User", event.getUser().getAsMention() + " (`" + event.getUser().getId() + "`)", false)
                    .addField("Message", message, false)
                    .setTimestamp(java.time.Instant.now())
                    .setFooter("RP Hub Support");

            channel.sendMessageEmbeds(embed.build()).queue(
                    success -> event.getHook().sendMessage("✅ Your ticket was submitted to staff.").queue(),
                    err -> event.getHook().sendMessage("❌ Failed to post ticket: " + err.getMessage()).queue()
            );

            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Ticket Submitted (Discord)",
                        "**User:** " + event.getUser().getAsTag()
                                + " (`" + event.getUser().getId() + "`)\n**Message:** " + message);
            }
        }
    }

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
            event.getHook().sendMessage("❌ Code not found or expired. Run `/link` again in Minecraft.").queue();
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

        boolean hasRole = allowedRoleIds.isEmpty() ? member.isBoosting() :
                member.getRoles().stream().anyMatch(r -> allowedRoleIds.contains(r.getId()));

        if (!hasRole) {
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
            event.getHook().sendMessage("❌ Only the configured guild owner can use this command.").queue();
            return;
        }

        String sub = event.getSubcommandName();
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList("co-owner-ids"));

        if ("list".equals(sub)) {
            if (list.isEmpty()) {
                event.getHook().sendMessage("No co-owners configured.").queue();
            } else {
                StringBuilder sb = new StringBuilder("**Co-owners:**\n");
                for (String id : list) sb.append("- <@").append(id).append("> (`").append(id).append("`)\n");
                event.getHook().sendMessage(sb.toString()).queue();
            }
            return;
        }

        var userOpt = event.getOption("user");
        if (userOpt == null) {
            event.getHook().sendMessage("❌ Missing user.").queue();
            return;
        }
        String targetId = userOpt.getAsUser().getId();

        if ("add".equals(sub)) {
            if (list.contains(targetId)) {
                event.getHook().sendMessage("Already a co-owner.").queue();
                return;
            }
            list.add(targetId);
            saveConfigList("co-owner-ids", list);
            event.getHook().sendMessage("✅ Added <@" + targetId + "> as co-owner.").queue();
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Co-Owner Added", "**By:** " + event.getUser().getAsTag() + "\n**User:** <@" + targetId + ">");
            }
        } else if ("remove".equals(sub)) {
            if (!list.remove(targetId)) {
                event.getHook().sendMessage("Not a co-owner.").queue();
                return;
            }
            saveConfigList("co-owner-ids", list);
            event.getHook().sendMessage("✅ Removed <@" + targetId + "> from co-owners.").queue();
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log("Co-Owner Removed", "**By:** " + event.getUser().getAsTag() + "\n**User:** <@" + targetId + ">");
            }
        }
    }

    private void handleManagementRoles(SlashCommandInteractionEvent event, String configKey, String label) {
        event.deferReply(true).queue();
        if (!isOwnerOrCoOwner(event.getUser().getId())) {
            event.getHook().sendMessage("❌ Only the guild owner or co-owners can use this command.").queue();
            return;
        }

        String sub = event.getSubcommandName();
        List<String> list = new ArrayList<>(plugin.getConfig().getStringList(configKey));

        if ("list".equals(sub)) {
            if (list.isEmpty()) {
                event.getHook().sendMessage("No " + label.toLowerCase() + " configured.").queue();
            } else {
                StringBuilder sb = new StringBuilder("**" + label + ":**\n");
                for (String id : list) sb.append("- <@&").append(id).append("> (`").append(id).append("`)\n");
                event.getHook().sendMessage(sb.toString()).queue();
            }
            return;
        }

        var roleOpt = event.getOption("role");
        if (roleOpt == null) {
            event.getHook().sendMessage("❌ Missing role.").queue();
            return;
        }
        String roleId = roleOpt.getAsRole().getId();

        if ("add".equals(sub)) {
            if (list.contains(roleId)) {
                event.getHook().sendMessage("Role already in list.").queue();
                return;
            }
            list.add(roleId);
            saveConfigList(configKey, list);
            event.getHook().sendMessage("✅ Added <@&" + roleId + "> to " + label.toLowerCase() + ".").queue();
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(label + " Added", "**By:** " + event.getUser().getAsTag() + "\n**Role:** <@&" + roleId + ">");
            }
        } else if ("remove".equals(sub)) {
            if (!list.remove(roleId)) {
                event.getHook().sendMessage("Role not in list.").queue();
                return;
            }
            saveConfigList(configKey, list);
            event.getHook().sendMessage("✅ Removed <@&" + roleId + "> from " + label.toLowerCase() + ".").queue();
            if (plugin.getAuditLogger() != null) {
                plugin.getAuditLogger().log(label + " Removed", "**By:** " + event.getUser().getAsTag() + "\n**Role:** <@&" + roleId + ">");
            }
        }
    }

    /** Posts a support ticket embed from Minecraft. */
    public void sendTicketEmbed(Player player, String message) {
        if (jda == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cDiscord bot is not available."));
            return;
        }

        String channelId = plugin.getConfig().getString("ticket-channel-id", "");
        if (channelId == null || channelId.isBlank() || channelId.contains("DISCORD_")) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cTicket channel is not configured."));
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            Bukkit.getScheduler().runTask(plugin, () ->
                    player.sendMessage("§cCould not find the ticket channel."));
            return;
        }

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Support Ticket (Minecraft)")
                .setColor(Color.ORANGE)
                .addField("Player", player.getName(), true)
                .addField("UUID", player.getUniqueId().toString(), true)
                .addField("Message", message, false)
                .setTimestamp(java.time.Instant.now())
                .setFooter("RP Hub Support");

        channel.sendMessageEmbeds(embed.build()).queue(
                success -> Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§aYour support ticket has been sent to staff!")),
                err -> Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage("§cFailed to send ticket. Please try again."))
        );

        if (plugin.getAuditLogger() != null) {
            plugin.getAuditLogger().log("Ticket Submitted (Minecraft)",
                    "**Player:** " + player.getName() + " (`" + player.getUniqueId() + "`)\n**Message:** " + message);
        }
    }
}

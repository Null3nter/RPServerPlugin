package com.rpserver.plugin;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

        // Load allowed role IDs (list)
        List<String> roles = plugin.getConfig().getStringList("allowed-role-ids");
        allowedRoleIds.clear();
        if (roles != null) {
            for (String r : roles) {
                if (r != null && !r.isBlank() && !r.contains("YOUR_")) {
                    allowedRoleIds.add(r.trim());
                }
            }
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

        // Register slash command globally or per-guild
        Guild guild = null;
        if (guildId != null && !guildId.isBlank() && !guildId.contains("YOUR_")) {
            try {
                guild = jda.getGuildById(guildId);
            } catch (Exception ignored) {
            }
        }

        if (guild != null) {
            guild.updateCommands().addCommands(
                    Commands.slash("verify", "Link your Minecraft account using the code from /link")
                            .addOption(OptionType.STRING, "code", "The 4-digit code shown in Minecraft", true)
            ).queue();
            plugin.getLogger().info("Registered /verify slash command in guild " + guild.getName());
        } else {
            jda.updateCommands().addCommands(
                    Commands.slash("verify", "Link your Minecraft account using the code from /link")
                            .addOption(OptionType.STRING, "code", "The 4-digit code shown in Minecraft", true)
            ).queue();
            plugin.getLogger().info("Registered /verify slash command globally (may take up to 1 hour to appear).");
        }

        plugin.getLogger().info("Loaded " + allowedRoleIds.size() + " allowed Discord role(s) for verification.");
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("verify")) return;

        event.deferReply(true).queue(); // ephemeral

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

        // Check allowed roles
        Member member = event.getMember();
        if (member == null) {
            if (event.getGuild() != null) {
                member = event.getGuild().retrieveMember(event.getUser()).complete();
            }
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
            // Fallback: if no roles configured, accept anyone who is boosting
            hasAllowedRole = member.isBoosting();
        }

        if (!hasAllowedRole) {
            event.getHook().sendMessage("❌ You do not have any of the required roles to verify and play on this server.").queue();
            return;
        }

        // Success – link the account
        UUID mcUuid = pending.playerUuid;
        String discordId = event.getUser().getId();

        manager.consumeCode(code);
        manager.linkAccount(mcUuid, discordId);

        event.getHook().sendMessage("✅ Successfully linked! Your Minecraft account is now verified. You can return to the game.").queue();

        // Make sure restriction is lifted on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            manager.setRestricted(mcUuid, false);
            var player = Bukkit.getPlayer(mcUuid);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a§lVerification successful! §aYou are now fully linked via Discord.");
            }
        });
    }
}

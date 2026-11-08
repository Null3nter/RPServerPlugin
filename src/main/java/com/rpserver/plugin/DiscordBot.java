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

import java.util.UUID;

public class DiscordBot extends ListenerAdapter {

    private final RPServerPlugin plugin;
    private JDA jda;
    private String guildId;
    private String boosterRoleId;

    public DiscordBot(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() throws Exception {
        String token = plugin.getConfig().getString("bot-token", "");
        this.guildId = plugin.getConfig().getString("guild-id", "");
        this.boosterRoleId = plugin.getConfig().getString("booster-role-id", "");

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

        // Check booster role
        Member member = event.getMember();
        if (member == null) {
            // Try to retrieve
            if (event.getGuild() != null) {
                member = event.getGuild().retrieveMember(event.getUser()).complete();
            }
        }

        if (member == null) {
            event.getHook().sendMessage("❌ Could not retrieve your member information.").queue();
            return;
        }

        boolean hasBoosterRole = false;
        if (boosterRoleId != null && !boosterRoleId.isBlank() && !boosterRoleId.contains("YOUR_")) {
            Role role = member.getGuild().getRoleById(boosterRoleId);
            if (role != null && member.getRoles().contains(role)) {
                hasBoosterRole = true;
            }
            // Also accept nitro booster indicator if present
            if (!hasBoosterRole && member.isBoosting()) {
                hasBoosterRole = true;
            }
        } else {
            // No role configured → just check isBoosting
            hasBoosterRole = member.isBoosting();
        }

        if (!hasBoosterRole) {
            event.getHook().sendMessage("❌ You need the **Server Booster** role to verify and play on this server.").queue();
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

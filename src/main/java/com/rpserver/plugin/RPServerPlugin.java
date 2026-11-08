package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class RPServerPlugin extends JavaPlugin {

    private static RPServerPlugin instance;
    private LinkManager linkManager;
    private DiscordBot discordBot;
    private PlayerListener playerListener;

    @Override
    public void onEnable() {
        instance = this;

        // Save default config if it doesn't exist
        saveDefaultConfig();

        // Initialize managers
        this.linkManager = new LinkManager(this);
        this.linkManager.load();

        // Register command
        getCommand("link").setExecutor(new LinkCommand(this));

        // Register events
        this.playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

        // Start Discord bot (async to not block server start)
        this.discordBot = new DiscordBot(this);
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            try {
                discordBot.start();
                getLogger().info("Discord bot started successfully.");
            } catch (Exception e) {
                getLogger().severe("Failed to start Discord bot: " + e.getMessage());
                e.printStackTrace();
            }
        });

        getLogger().info("RPServerPlugin enabled!");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) {
            discordBot.shutdown();
        }
        if (linkManager != null) {
            linkManager.save();
        }
        getLogger().info("RPServerPlugin disabled.");
    }

    public static RPServerPlugin getInstance() {
        return instance;
    }

    public LinkManager getLinkManager() {
        return linkManager;
    }

    public DiscordBot getDiscordBot() {
        return discordBot;
    }

    public PlayerListener getPlayerListener() {
        return playerListener;
    }
}

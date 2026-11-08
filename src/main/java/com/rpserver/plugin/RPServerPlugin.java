package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class RPServerPlugin extends JavaPlugin {

    private static RPServerPlugin instance;
    private LinkManager linkManager;
    private DiscordBot discordBot;
    private PlayerListener playerListener;
    private AuditLogger auditLogger;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.linkManager = new LinkManager(this);
        this.linkManager.load();
        this.auditLogger = new AuditLogger(this);

        getCommand("link").setExecutor(new LinkCommand(this));
        getCommand("forceverify").setExecutor(new ForceVerifyCommand(this));
        getCommand("plusone").setExecutor(new PlusOneCommand(this));
        getCommand("staff").setExecutor(new StaffCommand(this));
        getCommand("stafflink").setExecutor(new StaffLinkCommand(this));
        getCommand("ticket").setExecutor(new TicketCommand(this));

        this.playerListener = new PlayerListener(this);
        Bukkit.getPluginManager().registerEvents(playerListener, this);

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

        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            if (discordBot != null && linkManager != null) {
                linkManager.validatePlusOnes(discordBot);
            }
        }, 20L * 60 * 2, 20L * 60 * 5);

        getLogger().info("RPServerPlugin v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        if (discordBot != null) discordBot.shutdown();
        if (linkManager != null) linkManager.save();
        getLogger().info("RPServerPlugin disabled.");
    }

    public static RPServerPlugin getInstance() { return instance; }
    public LinkManager getLinkManager() { return linkManager; }
    public DiscordBot getDiscordBot() { return discordBot; }
    public PlayerListener getPlayerListener() { return playerListener; }
    public AuditLogger getAuditLogger() { return auditLogger; }
}

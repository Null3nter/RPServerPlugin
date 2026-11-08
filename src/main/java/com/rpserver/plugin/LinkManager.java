package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager {

    private final RPServerPlugin plugin;
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>(); // code -> PendingCode
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>(); // MC UUID -> Discord ID
    private final Set<UUID> restrictedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> manualBypass = ConcurrentHashMap.newKeySet(); // lowercase username or UUID string

    private File linkedFile;
    private FileConfiguration linkedConfig;

    public LinkManager(RPServerPlugin plugin) {
        this.plugin = plugin;
        this.linkedFile = new File(plugin.getDataFolder(), "linked-accounts.yml");
    }

    public void load() {
        // Load manual bypass from config
        List<String> bypassList = plugin.getConfig().getStringList("manual-bypass-players");
        manualBypass.clear();
        for (String entry : bypassList) {
            if (entry != null && !entry.isBlank()) {
                manualBypass.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }

        // Load linked accounts
        if (!linkedFile.exists()) {
            try {
                linkedFile.getParentFile().mkdirs();
                linkedFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create linked-accounts.yml");
            }
        }
        linkedConfig = YamlConfiguration.loadConfiguration(linkedFile);
        linkedAccounts.clear();
        if (linkedConfig.isConfigurationSection("linked")) {
            for (String uuidStr : linkedConfig.getConfigurationSection("linked").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    String discordId = linkedConfig.getString("linked." + uuidStr);
                    if (discordId != null) {
                        linkedAccounts.put(uuid, discordId);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        plugin.getLogger().info("Loaded " + linkedAccounts.size() + " linked accounts and " + manualBypass.size() + " manual bypass entries.");
    }

    public void save() {
        linkedConfig = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : linkedAccounts.entrySet()) {
            linkedConfig.set("linked." + entry.getKey().toString(), entry.getValue());
        }
        try {
            linkedConfig.save(linkedFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save linked-accounts.yml: " + e.getMessage());
        }
    }

    public boolean isBypassed(Player player) {
        String uuidStr = player.getUniqueId().toString().toLowerCase(Locale.ROOT);
        String name = player.getName().toLowerCase(Locale.ROOT);
        return manualBypass.contains(uuidStr) || manualBypass.contains(name);
    }

    public boolean isLinked(UUID uuid) {
        return linkedAccounts.containsKey(uuid);
    }

    public boolean isRestricted(UUID uuid) {
        return restrictedPlayers.contains(uuid);
    }

    public void setRestricted(UUID uuid, boolean restricted) {
        if (restricted) {
            restrictedPlayers.add(uuid);
        } else {
            restrictedPlayers.remove(uuid);
        }
    }

    public String generateCode(UUID playerUuid) {
        // Clean expired codes
        long now = System.currentTimeMillis();
        pendingCodes.entrySet().removeIf(e -> e.getValue().expiresAt < now);

        // Generate unique 4-digit code
        String code;
        do {
            code = String.format("%04d", new Random().nextInt(10000));
        } while (pendingCodes.containsKey(code));

        int expirySeconds = plugin.getConfig().getInt("code-expiry-seconds", 300);
        pendingCodes.put(code, new PendingCode(playerUuid, now + (expirySeconds * 1000L)));
        return code;
    }

    public PendingCode getPendingCode(String code) {
        PendingCode pending = pendingCodes.get(code);
        if (pending == null) return null;
        if (pending.expiresAt < System.currentTimeMillis()) {
            pendingCodes.remove(code);
            return null;
        }
        return pending;
    }

    public void consumeCode(String code) {
        pendingCodes.remove(code);
    }

    public void linkAccount(UUID minecraftUuid, String discordId) {
        linkedAccounts.put(minecraftUuid, discordId);
        setRestricted(minecraftUuid, false);
        save();

        // Notify player if online
        Player player = Bukkit.getPlayer(minecraftUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§aYour account has been successfully linked to Discord! You can now play freely.");
        }
    }

    public String getDiscordId(UUID uuid) {
        return linkedAccounts.get(uuid);
    }

    public static class PendingCode {
        public final UUID playerUuid;
        public final long expiresAt;

        public PendingCode(UUID playerUuid, long expiresAt) {
            this.playerUuid = playerUuid;
            this.expiresAt = expiresAt;
        }
    }
}

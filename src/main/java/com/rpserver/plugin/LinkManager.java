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

    // Plus-one system: booster UUID -> plus-one UUID
    private final Map<UUID, UUID> plusOnes = new ConcurrentHashMap<>(); // booster -> guest
    private final Map<UUID, UUID> plusOneOf = new ConcurrentHashMap<>(); // guest -> booster (reverse lookup)

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

        // Load linked accounts + plus-ones
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

        plusOnes.clear();
        plusOneOf.clear();
        if (linkedConfig.isConfigurationSection("plusones")) {
            for (String boosterStr : linkedConfig.getConfigurationSection("plusones").getKeys(false)) {
                try {
                    UUID booster = UUID.fromString(boosterStr);
                    String guestStr = linkedConfig.getString("plusones." + boosterStr);
                    if (guestStr != null) {
                        UUID guest = UUID.fromString(guestStr);
                        plusOnes.put(booster, guest);
                        plusOneOf.put(guest, booster);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        plugin.getLogger().info("Loaded " + linkedAccounts.size() + " linked accounts, "
                + plusOnes.size() + " plus-ones and " + manualBypass.size() + " manual bypass entries.");
    }

    public void save() {
        linkedConfig = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : linkedAccounts.entrySet()) {
            linkedConfig.set("linked." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, UUID> entry : plusOnes.entrySet()) {
            linkedConfig.set("plusones." + entry.getKey().toString(), entry.getValue().toString());
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
        long now = System.currentTimeMillis();
        pendingCodes.entrySet().removeIf(e -> e.getValue().expiresAt < now);

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

        Player player = Bukkit.getPlayer(minecraftUuid);
        if (player != null && player.isOnline()) {
            player.sendMessage("§aYour account has been successfully linked to Discord! You can now play freely.");
        }
    }

    public String getDiscordId(UUID uuid) {
        return linkedAccounts.get(uuid);
    }

    // ===== Plus-One System =====

    public boolean hasPlusOne(UUID booster) {
        return plusOnes.containsKey(booster);
    }

    public UUID getPlusOne(UUID booster) {
        return plusOnes.get(booster);
    }

    public UUID getBoosterOf(UUID guest) {
        return plusOneOf.get(guest);
    }

    public boolean isPlusOne(UUID guest) {
        return plusOneOf.containsKey(guest);
    }

    /**
     * Assign a plus-one. Returns null on success, or an error message.
     */
    public String setPlusOne(UUID booster, UUID guest) {
        if (!isLinked(booster)) {
            return "You must be a verified linked player to invite a plus-one.";
        }
        if (isLinked(guest) || isBypassed(Bukkit.getOfflinePlayer(guest).getPlayer() != null
                ? Bukkit.getOfflinePlayer(guest).getPlayer() : null)) {
            // Note: isBypassed needs online player; we just check linked for safety
        }
        if (isLinked(guest)) {
            return "That player is already fully linked and does not need a plus-one slot.";
        }
        if (plusOnes.containsKey(booster)) {
            return "You already have a plus-one. Use /plusone remove first.";
        }
        if (plusOneOf.containsKey(guest)) {
            return "That player is already someone else's plus-one.";
        }
        if (booster.equals(guest)) {
            return "You cannot invite yourself.";
        }

        plusOnes.put(booster, guest);
        plusOneOf.put(guest, booster);
        save();
        return null; // success
    }

    public void removePlusOne(UUID booster) {
        UUID guest = plusOnes.remove(booster);
        if (guest != null) {
            plusOneOf.remove(guest);
            // Restrict the guest again if they are online and not otherwise allowed
            setRestricted(guest, true);
            Player guestPlayer = Bukkit.getPlayer(guest);
            if (guestPlayer != null && guestPlayer.isOnline()) {
                guestPlayer.sendMessage("§cYour plus-one access has been revoked by the booster.");
            }
            save();
        }
    }

    public void removePlusOneByGuest(UUID guest) {
        UUID booster = plusOneOf.remove(guest);
        if (booster != null) {
            plusOnes.remove(booster);
            setRestricted(guest, true);
            save();
        }
    }

    /**
     * Called periodically or on join: if a plus-one's booster is no longer valid,
     * revoke access.
     */
    public void validatePlusOnes(DiscordBot bot) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, UUID> entry : plusOnes.entrySet()) {
            UUID booster = entry.getKey();
            UUID guest = entry.getValue();

            String discordId = linkedAccounts.get(booster);
            if (discordId == null || discordId.startsWith("admin-forced:")) {
                // Booster is no longer properly linked → revoke
                toRemove.add(booster);
                continue;
            }

            // Ask DiscordBot whether this Discord user still has an allowed role
            if (bot != null && !bot.stillHasAllowedRole(discordId)) {
                toRemove.add(booster);
            }
        }

        for (UUID booster : toRemove) {
            UUID guest = plusOnes.get(booster);
            removePlusOne(booster);
            plugin.getLogger().info("Revoked plus-one of " + booster + " (guest " + guest + ") because booster no longer has an allowed role.");
        }
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

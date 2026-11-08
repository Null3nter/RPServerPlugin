package com.rpserver.plugin;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class LinkManager {

    private final RPServerPlugin plugin;
    private final Map<String, PendingCode> pendingCodes = new ConcurrentHashMap<>();
    private final Map<UUID, String> linkedAccounts = new ConcurrentHashMap<>();
    private final Set<UUID> restrictedPlayers = ConcurrentHashMap.newKeySet();
    private final Set<String> manualBypass = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> staffLinks = new ConcurrentHashMap<>(); // UUID -> Discord ID

    private final Map<UUID, UUID> plusOnes = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> plusOneOf = new ConcurrentHashMap<>();
    private final Map<UUID, Long> plusOneLastChange = new ConcurrentHashMap<>();

    private File linkedFile;
    private FileConfiguration linkedConfig;

    public LinkManager(RPServerPlugin plugin) {
        this.plugin = plugin;
        this.linkedFile = new File(plugin.getDataFolder(), "linked-accounts.yml");
    }

    public void load() {
        List<String> bypassList = plugin.getConfig().getStringList("manual-bypass-players");
        manualBypass.clear();
        for (String entry : bypassList) {
            if (entry != null && !entry.isBlank()) {
                manualBypass.add(entry.trim().toLowerCase(Locale.ROOT));
            }
        }

        staffLinks.clear();
        ConfigurationSection staffSec = plugin.getConfig().getConfigurationSection("manual-staff-link-list");
        if (staffSec != null) {
            for (String key : staffSec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String discordId = staffSec.getString(key);
                    if (discordId != null) {
                        staffLinks.put(uuid, discordId);
                        linkedAccounts.put(uuid, discordId);
                    }
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        if (!linkedFile.exists()) {
            try {
                linkedFile.getParentFile().mkdirs();
                linkedFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create linked-accounts.yml");
            }
        }
        linkedConfig = YamlConfiguration.loadConfiguration(linkedFile);

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

        plusOneLastChange.clear();
        if (linkedConfig.isConfigurationSection("plusone-cooldowns")) {
            for (String uuidStr : linkedConfig.getConfigurationSection("plusone-cooldowns").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidStr);
                    long ts = linkedConfig.getLong("plusone-cooldowns." + uuidStr);
                    plusOneLastChange.put(uuid, ts);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }

        plugin.getLogger().info("Loaded " + linkedAccounts.size() + " linked accounts, "
                + plusOnes.size() + " plus-ones, " + staffLinks.size() + " staff links, "
                + manualBypass.size() + " bypass entries.");
    }

    public void save() {
        linkedConfig = new YamlConfiguration();
        for (Map.Entry<UUID, String> entry : linkedAccounts.entrySet()) {
            linkedConfig.set("linked." + entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<UUID, UUID> entry : plusOnes.entrySet()) {
            linkedConfig.set("plusones." + entry.getKey().toString(), entry.getValue().toString());
        }
        for (Map.Entry<UUID, Long> entry : plusOneLastChange.entrySet()) {
            linkedConfig.set("plusone-cooldowns." + entry.getKey().toString(), entry.getValue());
        }
        try {
            linkedConfig.save(linkedFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save linked-accounts.yml: " + e.getMessage());
        }
    }

    public void saveBypassToConfig() {
        plugin.getConfig().set("manual-bypass-players", new ArrayList<>(manualBypass));
        plugin.saveConfig();
    }

    public void addStaffLink(UUID uuid, String discordId) {
        staffLinks.put(uuid, discordId);
        // Persist into config
        plugin.getConfig().set("manual-staff-link-list." + uuid.toString(), discordId);
        plugin.saveConfig();
        save();
    }

    public boolean isStaffLinked(UUID uuid) {
        return staffLinks.containsKey(uuid);
    }

    public boolean isBypassed(Player player) {
        if (player == null) return false;
        String uuidStr = player.getUniqueId().toString().toLowerCase(Locale.ROOT);
        String name = player.getName().toLowerCase(Locale.ROOT);
        return manualBypass.contains(uuidStr) || manualBypass.contains(name)
                || staffLinks.containsKey(player.getUniqueId());
    }

    public Set<String> getBypassList() {
        return Collections.unmodifiableSet(manualBypass);
    }

    public boolean addBypass(String entry) {
        if (entry == null || entry.isBlank()) return false;
        String key = entry.trim().toLowerCase(Locale.ROOT);
        boolean added = manualBypass.add(key);
        if (added) {
            saveBypassToConfig();
            Player p = Bukkit.getPlayerExact(entry.trim());
            if (p == null) {
                try {
                    p = Bukkit.getPlayer(UUID.fromString(entry.trim()));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (p != null) setRestricted(p.getUniqueId(), false);
        }
        return added;
    }

    public boolean removeBypass(String entry) {
        if (entry == null || entry.isBlank()) return false;
        String key = entry.trim().toLowerCase(Locale.ROOT);
        boolean removed = manualBypass.remove(key);
        if (removed) saveBypassToConfig();
        return removed;
    }

    public boolean isLinked(UUID uuid) {
        return linkedAccounts.containsKey(uuid);
    }

    public boolean isRestricted(UUID uuid) {
        return restrictedPlayers.contains(uuid);
    }

    public void setRestricted(UUID uuid, boolean restricted) {
        if (restricted) restrictedPlayers.add(uuid);
        else restrictedPlayers.remove(uuid);
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

    public UUID findMinecraftUuidByName(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (var off : Bukkit.getOfflinePlayers()) {
            if (off.getName() != null && off.getName().equalsIgnoreCase(name)) {
                return off.getUniqueId();
            }
        }
        return null;
    }

    public boolean hasPlusOne(UUID booster) { return plusOnes.containsKey(booster); }
    public UUID getPlusOne(UUID booster) { return plusOnes.get(booster); }
    public UUID getBoosterOf(UUID guest) { return plusOneOf.get(guest); }
    public boolean isPlusOne(UUID guest) { return plusOneOf.containsKey(guest); }

    public long getPlusOneCooldownRemaining(UUID booster) {
        Long last = plusOneLastChange.get(booster);
        if (last == null) return 0;
        int cooldownSec = plugin.getConfig().getInt("plusone-cooldown-seconds", 3600);
        return Math.max(0, cooldownSec - ((System.currentTimeMillis() - last) / 1000L));
    }

    private void markPlusOneChanged(UUID booster) {
        plusOneLastChange.put(booster, System.currentTimeMillis());
    }

    public String setPlusOne(UUID booster, UUID guest) {
        if (!isLinked(booster)) return "You must be a verified linked player to invite a plus-one.";
        long remaining = getPlusOneCooldownRemaining(booster);
        if (remaining > 0) return "You must wait " + formatDuration(remaining) + " before changing your plus-one again.";
        if (isLinked(guest)) return "That player is already fully linked and does not need a plus-one slot.";
        if (plusOnes.containsKey(booster)) return "You already have a plus-one. Use /plusone remove first.";
        if (plusOneOf.containsKey(guest)) return "That player is already someone else's plus-one.";
        if (booster.equals(guest)) return "You cannot invite yourself.";
        plusOnes.put(booster, guest);
        plusOneOf.put(guest, booster);
        markPlusOneChanged(booster);
        save();
        return null;
    }

    public String removePlusOne(UUID booster) {
        long remaining = getPlusOneCooldownRemaining(booster);
        if (remaining > 0) return "You must wait " + formatDuration(remaining) + " before changing your plus-one again.";
        UUID guest = plusOnes.remove(booster);
        if (guest != null) {
            plusOneOf.remove(guest);
            setRestricted(guest, true);
            Player guestPlayer = Bukkit.getPlayer(guest);
            if (guestPlayer != null && guestPlayer.isOnline()) {
                guestPlayer.sendMessage("§cYour plus-one access has been revoked by the booster.");
            }
            markPlusOneChanged(booster);
            save();
            return null;
        }
        return "You do not have a plus-one to remove.";
    }

    private String formatDuration(long seconds) {
        if (seconds < 60) return seconds + " seconds";
        long minutes = seconds / 60;
        long secs = seconds % 60;
        if (minutes < 60) {
            return minutes + " minute" + (minutes == 1 ? "" : "s")
                    + (secs > 0 ? " and " + secs + " second" + (secs == 1 ? "" : "s") : "");
        }
        long hours = minutes / 60;
        long mins = minutes % 60;
        return hours + " hour" + (hours == 1 ? "" : "s")
                + (mins > 0 ? " and " + mins + " minute" + (mins == 1 ? "" : "s") : "");
    }

    public void validatePlusOnes(DiscordBot bot) {
        List<UUID> toRemove = new ArrayList<>();
        for (UUID booster : plusOnes.keySet()) {
            String discordId = linkedAccounts.get(booster);
            if (discordId == null || discordId.startsWith("admin-forced:")) {
                toRemove.add(booster);
                continue;
            }
            if (bot != null && !bot.stillHasAllowedRole(discordId)) {
                toRemove.add(booster);
            }
        }
        for (UUID booster : toRemove) {
            UUID guest = plusOnes.remove(booster);
            if (guest != null) {
                plusOneOf.remove(guest);
                setRestricted(guest, true);
                Player guestPlayer = Bukkit.getPlayer(guest);
                if (guestPlayer != null && guestPlayer.isOnline()) {
                    guestPlayer.sendMessage("§cYour plus-one access has been revoked because the booster no longer has the required role.");
                }
            }
        }
        if (!toRemove.isEmpty()) save();
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

package com.rpserver.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

public class PlayerListener implements Listener {

    private final RPServerPlugin plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public PlayerListener(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        LinkManager manager = plugin.getLinkManager();

        // Manual bypass takes priority
        if (manager.isBypassed(player)) {
            manager.setRestricted(uuid, false);
            player.sendMessage("§aYou have a manual bypass. Enjoy the server!");
            return;
        }

        // Already linked
        if (manager.isLinked(uuid)) {
            manager.setRestricted(uuid, false);
            return;
        }

        // Restrict the player
        manager.setRestricted(uuid, true);
        String msg = plugin.getConfig().getString("restriction-message",
                "&cYou must link your Discord account to play. Use &e/link &cthen verify in Discord with the code.")
                .replace('&', '§');
        player.sendMessage(msg);

        // Keep reminding them periodically while restricted
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !manager.isRestricted(uuid)) {
                    cancel();
                    return;
                }
                player.sendMessage(msg);
            }
        }.runTaskTimer(plugin, 20L * 30, 20L * 60); // every 60s after first 30s
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            // Allow looking around but not moving position
            if (event.getFrom().getX() != event.getTo().getX()
                    || event.getFrom().getY() != event.getTo().getY()
                    || event.getFrom().getZ() != event.getTo().getZ()) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (plugin.getLinkManager().isRestricted(event.getPlayer().getUniqueId())) {
            String msg = event.getMessage().toLowerCase();
            // Allow only /link
            if (!msg.startsWith("/link")) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§cYou can only use §e/link §cwhile restricted.");
            }
        }
    }

    // Live chat synchronization to Discord webhook
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        String webhookUrl = plugin.getConfig().getString("chat-webhook-url", "");
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.contains("YOUR_DISCORD")) {
            return;
        }

        Player player = event.getPlayer();
        String content = "**" + player.getName() + "**: " + event.getMessage();

        // Escape for JSON
        content = content
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");

        String json = "{\"content\":\"" + content + "\",\"username\":\"" + player.getName() + "\"}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhookUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(5))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to send chat to Discord webhook: " + ex.getMessage());
                    return null;
                });
    }
}

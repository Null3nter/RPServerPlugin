package com.rpserver.plugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;

public class AuditLogger {

    private final RPServerPlugin plugin;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public AuditLogger(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    public void log(String title, String description) {
        String webhook = plugin.getConfig().getString("audit-log-webhook-url", "");
        if (webhook == null || webhook.isBlank() || webhook.contains("YOUR_")) {
            return;
        }

        String safeTitle = escape(title);
        String safeDesc = escape(description);
        String timestamp = Instant.now().toString();

        String json = "{"
                + "\"embeds\":[{"
                + "\"title\":\"" + safeTitle + "\","
                + "\"description\":\"" + safeDesc + "\","
                + "\"color\":5793266,"
                + "\"timestamp\":\"" + timestamp + "\""
                + "}]"
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(5))
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    plugin.getLogger().warning("Failed to send audit log: " + ex.getMessage());
                    return null;
                });
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "");
    }
}

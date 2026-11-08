package com.rpserver.plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TicketManager {

    private final RPServerPlugin plugin;

    // channelId -> TicketInfo
    private final Map<String, TicketInfo> openTickets = new ConcurrentHashMap<>();
    // creator key (discord id or mc uuid string) -> channelId
    private final Map<String, String> creatorToChannel = new ConcurrentHashMap<>();

    public TicketManager(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasOpenTicket(String creatorKey) {
        return creatorToChannel.containsKey(creatorKey);
    }

    public String getChannelForCreator(String creatorKey) {
        return creatorToChannel.get(creatorKey);
    }

    public TicketInfo getTicket(String channelId) {
        return openTickets.get(channelId);
    }

    public void registerTicket(String channelId, String creatorKey, String creatorName, String reason, boolean fromMinecraft) {
        TicketInfo info = new TicketInfo(channelId, creatorKey, creatorName, reason, fromMinecraft, System.currentTimeMillis());
        openTickets.put(channelId, info);
        creatorToChannel.put(creatorKey, channelId);
    }

    public TicketInfo closeTicket(String channelId) {
        TicketInfo info = openTickets.remove(channelId);
        if (info != null) {
            creatorToChannel.remove(info.creatorKey);
        }
        return info;
    }

    public Collection<TicketInfo> getOpenTickets() {
        return Collections.unmodifiableCollection(openTickets.values());
    }

    public static class TicketInfo {
        public final String channelId;
        public final String creatorKey;
        public final String creatorName;
        public final String reason;
        public final boolean fromMinecraft;
        public final long createdAt;

        public TicketInfo(String channelId, String creatorKey, String creatorName, String reason, boolean fromMinecraft, long createdAt) {
            this.channelId = channelId;
            this.creatorKey = creatorKey;
            this.creatorName = creatorName;
            this.reason = reason;
            this.fromMinecraft = fromMinecraft;
            this.createdAt = createdAt;
        }
    }
}

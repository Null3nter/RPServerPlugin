package com.rpserver.plugin;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class TicketManager {

    public enum TicketType {
        PUBLIC,  // non-SMP members
        SMP      // verified / allowed-role members
    }

    private final RPServerPlugin plugin;

    // channelId -> TicketInfo
    private final Map<String, TicketInfo> openTickets = new ConcurrentHashMap<>();
    // creator Discord ID -> channelId
    private final Map<String, String> creatorToChannel = new ConcurrentHashMap<>();

    public TicketManager(RPServerPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasOpenTicket(String creatorDiscordId) {
        return creatorToChannel.containsKey(creatorDiscordId);
    }

    public String getChannelForCreator(String creatorDiscordId) {
        return creatorToChannel.get(creatorDiscordId);
    }

    public TicketInfo getTicket(String channelId) {
        return openTickets.get(channelId);
    }

    public void registerTicket(String channelId, String creatorDiscordId, String creatorName,
                               TicketType type, String reason) {
        TicketInfo info = new TicketInfo(channelId, creatorDiscordId, creatorName, type, reason, System.currentTimeMillis());
        openTickets.put(channelId, info);
        creatorToChannel.put(creatorDiscordId, channelId);
    }

    public TicketInfo closeTicket(String channelId) {
        TicketInfo info = openTickets.remove(channelId);
        if (info != null) {
            creatorToChannel.remove(info.creatorDiscordId);
        }
        return info;
    }

    public Collection<TicketInfo> getOpenTickets() {
        return Collections.unmodifiableCollection(openTickets.values());
    }

    public static class TicketInfo {
        public final String channelId;
        public final String creatorDiscordId;
        public final String creatorName;
        public final TicketType type;
        public final String reason;
        public final long createdAt;

        public TicketInfo(String channelId, String creatorDiscordId, String creatorName,
                          TicketType type, String reason, long createdAt) {
            this.channelId = channelId;
            this.creatorDiscordId = creatorDiscordId;
            this.creatorName = creatorName;
            this.type = type;
            this.reason = reason;
            this.createdAt = createdAt;
        }
    }
}

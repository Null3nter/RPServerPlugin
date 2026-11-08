# RPServerPlugin – Full Feature & Changelog Log

---

## Current Version: 1.3.0

### Core Features

1. **Discord Account Linking** – `/link` → 4-digit code → Discord `/verify <code>` (requires an `allowed-role-ids` role)
2. **Restriction System** – Unlinked players cannot move/interact; allowed commands: `/link`, `/ticket`
3. **Manual Bypass** – `manual-bypass-players` + `/staff add|remove|list`
4. **Staff Links** – `/stafflink <player> <discord_id>` + `manual-staff-link-list`
5. **Plus-One System** – `/plusone` with cooldown and auto-revoke on role loss
6. **Live Chat Sync** – In-game chat → `chat-webhook-url`
7. **Support Tickets** – `/ticket <message>` posts a rich embed to `ticket-channel-id`
8. **Hierarchy Management (Discord)**
   - `/management-owner` – Guild Owner only; manages `co-owner-ids`
   - `/management-admin` – Owner/Co-Owners; manages `staff-role-ids`
   - `/management-owner-admin` – Owner/Co-Owners; manages `bot-admin-role-ids`
9. **Audit Logging** – All admin actions → `audit-log-webhook-url`
10. **Self-contained JDA bot** (Maven Shade)
11. **Unique GitHub Release on every push**

### Configuration

```yaml
bot-token: "..."
guild-id: "..."
owner-id: "DISCORD_GUILD_OWNER_ID"
co-owner-ids: []
bot-admin-role-ids: []
staff-role-ids: []
allowed-role-ids: []
chat-webhook-url: "..."
ticket-channel-id: "..."
audit-log-webhook-url: "..."
manual-bypass-players: []
manual-staff-link-list: {}
code-expiry-seconds: 300
plusone-cooldown-seconds: 3600
restriction-message: "&cYou must link your Discord account to play!"
```

### Commands

| Command | Description | Access |
|---------|-------------|--------|
| `/link` | Generate verify code | Everyone |
| `/plusone [player\|remove]` | Plus-one invite | Verified |
| `/ticket <message>` | Submit support ticket | Everyone |
| `/forceverify <player>` | Force verify | `rpserver.admin` |
| `/staff add\|remove\|list` | Manage bypass list | `rpserver.admin` |
| `/stafflink <player> <discord_id>` | Staff-link account | `rpserver.admin` |
| `/verify <code>` (Discord) | Link account | Allowed role |
| `/management-owner` (Discord) | Manage co-owners | Guild Owner |
| `/management-admin` (Discord) | Manage staff roles | Owner / Co-Owner |
| `/management-owner-admin` (Discord) | Manage bot-admin roles | Owner / Co-Owner |

### Version History

#### 1.3.0
- Hierarchy management slash commands
- Ticket embeds to a single channel
- `/stafflink`, owner/co-owner/bot-admin config hierarchy
- Audit webhook for all admin actions

#### 1.2.x
- Plus-one system + cooldown
- Unique release per push

#### 1.1.0 / 1.0.0
- Multi-role allow list, force-verify, core linking & chat sync

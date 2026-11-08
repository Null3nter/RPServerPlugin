# RPServerPlugin – Full Feature & Changelog Log

This file is included in every GitHub Release so you always have a complete record of what the plugin does.

---

## Current Version: 1.3.0

### Core Features

#### 1. Discord Account Linking
- Players run `/link` in-game → receive a temporary 4-digit code
- Code expires after a configurable time (`code-expiry-seconds`, default 300s)
- In Discord they run the slash command `/verify <code>`
- The bot checks that the Discord user has **at least one** of the roles listed in `allowed-role-ids`
- On success the Minecraft UUID is permanently linked to the Discord ID and the player is unrestricted

#### 2. Allowed Role List
- Config key: `allowed-role-ids` (list of Discord role snowflake IDs)
- A player only needs **any one** of the listed roles to verify

#### 3. Restriction System
- Unlinked players are restricted on join
- Cannot move, interact, break/place blocks, drop items, or use most commands
- Allowed while restricted: `/link`, `/ticket`, `/plusone`
- Fully linked players, manual-bypass players, and valid plus-ones are unrestricted

#### 4. Manual Bypass / Staff List
- Config key: `manual-bypass-players`
- Also managed in-game with `/staff add|remove|list`
- Players on this list skip Discord verification entirely

#### 5. Admin Force-Verify
- `/forceverify <player>` (permission `rpserver.admin`)
- Instantly links and unrestricts a player without Discord

#### 6. Plus-One System
- Verified players can invite exactly one friend with `/plusone <player>`
- Cooldown after add/remove (`plusone-cooldown-seconds`, default 1 hour)
- Auto-revoked if the booster loses their allowed Discord role

#### 7. Support Ticket System (NEW in 1.3.0)
- **In-game:** `/ticket create <reason>` or `/ticket <reason>`, `/ticket close`
- **Discord:** `/ticket create <reason>`, `/ticket close`
- Creates a private Discord channel visible only to the creator and staff roles
- Optional category via `ticket-category-id`
- On close: posts a transcript to `ticket-transcript-channel-id` and deletes the channel

#### 8. Staff Management (NEW in 1.3.0)
- Config: `staff-role-ids` – Discord roles that can use staff commands
- In-game: `/staff add|remove|list <player|UUID>`
- Discord: `/manual-link <minecraft> <discord user>` – staff can link accounts on behalf of players

#### 9. Audit Logging (NEW in 1.3.0)
- Config: `audit-webhook-url`
- Logs: force-verifies, staff add/remove, account links, ticket create/close, manual links

#### 10. Live Chat Synchronization
- In-game chat forwarded to `chat-webhook-url`

#### 11. Self-Contained Discord Bot
- JDA shaded into the plugin JAR – no external bot hosting

#### 12. Automatic Builds & Releases
- Every push to `main` creates a **new unique GitHub Release** with the JAR + this changelog

---

## Configuration Reference (`config.yml`)

```yaml
bot-token: "YOUR_DISCORD_BOT_TOKEN"
guild-id: "YOUR_DISCORD_SERVER_ID"

allowed-role-ids:
  - "123456789012345678"

staff-role-ids:
  - "111222333444555666"

chat-webhook-url: "YOUR_DISCORD_WEBHOOK_URL"
audit-webhook-url: "YOUR_AUDIT_WEBHOOK_URL"

ticket-category-id: ""
ticket-transcript-channel-id: ""

manual-bypass-players: []

code-expiry-seconds: 300
plusone-cooldown-seconds: 3600

restriction-message: "&cYou must link your Discord account..."
```

---

## Commands

| Command | Description | Permission / Role |
|---------|-------------|-------------------|
| `/link` | Generate temporary Discord verification code | `rpserver.link` |
| `/plusone [player\|remove]` | Manage your one allowed friend invite | `rpserver.plusone` |
| `/ticket create\|close [reason]` | Create or close a support ticket | `rpserver.ticket` |
| `/forceverify <player>` | Admin: manually verify a player | `rpserver.admin` |
| `/staff add\|remove\|list [player]` | Admin: manage bypass / staff entries | `rpserver.admin` |
| `/verify <code>` (Discord) | Link Minecraft account | Allowed role |
| `/ticket create\|close` (Discord) | Create or close a support ticket | Everyone / Staff |
| `/manual-link <player> <user>` (Discord) | Staff: manually link accounts | Staff role |

---

## Version History

### 1.3.0
- Support ticket system (in-game + Discord)
- Private ticket channels with staff-only access
- Ticket transcripts on close
- `/staff add|remove|list` for dynamic bypass management
- Discord `/manual-link` for staff
- `staff-role-ids` configuration
- Audit webhook logging for admin and ticket actions

### 1.2.1
- Plus-one cooldown
- Unique release per push

### 1.2.0
- Plus-one system

### 1.1.0
- Multi-role allow list + `/forceverify`

### 1.0.0
- Initial release

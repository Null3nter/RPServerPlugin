# RPServerPlugin – Full Feature & Changelog Log

This file is included in every GitHub Release so you always have a complete record of what the plugin does.

---

## Current Version: 1.2.1

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
- Supports boosters, VIP, staff, or any custom roles you want

#### 3. Restriction System
- Unlinked players are restricted on join
- Cannot move (position), interact, break/place blocks, drop items, or use most commands
- Only `/link` is allowed while restricted
- Periodic reminder messages are sent while restricted
- Fully linked players, manual-bypass players, and valid plus-ones are unrestricted

#### 4. Manual Bypass List
- Config key: `manual-bypass-players`
- Accepts Minecraft UUIDs or usernames
- Players on this list skip Discord verification entirely and join with full access

#### 5. Admin Force-Verify
- Command: `/forceverify <player>` (permission `rpserver.admin`, default: op)
- Instantly links and unrestricts a player without Discord
- Records the admin who performed the action

#### 6. Plus-One System
- Verified players can invite **exactly one** friend with `/plusone <player>`
- Commands:
  - `/plusone` → show current plus-one + remaining cooldown
  - `/plusone <player>` → invite a friend
  - `/plusone remove` → revoke the invite
- The plus-one can join and play freely
- Every 5 minutes the plugin checks whether the booster still has an allowed Discord role
- If the booster loses the role, the plus-one is automatically restricted again
- **Cooldown**: After adding or removing a plus-one, the player must wait `plusone-cooldown-seconds` (default 3600 = 1 hour) before changing it again
- Automatic role-loss revocation does **not** trigger the cooldown

#### 7. Live Chat Synchronization
- Listens to in-game chat (`AsyncPlayerChatEvent`)
- Forwards messages asynchronously to the Discord webhook configured in `chat-webhook-url`
- Format: `**PlayerName**: message`

#### 8. Self-Contained Discord Bot
- Uses JDA, fully shaded into the plugin JAR via Maven Shade
- No external bot process or hosting required
- Starts automatically when the plugin enables
- Registers the `/verify` slash command in the configured guild (or globally)

#### 9. Automatic Builds & Releases
- Every push to `main` triggers a GitHub Actions build
- **A brand-new unique GitHub Release is created for every update** (tag format: `v{version}-{shortsha}`)
- Each release includes the shaded `.jar` and this full changelog
- The newest release is always marked as the latest

---

## Configuration Reference (`config.yml`)

```yaml
bot-token: "YOUR_DISCORD_BOT_TOKEN"
guild-id: "YOUR_DISCORD_SERVER_ID"

allowed-role-ids:
  - "123456789012345678"
  - "987654321098765432"

chat-webhook-url: "YOUR_DISCORD_WEBHOOK_URL"

manual-bypass-players:
  - "uuid-or-username"

code-expiry-seconds: 300
plusone-cooldown-seconds: 3600

restriction-message: "&cYou must link your Discord account..."
```

---

## Commands

| Command                    | Description                                      | Permission         |
|----------------------------|--------------------------------------------------|--------------------|
| `/link`                    | Generate temporary Discord verification code     | `rpserver.link`    |
| `/plusone [player\|remove]`| Manage your one allowed friend invite            | `rpserver.plusone` |
| `/forceverify <player>`    | Admin: manually verify a player                  | `rpserver.admin`   |
| `/verify <code>` (Discord) | Link Minecraft account (requires allowed role)   | –                  |

---

## Version History

### 1.2.1
- Added configurable cooldown after adding/removing a plus-one (`plusone-cooldown-seconds`)
- Cooldown is persisted and survives restarts
- Automatic role-loss revocation does not start the cooldown
- Full feature log included in every GitHub Release
- Every code push now creates a **new unique release** (no longer overwrites a single "latest" release)

### 1.2.0
- Full Plus-One system (`/plusone`)
- Periodic validation that the booster still has an allowed role
- Automatic restriction of plus-ones when the booster loses access

### 1.1.0
- Replaced single `booster-role-id` with a list: `allowed-role-ids`
- Added admin command `/forceverify <player>`

### 1.0.0
- Initial release
- Discord bot (JDA) running inside the plugin
- `/link` + Discord `/verify`
- Restriction system for unlinked players
- Manual bypass list
- Live chat → Discord webhook
- Maven Shade packaging

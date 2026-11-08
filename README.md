# RPServerPlugin

A complete Minecraft **Paper** plugin that runs a Discord bot **inside the plugin itself** (using JDA + Maven Shade).  
No external bot hosting required.

## Features

- **Account linking** via temporary 4-digit codes (`/link` in-game → `/verify <code>` in Discord)
- **Multiple allowed roles** – configure a list of Discord role IDs that are permitted to verify
- **Plus-One system** – verified players can invite **one friend**. The friend can play only while the booster still has an allowed Discord role
- **Admin force-verify** – `/forceverify <player>` for owners/admins to manually unlock accounts
- **Manual bypass list** in `config.yml` (UUID or username) – skips Discord entirely
- **Restriction system** – unlinked players cannot move, interact, break/place blocks, or use most commands until verified
- **Live chat sync** – in-game chat is forwarded to a Discord webhook
- Fully self-contained JAR (JDA is shaded)

## Requirements

- Paper (or compatible) 1.21+
- Java 21+
- A Discord bot token + one or more role IDs + Webhook

## Setup

1. Download the latest JAR from the **Actions** tab / Releases (or build yourself with `mvn package`).
2. Place the JAR in your server’s `plugins/` folder.
3. Start the server once so `config.yml` is generated.
4. Edit `plugins/RPServerPlugin/config.yml`:

```yaml
bot-token: "YOUR_DISCORD_BOT_TOKEN"
guild-id: "YOUR_DISCORD_SERVER_ID"

# Players need ANY of these Discord roles to /verify successfully
allowed-role-ids:
  - "123456789012345678"   # Server Booster
  - "987654321098765432"   # VIP
  - "111222333444555666"   # Staff

chat-webhook-url: "YOUR_DISCORD_WEBHOOK_URL"

manual-bypass-players:
  - "your-uuid-or-username"
```

5. Invite the bot to your Discord server with the following permissions / intents:
   - Applications Commands
   - Bot
   - Privileged Gateway Intent: **Server Members Intent**
6. Restart the server. The bot will register the `/verify` slash command.

## Commands

| Command                    | Description                                              | Permission         |
|----------------------------|----------------------------------------------------------|--------------------|
| `/link`                    | Generates a temporary 4-digit code for Discord           | `rpserver.link`    |
| `/plusone [player\|remove]`| Invite one friend or remove your current plus-one        | `rpserver.plusone` |
| `/forceverify <player>`    | Manually verify a player (admin/owner)                   | `rpserver.admin`   |
| `/verify <code>` (Discord) | Links the Minecraft account if the user has an allowed role | –                |

### Plus-One rules

- Only **verified / linked** players can use `/plusone`.
- Each verified player can have **exactly one** plus-one at a time.
- The plus-one can join and play freely.
- Every few minutes the plugin checks whether the booster still has one of the `allowed-role-ids`.  
  If they lost the role (or stopped boosting), the plus-one is automatically restricted again.

## Building

```bash
mvn clean package
```

The shaded JAR will be in `target/RPServerPlugin-1.2.0.jar`.

## License

MIT

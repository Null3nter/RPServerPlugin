# RPServerPlugin

A complete Minecraft **Paper** plugin that runs a Discord bot **inside the plugin itself** (using JDA + Maven Shade).  
No external bot hosting required.

## Features

- **Account linking** via temporary 4-digit codes (`/link` in-game → `/verify <code>` in Discord)
- **Server Booster role check** – only boosters can verify
- **Manual bypass list** in `config.yml` (UUID or username) – skips Discord entirely
- **Restriction system** – unlinked players cannot move, interact, break/place blocks, or use most commands until verified
- **Live chat sync** – in-game chat is forwarded to a Discord webhook
- Fully self-contained JAR (JDA is shaded)

## Requirements

- Paper (or compatible) 1.21+
- Java 21+
- A Discord bot token + Server Booster role + Webhook

## Setup

1. Download the latest JAR from the **Actions** tab (or build yourself with `mvn package`).
2. Place the JAR in your server’s `plugins/` folder.
3. Start the server once so `config.yml` is generated.
4. Edit `plugins/RPServerPlugin/config.yml`:

```yaml
bot-token: "YOUR_DISCORD_BOT_TOKEN"
guild-id: "YOUR_DISCORD_SERVER_ID"
booster-role-id: "YOUR_BOOSTER_ROLE_ID"
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

| Command     | Description                                      |
|-------------|--------------------------------------------------|
| `/link`     | Generates a temporary 4-digit code for Discord   |
| `/verify <code>` (Discord) | Links the Minecraft account if the user is a booster |

## Building

```bash
mvn clean package
```

The shaded JAR will be in `target/RPServerPlugin-1.0.0.jar`.

## License

MIT

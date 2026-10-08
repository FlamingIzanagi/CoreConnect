# CoreConnect

**Author:** FlamingIzanagi

CoreConnect is a pair of Minecraft tools that send a player from the proxy to the right backend server using a private client signal. The lobby never has to guess which packed world the player should join: a NeoForge client mod sends a numeric route id, and a Velocity plugin maps that id to a registered server name.

This keeps packed instances behind the proxy instead of exposing them as public entry points.

## Components

| Piece | Artifact | Platform |
| --- | --- | --- |
| Proxy plugin | `CoreConnect-Plugin-1.0.jar` | Velocity 4.1.0 or newer (Java 25) |
| Client mod | `coreconnect-client-1.0.0.jar` | NeoForge 1.21.1, client only |

Both sides talk on the fixed plugin channel `coreconnect:listening`. That id is not configurable so the mod and the plugin always match.

## How it works

1. The player connects to Velocity with the CoreConnect client mod installed.
2. The mod sends one big-endian `int` (the route id from `coreconnect-client.toml`).
3. The plugin looks that number up in `server_routes`.
4. Optional LuckPerms group checks can block the transfer.
5. Optional nLogin support can hold the destination until login or register finishes.
6. The plugin answers on the same channel so the client log can confirm the ping:
   - Immediate redirect: the same route id. Client log: `The proxy received the ping for key ID 1.`
   - Waiting for nLogin: `routeId XOR 1073741824`. Client log: `The proxy received the ping. Waiting for the login phase to finish before redirecting the player.`
7. Velocity then sends the player to the mapped server with `ConnectionRequestBuilder`.

The ping is sent once per proxy connection. Switching backends on the same connection does not send it again, so the player is not redirected in a loop.

## Proxy plugin

Drop `CoreConnect-Plugin-1.0.jar` into the Velocity `plugins` folder. On a successful start the console prints:

```
[CoreConnect] Developed by FlamingIzanagi
[CoreConnect] Enabled successfully.
```

### Command

- `/coreconnect reload` — reloads `config.yml` without restarting the proxy. Permission: `coreconnect.reload`.

### `plugins/coreconnect/config.yml`

```yaml
log_redirections: false

rank_restriction:
  enabled: false
  allowed_groups:
    - "vip"
    - "premium"
    - "owner"

respect_auth:
  enabled: false

server_routes:
  1: "lobby_general"
  2: "survival_general"
```

- `log_redirections` — when `true`, the proxy prints `Redirection ID [1] from (Steve). Redirecting to "lobby_general".`
- `rank_restriction` — optional LuckPerms filter. Groups are case-insensitive.
- `respect_auth` — when `true`, uses the nLogin Velocity API and keeps the destination in a concurrent map until login, register, premium, session, Bedrock, or authenticate events fire. Disconnects remove the UUID immediately (no timers).
- `server_routes` — map of integer id to a Velocity server name from `velocity.toml`.

LuckPerms (`luckperms`) and nLogin (`nlogin`) are optional. The plugin starts without them; those hooks only run when the matching option is enabled.

## Client mod

Install `coreconnect-client-1.0.0.jar` in the NeoForge **client** mods folder. It does not need to be on backend servers.

Client config: `config/coreconnect-client.toml`

- `routeId` — must match a key in the plugin `server_routes`.
- `silentPingLog` — `false` by default. Set to `true` to hide the send / wait / acknowledgement lines. The ping is still sent.

## Building

**Plugin (Java 25, Gradle 9+):**

```bash
gradle build
```

Output: `build/libs/CoreConnect-Plugin-1.0.jar`

**Client (Java 21, NeoForge MDG):**

```bash
cd client
gradle build
```

Output: `client/build/libs/coreconnect-client-1.0.0.jar`

## Releases

GitHub Releases publish the plugin and the client mod as separate versioned downloads, with notes for what each artifact does.

## License

All Rights Reserved. Author: FlamingIzanagi.

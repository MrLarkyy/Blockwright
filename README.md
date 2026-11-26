# Blockwright

**Playwright for Minecraft: real clients, headless, scriptable.**

Blockwright runs the real Minecraft Java client without a window and lets any system drive it: walk, look, click,
open menus, accept resource packs, take screenshots and record clips — through scenarios, an HTTP/WebSocket API
or MCP. Test your server, plugins, resource packs and networks the way a player sees them.

Status: early development.

The Minecraft client is downloaded from Mojang at runtime into your local cache; Blockwright never ships it.
Blockwright is not an official Minecraft product and is not affiliated with Mojang or Microsoft.

## Build

Gradle runs on JDK 17 or newer; the JDK 25 toolchain is found locally or downloaded automatically. Modules: `protocol`, `launcher`, `agent`,
`orchestrator`, `mcp`, `cli`, `server-runner`.

```
./gradlew build
```

Downloaded client files go to `%LOCALAPPDATA%\blockwright\cache` (Windows), `~/Library/Caches/blockwright` (macOS)
or `$XDG_CACHE_HOME/blockwright`, else `~/.cache/blockwright` (Linux); never into the repository.
`./gradlew build` makes no network request; `./gradlew :launcher:integrationTest` (opt-in) downloads a real version from Mojang.

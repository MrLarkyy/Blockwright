# AGENTS.md — rules that bind the Blockwright code

Blockwright is "Playwright for Minecraft": a standalone tool that downloads the official Minecraft client at runtime,
runs it headless with a built-in agent, and drives it through a scenario runner, an HTTP/WebSocket API and an MCP server.
It tests servers, plugins, resource packs and networks from the player's side.

## Architecture (target)

| module | what it holds |
|---|---|
| `protocol` | the control protocol between orchestrator and agent: messages, serialization (shared by both sides) |
| `launcher` | version manifest, downloads (client jar, libraries, assets, natives), cache, the patched ("baked") client jar, JVM launch profiles |
| `agent` | the in-client agent (Fabric mod, Java/Kotlin): control, observation, detectors, capture |
| `orchestrator` | client pool, scenarios, detectors' events, artefact storage, HTTP/WebSocket API |
| `mcp` | the MCP server, a thin adapter over the orchestrator API (the API is the source of truth) |
| `cli` | the `blockwright` command: `run`, `serve`, `mcp`, `up`; bundled JRE via jlink |
| `server-runner` | downloads and starts Paper test servers (offline mode) with the plugins under test |

Profiles: `logic` (no render, no assets, no sound), `snapshot` (logic + on-demand frame), `live` (continuous low-FPS
render), render worker (video from a recorded packet stream).

## Hard rules

- **No Mojang code or files in this repository or in any artefact we ship** (jar, Docker image, release). The client jar,
  libraries and assets are downloaded from Mojang at runtime into the user's cache. Our own agent and tools only.
- **Optimisations may cut rendering, audio and I/O, never game logic.** A change to client behaviour (movement
  prediction, packets sent) is a bug; the parity test (optimised vs unmodified client, same scenario) must stay green.
- **Performance budgets are tests.** Startup, PSS memory, CPU and screenshot latency per profile are measured in CI;
  a regression past the budget fails the build.
- The API and MCP are internal tools: token auth, bound to localhost by default; nothing is exposed publicly by default.
- Downloaded resource packs and server-provided URLs are untrusted: size limits, timeouts, zip-bomb protection.
- Secrets never enter the repository, logs or test output.

## Code

- Kotlin, JDK 25, Gradle Kotlin DSL multi-module; dependencies from Maven Central, the Gradle plugin portal and the
  Fabric maven only. Gradle runs non-interactively (`--console=plain`).
- One production top-level class/interface/object/enum per file, named like the file; package matches the folder.
- Small classes and functions with one responsibility. No business logic in API routes or transport handlers.
- Comments only for a non-obvious why, constraint or invariant.
- Tests: JUnit 5. Every behaviour change carries a test; a fix carries the test that would have caught it.

## Git

- Task branch `BW-<n>` from `origin/main`, worktree `../blockwright-worktrees/BW-<n>`; commit subject
  `BW-<n> <lowercase imperative>`; no AI attribution.
- Finished work is merged into `main` (`Merge BW-<n> <summary>`) and pushed; no force-push, no history rewrite.
- Before landing: the full test suite passes on the merge candidate.

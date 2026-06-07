# Tetris Pro — AGENTS.md

## Build & run

```sh
mvn compile exec:java           # compile + run TetrisPro (main entry point)
mvn compile                     # compile only
```

- `pom.xml` sets source/target to Java 8.
- Source root is `src/java/main/` (non-standard — Maven is configured accordingly).
- Main class: `TetrisPro`. Runs a single-player Swing Tetris with 4 themes, particles, and MP3 sound.
- System-scoped JAR dependency `lib/jlayer.jar` for MP3 playback (bundled). Missing JAR → music features gracefully disabled.
- Music files go in a `music/` directory (configurable at runtime).

## Entry points

| File | Class | Role |
|---|---|---|
| `src/java/main/TetrisPro.java` | `TetrisPro` | Main game, launched by `mvn exec:java` |
| `src/java/main/TetrisNetCLiet.java` | `TetrisNet` (note typo in filename) | Network client — P2P host/join or dedicated-server lobby. Run via its own `main()` |
| `src/java/main/TetrisNetServer.java` | `TetrisNetServer` | Dedicated server. Run via its own `main()`. Default port 8888, overridable via CLI arg |

## Network multiplayer

- **TetrisNet client** supports three network modes:
  1. **Host** (P2P) — listens on chosen port, waits for one direct connection
  2. **Join** (P2P) — connects to host IP:port
  3. **Server mode** — connects to `TetrisNetServer` lobby, browse/challenge players
- **TetrisNetServer**: `java TetrisNetServer [port]`. Port must be 1024–65535.
- Server supports up to 100 concurrent players, rate-limited (60 msg/s), heartbeat timeout (30s).

## Controls (both game clients)

| Key | Action |
|---|---|
| Left/Right | Move |
| Down | Soft drop |
| Up | Rotate |
| Space | Hard drop |
| C / H | Hold |
| G | Toggle ghost piece |
| Escape | Pause / menus |

## Code conventions

- Single-file classes (no inner class split across files).
- All rendering via `paintComponent(Graphics)` on a custom `JPanel`.
- All game state is in instance fields of the `JFrame` subclass.
- No tests, no CI, no lint/format tooling.
- UI strings are in Chinese.

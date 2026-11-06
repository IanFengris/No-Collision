# NoCollision

Players can't push each other in the worlds you list in `config.yml`.
Works on Paper 1.21.11 and 26.1.x. The same jar is also a Velocity plugin, which passes the setting to TAB on the proxy.

## Install
1. Build with `./gradlew build`. The jar is in `build/libs/`.
2. Put the jar into `plugins/` on **every backend server** that should use it.
3. If TAB runs on your **Velocity proxy**, put **the same jar** into the proxy's `plugins/` too.
4. List the worlds in `plugins/NoCollision/config.yml` and run `/nocollision reload`.

## How it works
| Where TAB runs | What NoCollision does |
|---|---|
| No TAB | Adds players to its own scoreboard team `nocollision` with `collisionRule: never`. |
| TAB on the backend | Uses TAB's API (`setCollisionRule`), so TAB's teams and prefixes stay intact. |
| TAB on the Velocity proxy | The backend sends the state to the proxy (channel `nocollision:sync`), and the proxy part applies it through TAB's API. |

Collision is turned back on when a player leaves a listed world or switches to a server without NoCollision.

## TAB notes
- Leave TAB's `scoreboard-teams` enabled. Collision is set through TAB's teams.
- Keep TAB's `anti-override` for teams on. Otherwise the backend's own team packets could clash with TAB's teams.
- You don't need TAB-Bridge.

## Commands / permissions
- `/nocollision reload`: reload the config and re-apply it to everyone
- `/nocollision status`: show the configured worlds, the mode, and your current state
- Permission `nocollision.admin` (default: op)

## Build checks
- `./gradlew build`: Java 21 jar for Paper 1.21.11 and 26.1.x
- `./gradlew compileJava -PpaperApi=26.1.2.build.+ -PvelocityApi=4.3.1`: checks the code against the 26.1 API (output is Java 25; run `./gradlew clean build` afterwards for the release jar)

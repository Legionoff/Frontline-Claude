# Frontline Gamemode

Fabric mod for Minecraft 26.3 (server-side; the client part is optional and only adds a rebindable key for the menu).
Tug-of-war game mode: two teams push the front line across ordered capture points.

## Build
Pushing to GitHub runs the `build` workflow; the jar is in the run's artifacts, and on `main` also in the **latest** release.
Requires Fabric Loader >= 0.20.0, Fabric API, Java 25. Put the jar in the server's `mods` folder.

## Commands (permission: gamemaster)
- `/cappoint create <name>` · `remove <name>` · `list` · `center <name>` · `move <name> earlier|later`
- `/cappoint edge <name> pos1|pos2 [x y z]` (without coordinates: your position; height counts). Names with non-ASCII letters or spaces need quotes.
- `/frontline start|stop|pause|resume|menu|swap`
- `/frontline time capture|hold <seconds>` (default 60 / 60)
- `/frontline team red|blue|none <players>` · `team auto`
- `/frontline preset save|load|delete|list <name>`

Points are ordered from the Red base (first) to the Blue base (last); the central point is where the round starts.
Red pushes toward the last point, Blue toward the first (`/frontline swap` flips it).
The menu (key K by default, rebindable in Controls, or `/frontline menu`) offers every function.
Languages: English, Russian, German, Ukrainian (chosen from the player's client language).
Data lives in `config/frontline/` (`state.json`, `presets/`).

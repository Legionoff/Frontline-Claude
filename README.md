# Frontline Gamemode

Fabric mod for Minecraft 26.3 (server-side; the client part is optional and only adds a rebindable key for the menu).
King-of-the-hill tug-of-war: two teams fight over one capture point at a time and pull the rope along ordered points.

## Build
Pushing to GitHub runs the `build` workflow; the jar is in the run's artifacts, and on `main` also in the **latest** release.
Requires Fabric Loader >= 0.20.0, Fabric API, Java 25. Put the jar in the server's `mods` folder.

## Permissions
Everything that edits the game (rounds, points, settings, assigning other players) needs operator rights (`/op`).
Everybody else can only open a small menu (key K or `/frontline menu`) and pick their own team, or use `/frontline join red|blue|leave`.

## Commands (permission: gamemaster)
- `/cappoint create <name>` · `remove <name>` · `list` · `center <name>` · `move <name> earlier|later`
- `/cappoint edge <name> pos1|pos2 [x y z]` (without coordinates: your position; height counts). Names with non-ASCII letters or spaces need quotes.
- `/frontline start|stop|pause|resume|menu|swap`
- `/frontline time capture|hold <seconds>` (default 60 / 60)
- `/frontline team red|blue|none <players>` · `team auto`
- `/frontline preset save|load|delete|list <name>`

Points are ordered from the Red base (first) to the Blue base (last); the central point is where the round starts.
Red pulls the rope toward the last point, Blue toward the first (`/frontline swap` flips it).
The menu (key K by default, rebindable in Controls, or `/frontline menu`) offers every function, including creating points, setting their corners at your position and reordering them (`/cappoint` commands still work too, plus `/cappoint rename`).
Languages: English, Russian, German, Ukrainian (chosen from the player's client language).
Data lives in `config/frontline/` (`state.json`, `presets/`).

## Moving a map from singleplayer to a server
Points, their coordinates, the central point, times and rollback settings are stored inside the world folder in `frontline_points.json`
(updated automatically on every change). Copy the whole world folder to the server (with the mod installed) and everything is there
when the server starts. If you replace the file while the server runs, use `/frontline reload`.
Teams are not copied because player IDs differ between singleplayer and a server.

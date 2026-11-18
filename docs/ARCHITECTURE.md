# Architecture and extension guide

The game keeps Java, Swing, Java2D, and the original assets. The expansion separates **content**, **gameplay state**, and **drawing**, so another level does not require changes to movement or rendering.

## 1. Where things belong

~~~text
src/
  main/       Window, input, drawing loop, and HUD
  game/       Session rules, combat, inventory, and game states
  entity/     Player, monsters, weapons, directions, and projectiles
  world/      Level definitions, loader, tiles, and runtime objects
  core/       Shared assets, audio, and camera
  render/     Terrain, object, actor, and lighting rendering
res/
  levels/     Catalog, placements, rules, and tile palette
  maps/       Rectangular grids of terrain IDs
  Player/     Existing animation frames
  objects/    Existing equipment, chest, heart, and crystal art
  tiles/      Existing terrain art
  sounds/     Existing WAV audio
  Monster/    Slime, bat, orc, and two-phase boss animations
  NPC/        Sage, merchant, and movable rock art
  Projectile/ Fireball and thrown-rock animations
  Tiles_Interactive/  Dry trees, stumps, cracked walls, and metal plates
tests/        Headless gameplay and content checks
~~~

~~~mermaid
flowchart TD
  Main["Main: create Swing window"] --> Panel["GamePanel: update and render"]
  Main --> Window["WindowMode: fullscreen and window bounds"]
  Panel --> Viewport["Viewport: scale and map pointer coordinates"]
  Input["KeyHandler: held keys and new presses"] --> Session["GameSession: game rules"]
  Panel --> Session
  Session --> Combat["CombatSystem: attacks, projectiles, rewards"]
  Combat --> Actors["Player and Enemy: movement and combat"]
  Session --> Inventory["Inventory: supplies, gear, flags"]
  Session --> Levels["Level: terrain and runtime objects"]
  Loader["LevelLoader"] --> Levels
  Data["Catalog + properties + maps"] --> Loader
  Panel --> View["World drawing + UI"]
  View -. reads .-> Session
  View --> Assets["Assets cache / camera"]
  Session --> Audio["AudioManager"]
~~~

The central rule is **updates change the world; drawing reads it**. GameSession.update(dt, input) advances gameplay. The dt argument is elapsed time in seconds. Movement, cooldowns, messages, and the run timer use elapsed time. Drawing an extra frame cannot grant an item, advance a puzzle, or make the timer faster.

The Swing timer and keyboard events run on the Swing event thread. This avoids the old pattern of one thread modifying the world while Swing paints it.

| Class | Responsibility |
| --- | --- |
| main.Main | Create the application window and start it. |
| main.GamePanel | Schedule updates; ask WorldRenderer and UI to draw. |
| main.WindowMode | Switch the Swing frame between fullscreen and its saved windowed bounds. |
| main.Viewport | Fit the logical 1024×768 view into the window and convert mouse positions back to logical coordinates. |
| main.MenuLayout | Share button and menu-row bounds between rendering and mouse input. |
| render.WorldRenderer | Draw visible terrain, objects, actors, effects, and dungeon lighting. |
| main.KeyHandler | Track held movement keys and consume single presses for actions. |
| main.UI | Display HUD, map, prompts, equipment, and menus without changing gameplay. |
| main.HudView | Draw the traveler portrait, hearts, supplies, field notes, survey map, and quick-use belt. |
| main.InventoryView | Draw the two-page field journal, equipment sockets, supplies, and quest seals. |
| main.UiTheme | Share parchment, leather, brass frames, typography, and keycaps between the views. |
| game.GameSession | Coordinate name entry, menu state, actions, pickups, doors, puzzles, travel, and combat outcomes. |
| game.CombatSystem | Advance enemy attacks and projectiles; resolve damage, parries, rewards, and defeat flags. |
| game.Inventory | Store keys, coins, potions, equipment, and progression flags. |
| game.PlayerProfile | Validate and keep the player's name separately from actor and level state. |
| game.GameState | Distinguish title/name entry, play, pause, controls, inventory, defeat, and victory. |
| entity.Player | Position, collision-aware movement, animation, health, attack timing, and hitboxes. |
| entity.Enemy | Typed monster health, patrol/chase/attack states, and damage response. |
| entity.Weapon | Select sword, axe, or pickaxe animation and behavior. |
| entity.Projectile | Time-based fireball/rock motion, collision, and rendering. |
| world.LevelDefinition | Immutable content loaded from a level's data file. |
| world.Level | Terrain and mutable objects for one area. |
| world.WorldObject | One placement plus its removed/open/activated state. |
| world.LevelLoader | Parse and validate content, including terrain, spawns, and links. |
| world.TileSet | Map explicit numeric tile IDs to textures and collision flags. |
| core.Assets | Load and cache images from the classpath. |
| core.AudioManager | Play music/effects and handle mute or unavailable audio. |
| core.Camera | Convert world positions into screen positions. |

Player and Enemy receive a collision function rather than reaching into the UI. Another actor can use the same terrain rules. Objects use composition: ObjectSpawn describes the authored placement, while WorldObject stores removed, activated, and current tile-position state. Movable rocks change their runtime position; their original spawn remains available for a reset. You do not need a subclass for every coin or key.

## 2. What happens during play

An active update follows this flow:

1. Read actions and handle pause, inventory, mute, or map toggles.
2. Move the player using elapsed time and a collision query.
3. Collect pickups touched by the player's hitbox.
4. Handle potions, selected tools, guarding, spells, and pressure-plate latches.
5. Ask CombatSystem to update monsters/projectiles, apply hits, and check defeat.
6. If E was pressed, interact with the nearest usable object.
7. Render the resulting world and UI.

Title/name entry, controls, inventory, pause, defeat, and victory stop gameplay updates. Switching away from the game pauses it.

Name entry belongs to the session rather than the player actor. Text input on the title screen is validated before a run starts; the HUD and journal read the chosen name. The profile lasts for the open application session. Keeping this separate from `entity.Player` prevents retrying a checkpoint or rebuilding actor state from changing the player's name.

The simulation and UI use a logical 1024×768 canvas. `Viewport` applies one aspect-preserving transform when painting and the inverse transform for mouse input. This keeps menu hitboxes and world coordinates consistent in fullscreen. `WindowMode` owns frame decoration and saved window bounds; it does not change game rules or the monitor's display mode. F11 and the pause-menu fullscreen option request the same window action.

Pause selection is session state, and `MenuLayout` supplies shared row bounds. Keyboard input and mouse clicks activate the same options. The controls page returns to the state it was opened from, so reading help never advances the world.

All catalogued Level instances remain in memory during a run. Returning through a portal preserves collected items, opened doors, activated beacons, and defeated enemies. Inventory is shared across levels. A new run constructs fresh runtime state from the definitions.

On defeat, the checkpoint is the entrance used to enter the current area. Retrying restores health and moves the player there; it keeps the run's discoveries. Projectiles are cleared when changing areas or retrying, so a shot cannot follow a portal. Nothing is saved to disk.

## 3. Add a level without Java changes

A level needs an ID in **res/levels/catalog.txt**, a matching **res/levels/ID.properties** file, and a terrain map. Catalog order matters: the first ID starts a new run. IDs are stable references used by portals; there is no separate id property.

Maps are rectangular grids of integer tile IDs. Every row must have the same number of columns. Width and height come from the file, so new levels need not be 50 by 50.

Coordinates are zero-based. (0,0) is the top-left tile. Columns increase to the right, rows increase downward. Each tile occupies 64 pixels at runtime.

### A complete small-room example

Save this seven-column, seven-row map as **res/maps/cave.txt**:

~~~text
1 1 1 1 1 1 1
1 6 6 6 6 6 1
1 6 6 6 6 6 1
1 6 6 6 6 6 1
1 6 6 6 6 6 1
1 6 6 6 6 6 1
1 1 1 1 1 1 1
~~~

Tile 1 is a blocking wall; tile 6 is walkable floor. Save this as **res/levels/cave.properties**:

~~~properties
name=Quiet Cave
objective=Look around the room. Return to the forest.
map=/maps/cave.txt
music=/sounds/Dungeon.wav
spawn=3|5
dark=false

object.return=PORTAL|2|5|forest:24:22|
object.note=SIGN|3|3|You are safe here. Rest before you go back.|
object.supply=POTION|4|3||
object.coin=COIN|4|2||
~~~

Append **cave** to the catalog. Add this portal at the unused, walkable forest tile (26,22), beside the starting clearing:

~~~properties
object.cave-passage=PORTAL|26|22|cave:3:5|
~~~

Build and play both directions of the connection:

~~~powershell
.\build.ps1 -Test
.\play.bat
~~~

No Java change is required. The required level properties are name, objective, map, music, and spawn. Resource paths begin with / and are relative to res. The optional dark property accepts true or false and defaults to false. Set dark=true for a dim level where the lantern expands visibility; lighting follows this metadata rather than a particular level ID.

### Data conventions

The level files use a small UTF-8 properties format: key=value, one entry per line. Comments start with # on their own line. Values are literal text; Java-style escapes and continuation lines are not supported. Avoid a vertical bar inside text because it separates fields.

~~~text
object.UNIQUE-ID=TYPE|column|row|argument|required-flags
enemy.UNIQUE-ID=column|row|health|speed|patrol-radius|KIND|defeat-flag
puzzle.UNIQUE-ID=switch-id+switch-id|solved message
~~~

Keep all five object fields, including trailing empty fields. Put objects and enemy spawns on walkable terrain. Objects cannot share a tile.

Enemy KIND and defeat-flag are optional trailing fields. The original five-field format still works. Kinds are GREEN_SLIME, RED_SLIME, BAT, ORC, and SKELETON_LORD. A nonempty defeat flag is granted when that enemy dies, allowing a boss or guardian to protect a chest through ordinary requirements.

The [content-format reference](../res/levels/README.md) contains the complete object behavior table, tile palette, and puzzle examples.

### Progression and portals

~~~properties
object.seal-chest=CHEST|5|2|cave-seal|
object.exit=PORTAL|8|5|next-area:3:4|cave-seal
~~~

A chest grants its argument as a flag; a portal can require that flag. A requirement such as forest-seal+sanctuary-seal means **both** must have been earned. A chest with the special argument victory ends the adventure.

Flags come from chests, completed puzzles, NPC lessons, latched pressure plates, and configured enemy defeats. Choose globally unique names because inventory is shared across levels. Requirements apply to interactions and touch pickups. A pickup with unmet requirements stays on the ground until its required flags have been earned.

A portal's coordinates belong to its destination map. Landings must be walkable and free of blocking objects. The loader checks links and whether referenced flags can be granted; it cannot prove that every route is logically reachable. Keep enough keys accessible and test the route.

### Sequence puzzles

The sanctuary uses three SWITCH objects linked to puzzle solar. Its sequence is beacon-east, beacon-south, beacon-west. Correct switches light up; an incorrect choice resets their lights and the sequence. Completion grants solar as a flag. A GATE requiring solar can then be opened with E.

Another sequence puzzle needs only another puzzle entry, corresponding switches, a clue, and a gate with the puzzle ID as its requirement. Surround the gate with blocking terrain so players cannot walk around it.

### Tools, NPCs, and pressure plates

AXE and PICKAXE are collected equipment. Tab cycles the collected tools; Space uses the selected one. DRY_TREE becomes a nonblocking stump after an axe strike. CRACKED_WALL disappears after a pickaxe strike. The collision rules use object state, so the renderer never decides whether an obstacle is passable.

An NPC argument has three fields separated by a tilde: `oldman~grant-flag~dialogue`. For example, `oldman~fireball~I can teach you the ember word. Press R to cast.` gives the fireball lesson once and displays the dialogue. An empty grant field makes a normal conversation. MERCHANT uses a positive coin price as its argument and sells a potion when interacted with.

A ROCK moves one grid square away from the player when E is pressed, provided that square is clear. A PLATE uses its argument as a flag name. Pushing a rock onto it permanently latches that flag, so a nearby GATE can require it. Backspace restores rocks to their authored positions without undoing latched plates. Place an explanatory sign by new puzzles and leave room to stand behind the rock.

### The shipped five-area progression

Whisperwood, Sun Temple, and Lost Vault give their seals in order. Lost Vault's chest grants vault-seal and opens the way to Ember Village. In Ember Village, the axe cuts a dry tree, the pickaxe breaks a cracked wall, and a rock is pushed north onto the plate to unlock the fourth seal. King's Tomb's South → West → East rune order unlocks the boss room. The final chest requires all four seals and the crown-defeated flag from the two-phase boss. Display names can change without changing these stable level and flag IDs.

### More terrain

Register an unused ID in **res/levels/tiles.properties**, then use it in a map:

~~~properties
50=/tiles/grass00.png|false
~~~

The fields are a classpath texture path and a true/false collision value. Do not change the meaning of an existing ID unless you intend to change all maps that use it. Older unused maps may reference IDs outside the current palette; register those IDs before activating one.

## 4. Add another feature

Change the layer that owns the behavior:

| Feature | Place to change |
| --- | --- |
| New room, enemy placement, or existing puzzle arrangement | Maps and level properties |
| New terrain texture | Tile palette |
| New pickup or interactable type | ObjectType, GameSession interaction/pickup handling, and world drawing |
| New equipment effect | ItemType, Inventory, and the actor or renderer that uses the effect |
| New enemy behavior | Actor behavior; add data fields only if authors need to configure them |
| New action | KeyHandler.Action, key bindings, GameSession, and control hints |
| New screen | GameState and UI; decide whether gameplay advances in that state |

For example, a bow needs an equipment entry, a fire action, projectile runtime objects, collision/damage rules, and drawing. A bow texture alone does not create behavior.

GameSession coordinates exploration and interactions, while CombatSystem owns the growing combat loop. It receives collision queries and a message callback rather than depending on the window or HUD. If quests or puzzles grow substantially, extract QuestSystem or PuzzleSystem with similarly explicit inputs. Keep content in data and drawing free of mutations. A larger engine or entity-component system is unnecessary until the game needs one.

The HUD derives its seal goal from catalog chest flags. Level darkness follows the optional dark property. Some presentation remains specific to the current adventure, such as the title's five-lands description. Adding optional levels works through data alone; a completely different campaign should also update its presentation.

## 5. Validate changes

Run **.\build.ps1 -Test** on Windows or **sh build.sh --test** elsewhere. The build compiles and packages the game; tests run without opening a window and produce UI renders under **build/screenshots**. Content errors identify their resource and line.

Then play the changed route. Confirm that the spawn is safe, doors and gates cannot be bypassed, required pickups are reachable, portals land safely, clues explain the challenge, and text stays readable.

A future save system should serialize stable IDs and small state values: inventory, current location, opened object IDs, defeated enemy IDs, and puzzle progress. Avoid serializing Java UI objects or images.


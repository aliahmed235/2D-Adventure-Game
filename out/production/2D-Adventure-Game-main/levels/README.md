# Authoring levels

The campaign is data driven. A level needs a text map, a UTF-8 definition file, and a line in `catalog.txt`. Level dimensions come from the map rather than a fixed world-size constant. The original `world01.txt` is still used unchanged.

The shipped route is **Whisperwood → Sun Temple → Lost Vault → Ember Village → King's Tomb**. The first four chests give seals; the final chest in King's Tomb ends the game after its boss has been beaten. These are display names: level IDs remain `forest`, `sanctuary`, `vault`, `emberwood`, and `crypt`.

## Add a level

1. Create `res/maps/my-level.txt`. Each row contains space-separated tile IDs. Every row must have the same number of columns. Coordinates start at `(0, 0)` in the top-left corner.
2. Create `res/levels/my-level.properties` using the example below.
3. Add `my-level` to `res/levels/catalog.txt`. The first ID in the catalog is the starting level.
4. Add a `PORTAL` in an existing level that targets `my-level:column:row`. Add a return portal to make it possible to leave again.
5. Build and run the game. Loading the catalog validates every level and all portal destinations, even levels the player has not entered yet.

```properties
name=My New Level
objective=Find the key. Open the chest to get the moon seal.
map=/maps/my-level.txt
music=/sounds/Dungeon.wav
spawn=3|4

object.bronze-key=KEY|5|4||
object.locked-door=DOOR|8|4||
object.moon-chest=CHEST|10|4|moon-seal|
object.return=PORTAL|2|4|forest:24:22|
object.next-level=PORTAL|12|4|vault:3:22|moon-seal
enemy.west-slime=6|7|3|65|2|GREEN_SLIME|
```

The coordinates in an example must be walkable in the map you create. The loader reports an error with the resource filename and line number if a placement is outside the map, on solid terrain, or overlaps another object.

## Definitions and placement

Files contain literal UTF-8 text. Each property occupies one line. Whole lines beginning with `#` are comments. Continuation lines, inline comments, and Java-properties escape sequences are not supported. Fields use `|`; do not place `|` in signs or messages. Property keys cannot repeat. IDs are case sensitive.

| Property | Value |
| --- | --- |
| `name` | Name shown in the game |
| `objective` | Current level's goal |
| `map` | Absolute classpath resource path |
| `music` | Absolute classpath WAV path |
| `spawn` | `column|row` |
| `dark` | Optional `true` or `false` (default `false`); enables lantern-based darkness |
| `object.ID` | `TYPE|column|row|argument|required-flags` |
| `enemy.ID` | `column|row|health|speed|patrol-radius|KIND|defeat-flag` |
| `puzzle.ID` | `switch-id+switch-id+switch-id|completion-message` |

Enemy speed is in pixels per second. Patrol radius is in tiles. Health must be at least one. Object and enemy IDs identify runtime state within their level, so use stable IDs when editing an existing level.

Enemy kinds are `GREEN_SLIME`, `RED_SLIME`, `BAT`, `ORC`, and `SKELETON_LORD`. The Skeleton Lord changes phase at half health. A nonempty defeat flag is granted when that particular enemy dies. The last two enemy fields are optional for older content: five fields default to `GREEN_SLIME` with no defeat flag; six fields specify the kind; seven fields also specify the reward flag.

```properties
enemy.crown-lord=22|11|14|82|4|SKELETON_LORD|crown-defeated
object.final-treasure=CHEST|22|5|victory|crown-defeated+moon-seal
```

Object records always have five fields, including empty fields. For example, `KEY|5|4||` has an empty argument and no requirement.

## Object behaviours

| Type | Argument | Behaviour |
| --- | --- | --- |
| `KEY` | Empty | Adds a key when collected |
| `DOOR` | Empty | Blocks the path; interaction spends one key and removes it |
| `BOOTS` | Empty | Equips the speed upgrade |
| `COIN` | Empty | Adds currency |
| `POTION` | Empty | Adds a healing potion |
| `HEART` | Empty | Restores health |
| `SWORD` | Empty | Equips the sword |
| `AXE` | Empty | Adds an axe; cycle tools with Tab and strike dry trees with Space |
| `PICKAXE` | Empty | Adds a pickaxe; cycle tools with Tab and strike cracked walls with Space |
| `SHIELD` | Empty | Equips the shield |
| `LANTERN` | Empty | Equips the lantern |
| `CHEST` | Flag ID | Opens a chest and grants the flag; reserved argument `victory` ends the campaign |
| `PORTAL` | `level-id:column:row` | Travels to a walkable destination in a catalog level |
| `SIGN` | Message text | Displays a clue or instructions |
| `SWITCH` | Puzzle ID | Submits this object's ID to a sequence puzzle |
| `GATE` | Empty | Blocks the path until its required flags are satisfied |
| `NPC` | `oldman~grant-flag~dialogue` | Displays dialogue and grants the optional flag; an empty grant uses two adjacent `~` separators |
| `MERCHANT` | Positive potion price, or empty for 8 | Interaction buys one potion with coins |
| `DRY_TREE` | Empty | Blocks movement until hit with the axe, then remains as a walkable stump |
| `CRACKED_WALL` | Empty | Blocks movement until hit with the pickaxe |
| `ROCK` | Empty | Blocks movement; E pushes it one tile away from the player |
| `PLATE` | Flag ID | Latches when a rock is pushed onto it and grants its flag permanently |

The last field lists flags separated by `+`. All listed flags must be present. Flags come from chests, solved puzzles, NPCs, plates, and enemy defeats; they are shared across the campaign. Use unique flag IDs across levels. For example, `forest-seal+sanctuary-seal` requires both seals. The loader rejects requirements that nothing in the catalog can grant.

`oldman~fireball~Your dialogue here` teaches the fire rune used with R. `oldman~~Your dialogue here` is an ordinary conversation. Dialogue cannot contain `~` or `|` because those delimit fields. The merchant uses the imported merchant sprite; rocks use the imported large-rock sprite.

## Create a tool and weight challenge

Place the axe before a dry-tree barrier, then put the pickaxe beyond it and before a cracked-wall barrier. The underlying terrain must be walkable: the mutable objects supply their own collision. Interactive artwork belongs in objects rather than the terrain palette so it can change during a run.

```properties
object.axe=AXE|5|12||
object.dry-crossing=DRY_TREE|10|12||
object.pick=PICKAXE|13|12||
object.stone-crossing=CRACKED_WALL|18|12||
object.weight=ROCK|22|14||
object.plate=PLATE|22|11|weight-lock|
object.gate=GATE|25|7||weight-lock
```

Provide enough room to stand behind the rock for every push. A plate stays latched after activation. Backspace returns rocks to their authored spawn positions if the player gets stuck; authored terrain, doors, pickups, and solved flags are retained. Never place a required tool beyond the obstacle that requires it.

## Create a sequence puzzle

```properties
object.first-rune=SWITCH|5|5|moon-lock|
object.second-rune=SWITCH|8|5|moon-lock|
object.third-rune=SWITCH|11|5|moon-lock|
object.inscription=SIGN|8|7|Light the WEST rune, then the EAST rune, then the MIDDLE rune.|
object.moon-gate=GATE|8|3||moon-lock
puzzle.moon-lock=first-rune+third-rune+second-rune|The moon gate is open.
```

Every sequence entry must refer to a unique `SWITCH` in that level, and the switch argument must match the puzzle ID. A wrong input resets that puzzle's progress. The completion flag can unlock any gate, chest, or portal that names it.

Enclose a treasure room in solid tiles and leave exactly one walkable entrance for its gate. Make every switch and clue reachable without passing through that gate. Otherwise a puzzle could be bypassed or become impossible to solve. The shipped sanctuary and vault maps follow this layout.

## Extend the tile palette

`tiles.properties` maps numeric IDs to `texture-path|solid`. The original IDs are kept compatible:

| ID | Tile | Solid |
| --- | --- | --- |
| 0 | Grass | No |
| 1 | Wall | Yes |
| 2 | Water | Yes |
| 3 | Earth | No |
| 4 | Tree | Yes |
| 5 | Road | No |
| 6 | Interior floor | No |
| 7 | Hut | Yes |
| 8 | Table | Yes |
| 9 | Alternate grass | No |

To use another existing texture, register a new ID, such as `10=/tiles/road01.png|false`, and use `10` in your map. IDs do not need to be consecutive. Changing an existing ID changes every map that uses it, so add a new ID when introducing a different tile.

The older unused `worldV2`, `worldV3`, and `interior01` resources use a different numeric palette. Register their IDs deliberately before adding those maps to this campaign.

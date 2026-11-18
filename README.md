# The Sunken Seal

A top-down Java adventure built from the original 2D Adventure project and its existing pixel art and audio.

Explore five connected maps: Whisperwood, Sun Temple, Lost Vault, Ember Village, and King's Tomb. Find four seals, learn fireball from the sage, and beat the two-phase Crownless King. Slimes, bats, and orcs use the supplied monster animations.

Ember Village has an axe, pickaxe, potion merchant, breakable paths, and a rock-and-plate puzzle. King's Tomb has a rune puzzle, the final boss, and the treasure. The original forest and the field journal are retained.

Enter your name on the title screen before starting. Names can contain up to 16 letters, digits, spaces, hyphens, apostrophes, or underscores. Your name appears in the small health panel and the journal. The name and game progress last for the current session; they are not saved after closing the game.

The smaller HUD leaves more of the map visible. Press **F11** to switch between windowed and fullscreen play. The game keeps its proportions, with borders where needed. The pause menu provides Resume, Inventory, How to play, Fullscreen, Sound, and Quit options; use the arrow keys or mouse to choose.

## Build and play

Use **JDK 17 or newer**. There are no third-party dependencies, Maven, or Gradle requirements.

Windows PowerShell:

~~~powershell
.\build.ps1 -Test
.\play.bat
~~~

To build and launch in one command:

~~~powershell
.\build.ps1 -Run
~~~

Linux or macOS:

~~~sh
sh build.sh --test
sh play.sh
~~~

The build produces **build/sunken-seal.jar** and updates **game.jar** and **dist/game.jar**. You can also run the game with:

~~~sh
java -jar game.jar
~~~

On Windows, **dist/2DAdventureGame.exe** is a small launcher for the packaged Java game. The build refreshes it when the installed Windows .NET Framework C# compiler is available; its source is **tools/WindowsLauncher.cs**. The game itself still uses Java 17. **dist/RUN_GAME.bat** and **play.bat** provide direct Java launch options if the optional executable cannot be rebuilt.

The test command also renders the title, HUD, inventory, levels, and victory screen to **build/screenshots**. These headless checks complement a normal playthrough in the game window.

In IntelliJ, mark **src** as Sources and **res** as Resources, select JDK 17+, and run **main.Main**.

## Controls

| Key | Action |
| --- | --- |
| WASD / arrow keys | Move |
| E | Use things, talk, buy, open gates, light runes, or push a rock |
| Space | Strike with the selected sword, axe, or pickaxe |
| Tab | Cycle collected tools |
| Shift | Hold to guard after collecting the shield |
| R | Cast fireball after learning it from the sage |
| Q | Drink a potion to restore two hearts |
| Backspace | Reset rocks in the current area to their original positions |
| I | Open or close equipment and supplies |
| M | Show or hide the area map |
| Escape / P | Pause or resume |
| H | Open How to play |
| F11 | Switch fullscreen on or off |
| F | Mute or unmute audio |
| Enter | Start with your name; choose a pause option; retry; start a new run after winning |

Walk over equipment, keys, coins, and potions to collect them. Boots and lanterns work automatically. The shield reduces incoming damage; holding Shift raises it to guard. Select the axe to cut dry trees and the pickaxe to break cracked walls. A heart pickup stays on the ground if your health is already full.

Push a large rock onto a metal plate to unlock its gate. Backspace puts rocks back if a push goes wrong; unlocked gates stay unlocked. The merchant sells one potion for eight coins.

On defeat, Enter returns you to the current area's entrance with restored health. Your discoveries, equipment, defeated enemies, and puzzle progress are kept. Closing the application ends the run; disk saves are not implemented.

## Build more

Read [the architecture and extension guide](docs/ARCHITECTURE.md) for the code structure, a complete new-level example, and a guide to adding mechanics. The [content-format reference](res/levels/README.md) lists every object type and data field.

The [asset integration manifest](docs/ASSETS.md) lists all 106 newly supplied gameplay PNGs and how each group is used. The original files in **bin** stay untouched; runtime assets live under **res** and are included in the JAR.

Start with these files:

- **res/levels/catalog.txt**: which levels exist and which starts the adventure.
- **res/levels/*.properties**: names, spawns, enemies, objects, portals, and puzzles.
- **res/maps/*.txt**: terrain layouts.
- **res/levels/tiles.properties**: texture and collision definitions.

The original forest map is retained. Older unused maps and art remain available as material for future levels. A snapshot of the source before the expansion is in **archive/pre-expansion-source.zip**.

## What changed from the original

Level files replace hardcoded object coordinates in AssetSetter. Actors receive collision queries instead of depending on GamePanel, and typed object categories replace name-string comparisons. Elapsed-time updates run on Swing's event thread, while drawing only reads state. Separate runtime Level objects keep discoveries when you travel between areas.


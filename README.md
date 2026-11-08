# Jeth's Portable

A Fabric mod for Minecraft 26.3. Carry a crafting table, link the chests and furnaces you already have to a Core block, and reach all of it from your inventory.

## Requirements

- Minecraft 26.3, Fabric Loader 0.19.5+, Fabric API, Java 25
- Optional: Mod Menu (adds a "Configure" button)
- Install on both the client and the server. It adds blocks and items, so a server without it won't work with it.

## Getting started

1. Craft a **Portable Core Block** and place it. That starts a base, owned by you.
2. Craft **Portable Links** and use one on each chest, barrel, shulker box, furnace, smoker, blast furnace or brewing stand you want in the base. The block stays exactly as it was.
3. Press `K`, or click the button in your inventory, to open the storage window.
4. Install **modules** in the Core for remote stonecutters, anvils, enchanting and more.

The **Portable Crafting Table** works by itself, with no base needed.

## Recipes

| Item | Ingredients |
|---|---|
| Portable Crafting Table | 1 crafting table, 2 leather, 2 string |
| Portable Core Block | 1 chest, 4 iron ingots, 4 redstone |
| Portable Link (makes 4) | 2 copper ingots, 1 redstone |
| Iron Range Upgrade | 8 iron ingots, 1 block of redstone |
| Gold Range Upgrade | 8 gold ingots, 1 ender pearl |
| Diamond Range Upgrade | 4 diamonds, 1 eye of ender |
| Netherite Range Upgrade | 1 netherite ingot, 4 blaze rods, 4 crying obsidian |
| Nether Star Range Upgrade | 1 nether star, 4 eyes of ender, 4 amethyst shards |
| Blank Module (makes 2) | 2 iron ingots, 2 copper ingots, 1 redstone |
| Built-in module | 1 Blank Module and the station block, anywhere in the grid |

## Portable Crafting Table

A full 3x3 crafting table, using the vanilla screen and recipe book. Open it by right clicking it in your hand, right clicking it in an inventory slot, the button in your inventory, or `V`. Items left in the grid go back to your inventory when you close it.

## Levels

Right click the Core with an upgrade to level it up. Upgrades go in order.

| Level | Upgrade | Reach | Links |
|---|---|---|---|
| 1 | | 24 blocks | 16 |
| 2 | Iron | 48 blocks | 32 |
| 3 | Gold | 96 blocks | 64 |
| 4 | Diamond | 192 blocks | 128 |
| 5 | Netherite | 512 blocks | 256 |
| 6 | Nether Star | Anywhere, in any dimension | No limit |

Reach is measured sideways from the Core, so height doesn't count. You and the linked block both have to be inside it. You can have several Cores; the window merges every Core you're in range of.

Breaking a Core keeps its level and links: the dropped item remembers them, and placing it again brings the base back wherever you put it. Links outside the new reach show as "Out of range" until you move closer or upgrade.

## The storage window

**Items.** Everything in your linked chests, merged and searchable.

| Action | What it does |
|---|---|
| Left click an item | Take a stack |
| Right click an item | Take half a stack |
| Shift + click an item | Send a stack to your inventory |
| Click with something on the cursor | Store it (right click stores one) |
| Shift + click your inventory | Store that stack |
| Store all / Matching | Store your inventory, or only what's already in storage. Your hotbar and portable crafting table stay. |

Search by name, `@mod`, `#tag`, or `-word` to exclude. The 3x3 grid on the right refills from storage as you craft, so shift-clicking the result keeps going until storage runs out, and the recipe book counts what's in storage.

**Chests.** Every linked chest, with how full it is. Click a row to open that chest from where you stand. The buttons on each row are *Store here* (new items go to this chest first), *Show me* (outlines it in the world), *Rename* and *Unlink*.

Without a *Store here* chest, items go to a chest that already holds the same item, then wherever there's room.

**Machines.** Linked furnaces, smokers, blast furnaces and brewing stands, with live progress and fuel. Click a row to open it, or use the buttons to take the output into your inventory or send it to storage. Furnace-type machines need a Furnace Module in the Core, and brewing stands a Brewing Module.

**Modules.** The Core's 6 module slots. See below.

**Base.** Level, reach, links, the next upgrade, trusted players and notification settings.

## Modules

Every Core has 6 module slots, on the storage window's **Modules** tab. A module puts a workstation in reach from anywhere in the base: click it to open that station's screen, and closing it brings you back to the window. Modules can be swapped at any time.

| Action | What it does |
|---|---|
| Click an empty slot with a module on the cursor | Install it (on a used slot, the two swap) |
| Click a module | Open it |
| Shift + click / right click a module | Take it out, to your inventory / the cursor |
| Right click the Core with a module | Install it in the first free slot |

There are two ways to make one, both starting from a **Blank Module**:

- **Linked:** use a Blank Module on a station you've placed in your base. The block stays where it is, and the module opens that block. A linked enchanting table uses its own bookshelves, and a linked anvil wears down like any anvil.
- **Built in:** craft a Blank Module with the station block. The block goes inside the module.

| Module | Built in | Linked |
|---|---|---|
| Stonecutter, Smithing Table, Grindstone, Loom, Cartography Table | Yes | Yes |
| Anvil (any wear) | Yes. Each use has a 12% chance to wear it from intact to chipped to damaged, then it breaks, with a warning. Its health shows on the item and the Modules tab. | Yes, the placed anvil wears as usual |
| Enchanting Table | Yes. Bookshelves count around the Core block, placed like a real table's. | Yes, with the table's own bookshelves |
| Ender Chest | Yes | Yes. Either way it opens your own ender chest. |
| Furnace (or Smoker, Blast Furnace) | Yes. Unlocks linked furnaces, smokers and blast furnaces on the Machines tab. | No |
| Brewing Stand | Yes. Unlocks linked brewing stands on the Machines tab. | No |
| Modded stations | If the block declares its screen the vanilla way | Yes, if right clicking it opens a screen |

A module alone in a crafting grid comes apart again: the Blank Module back, plus the block if it had one inside. The crafting table isn't a module, since every Core has one built in.

If you're in range of several Cores, the modules you get are the nearest Core's, so a second Core doesn't add six more slots.

## Notifications

Pop-ups for a whole batch finishing (once, when the input runs out, not for every item), running out of fuel with work waiting, a full output slot, brewing finishing, storage almost full, and items that didn't fit. These cover linked machines, and any furnace you loaded by hand, with no Link needed. Each kind can be turned off under *Settings* on the base page.

## Sharing

A base belongs to the player who placed its Core. The owner can add players to its trusted list on the base page (they must be online to be added). Everyone else can't use the base, unlink its blocks or break its Core. Operators can break any Core.

## Chunks

Linked chests are only read while a storage window is open, and their chunks load in the background while it is, so far-away items fill in after a moment. From Level 3, a Core also keeps a linked machine's chunk running while it has work to do and the owner or a trusted player is online, so furnaces finish while you're away. A remotely opened chest or furnace keeps its chunk loaded until you close it.

## Server settings

`config/jethsportable-server.json`:

| Setting | Default | Meaning |
|---|---|---|
| `rangeByLevel` | `[24, 48, 96, 192, 512, -1]` | Reach per level. -1 means anywhere. |
| `linksByLevel` | `[16, 32, 64, 128, 256, -1]` | Links per level. -1 means no limit. |
| `keepMachinesLoadedFromLevel` | `3` | Lowest level that keeps machine chunks running. 0 turns it off. |
| `allowRemoteOpening` | `true` | Whether linked blocks can be opened from afar. |
| `maxActionsPerSecond` | `60` | Storage actions per player per second. |

## For mod and pack makers

Anything with a menu that exposes its items through Fabric's Transfer API (which includes ordinary `Container` blocks) can be linked. To stop a block being linked, add it to the block tag `jethsportable:not_linkable`.

Workstations without an inventory can be modules. A built-in module needs the block to declare its screen with `getMenuProvider`, like vanilla's stations; a linked one only needs a right click on the block to open a screen, so stations such as Nemo's Woodcutter work linked. To stop a block becoming a module, add it to the block tag `jethsportable:not_a_module`.

## Building

```
./gradlew build                 # jar in build/libs
./gradlew runClient             # dev client
./gradlew runClientGameTest     # automated in-game test; results in build/run/clientGameTest
```

To test alongside other mods, put their jars in a folder and run `./gradlew runClientGameTest -PcompatModsDir=<folder>`. Don't include Fabric API; the dev environment already has it.

## Limits

- Ender chests can't be linked with a Portable Link, since they hold a different inventory for each player. Use an Ender Chest Module instead.
- Modded stations that need their own block entity, or that open their screen only from a right click, can only be linked modules, not built in.
- Items in a machine don't count as storage, so they don't appear on the Items tab.
- Crafting grid contents go back to your inventory when you open a chest remotely, the same as closing any crafting menu.

## License

MIT. See [LICENSE](LICENSE). Made by probablyjeth.

# Jeth's Portable

Carry a crafting table. Link the chests and furnaces you already have. Reach all of it from your inventory.

No new chests to replace and no pipes to run. Your blocks stay exactly as they are, and everything is drawn with the game's own menu textures, so it feels like part of Minecraft.

---

## Portable Crafting Table

A real 3x3 crafting table in your pocket, with the vanilla screen and recipe book. Right click it in your hand or in an inventory slot, click the button in your inventory, or press `V`.

## One window for all your storage

Place a **Portable Core Block**, then use a **Portable Link** on each chest, barrel, shulker box or copper chest you want connected. Then press `K`, or click the button in your inventory or crafting screen.

**Items**
- Everything in every linked chest, merged into one searchable grid
- Click to take a stack, right click for half, Shift + click to send it straight to your inventory
- Click with an item on your cursor to store it, or use *Store all* and *Matching*
- Search by name, `@mod`, `#tag`, or `-word` to exclude
- A crafting grid that refills from storage as you craft, and a recipe book that counts what's in your chests

**Chests**
- A list of your linked chests, with how full each one is
- Click one to open it from wherever you're standing
- *Store here* picks where new items go first. Otherwise they go to a chest that already has them.
- *Show me* outlines a chest through walls. You can also rename and unlink from here.

**Machines**
- Linked furnaces, smokers, blast furnaces and brewing stands, with live progress and fuel
- Open them from afar, or collect the output with one click, XP included

## Range upgrades

| Level | Upgrade | Reach | Links |
|---|---|---|---|
| 1 | | 24 blocks | 16 |
| 2 | Iron | 48 blocks | 32 |
| 3 | Gold | 96 blocks | 64 |
| 4 | Diamond | 192 blocks | 128 |
| 5 | Netherite | 512 blocks | 256 |
| 6 | Nether Star | Anywhere, any dimension | No limit |

You and the linked block both need to be inside the reach. Have as many Cores as you like; the window merges the ones you're in range of. Break a Core and it keeps its level and links, ready to place somewhere new.

From Level 3, a Core also keeps a linked machine's chunk running while it has work and you're online, so your furnaces finish while you're out exploring.

## Notifications

A small pop-up when it actually matters:

- **A whole batch is done.** "Smoker finished: 32 Steak", once, not for every item.
- A machine ran out of fuel with work still waiting
- An output slot is full
- Brewing finished
- Storage is almost full, or something didn't fit

These work for linked machines and for any furnace you loaded by hand, with no Link needed. Each kind can be turned off.

## Multiplayer

The mod has to be on both the server and the client. A base belongs to whoever placed its Core, and the owner can add trusted players. Nobody else can use it, unlink its chests or break its Core.

Every action is checked on the server, and moving items is all-or-nothing, so nothing gets duplicated or lost. Chests are only read while someone has the window open, a few each tick. With 200 full chests linked, the window costs the server well under a millisecond per tick.

## Works with

- Modded containers that use Fabric's Transfer API or vanilla's `Container`, including most modded chests
- Recipe viewers, inventory mods and anything else that adds to container screens, since the window is a normal game menu

Server owners can change the reach, the link limits and chunk loading, or turn remote opening off, in `config/jethsportable-server.json`.

## Controls

| Key | What it does |
|---|---|
| `K` | Open or close the storage window |
| `V` | Open the portable crafting table |
| `Ctrl` + `F` | Jump to the search box |

All keys can be changed in Controls.

## Recipes

Recipes unlock in the recipe book as you collect the materials.

| Item | Ingredients |
|---|---|
| Portable Crafting Table | Crafting table, 2 leather, 2 string |
| Portable Core Block | Chest, 4 iron ingots, 4 redstone |
| Portable Link (x4) | 2 copper ingots, 1 redstone |
| Iron Range Upgrade | 8 iron ingots, block of redstone |
| Gold Range Upgrade | 8 gold ingots, ender pearl |
| Diamond Range Upgrade | 4 diamonds, eye of ender |
| Netherite Range Upgrade | Netherite ingot, 4 blaze rods, 4 crying obsidian |
| Nether Star Range Upgrade | Nether star, 4 eyes of ender, 4 amethyst shards |

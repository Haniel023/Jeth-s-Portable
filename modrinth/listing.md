# Modrinth listing for Jeth's Portable

Fill these in on modrinth.com → **Create a project**, then the project's **Settings** tabs. Author: probablyjeth.

## 1. Create project dialog

| Field | Value |
|---|---|
| Project type | Mod |
| Name | `Jeth's Portable` |
| URL (slug) | `jeths-portable` (free as of 4 Nov 2026) |
| Visibility | Public (or Unlisted while you set it up) |
| Summary | `Carry a crafting table and reach all your storage from your inventory. Link the chests and furnaces you already have to a Core, then search, take, store and craft from one window. Range upgrades, remote furnaces and batch notifications.` |

The summary is 236 of the 256 characters allowed.

## 2. Settings → General

| Field | Value |
|---|---|
| Icon | `modrinth/icon-512.png` (see "Before you publish") |
| Client-side | **Required** |
| Server-side | **Required** |

## 3. Settings → Description

Paste the whole of `modrinth/description.md`.

## 4. Settings → Tags

| Field | Value |
|---|---|
| Featured categories (up to 3) | **Storage**, **Utility**, **Management** |
| Additional categories | Technology, Game Mechanics |

## 5. Settings → License

| Field | Value |
|---|---|
| License | **MIT License** |
| License URL (if asked) | `https://github.com/Haniel023/Jeth-s-Portable/blob/main/LICENSE` |

## 6. Settings → Links

| Field | Value |
|---|---|
| Issue tracker | `https://github.com/Haniel023/Jeth-s-Portable/issues` |
| Source code | `https://github.com/Haniel023/Jeth-s-Portable` |
| Wiki | (leave empty) |
| Discord | (leave empty) |

## 7. Gallery

Upload from `modrinth/gallery/`. Mark the first one as **Featured**.

| File | Title | Description |
|---|---|---|
| `01_storage_window.png` (Featured) | One window for everything | Every linked chest in one searchable grid, with a crafting grid that refills from storage. |
| `02_linked_chests.png` | Your chests stay your chests | Link the blocks you already have. Hold a Link to see what's connected. |
| `03_inventory_button.png` | Right in your inventory | One button for storage, one for your portable crafting table. |
| `04_chest_list.png` | Chest list | Open any chest from afar, choose where new items go, or outline it in the world. |
| `05_remote_furnaces.png` | Remote furnaces | Live progress, remote opening and one-click collecting. |
| `06_base_page.png` | Your base | Level, reach, links, the next upgrade and who you trust. |
| `07_portable_crafting.png` | Portable crafting table | The vanilla crafting screen, without the table. |
| `08_batch_notification.png` | Batch notifications | Told once, when the whole batch is done. |

These came from the automated test world at a small window size. Screenshots from your own game, in a real base, at a bigger window (or with shaders) will look better if you want to replace them.

## 8. Versions → Create a version

| Field | Value |
|---|---|
| File | `build/libs/jeths-portable-0.1.0.jar` (don't upload the `-sources` jar as the main file) |
| Version number | `0.1.0` |
| Version title | `Jeth's Portable 0.1.0` |
| Release channel | **Beta** |
| Loaders | Fabric |
| Game versions | 26.3 |
| Dependencies | **Fabric API**: Required. **Mod Menu**: Optional. |
| Changelog | Paste `modrinth/changelog-0.1.0.md` |

## Before you publish

Modrinth's rules on AI content apply to this project, and they can stop it being published:

- **Most of this project was written by Claude, an AI**, including the code, the textures, the icon and this text. Modrinth doesn't allow public projects that are entirely or primarily AI-generated. AI-assisted projects are allowed only if a person contributed in a primary and significant way.
- **If you publish it, turn on "Contains AI-generated content"** in the project's Disclosures tab.
- **The icon is AI-made, and AI-made images aren't allowed anywhere on a project page, icon included.** Replace `icon-512.png` with one you make yourself, or with a screenshot of the block in your own game. The mod's textures appear in every gallery screenshot too, so read the rule against your own screenshots.
- Check Modrinth's current content rules before submitting; they may have changed since 4 Nov 2026.

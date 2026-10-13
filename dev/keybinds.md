# Keybind map (Default Options, `config/defaultoptions/keybindings.txt`)

Default Options 21.1.8 applies each line as the mapping's new *default*: a fresh install gets it, "Reset" in Controls
returns to it, and a player whose binding still sits on the mod's original default is moved to the pack value.
Players may rebind anything. `unknown` = unbound by default. Mapping names were checked against the live `options.txt`
of the played instance (287 bindings, 2026-10-13); the conflict list is from
`Modpack-Planung/research-raw/ux-screens.md` §4.3.

## Movement

| Key | Action | Note |
|---|---|---|
| Left Shift | Sprint | swapped with vanilla |
| Left Control | Sneak | swapped with vanilla; Ctrl+B narrator hotkey is off (`narratorHotkey:false`) |

## Pack keys (plain, no modifier)

| Key | Action | Mapping |
|---|---|---|
| K | Quest book | `key.ftbquests.quests` |
| M | World map | `key.ftbchunks.map` |
| B | Backpack | `key.sophisticatedbackpacks.open_backpack` |
| C | Worn Notebook (Ars Nouveau) | `key.ars_nouveau.open_book` |
| X / Z | Next / previous spell slot (Ars) | `key.ars_nouveau.next_slot`, `previous_slot` |
| V | TFC place block | `tfc.key.place_block` |
| G | Curios inventory; GuideME (only while a tooltip is open) | `key.curios.open.desc`, `key.guideme.guide` |
| J | Jetpack on/off | `key.jetpack.toggle_active.description` |
| R | Cart action (Astikor Carts) | `key.astikorcartsredux.action` |
| U | Deep Learner (Hostile Networks) | `key.hostilenetworks.open_deep_learner` |
| Y | Cataclysm ability | `key.cataclysm.ability` |
| ' (apostrophe; Ä on a German keyboard) | Cataclysm chestplate ability (its default Y clashed with the ability) | `key.cataclysm.chestplate_ability` |
| ; (semicolon) | TFC chisel mode | `tfc.key.cycle_chisel_mode` |
| . (period) | Tom's Storage terminal | `key.toms_storage.open_terminal` |
| , (comma) | Physics Staff rotate mode (Create Simulated; its default Tab clashed with the player list) | `simulated.keyinfo.rotate_mode` |
| H (hold) | Show the Age indicator (firmages-core HUD) | `key.firmages.show_age` |
| O | Shader pack menu (Iris: switch pack, settings, shaders on/off) | `iris.keybind.shaderPackSelection` |
| Tab | Player list (vanilla, kept) | `key.playerlist` |

## Alt combos (late-Age tools and secondary functions)

| Key | Action | Mapping |
|---|---|---|
| Alt+B | Occultism satchel | `key.occultism.backpack` |
| Alt+C | Cataclysm helmet ability | `key.cataclysm.helmet_ability` |
| Alt+G | Portable stock ticker (Create Mobile Packages) | `create_mobile_packages.keyinfo.open_portable_stock_ticker` |
| Alt+H | Mekanism HUD | `key.mekanism.key_hud` |
| Alt+J | Jetpack hover | `key.jetpack.toggle_hover.description` |
| Alt+N | Mekanism item mode | `key.mekanism.mode` |
| Alt+R | Railways route overlay options | `key.createrailwaysnavigator.route_overlay_options` |
| Alt+S | IE magnet equip | `key.immersiveengineering.magnetEquip` |
| Alt+V | Ars selection HUD | `key.ars_nouveau.selection_hud` |

## Unbound by default (rebind in Controls if wanted)

Vanilla: advancements screen (L, the quest book is the progress screen), social interactions (P), save/load toolbar
(C/X). Mods: KubeDex (K), Mekanism armour modes (feet/legs/chest/head) and boost,
hide icons (H), Immersive Petroleum projector flip, Ars head curio and documentation, Cataclysm boots,
Create Big Cannons pitch, Draconic tool config and place item, Sophisticated Backpacks inventory interaction, Twilight
Forest map cycle, hotbar swap and zoom, Astikor slow, Ad Astra radio and suit flight, Occultism ender bag and storage
remote, Mekanism Additions voice, ExtendedAE view pattern, Jade overlay toggle, config, show recipes and show uses
(keypad keys; they could open the recipe viewer during Dawn), IE railgun zoom (middle mouse clashed with pick block),
Create Mobile Packages player networks, Iris reload shaders (R, clashed with the Astikor cart action), Iris
shaders on/off (K, clashed with the quest book; the O menu has the same toggle) and Iris wireframe (unbound in
Iris too).

Hold-modifier bindings that share Left Shift / Left Control (Jade details, Apotheosis compare, Hypertube escape,
Mekanism details, AE2 wheel modifier, Create modifiers) are left alone: they only act while held inside a GUI.

Tab and Y (Felix, 2026-10-13 late): Tab stays the vanilla player list and Y the Cataclysm ability; the two bindings
that shared them moved to keys no binding of the pack uses with or without a modifier (checked against this file and
the defaults in the client jars: comma and apostrophe are only GUI-internal keys of a few screens).

Verify in-game with Controlling's "Conflicts" filter after the first launch of a fresh profile.

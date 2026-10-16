# Changelog lines, round C2 harmony (A4, A5, A6, H2A, miner vs ladder)

For the next version section of `CHANGELOG.md` (player-facing, English). The integrator merges them.

- **One ore ladder.** Every way of turning ore into metal now sits on one step of a ladder, and every Age climbs it: crushing wheels give 1.5 times the metal of the crucible (was 1.7), spagyrics 2 times, the Immersive Engineering crusher 2.5 times (was 2) plus mineral powder and a small chance of a second metal, Purification 3 times, Injection 4 times and Dissolution 5 times. The Mekanism steps take TerraFirmaCraft ore pieces of every grade.
- **Mineral powder is not waste.** From the Information Age the enrichment chamber turns 64 mineral powder from the crusher or the quern into a metal dust. The arc furnace no longer melts powder.
- **The arc furnace is the Electric Age smelter.** It melts any metal dust into its ingot for 6,400 FE (was 51,200).
- **One iron.** Wrought iron is the pack's iron. The vanilla iron ingot is gone: every recipe that asked for it takes wrought iron. Iron dust smelts into cast iron in a furnace or on a fan, and into wrought iron only in the arc furnace and the energized smelter.
- **The Dimensional Mineshaft** brings up ore pieces of every grade (mostly poor and normal) instead of only rich ones.
- **Fuel has one value.** A bucket of fuel gives the same energy in every generator that burns it: creosote 82 thousand FE, kerosene 0.85 million, diesel 1.32 million, ethylene 11.3 million. Every diesel engine burns at the rate that gives this value through an alternator: the modular engine burns faster (it gave 1.68 million FE per bucket), the turbocharged engine burns twice as fast as it turns.
- **Mekanism machines need real power.** Every Mekanism and Mekanism: More Machine machine uses ten times the energy (200 to 1,600 FE/t); energy upgrades matter.
- **Miners are not free ore.** The Digital Miner uses 36,000 FE/t and takes 10 seconds per block without upgrades (energy and speed upgrades work as before), the excavator digs at half speed for the same power. A mined ore piece costs enough that every step of the ore ladder lowers the energy per ingot, as long as your Mekanism machines carry energy upgrades.

### Technical

- `kubejs/data/firmages/balance/metals.json` (moved from `dev/data/metals.json`) is the metal table that the recipe scripts read (`startup_scripts/metal_table.js`, `global.FA_METALS`); `dev/poc_analyze.py` checks it (L-1 to L-7, U-2).
- `kubejs/data/firmages/balance/fuels.json` is the fuel table; `dev/gen_fuels.py` writes the generator data from it and `--check` compares.
- The pack now ships `config/Mekanism/machine-usage.toml`, `machine-storage.toml` and `general.toml`, `config/MekanismMoreMachine/machine-usage.toml` and `machine-storage.toml`, and `config/immersiveengineering-server.toml` (full files, written by `dev/gen_machine_usage.py`; `--check`, `--server-dir`). An update overwrites a server's own copies of these files. `dev/miner_ladder.py` computes the miners against the ore ladder.

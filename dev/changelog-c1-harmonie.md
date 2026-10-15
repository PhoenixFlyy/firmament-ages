# Changelog lines from c1/harmonie (for the integrator)

Player-facing, English, to merge into the next `CHANGELOG.md` section.

- **Power has a price.** The alternator turns rotation into less FE (168 FE/t at 256 RPM instead of 360), lava no longer feeds a blaze burner, and the huge diesel engine burns diesel about twice as fast: every diesel engine now gives about as much FE per bucket as the diesel generator (1.3 to 1.7 million FE). Plant oil and ethanol no longer run diesel engines. The crystal resonance generator, the Applied Kinetics energy provider and the solar upgrade are gone; your ME network runs on the power of your Age.
- **Boiler and turbine with fission.** The thermoelectric boiler, the industrial turbine and the fuelwood and resistive heaters come with the Space Age.
- **No metal without ore.** Metal essence of Mystical Agriculture grows TerraFirmaCraft ore pieces (eight essence give two normal pieces) instead of ingots, so your ore line multiplies it like any other ore; netherite essence gives ancient debris. Hostile Neural Networks gives ore pieces instead of iron, copper and gold ingots. Reformation no longer makes osmium or uranium, and earth, gem and mineral niter no longer turn into metal. The Gambler spirit trades gems for gems, not for nuggets. Washing gravel, soul sand and red sand no longer gives iron or gold nuggets.
- **Spagyrics on every grade.** Ore pieces of every grade digest in the digestion vat into alchemical sulfur at exactly twice the metal (ten rich pieces give seven sulfur, two normal pieces one); the liquefaction cauldron no longer takes ore.
- **Draconium and planet ores join the ore line.** End draconium ore drops two rich draconium pieces instead of dust; iron, copper and gold ores of the Moon, Mars, Mercury, Venus and Glacio drop rich TerraFirmaCraft ore pieces instead of raw metal.
- **Excavator veins:** banded iron, lazulitic intrusion, rich auricupride and mephitic quarzite give TerraFirmaCraft ore pieces; the alluvial sift gives rough diamonds, rubies, sapphires, topaz and opals, the super vein rough emeralds.
- **More channels.** Every ME cable carries four times the channels (32, dense cable 128).
- **Wireless crafting terminal from the Industrial Age**, crafted on the mechanical crafter from black steel, precision mechanisms, steel components, electron tubes, a capacitor and a stock link. It links to an ME network from the Information Age on, when the cheaper recipe opens.

### Technical

- `dev/data/metals.json` is the metal table; `dev/poc_analyze.py` checks the ore ladder against it (L-1 to L-5).

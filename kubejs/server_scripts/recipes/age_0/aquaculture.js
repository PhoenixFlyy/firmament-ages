// Firmament Ages - Aquaculture 2 (round G3, Felix O1C: ocean mod past the content lock).
// One iron (U-2) and one value per metal form (L-8): no vanilla nuggets from tools, tin cans or goldfish.
// The gold and diamond rods and the wooden, gold and diamond fillet knives are hidden (dev/age_map.toml [hidden]);
// TFC cooks the raw fillet (kubejs/data/firmages/recipe/heating/aquaculture_fish_fillet_cooked.json).

ServerEvents.recipes((event) => {
  ;[
    'aquaculture:gold_nugget_from_blasting',
    'aquaculture:gold_nugget_from_smelting',
    'aquaculture:gold_nugget_from_gold_fish',
    'aquaculture:iron_nugget_from_blasting',
    'aquaculture:iron_nugget_from_smelting',
    'aquaculture:tin_can_to_iron_nugget',
    'aquaculture:tin_can_to_iron_nugget_from_blasting',
    'aquaculture:neptunium_ingot_from_blasting',
    'aquaculture:golden_fishing_rod',
    'aquaculture:diamond_fishing_rod',
    'aquaculture:wooden_fillet_knife',
    'aquaculture:gold_fillet_knife',
    'aquaculture:diamond_fillet_knife',
    'aquaculture:cooked_fish_fillet',
    'aquaculture:cooked_fish_fillet_from_campfire',
    'aquaculture:cooked_fish_fillet_from_smoking'
  ].forEach((id) => event.remove({ id: id }))
})

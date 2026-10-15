// Firmament Ages - Exposure in the Industrial Age (research n7-build, dev/age_map.toml: exposure = age_4).
// Film is chemistry: silver for the light-sensitive layer, saltpeter instead of gunpowder. The lightroom's iron
// trapdoor and the vanilla nuggets of the jar have no TFC recipe; recipes/deco_inputs.js maps the plain vanilla
// metals of the whole deco set, this file only the Exposure-specific parts.

ServerEvents.recipes((event) => {
  ;['exposure:black_and_white_film', 'exposure:high_sensitivity_black_and_white_film'].forEach((id) =>
    event.replaceInput({ id: id }, '#c:nuggets/iron', 'tfc:metal/rod/silver'))
  ;['exposure:black_and_white_film', 'exposure:color_film'].forEach((id) =>
    event.replaceInput({ id: id }, 'minecraft:gunpowder', '#c:dusts/saltpeter'))
  // The album binds photographs in leather (phantoms do not spawn before the Iron Age).
  event.replaceInput({ id: 'exposure:album' }, 'minecraft:phantom_membrane', 'minecraft:leather')
})

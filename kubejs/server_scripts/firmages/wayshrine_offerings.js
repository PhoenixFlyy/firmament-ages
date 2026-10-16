// Firmament Ages - the Wayshrine offerings of the sky road (firmages-core SPEC 28.6, 27.8; V4A).
// The mod jar ships interim offerings of sky metal (meteoric iron, then starsteel) that every Age can make before the
// celestial alloys have a source. Once the sky road is complete (startup_scripts/sky.js), a Wayshrine of the Arcane to
// the Information Age takes that Age's alloy from the Lens Crown, as SPEC 28.6 names it; later Ages fall back to age_6.
// The file is written into KubeJS's last data pack, so it replaces the jar's offerings.json on every load.

ServerEvents.generateData('last', (event) => {
  if (!global.FA_SKY.REQUIRED.every((id) => Item.exists(id))) return
  const A = global.FA_SKY.ALLOYS
  event.json('firmages:firmages_wayshrine/offerings', {
    age_1: 'tfc:metal/double_ingot/bronze',
    age_2: 'firmages:meteoric_iron_ingot',
    age_3: A.age_3,
    age_4: A.age_4,
    age_5: A.age_5,
    age_6: A.age_6
  })
})

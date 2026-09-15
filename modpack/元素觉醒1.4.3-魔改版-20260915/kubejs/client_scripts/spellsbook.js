ItemEvents.tooltip(event => {
  const disabledBooks = [
    "elemental_wizards_rpg:wind_spell_book",
    "elemental_wizards_rpg:terra_spell_book",
    "elemental_wizards_rpg:aqua_spell_book",
    "berserker_rpg:berserker_spell_book",
    "archers_expansion:deadeye_spell_book",
    "archers_expansion:war_archer_spell_book",
    "archers_expansion:tundra_hunter_spell_book",
    "archers:archer_spell_book",
    "ysjx_weapons:lightning_spell_book",
    "wizards:frost_spell_book",
    "wizards:fire_spell_book",
    "wizards:arcane_spell_book",
    "spellbladenext:arcane_battlemage_spell_book",
    "spellbladenext:fire_battlemage_spell_book",
    "spellbladenext:frost_battlemage_spell_book",
    "rogues:rogue_spell_book",
    "paladins:priest_spell_book",
    "paladins:paladin_spell_book"
  ]

  disabledBooks.forEach(bookId => {
    event.add(bookId, [
      Text.yellow("施放法术前，请将法术书装备至饰品栏的「法术书」槽位")
    ])
  })
})
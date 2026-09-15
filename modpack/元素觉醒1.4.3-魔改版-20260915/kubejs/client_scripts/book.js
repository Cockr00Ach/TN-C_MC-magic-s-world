ItemEvents.tooltip(event => {
  const disabledBooks = [
    "spellbladenext:defiance_spell_book",
    "spellbladenext:vengeance_spell_book",
    "spellbladenext:deathchill_spell_book",
    "spellbladenext:phoenix_spell_book",
    "spellbladenext:runic_echoes_spell_book"
  ]

  disabledBooks.forEach(bookId => {
    event.add(bookId, [
      Text.red("此法术书已被禁用！")
    ])
  })
})

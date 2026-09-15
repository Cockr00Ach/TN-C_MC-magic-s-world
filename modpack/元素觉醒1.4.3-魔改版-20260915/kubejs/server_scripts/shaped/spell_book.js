ServerEvents.recipes(event => {
    const disabledBooks = [
      "elemental_wizards_rpg:aqua_spell_book",
      "elemental_wizards_rpg:terra_spell_book",
      "elemental_wizards_rpg:wind_spell_book"
    ]
  
    disabledBooks.forEach(id => {
      event.remove({ output: id })
    })

  })

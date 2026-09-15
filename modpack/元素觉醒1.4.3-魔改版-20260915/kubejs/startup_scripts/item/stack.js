ItemEvents.modification(event => {
    const noStackItems = [
      'bountiful_display:air',
      'bountiful_display:arcane',
      'bountiful_display:blood',
      'bountiful_display:earth',
      'bountiful_display:fire',
      'bountiful_display:frost',
      'bountiful_display:healing',
      'bountiful_display:lighting',
      'bountiful_display:unholy',
      'bountiful_display:water'
    ]
  
    noStackItems.forEach(id => {
      event.modify(id, item => {
        item.maxStackSize = 1
      })
    })
  })
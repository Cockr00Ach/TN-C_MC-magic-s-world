ItemEvents.tooltip(event => {
     event.addAdvanced("cavedelight:plate_of_grotto_burnt_ends", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升施法速度"))
        })

        event.addAdvanced("cavedelight:plate_of_roasted_dino_chop", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升远程伤害"))
        })

        event.addAdvanced("cavedelight:plate_of_tail_cut", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升暴击伤害"))
        })
        event.addAdvanced("farmersdelight:chicken_sandwich", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升暴击几率"))
        })
    
        event.addAdvanced("farmersdelight:hamburger", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升暴击伤害"))
        })
    
        event.addAdvanced("farmersdelight:bacon_sandwich", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升暴击伤害"))
        })
    
        event.addAdvanced("farmersdelight:mutton_wrap", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升拉弓速度"))
        })
    
        event.addAdvanced("farmersdelight:dumplings", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升暴击伤害"))
        })
    
        event.addAdvanced("farmersdelight:stuffed_potato", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升施法速度"))
        })
    
        event.addAdvanced("farmersdelight:cabbage_rolls", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升拉弓速度"))
        })
    
        event.addAdvanced("farmersdelight:salmon_roll", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升海洋法术强度"))
        })
    
        event.addAdvanced("farmersdelight:cod_roll", (item, advanced, text) => {
            text.add(Component.translate("食用后获得").gray())
            text.add(Text.blue("§a短暂提升海洋法术强度"))
        })
    })
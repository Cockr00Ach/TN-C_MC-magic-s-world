ItemEvents.tooltip(event => {
    event.addAdvanced(Ingredient.all, (item, advanced, text) => {
        Object.entries(global.suits).forEach((/** @type {[String, $ArmorSet_]} */[equip, armorSet]) => {
            if (!armorSet.armors.includes(item.id)) return
            text.add(Component.translate("item.modifiers.suit").gray())

            Object.entries(armorSet.attributes).forEach((/** @type {[String, $AttributeSet_]} */[attribute, attributeSet]) => {
                let tooltipAmount = attributeSet.amount
                let translateKey = "attribute.modifier.plus."
                if (attributeSet.operation === "addition") {
                    translateKey += "0"
                } else {
                    tooltipAmount *= 100
                    translateKey += "1"
                }

                let translateAttribute = Component.translatable(`attribute.name.${attributeSet.name.toString().replace(":", ".").replace("minecraft.", "")}`).getString()
                text.add(Component.translate(translateKey, tooltipAmount, translateAttribute).blue())
            });
            return
        })
    })
})
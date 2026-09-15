let $ForgeRegiestries = Java.loadClass("net.minecraftforge.registries.ForgeRegistries")
let $AttributeModifier = Java.loadClass("net.minecraft.world.entity.ai.attributes.AttributeModifier")
let $UUID = Java.loadClass("java.util.UUID")

let attributeModifierCache = []
let attributeInstanceCache = []
PlayerEvents.tick(event => {
    /** @type {[string, $ArmorSet_][]} */
    let suitEntries = Object.entries(global.suits);
    let player = event.player
    let armorSlots = player.armorSlots

    for (const [equip, armorSet] of suitEntries) {
        if (!armorSlots.every(stack => armorSet.armors.includes(stack.id))) continue;
        // console.info("I wear" + armorSet.armors)

        /** @type {[String, $AttributeSet_][]} */
        let attributeEntries = Object.entries(armorSet.attributes)

        for (const [attribute, attributeSet] of attributeEntries) {
            let name = getModifierName(attributeSet.name, attributeSet.operation, armorSlots);
            let modifier = new $AttributeModifier(attributeSet.uuid, name, attributeSet.amount, attributeSet.operation)
            let attributeInstance = player.getAttribute(attributeSet.name)
            if (!attributeInstanceCache.includes(attributeSet.name)) attributeInstanceCache.push(attributeSet.name)

            if (attributeInstance.hasModifier(modifier)) continue
            if (!attributeModifierCache.includes(attributeSet.uuid.toString())) attributeModifierCache.push(attributeSet.uuid.toString())

            attributeInstance.addTransientModifier(modifier)
        }
        return;
    }
    removeModifier(player)
})

/**
 * @param {Internal.Player_} player 
 */
function removeModifier(player) {
    try {
        attributeInstanceCache.forEach(name => {
            let attributeInstance = player.getAttribute(name)
            attributeModifierCache.forEach(uuid => {
                attributeInstance.removeModifier($UUID.fromString(uuid))
            })
        })
    } catch (error) {
        console.error(error)
    }
}

/**
 * @param {String} attribute
 * @param {String} operation
 * @param {Internal.ItemStack_[]} armors
 */
function getModifierName(attribute, operation, armors) {
    let nameType = attribute.replace(".", "_").split(":")[1]
    let armorType = armors[0].id.split(":")[1].split("_")[0];
    return `${armorType}_${nameType}_${operation}`
}
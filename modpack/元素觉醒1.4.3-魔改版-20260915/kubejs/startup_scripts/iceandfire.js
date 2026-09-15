let LivingEquipmentChangeEventAttribute = [
    { id: 'iceandfire:dragonsteel_fire_sword', slot: "mainhand", Attribute: "spell_power:fire", d: 8, operation: "addition" },
    { id: 'iceandfire:dragonsteel_ice_sword', slot: "mainhand", Attribute: "spell_power:frost", d: 8, operation: "addition" },
    { id: 'iceandfire:dragonsteel_lightning_sword', slot: "mainhand", Attribute: "spell_power:arcane", d: 8, operation: "addition" },
    { id: 'iceandfire:dragonsteel_lightning_sword', slot: "mainhand", Attribute: "extraspellattributes:converttoarcane", d: 8, operation: "addition" },
    { id: 'iceandfire:dragonsteel_fire_sword', slot: "mainhand", Attribute: "extraspellattributes:converttofire", d: 0.25, operation: "MULTIPLY_BASE" },
    { id: 'iceandfire:dragonsteel_ice_sword', slot: "mainhand", Attribute: "extraspellattributes:converttofrost", d: 0.25, operation: "MULTIPLY_BASE" },
]

let LivingEquipmentChangeEvent = Java.loadClass("net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent")
let LivingEntity = Java.loadClass("net.minecraft.world.entity.LivingEntity")
ForgeEvents.onEvent(LivingEquipmentChangeEvent, /** @param {Internal.LivingEquipmentChangeEvent} event */ event => {
    let entity = event.getEntity()
    if (entity instanceof LivingEntity) {
        /** @type {Internal.LivingEntity} */
        let livingEntity = entity
        LivingEquipmentChangeEventAttribute.forEach(c => {
            let { id, slot, Attribute, d, operation } = c
            if (livingEntity.getItemBySlot(slot).id == id) {
                livingEntity.modifyAttribute(Attribute, id, d, operation)
            } else {
                livingEntity.removeAttribute(Attribute, id)
            }
        })
    }
})
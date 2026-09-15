const IntBounds = Java.loadClass('net.minecraft.advancements.critereon.MinMaxBounds$Ints')

StartupEvents.registry('champions:affix', event => {
    event.create('kubejs:yuansuyazhi', 'affix')
        .settings(settings => {
            settings.withDefault()
            settings.setTier(IntBounds.between(1, 5))
        })
        .behavior(behavior => {
            behavior.onAttack((champion, target, source, amount) => {
                if (target.getRandom().nextFloat() < 0.2 && !target.potionEffects.isActive('kubejs:nengliyazhi')) {
                    target.potionEffects.add('kubejs:nengliyazhi', 20 * 30, 0)
                }

                return true
            })
        })

    event.create('kubejs:yuansukongzhi', 'affix')
        .settings(settings => {
            settings.withDefault()
            settings.setTier(IntBounds.between(6, 8))
        })
        .behavior(behavior => {
            behavior.onAttack((champion, target, source, amount) => {
                if (!target.potionEffects.isActive('kubejs:yuansukongzhi')) {
                    target.potionEffects.add('kubejs:yuansukongzhi', 20 * 30, 0)
                }

                return true
            })
        })
})

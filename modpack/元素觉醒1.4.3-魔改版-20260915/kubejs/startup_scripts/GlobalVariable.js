let $UUID = Java.loadClass("java.util.UUID")

global.magic_attribute = [
    "spell_power:arcane",
    "spell_power:fire",
    "spell_power:frost",
    "spell_power:healing",
    "spell_power:water",
    "spell_power:earth",
    "spell_power:air",
    "spell_power:lightning",
    "minecraft:generic.attack_damage",
    "ranged_weapon:damage"
]

/**
 * @typedef {{name: Internal.AttributeInstance_, uuid: Internal.UUID_, amount: number?, operation: Internal.AttributeModifier$Operation_}} $AttributeSet_
 * @typedef {{armors: Internal.Item_[], attributes: Object.<String, $AttributeSet_>}} $ArmorSet_
 * @type {Object.<String, $ArmorSet_>}
 */
global.suits = {
    equip1: {
        armors: [
            'cataclysm:ignitium_helmet',
            'cataclysm:ignitium_chestplate',
            'cataclysm:ignitium_leggings',
            'cataclysm:ignitium_boots'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:fire",
                uuid: $UUID.fromString("38edebf6-f75e-4c4e-b630-9b2919b2fa7a"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:fire",
                uuid: $UUID.fromString("e4597e62-7d7a-4b5c-8966-d71f3722fa1f"),
                amount: 5,
                operation: "addition"
            },
            attribute3: {
                name: "minecraft:generic.attack_damage",
                uuid: $UUID.fromString("2d8508c9-5f21-4d69-bd6f-2d4894327b75"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute4: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("007a6ec0-495b-4eca-963e-931178ba7f94"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    cursiumSet: {
        armors: [
            'cataclysm:cursium_helmet',
            'cataclysm:cursium_chestplate',
            'cataclysm:cursium_leggings',
            'cataclysm:cursium_boots'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:frost",
                uuid: $UUID.fromString("64b660ef-f841-45fa-b7ab-dfd9cd96c567"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:frost",
                uuid: $UUID.fromString("2655c6a0-32fe-4d90-83f7-16c1a79f075e"),
                amount: 5,
                operation: "addition"
            },
            attribute3: {
                name: "minecraft:generic.attack_damage",
                uuid: $UUID.fromString("bc884054-21f1-4e9b-9f7c-bf3b34b46db4"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute4: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("68bd1756-34c9-40e9-a6cf-dda15cf5a32c"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    blackGoldMarshalSet: {
        armors: [
            'blackgoldalliance:the_black_gold_marshal_helmet',
            'blackgoldalliance:the_black_gold_marshal_chestplate',
            'blackgoldalliance:the_black_gold_marshal_leggings',
            'blackgoldalliance:the_black_gold_marshal_boots'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:arcane",
                uuid: $UUID.fromString("b9eadb52-3cb2-4970-80aa-c028e53126e5"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:arcane",
                uuid: $UUID.fromString("a42a7b1a-3064-441f-94a2-bd03c3548cba"),
                amount: 5,
                operation: "addition"
            },
            attribute3: {
                name: "minecraft:generic.attack_damage",
                uuid: $UUID.fromString("8c6a545b-dbdc-4e69-a664-685b09a4778e"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute4: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("1d30faf5-d9f1-425b-b821-2ede42b5a1e5"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    equip6: {
        armors: [
            'armoroftheages:holy_armor_head',
            'armoroftheages:holy_armor_legs',
            'armoroftheages:holy_armor_chest',
            'armoroftheages:holy_armor_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:healing",
                uuid: $UUID.fromString("a00208c3-1a4d-4ac6-869a-3ad96df8f13a"),
                amount: 0.3,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:healing",
                uuid: $UUID.fromString("081015d2-7eb7-486a-8c55-1acf096f222e"),
                amount: 10,
                operation: "addition"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("af38d3db-59d7-4c2d-88d6-003eb804cfd2"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute4: {
                name: "minecraft:generic.armor_toughness",
                uuid: $UUID.fromString("63f8f82a-a818-499c-8f07-4adee423a46d"),
                amount: 10,
                operation: "addition"
            }
        }
    },
    equip7: {
        armors: [
            'armoroftheages:anubis_armor_head',
            'armoroftheages:anubis_armor_chest',
            'armoroftheages:anubis_armor_legs',
            'armoroftheages:anubis_armor_feet'
        ],
        attributes: {
            attribute1: {
                name: "more_rpg_classes:lifesteal_modifier",
                uuid: $UUID.fromString("84534902-1626-4001-822d-c665440e40ab"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute2: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("27fc1b87-1413-433f-81a0-5c06db700129"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "more_rpg_classes:rage_modifier",
                uuid: $UUID.fromString("50ad916a-a201-4a9e-af58-8ebf98df9768"),
                amount: 0.2,
                operation: "MULTIPLY_BASE"
            },
            attribute4: {
                name: "minecraft:generic.attack_damage",
                uuid: $UUID.fromString("7fe3e9a4-622f-408f-a8ba-f7213722607b"),
                amount: 5,
                operation: "addition"
            },
            attribute5: {
                name: "minecraft:generic.attack_speed",
                uuid: $UUID.fromString("99507a65-4782-4837-9e41-219ced47ee05"),
                amount: 0.05,
                operation: "multiply_total"
            }
        }
    },
    // 奥术至尊法师套
    arcaneSupreme: {
        armors: [
            'ysjx_weapons:diamond_mage_head',
            'ysjx_weapons:diamond_mage_chest',
            'ysjx_weapons:diamond_mage_legs',
            'ysjx_weapons:diamond_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:haste",
                uuid: $UUID.fromString("a08dd207-31b1-45d9-86b3-a83f06f4cfd7"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:arcane",
                uuid: $UUID.fromString("44a04c0a-585d-4c08-811d-6b18f55f9156"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("f961bfe3-7410-4b31-8d21-f184bc6e4e6b"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 大地至尊法师套
    earthSupreme: {
        armors: [
            'ysjx_weapons:earth_mage_head',
            'ysjx_weapons:earth_mage_chest',
            'ysjx_weapons:earth_mage_legs',
            'ysjx_weapons:earth_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:critical_chance",
                uuid: $UUID.fromString("e3a01c48-7fa3-4b9d-9fb7-9df54603b6b9"),
                amount: 0.05,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:earth",
                uuid: $UUID.fromString("d6e674f3-e82a-4023-9591-d541b2bc96bb"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("a8e2c81e-9550-49da-8f45-1e1861ed6955"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 火焰至尊法师套
    fireSupreme: {
        armors: [
            'ysjx_weapons:fire_mage_head',
            'ysjx_weapons:fire_mage_chest',
            'ysjx_weapons:fire_mage_legs',
            'ysjx_weapons:fire_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:critical_chance",
                uuid: $UUID.fromString("eb6d9b65-ea2b-4226-9c44-493dfe1f26f7"),
                amount: 0.05,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:fire",
                uuid: $UUID.fromString("2a63b29d-5028-4f5f-9107-6cd2b9060bd8"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("e9cac770-1e76-4410-a510-fd53bbb47ce7"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 冰霜至尊法师套
    frostSupreme: {
        armors: [
            'ysjx_weapons:frost_mage_head',
            'ysjx_weapons:frost_mage_chest',
            'ysjx_weapons:frost_mage_legs',
            'ysjx_weapons:frost_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:critical_damage",
                uuid: $UUID.fromString("4c685f5a-2f17-49aa-9d96-c2ad13dbd3af"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:frost",
                uuid: $UUID.fromString("437f621e-3542-4275-bf00-1d69794fd5d1"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("7a8c947b-63cf-4d3c-88f7-998c73ca1c19"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 神圣至尊法师套
    healingSupreme: {
        armors: [
            'ysjx_weapons:healing_head',
            'ysjx_weapons:healing_chest',
            'ysjx_weapons:healing_legs',
            'ysjx_weapons:healing_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:haste",
                uuid: $UUID.fromString("5d7e686f-cd8c-4d1c-8e13-b9f1be25d6ac"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:healing",
                uuid: $UUID.fromString("621a2700-753d-4a04-933c-495856ee469e"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("d7266758-dd5e-4c35-b67e-30e05ae5e7da"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 闪电至尊法师套
    lightningSupreme: {
        armors: [
            'ysjx_weapons:lightning_mage_head',
            'ysjx_weapons:lightning_mage_chest',
            'ysjx_weapons:lightning_mage_legs',
            'ysjx_weapons:lightning_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:critical_chance",
                uuid: $UUID.fromString("8a6a7709-1c13-4a4b-9874-8b91b126d3c9"),
                amount: 0.05,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:lightning",
                uuid: $UUID.fromString("5f3fe2d7-6f06-45fb-9db4-c94c647f595c"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("e4f33ce3-8057-4129-81bf-c7e62edfc7b8"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 海洋至尊法师套
    waterSupreme: {
        armors: [
            'ysjx_weapons:water_mage_head',
            'ysjx_weapons:water_mage_chest',
            'ysjx_weapons:water_mage_legs',
            'ysjx_weapons:water_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:haste",
                uuid: $UUID.fromString("36615768-21e2-40f0-9cd8-fa67ff7095d1"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:water",
                uuid: $UUID.fromString("b9c934f8-d661-4eaf-82d0-8306933b4648"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("58f46cd3-c0b0-4ab0-a1b5-bf17fd3c9460"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 空气至尊法师套
    airSupreme: {
        armors: [
            'ysjx_weapons:air_mage_head',
            'ysjx_weapons:air_mage_chest',
            'ysjx_weapons:air_mage_legs',
            'ysjx_weapons:air_mage_feet'
        ],
        attributes: {
            attribute1: {
                name: "spell_power:critical_damage",
                uuid: $UUID.fromString("e76a6d88-2fd4-4dc8-97e8-774f502017c5"),
                amount: 0.1,
                operation: "multiply_total"
            },
            attribute2: {
                name: "spell_power:air",
                uuid: $UUID.fromString("9eccf78c-2eed-4ee3-8c6c-cce1ff56c185"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("5da2015a-4de8-4a45-af6d-dac6b2d60239"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    },
    // 猎星套
    liexingSet: {
        armors: [
            'ysjx_weapons:liexing_head',
            'ysjx_weapons:liexing_chest',
            'ysjx_weapons:liexing_legs',
            'ysjx_weapons:liexing_feet'
        ],
        attributes: {
            attribute1: {
                name: "ranged_weapon:haste",
                uuid: $UUID.fromString("7025d6a7-6f0c-4c24-b332-a91551e264ab"),
                amount: 0.06,
                operation: "MULTIPLY_BASE"
            },
            attribute2: {
                name: "ranged_weapon:damage",
                uuid: $UUID.fromString("8338f610-2341-499a-83ff-7a6de5c27b74"),
                amount: 15,
                operation: "addition"
            },
            attribute3: {
                name: "minecraft:generic.movement_speed",
                uuid: $UUID.fromString("47089ead-adda-41d9-b925-7a610f6313f4"),
                amount: 0.03,
                operation: "MULTIPLY_BASE"
            }
        }
    },
    // 皇家骑士团套
    royalKnightsSet: {
        armors: [
            'ysjx_weapons:royal_knights_head',
            'ysjx_weapons:royal_knights_chest',
            'ysjx_weapons:royal_knights_legs',
            'ysjx_weapons:royal_knights_feet'
        ],
        attributes: {
            attribute1: {
                name: "minecraft:generic.attack_damage",
                uuid: $UUID.fromString("3dfed6cd-4ba9-4de4-b91a-f05ca5aa6efc"),
                amount: 10,
                operation: "addition"
            },
            attribute2: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("fa0bac45-a1cd-4885-944a-c9d7deec3ec4"),
                amount: 0.2,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.armor_toughness",
                uuid: $UUID.fromString("ef559ea5-6642-44c8-a9e2-d7bc031511ba"),
                amount: 5,
                operation: "addition"
            }
        }
    },
    // 猎魔套
    liemoSet: {
        armors: [
            'ysjx_weapons:liemo_head',
            'ysjx_weapons:liemo_chest',
            'ysjx_weapons:liemo_legs',
            'ysjx_weapons:liemo_feet'
        ],
        attributes: {
            attribute1: {
                name: "minecraft:generic.attack_damage",
                uuid: $UUID.fromString("a015732c-530a-4a58-a008-9927482aca77"),
                amount: 15,
                operation: "addition"
            },
            attribute2: {
                name: "minecraft:generic.attack_speed",
                uuid: $UUID.fromString("2d167576-82f5-42e8-80b9-7337ed6ea288"),
                amount: 0.05,
                operation: "multiply_total"
            },
            attribute3: {
                name: "minecraft:generic.max_health",
                uuid: $UUID.fromString("52e4a6dc-5384-41e2-883e-3e53e4606ef0"),
                amount: 0.1,
                operation: "multiply_total"
            }
        }
    }
}

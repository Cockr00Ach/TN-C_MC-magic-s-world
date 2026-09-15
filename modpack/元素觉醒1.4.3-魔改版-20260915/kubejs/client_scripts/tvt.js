ItemEvents.tooltip(event => {
  // 与 startup_scripts/item/Scroll.js 的 SPELLS_BY_SCHOOL 保持一致
  const spellTypes = {
    arcane: {
      label: '§5奥术法术',
      scrolls: [
        'lne_arcane_starfall_scroll', 'amethystslash_scroll',
        'arcaneflourish_scroll', 'arcaneoverdrive_scroll',
        'bladestorm_scroll',
        'eldritchblast_scroll', 'finalstrike_scroll',
        'frostblink_scroll', 'maelstrom_scroll',
        'starfall_scroll', 'arcane_beam_scroll',
        'arcane_blast_scroll', 'arcane_blink_scroll',
        'arcane_bolt_scroll', 'arcane_missile_scroll',
        'abyss_blast_portal_arcane_scroll', 'aoshufeidan_upgraded_scroll',
        'aoshuqianghua_scroll', 'atomic_arcane_scroll',
        'bingci1_arcane_scroll', 'bingci2_arcane_scroll',
        'cibeishexian_arcane_scroll', 'cibeishexian2_arcane_scroll',
        'cj_flame_strike_arcane_scroll', 'cj_huanyingji_arcane_scroll',
        'cj_luohunren_arcane_scroll', 'cj_water_wave_arcane_scroll',
        'divine_gear_arcane_scroll', 'explosion_arcane_scroll',
        'flame_strike_arcane_scroll', 'heidong_scroll',
        'huanmo1_arcane_scroll', 'huanmo2_arcane_scroll',
        'huanyingji_arcane_scroll', 'jianjizhedaodan_arcane_scroll',
        'luohunren_arcane_scroll', 'lvyanqiangxi_scroll',
        'sandstorm_arcane_scroll', 'scrap_drone_arcane_scroll',
        'scrap_drone_basic_arcane_scroll', 'soul_flame_strike_arcane_scroll',
        'turret_arcane_scroll', 'turret_basic_arcane_scroll',
        'water_wave_arcane_scroll', 'xiajiepaodan_arcane_scroll',
        'xianqudaodan_arcane_scroll', 'xukongfuwen_arcane_scroll',
        'xushici_arcane_scroll', 'yanmiechuansongmen_arcane_scroll',
        'yanmiezhadan_scroll'
      ]
    },
    fire: {
      label: '§c火焰法术',
      scrolls: [
        'lne_fire_flamerush_scroll', 'combustion_scroll',
        'fireflourish_scroll', 'fireoverdrive_scroll',
        'flamelance_scroll', 'flameslash_scroll',
        'flicker_strike_scroll', 'frostvert_scroll',
        'inferno_scroll', 'phoenixdive_scroll',
        'snuffout_scroll', 'fire_breath_scroll',
        'fire_meteor_scroll', 'fire_scorch_scroll',
        'fire_wall_scroll', 'fireball_scroll',
        'abyss_blast_portal_fire_scroll', 'atomic_fire_scroll',
        'bingci1_fire_scroll', 'bingci2_fire_scroll',
        'cibeishexian_fire_scroll', 'cibeishexian2_fire_scroll',
        'cj_flame_strike_fire_scroll', 'cj_huanyingji_fire_scroll',
        'cj_luohunren_fire_scroll', 'cj_water_wave_fire_scroll',
        'divine_gear_fire_scroll', 'explosion_fire_scroll',
        'flame_strike_fire_scroll', 'huanmo1_fire_scroll',
        'huanmo2_fire_scroll', 'huanyingji_fire_scroll',
        'huoyanfeidan_upgraded_scroll', 'huoyanqianghua_scroll',
        'huoyun_gaojie_scroll', 'jianjizhedaodan_fire_scroll',
        'luohunren_fire_scroll', 'lvyanqiangxi_fire_scroll',
        'malkuth_fireball_fire_scroll', 'sandstorm_fire_scroll',
        'scrap_drone_basic_fire_scroll', 'scrap_drone_fire_scroll',
        'soul_flame_strike_fire_scroll', 'turret_basic_fire_scroll',
        'turret_fire_scroll', 'wangguohuopao_scroll',
        'water_wave_fire_scroll', 'xiajiepaodan_fire_scroll',
        'xianqudaodan_fire_scroll', 'xukongfuwen_fire_scroll',
        'xushici_fire_scroll', 'yanmiechuansongmen_fire_scroll',
        'yanmiezhadan_fire_scroll'
      ]
    },
    frost: {
      label: '§3冰霜法术',
      scrolls: [
        'lne_frost_ray_scroll', 'coldbuff_scroll',
        'deathchill_scroll', 'eviscerate_scroll',
        'frostbloom0_scroll', 'frostflourish_scroll',
        'frostgrasp_scroll', 'frostoverdrive_scroll',
        'frostslash_scroll', 'tempest_scroll',
        'whirlingblades_scroll', 'frost_blizzard_scroll',
        'frost_nova_scroll', 'frost_shard_scroll',
        'frost_shield_scroll', 'frostbolt_scroll',
        'abyss_blast_portal_frost_scroll', 'atomic_frost_scroll',
        'bingci1_frost_scroll', 'bingci2_frost_scroll',
        'bingdongfazhen_scroll', 'bingshuangfeidan_upgraded_scroll',
        'bingshuangqianghua_scroll', 'cibeishexian_frost_scroll',
        'cibeishexian2_frost_scroll', 'cj_flame_strike_frost_scroll',
        'cj_huanyingji_frost_scroll', 'cj_luohunren_frost_scroll',
        'cj_water_wave_frost_scroll', 'divine_gear_frost_scroll',
        'explosion_frost_scroll', 'flame_strike_frost_scroll',
        'frostnova_lullabye_scroll', 'huanmo1_frost_scroll',
        'huanmo2_frost_scroll', 'huanyingji_frost_scroll',
        'jianjizhedaodan_frost_scroll', 'luohunren_frost_scroll',
        'lvyanqiangxi_frost_scroll', 'malkuth_fireball_ice_scroll',
        'sandstorm_frost_scroll', 'scrap_drone_basic_frost_scroll',
        'scrap_drone_frost_scroll', 'soul_flame_strike_frost_scroll',
        'turret_basic_frost_scroll', 'turret_frost_scroll',
        'wangguobingpao_scroll', 'water_wave_frost_scroll',
        'xiajiepaodan_frost_scroll', 'xianqudaodan_frost_scroll',
        'xukongfuwen_frost_scroll', 'xushici_frost_scroll',
        'yanmiechuansongmen_frost_scroll', 'yanmiezhadan_frost_scroll'
      ]
    },
    physical_melee: {
      label: '§6物理技能',
      scrolls: [
        'lne_dancing_dagger_scroll', 'lne_second_wind_scroll',
        'charge_scroll', 'shadow_step_scroll',
        'shock_powder_scroll', 'shout_scroll',
        'slice_and_dice_scroll', 'throw_scroll',
        'vanish_scroll', 'whirlwind1_scroll',
        'monkeyreflect_scroll', 'monkeyslam_scroll',
        'reckoning_scroll', 'spellstrike_scroll',
        'staffstrike_scroll', 'whirlwind_scroll',
        'defiance_of_destiny_scroll', 'shield_shatter_scroll',
        'blood_reckoning_scroll', 'bloody_strike_scroll',
        'nordic_storm_scroll', 'outrage_scroll',
        'rumbling_swing_scroll', 'thunder_call_scroll',
        'undertow_scroll', 'wild_rage_scroll',
        'judgement_scroll', 'abyss_blast_portal_wuli_scroll',
        'aolafu_q_scroll', 'atomic_wuli_scroll',
        'bingci1_wuli_scroll', 'bingci2_wuli_scroll',
        'cibeishexian_wuli_scroll', 'cibeishexian2_wuli_scroll',
        'cj_flame_strike_wuli_scroll', 'cj_huanyingji_wuli_scroll',
        'cj_luohunren_wuli_scroll', 'cj_water_wave_wuli_scroll',
        'cleave_scroll', 'divine_gear_wuli_scroll',
        'explosion_wuli_scroll', 'fan_of_knives_scroll',
        'fengbaojushe_scroll', 'flame_strike_wuli_scroll',
        'huanmo1_wuli_scroll', 'huanmo2_wuli_scroll',
        'huanyingji_shanxing_wuli_scroll', 'huanyingji_wuli_scroll',
        'jianjizhedaodan_wuli_scroll', 'lvyanqiangxi_wuli_scroll',
        'malkuth_giant_sword_slash_scroll', 'sandstorm_wuli_scroll',
        'scrap_drone_basic_wuli_scroll', 'scrap_drone_wuli_scroll',
        'shejiqianghua_scroll', 'soul_flame_strike_wuli_scroll',
        'swift_strikes_scroll', 'tiaozhan_scroll',
        'turret_basic_wuli_scroll', 'turret_wuli_scroll',
        'wanglingdun_scroll', 'water_wave_wuli_scroll',
        'wuliqianghua_scroll', 'wulislash_scroll',
        'wulislash_2_scroll', 'wulislash_3_scroll',
        'xiajiepaodan_wuli_scroll', 'xianqudaodan_wuli_scroll',
        'xukongfuwen_wuli_scroll', 'xushici_wuli_scroll',
        'yanmiechuansongmen_wuli_scroll', 'yanmiezhadan_wuli_scroll',
        'zhanfutouzhi_scroll'
      ]
    },
    healing: {
      label: '§a治疗法术',
      scrolls: [
        'lne_holy_prevention_scroll', 'lne_holy_weapon_scroll',
        'barrier_scroll', 'battle_banner_scroll',
        'circle_of_healing_scroll', 'divine_protection_scroll',
        'flash_heal_scroll', 'heal_scroll',
        'holy_beam_scroll', 'holy_shock_scroll',
        'bolster_scroll', 'challenge_scroll',
        'inexorable_scroll', 'overpower_scroll',
        'smite_scroll', 'soul_of_vengeance_scroll',
        'abyss_blast_portal_healing_scroll', 'atomic_healing_scroll',
        'bingci1_healing_scroll', 'bingci2_healing_scroll',
        'cibeishexian_healing_scroll', 'cibeishexian2_healing_scroll',
        'cj_flame_strike_healing_scroll', 'cj_huanyingji_healing_scroll',
        'cj_luohunren_healing_scroll', 'cj_water_wave_healing_scroll',
        'divine_gear_healing_scroll', 'explosion_healing_scroll',
        'flame_strike_healing_scroll', 'huanmo1_healing_scroll',
        'huanmo2_healing_scroll', 'huanyingji_healing_scroll',
        'jianjizhedaodan_healing_scroll', 'luohunren_healing_scroll',
        'lvyanqiangxi_healing_scroll',
        'mikaerzhufu_gao_scroll', 'sandstorm_healing_scroll',
        'scrap_drone_basic_healing_scroll', 'scrap_drone_healing_scroll',
        'shenshengqianghua_scroll', 'soul_flame_strike_healing_scroll',
        'turret_basic_healing_scroll', 'turret_healing_scroll',
        'water_wave_healing_scroll', 'xiajiepaodan_healing_scroll',
        'xianqudaodan_healing_scroll', 'xukongfuwen_healing_scroll',
        'xushici_healing_scroll', 'yanmiechuansongmen_healing_scroll',
        'yanmiezhadan_healing_scroll'
      ]
    },
    water: {
      label: '§9水系法术',
      scrolls: [
        'aqua_bubble_beam_scroll', 'aqua_explosive_bubbles_scroll',
        'aqua_hydro_beam_scroll', 'aqua_splash_scroll',
        'aqua_springwater_scroll', 'aqua_water_whip_scroll',
        'abyss_blast_portal_water_scroll', 'atomic_water_scroll',
        'bingci1_water_scroll', 'bingci2_water_scroll',
        'cibeishexian_water_scroll', 'cibeishexian2_water_scroll',
        'cj_flame_strike_water_scroll', 'cj_huanyingji_water_scroll',
        'cj_luohunren_water_scroll', 'cj_water_wave_water_scroll',
        'divine_gear_water_scroll', 'explosion_water_scroll',
        'flame_strike_water_scroll', 'haiyangfeidan_scroll',
        'haiyangjiejie_scroll', 'haiyangqianghua_scroll',
        'haiyangshuangfeidan_upgraded_scroll', 'huanmo1_water_scroll',
        'huanmo2_water_scroll', 'huanyingji_water_scroll',
        'jianjizhedaodan_water_scroll', 'luohunren_water_scroll',
        'lvyanqiangxi_water_scroll', 'sandstorm_water_scroll',
        'scrap_drone_basic_water_scroll', 'scrap_drone_water_scroll',
        'soul_flame_strike_water_scroll', 'turret_basic_water_scroll',
        'turret_water_scroll', 'water_wave_water_scroll',
        'waterslash_scroll', 'waterslash_2_scroll',
        'waterslash_3_scroll', 'xiajiepaodan_water_scroll',
        'xianqudaodan_water_scroll', 'xukongfuwen_water_scroll',
        'xushici_water_scroll', 'yanmiechuansongmen_water_scroll',
        'yanmiezhadan_water_scroll'
      ]
    },
    earth: {
      label: '§6土系法术',
      scrolls: [
        'terra_drip_circle_scroll', 'terra_earthquake_scroll',
        'terra_shattering_stone_scroll', 'terra_stone_flesh_scroll',
        'terra_stone_spear_scroll', 'terra_stone_throw_scroll',
        'abyss_blast_portal_earth_scroll', 'atomic_earth_scroll',
        'bingci1_earth_scroll', 'bingci2_earth_scroll',
        'cibeishexian_earth_scroll', 'cibeishexian2_earth_scroll',
        'cj_flame_strike_earth_scroll', 'cj_huanyingji_earth_scroll',
        'cj_luohunren_earth_scroll', 'cj_water_wave_earth_scroll',
        'dadiqianghua_scroll', 'divine_gear_earth_scroll',
        'dixin_scroll', 'explosion_earth_scroll',
        'flame_strike_earth_scroll', 'huanmo1_earth_scroll',
        'huanmo2_earth_scroll', 'huanyingji_earth_scroll',
        'jianjizhedaodan_earth_scroll', 'liedichongji_scroll',
        'luohunren_earth_scroll', 'lvyanqiangxi_earth_scroll',
        'sandstorm_earth_scroll', 'scrap_drone_basic_earth_scroll',
        'scrap_drone_earth_scroll', 'shimao_upgraded_scroll',
        'soul_flame_strike_earth_scroll', 'ysjxspells_terra_earthquake_scroll',
        'tianxing_scroll', 'turret_basic_earth_scroll',
        'turret_earth_scroll', 'water_wave_earth_scroll',
        'xiajiepaodan_earth_scroll', 'xianqudaodan_earth_scroll',
        'xukongfuwen_earth_scroll', 'xushici_earth_scroll',
        'yanmiechuansongmen_earth_scroll', 'yanmiezhadan_earth_scroll'
      ]
    },
    air: {
      label: '§f风系法术',
      scrolls: [
        'wind_aeroblast_scroll', 'wind_aeroburst_scroll',
        'wind_air_cutter_scroll', 'wind_gust_scroll',
        'wind_tornado_scroll', 'wind_updraft_scroll',
        'abyss_blast_portal_air_scroll', 'airslash_scroll',
        'airslash_2_scroll', 'airslash_3_scroll',
        'atomic_air_scroll', 'bingci1_air_scroll',
        'bingci2_air_scroll', 'cibeishexian_air_scroll',
        'cibeishexian2_air_scroll', 'cj_flame_strike_air_scroll',
        'cj_huanyingji_air_scroll', 'cj_luohunren_air_scroll',
        'cj_water_wave_air_scroll', 'divine_gear_air_scroll',
        'explosion_air_scroll', 'fengshenzhishi_scroll',
        'flame_strike_air_scroll', 'huanmo1_air_scroll',
        'huanmo2_air_scroll', 'huanyingji_air_scroll',
        'jianjizhedaodan_air_scroll', 'kongqifeidan_scroll',
        'kongqiqianghua_scroll', 'kongqishuangfeidan_upgraded_scroll',
        'luohunren_air_scroll', 'lvyanqiangxi_air_scroll',
        'sandstorm_air_scroll', 'scrap_drone_air_scroll',
        'scrap_drone_basic_air_scroll', 'soul_flame_strike_air_scroll',
        'turret_air_scroll', 'turret_basic_air_scroll',
        'water_wave_air_scroll', 'xiajiepaodan_air_scroll',
        'xianqudaodan_air_scroll', 'xukongfuwen_air_scroll',
        'xushici_air_scroll', 'yanmiechuansongmen_air_scroll',
        'yanmiezhadan_air_scroll'
      ]
    },
    lightning: {
      label: '§b闪电法术',
      scrolls: [
        'abyss_blast_portal_lightning_scroll', 'atomic_lightning_scroll',
        'bingci1_lightning_scroll', 'bingci2_lightning_scroll',
        'chesed_one_shot_vertical_ray_scroll', 'cibeishexian_lightning_scroll',
        'cibeishexian2_lightning_scroll', 'cj_flame_strike_lightning_scroll',
        'cj_huanyingji_lightning_scroll', 'cj_luohunren_lightning_scroll',
        'cj_water_wave_lightning_scroll', 'divine_gear_lightning_scroll',
        'explosion_lightning_scroll', 'fcdc_scroll',
        'fengbaozhaohuan_scroll', 'flame_strike_lightning_scroll',
        'huanmo1_lightning_scroll', 'huanmo2_lightning_scroll',
        'huanyingji_lightning_scroll', 'jianjizhedaodan_lightning_scroll',
        'leiji_scroll', 'leiji_basic_scroll',
        'leishenzhichui_scroll', 'liansuoshandian_scroll',
        'lingyuleiji_scroll', 'luohunren_lightning_scroll',
        'lvyanqiangxi_lightning_scroll', 'sandstorm_lightning_scroll',
        'scrap_drone_basic_lightning_scroll', 'scrap_drone_lightning_scroll',
        'shandianqianghua_scroll', 'soul_flame_strike_lightning_scroll',
        'turret_basic_lightning_scroll', 'turret_lightning_scroll',
        'water_wave_lightning_scroll', 'xiajiepaodan_lightning_scroll',
        'xianqudaodan_lightning_scroll', 'xukongfuwen_lightning_scroll',
        'xushici_lightning_scroll', 'yanmiechuansongmen_lightning_scroll',
        'yanmiezhadan_lightning_scroll'
      ]
    },
    physical_ranged: {
      label: '§b游侠技能',
      scrolls: [
        'barrage_scroll', 'entangling_roots_scroll',
        'magic_arrow_scroll', 'lne_rangers_focus_scroll',
        'abyss_blast_portal_ranged_scroll', 'atomic_ranged_scroll',
        'baozhajian_scroll', 'bingci1_ranged_scroll',
        'bingci2_ranged_scroll', 'cibeishexian_ranged_scroll',
        'cibeishexian2_ranged_scroll', 'cj_flame_strike_ranged_scroll',
        'divine_gear_ranged_scroll', 'explosion_ranged_scroll',
        'flame_strike_ranged_scroll', 'huanmo1_ranged_scroll',
        'huanmo2_ranged_scroll', 'jianjizhedaodan_ranged_scroll',
        'jianyu_scroll', 'jianyu_gaojie_scroll',
        'jingujian_scroll', 'lvyanqiangxi_ranged_scroll',
        'sandstorm_ranged_scroll', 'scrap_drone_basic_ranged_scroll',
        'scrap_drone_ranged_scroll', 'soul_flame_strike_ranged_scroll',
        'turret_basic_ranged_scroll', 'turret_ranged_scroll',
        'wanglingdun_ranged_scroll', 'wangzhibaoku_scroll',
        'wangzhibaoku_1_scroll', 'wangzhibaoku_2_scroll',
        'wanjianqifa_scroll', 'water_wave_ranged_scroll',
        'xiajiepaodan_ranged_scroll', 'xianqudaodan_ranged_scroll',
        'xukongfuwen_ranged_scroll', 'xushici_ranged_scroll',
        'yanmiechuansongmen_ranged_scroll', 'yanmiezhadan_ranged_scroll'
      ]
    }
  };

  Object.keys(spellTypes).forEach(school => {
    const spellType = spellTypes[school];

    spellType.scrolls.forEach(scrollId => {
      event.addAdvanced('kubejs:' + scrollId, (item, advanced, text) => {
        text.add(1, Text.of('§f使用§e法术注册台§f将法术与法术书进行绑定'));
        text.add(2, Text.of(spellType.label));
      });
    });
  });

  event.addAdvanced('kubejs:zhongyashalou_scroll', (item, advanced, text) => {
    text.add(1, Text.of('§f使用§e法术注册台§f将法术与法术书进行绑定'));
    text.add(2, Text.of('§e功能性法术'));
  });

  event.addAdvanced('kubejs:mikaerzhufu_scroll', (item, advanced, text) => {
    text.add(1, Text.of('§f使用§e法术注册台§f将法术与法术书进行绑定'));
    text.add(2, Text.of('§e增益类法术'));
    text.add(3, Text.of('§f佩戴§e§l糖果心§r§f，在§e§l糖果洞穴维度§r§f击杀生物时§e§l概率获取'));
  });
});

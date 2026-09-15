ItemEvents.tooltip(event => {
  const colorMap = {
    common: '§7',     
    uncommon: '§a',   
    epic: '§c'       
  };

  const rarities = {
    common: [
      'power_shot',
      'undertow', 'wild_rage',
      'aqua_water_whip', 'terra_stone_spear', 'wind_air_cutter',
      'flash_heal', 'holy_shock',
      'slice_and_dice', 'throw',
      'arcaneoverdrive', 'blastarcane', 'blastfire', 'blastfrost', 'blastlightning',
      'bolster', 'box', 'deathchill2', 'fireoverdrive', 'flamelance',
      'frostgrasp', 'frostoverdrive', 'gem_barrage', 'gem_barrage2',
      'lightningoverdrive', 'overblaze', 'protect', 'reckoning', 'riflebarrage',
      'smite', 'sniper', 'staffstrike', 'starfall', 'whirlwind',
      'arcane_missile', 'fireball', 'frostbolt',
      'arcane_bolt', 'fire_scorch', 'frost_shard', 'monkeyreflect',
      'terra_stone_throw', 'heal', 'aqua_splash', 'wind_gust'
    ],
    uncommon: [
      'entangling_roots', 'blood_reckoning',
      'aqua_bubble_beam', 'aqua_springwater', 'terra_stone_flesh', 'wind_aeroblast',
      'divine_protection', 'holy_beam',
      'shock_powder', 'shout',
      'arcaneflourish', 'challenge', 'combustion', 'deathchill', 'echoes',
      'fervoussmite', 'fireflourish', 'frostblink',
      'frostbloom1', 'frostbloom2', 'frostbloom3', 'frostbloom4',
      'frostflourish', 'frostvert', 'lightningstep', 'monkeyslam', 'rebuke',
      'shield_shatter', 'whirlingblades',
      'arcane_blast', 'fire_breath', 'frost_nova',
      'barrage', 'bloody_strike',
      'aqua_explosive_bubbles', 'terra_drip_circle', 'terra_shattering_stone',
      'wind_aeroburst', 'wind_updraft',
      'lne_rangers_focus', 'lne_holy_prevention', 'lne_holy_weapon',
      'lne_dancing_dagger', 'lne_second_wind',
      'lne_arcane_starfall', 'lne_fire_flamerush',
      'circle_of_healing', 'judgement',
      'charge', 'shadow_step',
      'amethystslash', 'bladestorm', 'defiance_of_destiny',
      'defiance_of_destiny_heal', 'eviscerate', 'flameslash', 'flicker_strike',
      'frostbloom0', 'frostslash', 'grandstanding', 'inferno', 'lightningslash',
      'maelstrom', 'monkeydash', 'phoenixdive', 'soul_of_vengeance',
      'tempest', 'xslash',
      'arcane_blink', 'frost_shield'
    ],
    epic: [
      'magic_arrow',
      'nordic_storm', 'outrage', 'rumbling_swing', 'soulaxe_drain', 'thunder_call',
      'aqua_hydro_beam', 'terra_earthquake', 'wind_tornado',
      'lne_passive_dragon_bow', 'lne_frost_ray',
      'barrier', 'battle_banner',
      'vanish', 'whirlwind1',
      'bulwark', 'coldbuff', 'eldritchblast', 'finalstrike', 'inexorable',
      'overpower', 'particlesholy', 'snuffout', 'thesis',
      'arcane_beam', 'fire_meteor', 'fire_wall', 'frost_blizzard'
    ]
  };

  for (let id of rarities.common) {
    event.add(`kubejs:${id}_scroll`, [ `${colorMap.common}初阶法术` ]);
  }

  for (let id of rarities.uncommon) {
    event.add(`kubejs:${id}_scroll`, [ `${colorMap.uncommon}中阶法术` ]);
  }

  for (let id of rarities.epic) {
    event.add(`kubejs:${id}_scroll`, [ `${colorMap.epic}高阶法术` ]);
  }

  // ysjxspells 法术卷轴等级
  const ysjxElements = [
    'arcane', 'fire', 'frost', 'wuli', 'healing',
    'water', 'earth', 'air', 'lightning', 'ranged'
  ]
  const ysjxNonRangedElements = ysjxElements.filter(element => element !== 'ranged')
  const ysjxLuohunrenElements = ysjxElements.filter(element => element !== 'wuli' && element !== 'ranged')
  const ysjxTiers = {
    common: [],
    uncommon: [],
    epic: []
  }

  const addElementSeries = (target, prefix, elements) => {
    elements.forEach(element => target.push(`${prefix}_${element}`))
  }

  // 初阶：所有初阶系列，以及作为升级链起点的飞弹与雷击
  const ysjxFullInitialSeries = [
    'bingci1',
    'cibeishexian',
    'cj_flame_strike',
    'huanmo1',
    'scrap_drone_basic',
    'turret_basic'
  ]
  ysjxFullInitialSeries.forEach(prefix => addElementSeries(ysjxTiers.common, prefix, ysjxElements))

  const ysjxNonRangedInitialSeries = [
    'cj_huanyingji',
    'cj_luohunren',
    'cj_water_wave'
  ]
  ysjxNonRangedInitialSeries.forEach(prefix => addElementSeries(ysjxTiers.common, prefix, ysjxNonRangedElements))

  ysjxTiers.common.push(
    'wulislash', 'waterslash', 'airslash',
    'wangzhibaoku_1',
    'leiji_basic', 'haiyangfeidan', 'kongqifeidan',
    'aolafu_q', 'cleave', 'fan_of_knives', 'zhanfutouzhi', 'swift_strikes'
  )

  // 中阶：冰刺、唤魔尖牙、飞弹升级与斩击中阶
  addElementSeries(ysjxTiers.uncommon, 'bingci2', ysjxElements)
  addElementSeries(ysjxTiers.uncommon, 'huanmo2', ysjxElements)
  ysjxTiers.uncommon.push(
    'aoshufeidan_upgraded',
    'huoyanfeidan_upgraded',
    'bingshuangfeidan_upgraded',
    'haiyangshuangfeidan_upgraded',
    'kongqishuangfeidan_upgraded',
    'wulislash_2', 'waterslash_2', 'airslash_2',
    'baozhajian', 'jianyu', 'jingujian', 'wanjianqifa',
    'dixin', 'liedichongji',
    'malkuth_fireball_ice', 'wangguobingpao',
    'malkuth_fireball_fire',
    'frostnova_lullabye',
    'leishenzhichui', 'fcdc', 'liansuoshandian',
    'chesed_one_shot_vertical_ray'
  )

  // 绿焰强袭：奥术版本没有元素后缀
  ysjxTiers.uncommon.push('lvyanqiangxi')
  ysjxElements.forEach(element => {
    if (element !== 'arcane') {
      ysjxTiers.uncommon.push(`lvyanqiangxi_${element}`)
    }
  })

  // 高阶：除上述中阶之外的全部明确升级链产物
  addElementSeries(ysjxTiers.epic, 'cibeishexian2', ysjxElements)
  addElementSeries(ysjxTiers.epic, 'flame_strike', ysjxElements)
  addElementSeries(ysjxTiers.epic, 'luohunren', ysjxLuohunrenElements)
  addElementSeries(ysjxTiers.epic, 'huanyingji', ysjxNonRangedElements)
  addElementSeries(ysjxTiers.epic, 'water_wave', ysjxNonRangedElements)
  addElementSeries(ysjxTiers.epic, 'scrap_drone', ysjxElements)
  addElementSeries(ysjxTiers.epic, 'turret', ysjxElements)
  addElementSeries(ysjxTiers.epic, 'soul_flame_strike', ysjxElements)
  addElementSeries(ysjxTiers.epic, 'yanmiechuansongmen', ysjxElements)

  // 高阶全元素系列
  const ysjxFullHighTierSeries = [
    'abyss_blast_portal',
    'divine_gear',
    'jianjizhedaodan',
    'sandstorm',
    'xiajiepaodan',
    'xianqudaodan',
    'xukongfuwen'
  ]
  ysjxFullHighTierSeries.forEach(prefix => addElementSeries(ysjxTiers.epic, prefix, ysjxElements))

  ysjxTiers.epic.push('yanmiezhadan')
  ysjxElements.forEach(element => {
    if (element !== 'arcane') {
      ysjxTiers.epic.push(`yanmiezhadan_${element}`)
    }
  })

  ysjxTiers.epic.push(
    'huanyingji_shanxing_wuli',
    'tiaozhan', 'wanglingdun_ranged',
    'shimao_upgraded', 'leiji',
    'wangzhibaoku_2', 'wangguohuopao',
    'wulislash_3', 'waterslash_3', 'airslash_3',
    'water_wave_ranged', 'wanglingdun', 'fengbaojushe',
    'ysjxspells_terra_earthquake', 'fengbaozhaohuan'
  )

  ysjxTiers.common.forEach(id => {
    event.add(`kubejs:${id}_scroll`, [ `${colorMap.common}初阶法术` ])
  })

  ysjxTiers.uncommon.forEach(id => {
    event.add(`kubejs:${id}_scroll`, [ `${colorMap.uncommon}中阶法术` ])
  })

  ysjxTiers.epic.forEach(id => {
    event.add(`kubejs:${id}_scroll`, [ `${colorMap.epic}高阶法术` ])
  })

  // 仍可作为后续升级材料的卷轴；最终形态不加入此提示
  const upgradeableScrolls = []

  ysjxFullInitialSeries.forEach(prefix => {
    addElementSeries(upgradeableScrolls, prefix, ysjxElements)
  })

  ysjxNonRangedInitialSeries.forEach(prefix => {
    addElementSeries(upgradeableScrolls, prefix, ysjxNonRangedElements)
  })

  upgradeableScrolls.push(
    'fireball', 'arcane_missile', 'frostbolt',
    'haiyangfeidan', 'kongqifeidan', 'terra_stone_spear',
    'wulislash', 'waterslash', 'airslash',
    'leiji_basic', 'wangzhibaoku_1',
    'wulislash_2', 'waterslash_2', 'airslash_2',
    'wangzhibaoku_2'
  )

  // 烈焰打击还可继续升级为魂焰打击
  addElementSeries(upgradeableScrolls, 'flame_strike', ysjxElements)

  // 绿焰强袭可继续升级为湮灭传送门或湮灭炸弹
  upgradeableScrolls.push('lvyanqiangxi')
  ysjxElements.forEach(element => {
    if (element !== 'arcane') {
      upgradeableScrolls.push(`lvyanqiangxi_${element}`)
    }
  })

  upgradeableScrolls.forEach(id => {
    event.add(`kubejs:${id}_scroll`, [ '§e此法术可升级' ])
  })
});

"""Manual creative-only inventory kits. These functions never run automatically."""
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
out = ROOT / 'src/main/resources/data/tnc/functions'
out.mkdir(parents=True, exist_ok=True)
plants = ['road_bell_seed', 'night_gourd_seed', 'tide_reed_seed',
          'homeward_flower_seed', 'warning_moss_spore', 'hushcap_spore',
          'rainletter_seed', 'mana_root_seed', 'verdant_vein_seed']
species = re.findall(r'\w+\("([a-z_]+)",', (ROOT / 'src/main/java/com/tnc/tnc/life/botanical/BotanicalSpecies.java').read_text(encoding='utf-8'))
plants += [s + '_seed' for s in species] + ['sky_vine_seed']
animals = ['bellwool_sheep_spawn_egg'] + [s + '_spawn_egg' for s in re.findall(r's\("([a-z_]+)",', (ROOT / 'src/main/java/com/tnc/tnc/life/pasture/PastureSpecies.java').read_text(encoding='utf-8'))]

def kit(name, items, message):
    lines = ['# Requires a creative player and operator permission to invoke /function.',
             '# No world changes, inventory clearing, maturity changes or free survival rewards.']
    for item, count in items:
        lines.append(f'execute if entity @s[type=minecraft:player,gamemode=creative] run give @s {item if ":" in item else "tnc:"+item} {count}')
    lines.append('execute if entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"'+message+'","color":"green"}')
    lines.append('execute unless entity @s[type=minecraft:player,gamemode=creative] run tellraw @s {"text":"生态测试包仅供创造模式手动测试。","color":"yellow"}')
    (out / f'ecology_{name}_kit.mcfunction').write_text('\n'.join(lines)+'\n', encoding='utf-8')

kit('plant', [(p, 2) for p in plants] + [(p, 1) for p in ['plant_sample_clip', 'rain_watering_flask', 'pollination_brush', 'field_tuning_bell']] + [('field_frame', 8), ('salt_basin', 1), ('minecraft:glass_bottle', 16)], '已给29种种源与栽培工具。普通野外即可种植；先查看物品说明。')
kit('animal', [(a, 1) for a in animals] + [(p, 1) for p in ['pasture_book', 'pasture_staff', 'pasture_cage', 'pasture_scraper', 'mana_bottle', 'refined_mana_bottle', 'pasture_trough', 'habitat_marker', 'pasture_tray', 'egg_rack', 'charging_perch', 'mana_bottle_base']], '已给24种独立动物的测试蛋和牧养设施。牧养册对动物右键可查喜食与实际状态。')
kit('workshop', [(p, 1) for p in ['magic_forge', 'forge_mana_injector', 'forge_input_port', 'forge_output_port', 'forge_exhaust', 'forge_guide', 'mana_generator', 'mana_battery', 'mana_paper_press', 'mana_work_lamp', 'mana_bottle_base', 'mana_bottle', 'field_sound_relay', 'rainproof_seed_box', 'survey_archive_folder', 'sky_vine_survey', 'sky_canopy_rope']] + [('mana_cable', 16), ('forge_firebrick', 32), ('forge_copper_frame', 8), ('star_dew_fruit', 8), ('farlight_fruit', 8), ('focus_lens', 1), ('warning_lens', 1), ('hearth_oil', 1), ('shell_glue', 1)], '魔力工坊测试包：先按说明书组装3×3×3锻炉，右键核心打开界面。')
assert len(plants) == 29 and len(animals) == 24
print('Generated manual creative kits: 29 plant seeds / 24 animal eggs / workshop tools')

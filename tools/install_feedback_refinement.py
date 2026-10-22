"""Install the feedback revision only after server tests and both native shader runs."""
from pathlib import Path
import argparse, datetime, hashlib, importlib.util, json, re, shutil, subprocess
import xml.etree.ElementTree as ET
import zipfile
from install_life_routes import running
from install_magic_progression import CHAPTERS, protected, sha

R = Path(__file__).resolve().parents[1]

def same_loaded_quest(expected, actual, at=''):
    """FTB omits defaults, renumbers chapters and expands undamaged item icons on save."""
    if isinstance(expected, dict):
        assert isinstance(actual, dict), at
        for key, value in expected.items():
            if not at and key == 'order_index': continue  # ordinal in the complete loaded book
            if key not in actual and (value in (None, False, '', [], {}) or key == 'count' and value == 1): continue
            assert key in actual, at+'/'+key
            same_loaded_quest(value, actual[key], at+'/'+key)
    elif isinstance(expected, list):
        assert isinstance(actual, list) and len(expected) == len(actual), at
        for i, (a,b) in enumerate(zip(expected,actual)): same_loaded_quest(a,b,at+'/'+str(i))
    elif isinstance(expected, str) and isinstance(actual, dict):
        assert actual == {'Count': 1, 'id': expected, 'tag': {'Damage': 0}}, at
    else:
        assert expected == actual, at

def validate(jar):
    latest = max(p.stat().st_mtime for d in ['src/main/java', 'src/main/resources']
                 for p in (R/d).rglob('*') if p.is_file())
    assert jar.stat().st_mtime >= latest, 'Rebuild all current sources'
    log = R/'work/feedback-tests.log'
    contents = log.read_text('utf8', errors='replace')
    counts = re.findall(r'All (\d+) required tests passed', contents)
    assert log.stat().st_mtime >= latest and 'BUILD SUCCESSFUL' in contents
    assert counts and int(counts[-1]) >= 155, 'Combined regression including the six new feedback tests required'
    units = 0
    for p in (R/'build/test-results/test').glob('TEST-*.xml'):
        t = ET.parse(p).getroot()
        assert int(t.attrib['failures']) == int(t.attrib['errors']) == 0
        units += int(t.attrib['tests'])
    assert units >= 402
    reports = {}
    with zipfile.ZipFile(jar) as z:
        for mode in ['shaders', 'vanilla']:
            path = R/f'work/feedback-native-{mode}/report.json'
            report = json.loads(path.read_text('utf8'))
            assert path.stat().st_mtime >= latest
            assert report['screenshots'] == 11 and not report['errors'] and all(report['checks'].values()), report
            assert len(list(path.parent.glob('*.png'))) == 11
            for key in ['ftb_37B7FFAC33E9E92C', 'ftb_30D9D5470D862905', 'ftb_662EE66DA1916AAF',
                        'physical_lid_synced', 'real_magic_flame', 'tower_formed', 'spirit_visible',
                        'solar-corona_actual_yield', 'lunar-full_actual_yield', 'lunar-crescent_actual_yield']:
                assert report['checks'].get(key), key
            for n, h in report['class_sha256'].items():
                assert hashlib.sha256(z.read(n.replace('.', '/')+'.class')).hexdigest() == h, n
            assert len(report['asset_sha256']) == 10
            for n, h in report['asset_sha256'].items():
                assert hashlib.sha256(z.read(n)).hexdigest() == h, n
            for name in CHAPTERS:
                source = R/'questbook/ftbquests/chapters'/name
                if sha(source) != report['quest_sha256'][name]:
                    # The first native run loads the exact authored bytes. FTB writes
                    # canonical SNBT on exit, so the second run must match every authored
                    # field semantically, not just its quest count. Keep the tested bytes.
                    assert mode == 'vanilla', 'First run must load authored input: '+name
                    loaded = path.parent/'loaded-quests'/name
                    assert sha(loaded) == report['quest_sha256'][name], name
                    spec = importlib.util.spec_from_file_location('ftb_snbt', R/'tools/merge-field-redesign-guide.py')
                    module = importlib.util.module_from_spec(spec)
                    spec.loader.exec_module(module)
                    same_loaded_quest(json.loads(source.read_text('utf8')), module.SnbtReader(loaded.read_text('utf8')).read())
                    report.setdefault('verified_reformatted_quests', []).append(name)
            reports[mode] = report
        for n in ['com/tnc/tnc/world/eartharena/EarthArenaEncounter.class',
                  'com/tnc/tnc/boss/wind/WindGeneralEntity.class', 'com/tnc/tnc/dungeon/TNDungeonMob.class']:
            assert n in z.namelist(), 'Preserve teammate content: '+n
    return units, int(counts[-1]), reports

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--live', required=True)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    live = Path(args.live).resolve()
    mirror = R/'modpack'/live.name
    jar = R/'build/libs/tnc-1.0.0.jar'
    units, gametests, reports = validate(jar)
    assert not running(live), 'Save and fully close Minecraft before installing'
    before = {name: protected(pack) for name, pack in [('live', live), ('mirror', mirror)]}
    assert before['live'] and before['mirror']
    files = {'mods/tnc-1.0.0.jar': jar}
    files.update({'config/ftbquests/quests/chapters/'+c: R/'questbook/ftbquests/chapters'/c for c in CHAPTERS})
    if not args.apply:
        print(json.dumps({'validated': True, 'units': units, 'gametests': gametests,
                          'native_screenshots': 22, 'protected': len(before['live'])}))
        return
    backup = R/'work/backups'/('feedback-refinement-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'))
    backup.mkdir(parents=True)
    for name, pack in [('live', live), ('mirror', mirror)]:
        saved = backup/name
        saved.mkdir()
        shutil.copytree(pack/'config/ftbquests/quests', saved/'quests-full')
        for rel, source in files.items():
            dest = pack/rel
            if dest.exists():
                old = saved/rel
                old.parent.mkdir(parents=True, exist_ok=True)
                shutil.copy2(dest, old)
            dest.parent.mkdir(parents=True, exist_ok=True)
            tmp = dest.with_name(dest.name+'.feedback-new')
            shutil.copy2(source, tmp)
            assert sha(tmp) == sha(source)
            tmp.replace(dest)
        assert protected(pack) == before[name], 'An authored quest changed'
    receipt = {'installed_at': datetime.datetime.now().isoformat(), 'jar_sha256': sha(jar),
               'source_commit_before_release': subprocess.check_output(['git','rev-parse','HEAD'], cwd=R, text=True).strip(),
               'unit_tests': units, 'combined_game_tests': gametests, 'native_screenshots': 22,
               'native': reports, 'protected_quests': {n: len(v) for n,v in before.items()},
               'files': {n: sha(p) for n,p in files.items()}, 'backup': str(backup),
               'saves_modified': False, 'island_templates_modified': False, 'additional_third_party_jars': []}
    (R/'release/feedback-refinement-install-20261022.json').write_text(json.dumps(receipt, ensure_ascii=False, indent=2)+'\n', 'utf8')
    bundle = R/'work/feedback-refinement-update-20261022.zip'
    with zipfile.ZipFile(bundle, 'w', compression=zipfile.ZIP_DEFLATED, compresslevel=3) as z:
        for n,p in files.items(): z.write(p,n)
        z.write(R/'tools/apply_magic_progression_update.ps1', 'apply_magic_progression_update.ps1')
        z.writestr('manifest.json', json.dumps({'files': receipt['files']}, indent=2))
    print(json.dumps({'installed': True, 'units': units, 'gametests': gametests, 'native_screenshots': 22,
                      'sha256': sha(jar), 'protected': len(before['live']), 'bundle': str(bundle)}, ensure_ascii=False))

if __name__ == '__main__': main()

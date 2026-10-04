"""Prepare only our optional field chapter, retaining live layout and other nodes.

FTB writes comma-optional SNBT. This reader accepts that form and JSON SNBT;
the output is JSON-compatible SNBT. It does not install or change other pages.
"""
from pathlib import Path
import argparse
import json
import re


class SnbtReader:
    def __init__(self, text):
        self.text = text.lstrip('\ufeff')
        self.pos = 0

    def skip(self):
        while self.pos < len(self.text) and self.text[self.pos] in ' \t\r\n,':
            self.pos += 1

    def string(self):
        value, end = json.JSONDecoder().raw_decode(self.text[self.pos:])
        self.pos += end
        return value

    def value(self):
        self.skip()
        char = self.text[self.pos]
        if char == '"':
            return self.string()
        if char == '{':
            self.pos += 1
            result = {}
            while True:
                self.skip()
                if self.text[self.pos] == '}':
                    self.pos += 1
                    return result
                if self.text[self.pos] == '"':
                    key = self.string()
                else:
                    found = re.match(r'[^\s:,{}\[\]]+', self.text[self.pos:])
                    if not found:
                        raise ValueError(f'Invalid key at {self.pos}')
                    key = found.group()
                    self.pos += len(key)
                self.skip()
                if self.text[self.pos] != ':':
                    raise ValueError(f'Expected colon at {self.pos}')
                self.pos += 1
                if key in result:
                    raise ValueError(f'Duplicate key {key}')
                result[key] = self.value()
        if char == '[':
            self.pos += 1
            result = []
            while True:
                self.skip()
                if self.text[self.pos] == ']':
                    self.pos += 1
                    return result
                result.append(self.value())
        found = re.match(r'[^\s,}\]]+', self.text[self.pos:])
        if not found:
            raise ValueError(f'Invalid value at {self.pos}')
        token = found.group()
        self.pos += len(token)
        if token.lower() in ('true', 'false'):
            return token.lower() == 'true'
        if re.fullmatch(r'[-+]?\d+(?:[bBsSlL])?', token):
            return int(token.rstrip('bBsSlL'))
        if re.fullmatch(r'[-+]?(?:\d+(?:\.\d*)?|\.\d+)(?:[eE][-+]?\d+)?[fFdD]?', token):
            return float(token.rstrip('fFdD'))
        return token

    def read(self):
        result = self.value()
        self.skip()
        if self.pos != len(self.text):
            raise ValueError(f'Trailing content at {self.pos}')
        return result


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('live', type=Path)
    ap.add_argument('output', type=Path)
    args = ap.parse_args()
    source = Path(__file__).resolve().parents[1] / 'questbook/ftbquests/chapters/tnc_field_01_expeditions.snbt'
    updated = json.loads(source.read_text(encoding='utf-8'))
    live = SnbtReader(args.live.read_text(encoding='utf-8-sig')).read()
    if live.get('id') != updated['id'] or live.get('filename') != updated['filename']:
        raise ValueError('Wrong chapter; refusing to merge')
    by_id = {q['id']: q for q in live['quests']}
    new_icons = {'tnc:verdant_vein_seed', 'tnc:mana_generator', 'tnc:mana_paper_press'}
    changed_icons = {'tnc:homeward_flower_seed', 'tnc:homeward_lamp', 'tnc:bellwool_fodder',
                     'tnc:resonant_fleece', 'tnc:mana_root_seed', 'tnc:verdant_vein_seed',
                     'tnc:mana_generator', 'tnc:mana_paper_press'}
    changed = 0
    for wanted in updated['quests']:
        old = by_id.get(wanted['id'])
        if old is None:
            # FTB reassigns unsupported high-bit IDs when it saves a chapter.
            # Locate that same node by its original icon/position; keep its live
            # ID and progress. Never append a second copy of an older node.
            candidates = [q for q in live['quests'] if q.get('icon') == wanted['icon']
                          and q.get('x') == wanted['x'] and q.get('y') == wanted['y']]
            if len(candidates) == 1:
                old = candidates[0]
        if old is None:
            if wanted['icon'] in new_icons:
                live['quests'].append(wanted)
                changed += 1
            else:
                print(f'Preserved unrecognized existing node: {wanted["id"]}')
        elif wanted['icon'] in changed_icons or wanted['y'] == -5:
            # Keep live icons/coordinates/shape/tasks and all unrelated metadata.
            old['description'] = wanted['description']
            if wanted['icon'] == 'tnc:bellwool_fodder':
                old['title'] = wanted['title']
            changed += 1
    ids = [live['id']]
    for q in live['quests']:
        ids.append(q['id'])
        ids.extend(t['id'] for t in q.get('tasks', []))
        ids.extend(t['id'] for t in q.get('rewards', []))
    if len(ids) != len(set(ids)):
        raise ValueError('Duplicate IDs; refusing output')
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(live, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(f'Prepared {len(live["quests"])} nodes; updated {changed} relevant nodes, retaining live layout.')


if __name__ == '__main__':
    main()

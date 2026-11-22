"""Place the waterfall hermit in the starter's entity region, once per new world."""
import json
import uuid

import nbtlib as n
from finish_skyhaven_gateway import anvil


IDENTITY = uuid.UUID("91a63ba2-9317-4426-a9e6-dbb0ce63f81b")
TAG = "tnc_waterfall_hermit"


def place_master(files, feet):
    x, y, z = map(int, feet)
    cx, cz = x // 16, z // 16
    name = f"realm/entities/r.{cx // 32}.{cz // 32}.mca"
    raw = files.get(name, bytes(8192))
    records = anvil.region_chunks(raw)
    slot = cx % 32 + 32 * (cz % 32)
    if slot in records:
        chunk = anvil.read_chunk(records[slot])
        if tuple(chunk["Position"]) != (cx, cz):
            raise ValueError("Waterfall entity region has an incorrect chunk position")
    else:
        chunk = n.File({"Position": n.IntArray([cx, cz]), "DataVersion": n.Int(3465),
                        "Entities": n.List[n.Compound]()})
    entity_id = n.IntArray([int.from_bytes(IDENTITY.bytes[i:i + 4], "big", signed=True)
                            for i in range(0, 16, 4)])
    residents = chunk["Entities"]
    if any(tuple(e.get("UUID", ())) == tuple(entity_id) for e in residents):
        raise ValueError("The input template already contains the waterfall master")
    residents.append(n.Compound({
        "id": n.String("minecraft:villager"), "UUID": entity_id,
        "Pos": n.List[n.Double]([x + .5, float(y), z + .5]),
        "Motion": n.List[n.Double]([0., 0., 0.]), "Rotation": n.List[n.Float]([0., 0.]),
        "Tags": n.List[n.String]([TAG]),
        "CustomName": n.String(json.dumps({"text": "听泉大师 · 砚舟"}, ensure_ascii=False)),
        "VillagerData": n.Compound({"type": n.String("minecraft:snow"),
                                   "profession": n.String("minecraft:cleric"), "level": n.Int(5)}),
        "NoAI": n.Byte(1), "Invulnerable": n.Byte(1), "Silent": n.Byte(1),
        "PersistenceRequired": n.Byte(1), "OnGround": n.Byte(1),
    }))
    records[slot] = anvil.serialize(chunk)
    files[name] = anvil.pack_region(raw, records)

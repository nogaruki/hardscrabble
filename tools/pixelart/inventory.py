"""Writes docs/art/INVENTORY.csv: every texture under assets/hardscrabble/textures with what it shows, where it is used and which
artist batch owns it (A tools/weapons/armour, B materials/food/misc items, C blocks, D entities).

    python -I tools/pixelart/inventory.py      # rewrites the CSV, prints per-batch counts and missing/unused textures
"""
import csv
import json
import re
import sys
from collections import Counter, defaultdict
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import pix  # noqa: E402
import shapes  # noqa: E402
from palettes import MATERIAL_OF_PREFIX  # noqa: E402

ASSETS = pix.ROOT / "src/main/resources/assets/hardscrabble"
GEN = pix.ROOT / "src/generated/resources/assets/hardscrabble"
OUT = pix.ROOT / "docs/art/INVENTORY.csv"

WEAPONS = {"sword", "dagger", "knife", "club", "cudgel", "battle_axe", "war_hammer", "bow", "bow_pulling_0", "bow_pulling_1",
           "bow_pulling_2", "arrow"}
ARMOR = ("helmet", "chestplate", "leggings", "boots", "horse_armor")
MATERIAL_WORDS = ("ingot", "nugget", "raw_", "shard", "chain", "coin", "sinew", "_ball", "manure", "flour")
FOOD_EXTRA = {"onion", "milk_bowl", "water_bowl"}
BLOCK_MATERIALS = {"clay": "clay", "large_clay": "clay", "sandstone": "sandstone", "netherrack": "netherrack",
                   "emerald": "emerald", "diamond": "diamond", "quartz": "quartz", "glass": "glass"}
BATCH = {"tool": "A", "weapon": "A", "armor_item": "A", "armor_layer": "A", "material": "B", "food": "B", "misc": "B",
         "block": "C", "entity": "D"}


def food_ids():
    ids = set(FOOD_EXTRA)
    for p in (pix.ROOT / "src/main/java").rglob("*.java"):
        ids.update(re.findall(r'\b(?:dish|simpleFood|food)\("([a-z_]+)"', p.read_text("utf-8")))
    return ids


def references():
    """{texture path relative to textures/: [(json file, parent)]} from generated models and equipment assets."""
    refs = defaultdict(list)
    for p in (GEN / "models").rglob("*.json"):
        data = json.loads(p.read_text("utf-8"))
        for tex in data.get("textures", {}).values():
            if tex.startswith("hardscrabble:"):
                refs[tex[5:] + ".png"].append((p.relative_to(GEN).as_posix(), data.get("parent", "").replace("minecraft:", "")))
    for p in (GEN / "equipment").glob("*.json"):
        for layer, entries in json.loads(p.read_text("utf-8"))["layers"].items():
            for e in entries:
                refs[f"entity/equipment/{layer}/{e['texture'].split(':')[1]}.png"].append((p.relative_to(GEN).as_posix(), layer))
    return refs


def material_of(stem):
    if stem.endswith("spawn_egg"):
        return ""
    stem = re.sub(r"^(raw|deepslate)_", "", stem)
    for prefix in sorted(list(MATERIAL_OF_PREFIX) + list(BLOCK_MATERIALS), key=len, reverse=True):
        if stem == prefix or stem.startswith(prefix + "_"):
            return MATERIAL_OF_PREFIX.get(prefix) or BLOCK_MATERIALS[prefix]
    return ""


def subject(kind, stem, lang):
    """English name from the lang file; texture suffixes (_top, _front_on, _stage2...) become a face note."""
    parts = stem.split("_")
    for n in range(len(parts), 0, -1):
        key = f"{kind}.hardscrabble.{'_'.join(parts[:n])}"
        if key in lang:
            rest = " ".join(parts[n:])
            return lang[key] + (f" ({rest})" if rest else "")
    if kind == "item" and any(k.startswith(f"block.hardscrabble.{parts[0]}") for k in lang):
        return subject("block", stem, lang)
    return stem.replace("_", " ")


def classify(rel, stem, foods, tool_kinds):
    if rel.startswith("entity/equipment/"):
        return "armor_layer"
    if rel.startswith("entity/"):
        return "entity"
    if rel.startswith("block/"):
        return "block"
    if stem in tool_kinds:
        return "weapon" if tool_kinds[stem] in WEAPONS else "tool"
    if any(stem.endswith(a) for a in ARMOR):
        return "armor_item"
    if stem in foods:
        return "food"
    if any(w in stem for w in MATERIAL_WORDS):
        return "material"
    return "misc"


def main():
    lang = json.loads((GEN / "lang/en_us.json").read_text("utf-8"))
    foods = food_ids()
    tool_kinds = {p.stem: k for p, k, m in shapes.targets()}
    models = pix.entity_models()
    refs = references()
    rows = []
    for p in sorted(pix.TEXTURES.rglob("*.png")):
        rel = p.relative_to(pix.TEXTURES).as_posix()
        stem = p.stem
        cat = classify(rel, stem, foods, tool_kinds)
        size = Image.open(p).size
        need = pix.expected_size(p)
        if cat == "entity":
            model = models.get(stem, "?")
            subj = lang.get(f"entity.hardscrabble.{stem}", stem)
        elif cat == "armor_layer":
            model = rel.split("/")[2]
            subj = f"{stem.replace('_', ' ')} armour worn ({model})"
        else:
            parents = sorted({re.sub(r"(_(left|right|open|alt|ends|post|side|cap|top|bottom))+$", "", par)
                              for _, par in refs.get(rel, []) if par})
            model = " ".join(parents)
            subj = subject("block" if cat == "block" else "item", stem, lang)
        notes = []
        if need and size != need and not (cat == "block" and size[0] == 16 and size[1] % 16 == 0):
            notes.append(f"WRONG SIZE now {size[0]}x{size[1]}")
        if rel not in refs and cat not in ("entity",):
            notes.append("not referenced by any model/equipment json")
        if cat in ("tool", "weapon"):
            notes.append(f"shape {tool_kinds[stem]}")
        rows.append({"path": f"src/main/resources/assets/hardscrabble/textures/{rel}", "category": cat, "subject": subj,
                     "material": material_of(stem) if cat != "entity" else "", "model": model,
                     "size": f"{need[0]}x{need[1]}" if need else f"{size[0]}x{size[1]}", "batch": BATCH[cat],
                     "notes": "; ".join(notes)})
    missing = sorted(t for t in refs if not (pix.TEXTURES / t).exists())
    for t in missing:
        rows.append({"path": f"src/main/resources/assets/hardscrabble/textures/{t}", "category": "MISSING", "subject": "",
                     "material": material_of(Path(t).stem), "model": " ".join(sorted({par for _, par in refs[t]})), "size": "",
                     "batch": "", "notes": "referenced by " + ", ".join(sorted({f for f, _ in refs[t]}))})
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0]))
        w.writeheader()
        w.writerows(rows)
    print(f"{OUT}: {len(rows)} rows")
    print("per batch:", dict(sorted(Counter(r["batch"] for r in rows if r["batch"]).items())))
    print("per category:", dict(sorted(Counter(r["category"] for r in rows).items())))
    print("wrong size:", sum("WRONG SIZE" in r["notes"] for r in rows), " missing:", missing or "none")


if __name__ == "__main__":
    main()

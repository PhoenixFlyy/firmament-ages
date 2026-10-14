#!/usr/bin/env python3
"""Firmament Ages - why an item lands in a later Age than the Age map gives it: the blocking ingredient per recipe.

dev/derive_ages.py --explain names the one step that first reached an item. This report lists, for every item whose
final Age is later than its age_map Age, each recipe that makes it and the ingredient slots that are not reachable at
the age_map Age, with the first Age each slot becomes reachable ("never" = no Age). A recipe whose slots are all
reachable but that still does not fire waits for its station, a recipe lock or another output (shown as "fires: no").
Written for the Create andesite tier sweep (Modpack-Planung/research-raw/fb3-create-tier-sweep.md).

Usage
  python dev/age_blockers.py [--dump-dir DIR] SELECTOR...
    SELECTOR: an item id (create:chute) or a namespace with a colon (create:, sophisticatedbackpacks:)
"""
import argparse
import collections
import contextlib
import io
import os
import sys

DEV = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, DEV)
import derive_ages as da  # noqa: E402

AGES = da.AGES


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--dump-dir", default=os.path.join(da.TEST_SERVER, "local", "firmages"))
    ap.add_argument("selectors", nargs="+")
    a = ap.parse_args()
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    with contextlib.redirect_stdout(io.StringIO()):
        res = da.derive(a.dump_dir, 0)
    base, first, hidden, tags = res["base"], res["first"], set(res["hidden"]), res["tags"]
    walk, recs = res["walk"], res["recs"]

    def final(i):
        if base.get(i) == "disabled" or i in hidden:
            return "disabled"
        f = first.get(i)
        return f if f and AGES.index(f) > AGES.index(base[i]) else base[i]

    def picked(i):
        return any(i == s or (s.endswith(":") and i.startswith(s)) for s in a.selectors)
    items = sorted(i for i in base if picked(i) and base[i] in AGES and final(i) != base[i])
    makes = collections.defaultdict(list)
    for rid, sl, os_ in recs:
        for o in os_:
            makes[o].append((rid, sl))
    walks = {}

    def slot_first(slot):
        got = [first[m] for k, x in slot for m in ([x] if k != "tag" else tags.get(x, [])) if m in first]
        return min(got, key=AGES.index) if got else "never"
    for i in items:
        n = AGES.index(base[i])
        if n not in walks:
            have, live, _ = walk(n)
            walks[n] = (have, {rid for rid, _, _ in live})
        have, live_ids = walks[n]
        print(f"== {i}: age_map {base[i]}, final {final(i)} (via {res['via'].get(i)})")
        for rid, sl in makes.get(i, []):
            block = [(" | ".join(("#" if k == "tag" else "") + x for k, x in s), slot_first(s)) for s in sl
                     if not any(m in have for k, x in s for m in ([x] if k != "tag" else tags.get(x, [])))]
            print(f"   {rid}: " + (", ".join(f"{s} ({f})" for s, f in block) if block else
                                    f"all ingredients at {base[i]}; fires: {'yes' if rid in live_ids else 'no'}"))
        if not makes.get(i):
            print("   no recipe makes it")
    print(f"{len(items)} items")


if __name__ == "__main__":
    main()

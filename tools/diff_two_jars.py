# -*- coding: utf-8 -*-
"""
diff_two_jars.py  --  which entries differ between two mod jars?

WHY
===
"the installed jar is not the build output" is not actionable on its own.
This says exactly WHICH entries differ (added / removed / same name different
content), so you can tell "another session installed a slightly different build"
apart from "the installed copy is simply older".

USAGE
=====
    python tools/diff_two_jars.py <jarA> <jarB>
"""

import sys
import zipfile


def load(path):
    out = {}
    with zipfile.ZipFile(path) as z:
        for info in z.infolist():
            out[info.filename] = (info.file_size, info.CRC)
    return out


def main(argv):
    if len(argv) != 3:
        print(__doc__)
        return 2
    a, b = argv[1], argv[2]
    da, db = load(a), load(b)

    only_a = sorted(set(da) - set(db))
    only_b = sorted(set(db) - set(da))
    both = sorted(set(da) & set(db))
    differing = [n for n in both if da[n] != db[n]]

    def show(title, items, limit=40):
        print("%s: %d" % (title, len(items)))
        for n in items[:limit]:
            print("   %s" % n)
        if len(items) > limit:
            print("   ... and %d more" % (len(items) - limit))

    print("A = %s  (%d entries)" % (a, len(da)))
    print("B = %s  (%d entries)" % (b, len(db)))
    print("")
    show("only in A", only_a)
    show("only in B", only_b)
    show("same name, DIFFERENT content", differing)
    print("")
    print("identical entries: %d / %d" % (len(both) - len(differing), len(both)))
    return 1 if (only_a or only_b or differing) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))

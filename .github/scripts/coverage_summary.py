#!/usr/bin/env python3
"""Turn a JaCoCo XML report into a Markdown summary for $GITHUB_STEP_SUMMARY.

Usage: coverage_summary.py path/to/jacocoTestReport.xml [package-prefix]
"""
import sys
import xml.etree.ElementTree as ET

PREFIX = sys.argv[2] if len(sys.argv) > 2 else "com/rpeters/jellyfin/"


def counters(node):
    out = {}
    for c in node.findall("counter"):
        out[c.get("type")] = (int(c.get("covered")), int(c.get("missed")))
    return out


def pct(pair):
    covered, missed = pair
    total = covered + missed
    return 100.0 * covered / total if total else 0.0


def main():
    root = ET.parse(sys.argv[1]).getroot()
    total = counters(root)
    line = total.get("LINE", (0, 0))
    branch = total.get("BRANCH", (0, 0))

    print("## Unit-test coverage\n")
    print(f"**Lines:** {pct(line):.1f}% ({line[0]:,} of {sum(line):,})  ")
    print(f"**Branches:** {pct(branch):.1f}% ({branch[0]:,} of {sum(branch):,})\n")

    # Roll packages up two levels below the prefix (e.g. ui/screens, data/repository)
    groups = {}
    for pkg in root.findall("package"):
        name = pkg.get("name")
        if name + "/" == PREFIX:
            short = ""
        elif name.startswith(PREFIX):
            short = name[len(PREFIX):]
        else:
            short = name
        key = "/".join(short.split("/")[:2]) or "(app root)"
        cov, miss = counters(pkg).get("LINE", (0, 0))
        g = groups.setdefault(key, [0, 0])
        g[0] += cov
        g[1] += miss

    rows = sorted(groups.items(), key=lambda kv: kv[1][1], reverse=True)
    print("<details><summary>By area (most uncovered lines first)</summary>\n")
    print("| Area | Line coverage | Uncovered lines |")
    print("|---|---:|---:|")
    for key, (cov, miss) in rows:
        print(f"| `{key}` | {pct((cov, miss)):.1f}% | {miss:,} |")
    print("\n</details>")


if __name__ == "__main__":
    main()

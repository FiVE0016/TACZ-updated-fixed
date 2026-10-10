#!/usr/bin/env python3
"""Fail the build if a release jar ships LuaJ classes we deliberately removed.

WHY
---
Gun packs are downloadable content and may contain Lua scripts. The mod runs them in a
stripped LuaJ: ``ScriptManager`` loads only BaseLib / PackageLib / Bit32Lib / TableLib /
StringLib / MathLib, so there is no ``io``, ``os`` or ``luajava`` library. The bundled jars
also have the classes *behind* those libraries physically removed, so the capability is out
of the artefact entirely, not merely unregistered.

That removal is expressed in ``build.gradle`` as a hand-written list of exclusion patterns,
which is exactly the kind of thing that rots silently: the pattern ``luajc.class`` was already
matching nothing useful, because the LuaJ command-line tools compile into the *default package*
and their classes are named ``luajc$1``, ``luajc$InputFile`` and ``luajc$LocalClassLoader``.
Those three shipped in every jar until 2026-10-10. Nobody noticed, because nothing checked.

This script is therefore intentionally dumb and read-only: open every release jar, fail loudly
if a forbidden name reappears. Fix the exclusion pattern rather than this file.

USAGE
-----
    python3 scripts/verify_jar_exclusions.py            # checks build/libs
    python3 scripts/verify_jar_exclusions.py <dir>      # checks another directory
"""

from __future__ import annotations

import sys
import zipfile
from pathlib import Path

# Substrings that must not appear in any entry name of a shipped jar.
#
# Keep this list in sync with ext.bundledExclusions in the root build.gradle.
#   IoLib / OsLib / JseIoLib / JseOsLib  -> filesystem and process access from Lua
#   JseProcess                           -> starting external processes
#   JsePlatform                          -> the glue that makes package.loadlib able to load
#                                           a native library; without it loadlib cannot resolve
#   LuajavaLib                           -> arbitrary Java class access from Lua
#   luajc                                -> LuaJ's Lua-to-Java-bytecode compiler
#   /script/                             -> the javax.script front end (removed after it caused
#                                           a NoClassDefFoundError crash on 26.3)
FORBIDDEN = (
    "IoLib",
    "OsLib",
    "JseProcess",
    "JsePlatform",
    "LuajavaLib",
    "luajc",
    "/script/",
)

# Entries whose names merely *contain* a forbidden substring for unrelated reasons.
# (Empty today; kept so a future false positive has an obvious home.)
ALLOWED = ()


def jar_entries(jar: Path) -> list[str]:
    with zipfile.ZipFile(jar) as zf:
        return zf.namelist()


def main(argv: list[str]) -> int:
    directory = Path(argv[1]) if len(argv) > 1 else Path("build/libs")
    if not directory.is_dir():
        print(f"::error::jar directory not found: {directory}")
        return 1

    # Only shipping artefacts: skip -slim (no bundled libraries) and -sources.
    jars = sorted(
        jar
        for jar in directory.glob("*.jar")
        if not jar.name.endswith(("-slim.jar", "-sources.jar"))
    )
    if not jars:
        print(f"::error::no release jars found in {directory}")
        return 1

    failures: list[str] = []
    for jar in jars:
        entries = jar_entries(jar)
        found: dict[str, list[str]] = {}
        for needle in FORBIDDEN:
            hits = [
                name
                for name in entries
                if needle in name and not any(ok in name for ok in ALLOWED)
            ]
            if hits:
                found[needle] = hits

        if found:
            failures.append(jar.name)
            print(f"FAIL {jar.name}  ({len(entries)} entries)")
            for needle, hits in found.items():
                print(f"     {needle}: {len(hits)} entr{'y' if len(hits) == 1 else 'ies'}")
                for name in hits[:5]:
                    print(f"         {name}")
                if len(hits) > 5:
                    print(f"         ... and {len(hits) - 5} more")
        else:
            print(f"ok   {jar.name}  ({len(entries)} entries, none of the forbidden classes)")

    if failures:
        print()
        print("::error::A release jar still contains LuaJ capability classes. "
              f"Affected: {', '.join(failures)}. "
              "Fix ext.bundledExclusions in build.gradle (remember: the Figura fork's command "
              "line tools compile into the default package, so wildcards must cover luajc$*).")
        return 1

    print()
    print(f"All {len(jars)} release jar(s) are clean.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))

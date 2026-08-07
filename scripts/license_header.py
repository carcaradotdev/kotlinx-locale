#!/usr/bin/env python3
# Copyright 2026 Carcara.dev
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Applies and checks the Apache license header on every source file.

The header is not written down here. It is read out of the LICENSE file at the
repository root, from the boilerplate the Apache License carries in its own
appendix, so the two cannot disagree: change the copyright line in LICENSE and
every source file is stale until this script runs again.

    python3 scripts/license_header.py check    # exits 1 and names the offenders
    python3 scripts/license_header.py apply    # rewrites them in place

Files come from `git ls-files`, so nothing under a build directory is reached and
an untracked scratch file is never rewritten.

Generated Kotlin sources are in scope like any other. Their `// GENERATED`
marker stays directly under the header, and the emitters in
:kotlinx-locale-codegen-emitters write the same header, so regenerating does not
undo this.
"""

from __future__ import annotations

import argparse
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

# The marker that identifies a header as ours when deciding whether an existing
# one is current or stale. It is a phrase from the license text itself rather
# than a sentinel comment, so a header copied from any other Apache project is
# recognized and replaced rather than duplicated.
LICENSE_MARKER = "Licensed under the Apache License"

# A block comment for the languages that have one, `#` lines for the rest. The
# appendix asks for "the appropriate comment syntax for the file format" and
# says nothing more, so the only rule is that the text survives unchanged.
BLOCK_COMMENT = {".kt", ".kts", ".mjs"}
HASH_COMMENT = {".py", ".yml", ".yaml", ".toml", ".properties"}

# Written by the Gradle wrapper task, not by anyone here, so a header would come
# straight back off on the next `./gradlew wrapper`.
EXCLUDED_FILES = {
    "gradle/wrapper/gradle-wrapper.properties",
}

# Third-party data vendored verbatim. It carries the license of whoever
# published it and must not be given ours.
EXCLUDED_PREFIXES = (
    "codegen/src/main/resources/",
)


def repository_root() -> Path:
    out = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        check=True,
        capture_output=True,
        text=True,
    )
    return Path(out.stdout.strip())


def license_notice(root: Path) -> list[str]:
    """The boilerplate notice out of LICENSE, dedented, one entry per line.

    The Apache License ends with an appendix that explains how to apply it and
    then shows the notice to attach. Everything from the copyright line to the
    end of the file is that notice, already carrying this project's own
    identifying information in place of the bracketed placeholders.
    """
    lines = (root / "LICENSE").read_text(encoding="utf-8").split("\n")

    try:
        appendix = next(i for i, line in enumerate(lines) if "APPENDIX:" in line)
    except StopIteration:
        sys.exit("LICENSE has no APPENDIX section to take the notice from")

    try:
        start = next(
            i
            for i in range(appendix, len(lines))
            if lines[i].strip().startswith("Copyright ")
        )
    except StopIteration:
        sys.exit("LICENSE has no copyright line after its APPENDIX heading")

    notice = [line[3:] if line.startswith("   ") else line for line in lines[start:]]
    while notice and not notice[-1].strip():
        notice.pop()

    if not any(LICENSE_MARKER in line for line in notice):
        sys.exit(f"the notice taken from LICENSE does not contain {LICENSE_MARKER!r}")
    return notice


def render(notice: list[str], suffix: str) -> str:
    """The notice in the comment syntax of a file with this suffix.

    Trailing whitespace is left off the blank comment lines, which is what
    .editorconfig asks of every file in the repository and what ktlint enforces
    on the Kotlin ones.
    """
    if suffix in BLOCK_COMMENT:
        body = "\n".join((" * " + line).rstrip() for line in notice)
        return f"/*\n{body}\n */\n"
    body = "\n".join(("# " + line).rstrip() for line in notice)
    return body + "\n"


def split_shebang(text: str) -> tuple[str, str]:
    """Peels off a `#!` line, which has to stay the first bytes of the file."""
    if text.startswith("#!"):
        line, _, rest = text.partition("\n")
        return line + "\n", rest
    return "", text


def strip_existing(text: str, suffix: str) -> str:
    """Removes a leading license header, current or stale, and the blank line under it.

    A file that opens with a comment which is not a license header, a KDoc on
    the first declaration or the `// GENERATED` marker, is left alone.
    """
    end = None
    if suffix in BLOCK_COMMENT:
        if text.startswith("/*"):
            close = text.find("*/")
            if close != -1 and LICENSE_MARKER in text[:close]:
                end = close + 2
    else:
        consumed = 0
        for line in text.split("\n"):
            if not line.startswith("#"):
                break
            consumed += len(line) + 1
        if consumed and LICENSE_MARKER in text[:consumed]:
            end = consumed

    if end is None:
        return text
    return text[end:].lstrip("\n")


def expected(text: str, header: str, suffix: str) -> str:
    shebang, body = split_shebang(text)
    body = strip_existing(body, suffix)
    if not body.strip():
        return shebang + header
    return shebang + header + "\n" + body


@dataclass
class Result:
    path: str
    status: str  # "ok", "missing" or "stale"


def inspect(root: Path, path: str, notice: list[str]) -> Result:
    suffix = Path(path).suffix
    header = render(notice, suffix)
    text = (root / path).read_text(encoding="utf-8")

    if text == expected(text, header, suffix):
        return Result(path, "ok")
    _, body = split_shebang(text)
    stale = LICENSE_MARKER in body[: len(header) + 200]
    return Result(path, "stale" if stale else "missing")


def source_files(root: Path) -> list[str]:
    out = subprocess.run(
        ["git", "ls-files", "-z"],
        check=True,
        capture_output=True,
        text=True,
        cwd=root,
    )
    paths = [p for p in out.stdout.split("\0") if p]
    return sorted(
        path
        for path in paths
        if Path(path).suffix in BLOCK_COMMENT | HASH_COMMENT
        and path not in EXCLUDED_FILES
        and not path.startswith(EXCLUDED_PREFIXES)
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("mode", choices=("check", "apply"))
    args = parser.parse_args()

    root = repository_root()
    notice = license_notice(root)
    paths = source_files(root)
    if not paths:
        sys.exit("no source files matched, which means the file list is wrong")

    offenders = [r for r in (inspect(root, p, notice) for p in paths) if r.status != "ok"]

    if args.mode == "check":
        if not offenders:
            print(f"license header: {len(paths)} files, all current")
            return 0
        for result in offenders:
            try:
                print(f"{result.path}: license header {result.status}")
            except BrokenPipeError:
                # Someone piped the listing into `head`. Their business.
                return 1
        print(
            f"\n{len(offenders)} of {len(paths)} files are wrong. "
            f"Run: python3 scripts/license_header.py apply",
            file=sys.stderr,
        )
        return 1

    for result in offenders:
        path = root / result.path
        suffix = Path(result.path).suffix
        text = path.read_text(encoding="utf-8")
        path.write_text(expected(text, render(notice, suffix), suffix), encoding="utf-8")
    print(f"license header: rewrote {len(offenders)} of {len(paths)} files")
    return 0


if __name__ == "__main__":
    sys.exit(main())

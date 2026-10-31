#!/usr/bin/env python3
"""Catch user-facing Kotlin string literals in shared UI and Android app code."""

from pathlib import Path
import re
import sys


ROOT = Path(__file__).resolve().parents[1]
SOURCE_PATHS = (
    ROOT / "shared/src/commonMain/kotlin/info/anodsplace/evtimer/ui",
    ROOT / "shared/src/commonMain/kotlin/info/anodsplace/evtimer/data/ChargingNotificationText.kt",
    ROOT / "androidApp/src/main/kotlin",
)
PATTERNS = (
    re.compile(r'\bText\s*\(\s*(?:text\s*=\s*)?"(?:\\.|[^"\\])*[A-Za-z]'),
    re.compile(
        r'\b(?:label|title|text|contentDescription|onClickLabel|stateDescription|hint|placeholder)\s*=\s*'
        r'"(?:\\.|[^"\\])*[A-Za-z]'
    ),
    re.compile(
        r'\bsummary\s*=\s*\{\s*[A-Za-z_]\w*\s*->\s*"(?:\\.|[^"\\])*[A-Za-z]'
    ),
    re.compile(
        r'\.(?:setContentTitle|setContentText|setTicker|setSubText|bigText|setTitle|setMessage)'
        r'\s*\(\s*"(?:\\.|[^"\\])*[A-Za-z]'
    ),
    re.compile(r'\bappend\s*\(\s*"(?:\\.|[^"\\])*[A-Za-z]'),
    re.compile(
        r'\b(?:Toast\.makeText|Snackbar\.make)\s*\([^)]*"(?:\\.|[^"\\])*[A-Za-z]',
        re.DOTALL,
    ),
)


def source_before_previews(source: str) -> str:
    # Preview declarations are kept at the end of the Compose UI source files.
    return source.split("@Preview", maxsplit=1)[0]


def main() -> int:
    failures: list[str] = []
    for source_path in SOURCE_PATHS:
        paths = sorted(source_path.rglob("*.kt")) if source_path.is_dir() else [source_path]
        for path in paths:
            source = source_before_previews(path.read_text(encoding="utf-8"))
            for pattern in PATTERNS:
                for match in pattern.finditer(source):
                    line = source.count("\n", 0, match.start()) + 1
                    failures.append(f"{path.relative_to(ROOT)}:{line}: {match.group(0)!r}")

    if failures:
        print("Hardcoded user-visible strings found; move them to resources:")
        print("\n".join(failures))
        return 1

    print("No hardcoded user-visible strings found in shared UI or Android app code.")
    return 0


if __name__ == "__main__":
    sys.exit(main())

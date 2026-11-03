#!/usr/bin/env python3
"""Verify that an Android App Bundle contains its compiled Baseline Profile."""

import sys
import zipfile
from pathlib import Path


PROFILE_PATH = "BUNDLE-METADATA/com.android.tools.build.profiles/baseline.prof"


def main() -> int:
    if len(sys.argv) != 2:
        print(f"Usage: {Path(sys.argv[0]).name} <release.aab>", file=sys.stderr)
        return 2

    bundle_path = Path(sys.argv[1])
    if not bundle_path.is_file():
        print(f"Release bundle not found: {bundle_path}", file=sys.stderr)
        return 2

    try:
        with zipfile.ZipFile(bundle_path) as bundle:
            if PROFILE_PATH not in bundle.namelist():
                print(
                    f"Baseline Profile missing from {bundle_path}: {PROFILE_PATH}",
                    file=sys.stderr,
                )
                return 1
            profile = bundle.read(PROFILE_PATH)
            if not profile.startswith(b"pro\x00") or len(profile) <= 8:
                print(
                    f"Invalid or empty compiled Baseline Profile in {bundle_path}",
                    file=sys.stderr,
                )
                return 1
    except zipfile.BadZipFile:
        print(f"Invalid Android App Bundle: {bundle_path}", file=sys.stderr)
        return 2

    print(f"Verified Baseline Profile in {bundle_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""Produce an evidence report of classes actually bundled in release JARs.

Presence of a package does not identify its version or establish exploitability.
Compare this report with the resolved Maven trees and with the deployed host.
"""
from __future__ import annotations

import hashlib
import json
import pathlib
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "dependency-audit"
MODULES = (
    "voxelcore-v1_12",
    "voxelcore-v1_14",
    "voxelcore-v1_19_4",
    "voxelcore-v1_20_5",
    "voxelcore-v1_21_4",
    "voxelcore-v26_2",
    "voxelcore-v26_3",
)
PREFIXES = {
    "relocated_snakeyaml": "org/voxelhorizons/shaded/snakeyaml/",
    "unrelocated_snakeyaml": "org/yaml/snakeyaml/",
    "guava": "com/google/common/",
    "gson": "com/google/gson/",
    "netty": "io/netty/",
    "aircompressor": "io/airlift/compress/",
    "plexus_utils": "org/codehaus/plexus/util/",
}
ERRORS = []
RECORDS = []


def inspect(label: str, file: pathlib.Path, expect_relocated_yaml: bool) -> None:
    with zipfile.ZipFile(file) as archive:
        names = archive.namelist()
        classes = [name for name in names if name.endswith(".class")]
        counts = {
            key: sum(name.startswith(prefix) for name in classes)
            for key, prefix in PREFIXES.items()
        }
        if expect_relocated_yaml:
            if counts["relocated_snakeyaml"] == 0:
                ERRORS.append(f"{label}: patched relocated SnakeYAML classes missing")
            if counts["unrelocated_snakeyaml"] != 0:
                ERRORS.append(f"{label}: unrelocated SnakeYAML classes unexpectedly bundled")

        RECORDS.append({
            "module": label,
            "jar": file.name,
            "sha256": hashlib.sha256(file.read_bytes()).hexdigest(),
            "class_count": len(classes),
            "package_class_counts": counts,
        })


for module in MODULES:
    matches = list((ROOT / module / "target").glob("VoxelCore-*-mc*.jar"))
    if len(matches) != 1:
        ERRORS.append(f"{module}: expected one distribution JAR, found {len(matches)}")
        continue
    inspect(module, matches[0], True)

extension_matches = list((ROOT / "voxelcore-geyser-extension" / "target").glob("VoxelCore-GeyserExtension-*.jar"))
if len(extension_matches) != 1:
    ERRORS.append(f"voxelcore-geyser-extension: expected one JAR, found {len(extension_matches)}")
else:
    inspect("voxelcore-geyser-extension", extension_matches[0], False)

OUT.mkdir(exist_ok=True)
(OUT / "shipped-classes.json").write_text(
    json.dumps({"artifacts": RECORDS, "validation_errors": ERRORS}, indent=2) + "\n",
    encoding="utf-8",
)
headers = ["Module", "Relocated YAML", "Unrelocated YAML", "Guava", "Gson", "Netty", "Aircompressor", "Plexus"]
lines = [
    "# VoxelCore: shipped bytecode evidence",
    "",
    "**This is class presence, not dependency-version evidence or a vulnerability verdict.**",
    "Compare with the Maven dependency trees and the versions installed on Paper and Geyser.",
    "",
    "| " + " | ".join(headers) + " |",
    "| " + " | ".join("---" for _ in headers) + " |",
]
for r in RECORDS:
    c = r["package_class_counts"]
    lines.append("| " + " | ".join(str(s) for s in (
        r["module"],
        c["relocated_snakeyaml"], c["unrelocated_snakeyaml"],
        c["guava"], c["gson"], c["netty"], c["aircompressor"], c["plexus_utils"],
    )) + " |")
lines.extend([
    "",
    "## Interpretation",
    "",
    "- Bundled class presence must be paired with dependency versions and provenance.",
    "- Maven provided dependencies and their transitives are normally supplied by Paper/Geyser, not shaded into VoxelCore.",
    "- A vulnerable host-supplied dependency must be assessed and remediated in the host even when absent from these JARs.",
    "- Relocated SnakeYAML is expected in each Java distribution; an unrelocated copy fails this check.",
    "",
])
if ERRORS:
    lines += ["## Validation errors", ""] + ["- " + e for e in ERRORS] + [""]
(OUT / "shipped-classes.md").write_text("\n".join(lines), encoding="utf-8")
print("\n".join(lines))
if ERRORS:
    sys.exit(1)

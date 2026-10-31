# VoxelCore dependency vulnerability triage

The Dependabot **Critical/High** dashboard is a dependency-graph view. Multiple alerts may refer to the same upstream copy across Maven modules. They do **not** by themselves prove the vulnerable implementation is shipped in a VoxelCore distribution.

## Evidence from build CI

The `Build VoxelCore Distributions` workflow now produces a `VoxelCore-Dependency-Audit` artifact containing:

- `dependency-trees/<module>.txt`: Maven-resolved dependency versions and scopes for every module, including `provided` and test-scope dependencies.
- `shipped-classes.md` and `shipped-classes.json`: bytecode actually present in each of the seven Java-version JARs and the separate Geyser extension, plus SHA-256 hashes.

The script also fails CI when the shaded Java plugin distributions omit the relocated patched SnakeYAML or include an unrelocated SnakeYAML copy. Other package classes are **reported rather than auto-dismissed**: presence alone doesn't establish version or exploitability.

## Outstanding vulnerability groups

| Group | Likely origin to check | Decision required |
|---|---|---|
| SnakeYAML | Patched 2.4 copy is directly compiled/relocated in `voxelcore-common`; old copies may appear through Spigot APIs | Ensure **actual JARs** have only the relocated copy, and trace old transitive artifacts |
| Guava | Spigot/Paper and other compilation APIs | Check dependency-tree scope and JAR presence. A deserialization attack requires attacker-reachable Java deserialization |
| Gson | Direct pack compiler uses 2.11.0 (above the advisory's 2.8.9 fix); older copies may be transitive | Check selected versions and JAR presence, particularly legacy modules |
| Netty, aircompressor | Geyser API/core `provided` dependencies, plus modern `netty-transport` (also `provided`) | Inspect installed Paper/Geyser runtimes, verify exact Netty/aircompressor versions and affected features |
| Plexus Utils | Paper API transitive or Maven plugin tooling | Determine whether any Plexus bytecode is shipped or merely used while building |

Tracked work: [Netty/aircompressor #114](https://github.com/VoxelHorizons/VoxelCore/issues/114); [Guava/Gson/Plexus #115](https://github.com/VoxelHorizons/VoxelCore/issues/115).

## Before closing an alert

1. Match the **specific GHSA**, affected range and dependency path to the build report.
2. Confirm whether the vulnerable implementation is bundled, host-provided, or build-only.
3. For bundled vulnerable code, patch it, rerun CI and validate actual JAR contents.
4. For host-provided code, confirm the deployed server/Geyser version and mitigations; upgrade at the host level when necessary.
5. If genuinely not applicable, record concrete evidence and scope in a Dependabot dismissal. Never dismiss solely because CI is green, the dashboard has many alerts, or the dependency is `provided`.

**Limitations:** An absence of package class files in the plugin JAR doesn't prove the Minecraft server or Geyser process is secure; those runtimes may ship vulnerable libraries independently. A present package name doesn't establish a vulnerable version. The audit is evidence collection, not a vulnerability scanner.

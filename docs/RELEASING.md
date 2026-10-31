# VoxelCore — Release engineering and operator checklist

This project uses read-only CI for pull requests and branch pushes. A normal commit to `main` **must not** create or overwrite a GitHub Release. CI artifacts are test outputs, not certified public distributions.

## Version policy

- Adopt independent [Semantic Versioning](https://semver.org/): `MAJOR.MINOR.PATCH`, with pre-releases such as `1.0.0-rc.1`.
- Minecraft server versions belong in distribution JAR names and the tested compatibility table, **not** in the plugin's SemVer.
- On normal development branches, retain a `-SNAPSHOT` version. A reviewed release-candidate branch may temporarily set an explicit `-rc.N` version for complete CI and acceptance testing. **Do not merge or tag that candidate until the acceptance gates are met.** Do not mark an unverified build stable merely to exercise the workflow.
- Before a release, update every module/parent `pom.xml` as appropriate. Check plugin descriptor versions, supported server versions, dependencies, and current changelog.
- Keep the JARs produced by a single reviewed release build together with a `SHA256SUMS` file.

## Creating a release candidate (owner only)

1. Merge the reviewed release/version PR, then verify the default-branch CI and manual Java/Bedrock acceptance matrix.
2. Ensure all release dependencies and source revisions are pinned and compatible. No `-SNAPSHOT` dependencies may be used for a stable addon release.
3. Create an **existing annotated tag** on the approved `main` commit, such as `v1.0.0-rc.1`, where the tag name matches the Maven version on that commit.
4. From the repository's Actions tab, run **Prepare VoxelCore Release Draft** on `main` with that tag. Only the owner account `Fibbicles` is currently allowed to initiate the workflow.
5. The workflow verifies the existing tag and its ancestry on `main`, checks version metadata, tests/builds from that exact commit, produces hashes, and creates a **draft** GitHub Release, without exposing a public download.
6. Inspect release files, verify SHA-256 checksums and installation on the certified server matrix, then manually publish the draft. Never replace assets on an already published version.
7. Enable a protected `release` environment and immutable GitHub Releases in repository settings, and protect `v*` tags, before creating the first public stable release.

**Required GitHub governance:** A developer with Write access must not be able to trigger a public release by pushing to `main`. The release workflow deliberately does not run on `push` and the write token is granted only to its final draft-creation job. An environment name by itself is *not* an approval rule; configure required reviewers separately on GitHub.

**Important:** The draft workflow is intentionally blocked while the Maven version is `-SNAPSHOT`. Addon release jobs also reject `-SNAPSHOT` VoxelCore/Furniture dependencies. Review any pinned dependency commit references before the first release.

## Discord announcements — published releases only

The workflow `.github/workflows/discord-release.yml` announces **only** the `release.published` GitHub event to the VoxelCore Updates channel. Saving a draft, editing release notes, pushing commits, opening PRs, and creating tags do **not** send announcements. Both stable releases and published pre-releases are announced.

Before the first published release, set the repository-level Actions secret `DISCORD_RELEASE_WEBHOOK_URL`:

```powershell
gh secret set DISCORD_RELEASE_WEBHOOK_URL -R VoxelHorizons/VoxelCore
```

When prompted, paste the Discord webhook URL privately. The workflow accepts the ordinary Discord URL or the GitHub-compatible variant ending in `/github`; it removes that suffix because it sends a formatted Discord message rather than the raw GitHub payload. Verify the name exists with `gh secret list -R VoxelHorizons/VoxelCore`. Never commit the URL, add it to release notes, or include it in a support log.

Do not also create a native GitHub repository webhook to the same Discord channel: it would produce duplicate notifications and GitHub's native `release` event subscription includes release activities other than publication. Discord notifications do not publish releases; they run only *after* publication, so a notification failure cannot unpublish or block the GitHub Release.

## VoxelCore 1.0.0-rc.1 gate

This proposed pre-release is a **candidate**, not a declaration of 1.0 or automatic publication. Before merging the version PR and creating `v1.0.0-rc.1`, verify the full Maven reactor build, all eight required Minecraft smoke tests, Java and Bedrock runtime acceptance on the VoxelHorizons server, generated pack integrity, compatibility of the separately distributed Geyser extension, and any blockers tracked in [ROADMAP.md](../ROADMAP.md). Record which client/server versions and pack builds were checked.

The Geyser extension uses upstream `2.12.0-SNAPSHOT` build-time dependencies; investigate reproducible dependency pinning before any certified stable release. Confirm the RC was built from the exact source commit that will be tagged. Do not publish draft release assets or Discord announcements without manually reviewing the release notes and downloads.

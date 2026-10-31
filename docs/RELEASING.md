# VoxelCore — Release engineering and operator checklist

This project uses read-only CI for pull requests and branch pushes. A normal commit to `main` **must not** create or overwrite a GitHub Release. CI artifacts are test outputs, not certified public distributions.

## Version policy

- Use numbered [Semantic Versions](https://semver.org/) without prerelease suffixes: `MAJOR.MINOR.PATCH`. Start at `1.0.0`; use `1.0.1` for backward-compatible bug fixes, `1.1.0` for backward-compatible feature additions, and `2.0.0` for breaking changes.
- Minecraft server versions belong in distribution JAR names and the tested compatibility table, **not** in the plugin's SemVer.
- Prepare a numbered Maven version, such as `1.0.0`, on a reviewed PR, but **keep its GitHub Release as a draft** until acceptance is complete. Draft status is separate from the version; no `-rc` or `-SNAPSHOT` suffix is required.
- Before a release, update every module/parent `pom.xml` as appropriate. Check plugin descriptor versions, supported server versions, dependencies, and current changelog.
- Keep the JARs produced by a single reviewed release build together with a `SHA256SUMS` file.

## Preparing a numbered release (owner only)

1. Review the version PR and its required checks; finish the Java/Bedrock acceptance matrix before approving public publication. Merge the version PR only after reviewing the remaining release gates, and recheck default-branch CI.
2. Ensure all release dependencies and source revisions are pinned and compatible. No `-SNAPSHOT` dependencies may be used for a stable addon release.
3. Create a protected **annotated tag** on the approved `main` commit, such as `v1.0.0`. The `v` is only the Git tag prefix, and the numeric version must match the Maven version on that commit.
4. From the repository's Actions tab, run **Prepare VoxelCore Release Draft** on `main` with that tag. Only the owner account `Fibbicles` is currently allowed to initiate the workflow.
5. The workflow verifies the existing tag and its ancestry on `main`, checks Maven and embedded JAR versions, tests/builds from that exact commit, produces hashes, and creates a **draft** GitHub Release, without exposing a public download.
6. Inspect release files, verify SHA-256 checksums and installation on the supported server matrix, then manually publish the draft. Mark the stable release as Latest where appropriate. Never replace an already published artifact under the same version.
7. Enable a protected `release` environment and immutable GitHub Releases in repository settings, and protect `v*` tags, before creating the first public stable release.

**Required GitHub governance:** A developer with Write access must not be able to trigger a public release by pushing to `main`. The release workflow deliberately does not run on `push` and the write token is granted only to its final draft-creation job. An environment name by itself is *not* an approval rule; configure required reviewers separately on GitHub.

**Important:** The draft workflow requires a numbered Maven version such as `1.0.0` and a matching existing tag. Addon release jobs reject `-SNAPSHOT` VoxelCore/Furniture dependencies. Geyser API/Core currently use a `2.12.1-SNAPSHOT` compile-time dependency with Maven scope `provided`; verify the installed runtime's exact build and the VoxelCore Bedrock extension before stable publication. Also assess unresolved startup reliability issue #96 prior to publication.

## Discord announcements — published releases only

The workflow `.github/workflows/discord-release.yml` announces **only** the `release.published` GitHub event to the VoxelCore Updates channel. Saving a draft, editing release notes, pushing commits, opening PRs, and creating tags do **not** send announcements. Both stable releases and published pre-releases are announced.

Before the first published release, set the repository-level Actions secret `DISCORD_RELEASE_WEBHOOK_URL`:

```powershell
gh secret set DISCORD_RELEASE_WEBHOOK_URL -R VoxelHorizons/VoxelCore
```

When prompted, paste the Discord webhook URL privately. The workflow accepts the ordinary Discord URL or the GitHub-compatible variant ending in `/github`; it removes that suffix because it sends a formatted Discord message rather than the raw GitHub payload. Verify the name exists with `gh secret list -R VoxelHorizons/VoxelCore`. Never commit the URL, add it to release notes, or include it in a support log.

Do not also create a native GitHub repository webhook to the same Discord channel: it would produce duplicate notifications and GitHub's native `release` event subscription includes release activities other than publication. Discord notifications do not publish releases; they run only *after* publication, so a notification failure cannot unpublish or block the GitHub Release.

# CI/CD

A self-hosted Linux VM runs GitHub Actions and produces a debug APK on every push to
`main` and every PR.

## Why a VM rather than GitHub-hosted runners

The two largest build inputs are not in git and cannot be:

| Input | Size | Why untracked |
| --- | --- | --- |
| `libs/game.jar` | 27 MB | vendored, regenerable |
| `assets/` | ~2.0 GB, ~125k files | extracted game data |

On a GitHub-hosted runner both would have to be fetched from scratch every run. On a
persistent VM they are fetched once into `/srv/aoh2de` and re-fetched only when the
upstream archive's ETag changes.

## Layout

```
/srv/aoh2de/
  assets/          extracted asset tree, passed to Gradle as -PassetsDir
  assets.stamp     ETag of the archive currently extracted
  libs/game.jar    vendored jar, copied into the workspace each run
  out/             last 10 built APKs, plus a `latest.apk` symlink
/opt/android-sdk   cmdline-tools, platform-tools, platforms;android-36, build-tools;36.0.0
```

`/srv/aoh2de` sits outside the runner workspace deliberately — `actions/checkout` runs
`git clean -ffdx`, which would delete a 2 GB in-workspace `assets/` on every run.

## Setup

1. Provision a VM (Ubuntu 24.04, **8 GB RAM / 60 GB disk minimum** — 4 GB Gradle heap,
   2 GB asset cache, ~1.8 GB per retained APK):

   ```bash
   sudo ./scripts/ci/provision-vm.sh
   ```

   It installs Temurin 25, the Android SDK, and the cache tree, then prints the runner
   registration commands. Register with the label `aoh2de-builder`.

2. Upload the build inputs to object storage, laid out as:

   ```
   s3://<bucket>/<prefix>/game.jar
   s3://<bucket>/<prefix>/assets.tar.zst     # tar of assets/, or assets/ itself at root
   ```

3. Add repository secrets (Settings → Secrets and variables → Actions):

   | Secret | Notes |
   | --- | --- |
   | `AOH2DE_S3_BUCKET` | `s3://bucket/prefix` |
   | `AWS_ACCESS_KEY_ID` | read-only key is enough |
   | `AWS_SECRET_ACCESS_KEY` | |
   | `AWS_S3_ENDPOINT` | only for non-AWS S3 (R2, MinIO); leave unset otherwise |

4. Prime the cache once on the VM, then push:

   ```bash
   AOH2DE_S3_BUCKET=s3://bucket/prefix ./scripts/ci/fetch-inputs.sh
   ```

## Notes

- **Shaders.** `assets/game/shader/` is the one part of the asset tree tracked in git.
  The workflow rsyncs it from the checkout over the cached tree before building, so git
  stays authoritative for those files even when the storage archive is stale.
- **Artifact size.** The debug APK is ~1.8 GB (assets are STORED, not deflated — see
  `packAssetsZip`). Uploading it takes minutes and consumes Actions storage. Every build
  also lands at `/srv/aoh2de/out/latest.apk` on the VM, so the upload can be turned off
  for a manual run via the `upload_artifact` input.
- **Concurrency.** Runs are serialized (`concurrency: android-debug`); the Gradle daemon,
  `~/.gradle`, and the asset cache are all single-instance on this box.
- **Gradle daemon is on**, unusually for CI, for the reason `gradle.properties` gives: the
  retained VFS avoids re-stat-ing 125k asset files each build.
- **No release signing yet.** Adding `assembleRelease` needs a keystore plus
  `signingConfigs` in `android/build.gradle`; nothing here assumes one.

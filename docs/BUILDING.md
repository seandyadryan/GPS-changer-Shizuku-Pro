# Build Shizuku Pro

## GitHub Actions

Open **Actions > Build Shizuku Pro > Run workflow**, choose `main`, and run it.
Enable **Publish the signed APK to GitHub Releases** to also publish a release.
Ordinary pushes, pull requests and schedules never start a runner in this fork.
There is one Linux job, a 45-minute limit, a Gradle cache, and seven-day artifact retention.
The workflow never commits or pushes source changes. A release is tied to the exact checked-out commit.
All four Android ABIs supported by upstream are retained; only the release variant is built.

The build runs existing release unit tests, Android API compatibility lint, and release assembly.
Download the signed APK and `SHA256SUMS.txt` from the run's artifacts, or from Releases if publishing was enabled.

## Signing

The repository's Actions secrets are:

| Secret | Content |
| --- | --- |
| `KEYSTORE` | Base64-encoded JKS signing file |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEYSTORE_ALIAS` | Signing alias |
| `KEYSTORE_ALIAS_PASSWORD` | Signing key password |

Keep a private backup of the original JKS and `signing.properties`. They are ignored by Git.
The initial local copies are `key.jks` and `signing.properties` in the project root.
Never rotate or recreate the key for an ordinary update: Android requires the same signing certificate.
Do not commit signing files or passwords. GitHub secrets cannot be downloaded to recover a lost key.

The package ID remains `moe.shizuku.privileged.api` for Shizuku client compatibility.
An installation signed by another maintainer must be uninstalled before installing this fork.
Stop its running server first; reboot if an old server remains active.

## Local build

Install Java 21 and the Android command-line tools. Accept Android SDK licenses, then install:

```sh
sdkmanager 'platforms;android-36' 'build-tools;37.0.0' 'ndk;29.0.14206865' 'cmake;3.31.6'
git submodule update --init --recursive
```

Set `ANDROID_HOME` to your SDK, or set `sdk.dir` in an ignored `local.properties`.

```sh
./gradlew :manager:testReleaseUnitTest :manager:lintRelease :manager:assembleRelease
```

On Windows, use `gradlew.bat`. Release APKs appear in `manager/build/outputs/apk/release/`.
With no `signing.properties`, upstream's local debug-key fallback is used. Published builds always require the four signing secrets.

## Upstream updates

This fork starts from rushiranpise/Shizuku-Next commit
`6e350d79641fb7f1a838832e8c0db8e4331e7090`, with API submodule
`59be73fda4b173b36ba06ff58e0c74e98cd112fa`.

The complete upstream Git history and Apache 2.0 license are preserved.
The `upstream` remote points to the source repository; `origin` points to this fork.
The original README is in `docs/UPSTREAM_README.md`.
Original workflows are archived in `docs/upstream-workflows/`, outside GitHub's workflow directory.
Do not copy them back without reviewing their schedules, secrets and upstream-specific automation.

This fork changes the display name, version suffix, APK name, update/bug-report destinations,
build portability, and CI configuration. It keeps the upstream manager, server, starter, shell,
stub, modules, translations, credits and Shizuku API compatibility.

#!/usr/bin/env bash
# Provisions a fresh Ubuntu 24.04 VM as a self-hosted GitHub Actions runner for this repo.
#
# Run once, as a sudo-capable non-root user:
#   sudo ./scripts/ci/provision-vm.sh
#   ./scripts/ci/provision-vm.sh --runner-only   # re-register the runner only
#
# What it does NOT do: register the runner with GitHub (needs a short-lived token you
# fetch yourself) and populate the asset cache (see fetch-inputs.sh). Both are printed
# as follow-up steps at the end.
set -euo pipefail

# Kept in sync with the build by hand. Sources, in order:
#   JDK      — mise.toml pins zulu-25; any JDK 25 works, Temurin is what apt carries.
#   SDK      — android/build.gradle: compileSdk = 36.
#   AGP 8.9.3 wants build-tools 35+; 36.0.0 pairs with compileSdk 36.
JDK_VERSION=25
ANDROID_API=36
BUILD_TOOLS=36.0.0
CMDLINE_TOOLS_ZIP=commandlinetools-linux-11076708_latest.zip

CACHE_ROOT=/srv/aoh2de
ANDROID_SDK_ROOT=/opt/android-sdk
RUNNER_USER="${SUDO_USER:-$USER}"

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

if [[ "${1:-}" != "--runner-only" ]]; then

log "Base packages"
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
# unzip: sdkmanager + the wrapper distribution. git: checkout. rsync: shader overlay in
# the workflow. awscli comes from pip/snap on some images; apt's is fine for s3 sync.
apt-get install -y -qq curl unzip git rsync ca-certificates gnupg awscli zstd

log "Temurin JDK ${JDK_VERSION}"
install -d -m 0755 /etc/apt/keyrings
curl -fsSL https://packages.adoptium.net/artifactory/api/gpg/key/public \
  | gpg --dearmor --batch --yes -o /etc/apt/keyrings/adoptium.gpg
echo "deb [signed-by=/etc/apt/keyrings/adoptium.gpg] https://packages.adoptium.net/artifactory/deb $(. /etc/os-release && echo "$VERSION_CODENAME") main" \
  > /etc/apt/sources.list.d/adoptium.list
apt-get update -qq
apt-get install -y -qq "temurin-${JDK_VERSION}-jdk"

log "Android SDK at ${ANDROID_SDK_ROOT}"
# The build resolves the SDK via local.properties first, then ANDROID_SDK_ROOT/ANDROID_HOME
# (see resolveSdkDir in android/build.gradle). local.properties is gitignored and
# machine-specific, so CI relies on the env var and never writes one.
mkdir -p "${ANDROID_SDK_ROOT}/cmdline-tools"
if [[ ! -d "${ANDROID_SDK_ROOT}/cmdline-tools/latest" ]]; then
  tmp=$(mktemp -d)
  curl -fsSL "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" -o "${tmp}/tools.zip"
  unzip -q "${tmp}/tools.zip" -d "${tmp}"
  mv "${tmp}/cmdline-tools" "${ANDROID_SDK_ROOT}/cmdline-tools/latest"
  rm -rf "${tmp}"
fi

export ANDROID_SDK_ROOT
SDKMANAGER="${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager"
yes | "${SDKMANAGER}" --licenses > /dev/null
"${SDKMANAGER}" --install \
  "platform-tools" \
  "platforms;android-${ANDROID_API}" \
  "build-tools;${BUILD_TOOLS}"

log "Persistent cache at ${CACHE_ROOT}"
# Lives outside the runner workspace on purpose: actions/checkout runs `git clean -ffdx`,
# which would delete a 2 GB assets tree on every single run.
mkdir -p "${CACHE_ROOT}/assets" "${CACHE_ROOT}/libs" "${CACHE_ROOT}/out"
chown -R "${RUNNER_USER}:${RUNNER_USER}" "${CACHE_ROOT}" "${ANDROID_SDK_ROOT}"

log "Machine-wide environment"
cat > /etc/profile.d/aoh2de-ci.sh <<PROFILE
export ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT}
export ANDROID_HOME=${ANDROID_SDK_ROOT}
export PATH=\$PATH:${ANDROID_SDK_ROOT}/platform-tools:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin
PROFILE
chmod 0644 /etc/profile.d/aoh2de-ci.sh

fi

log "Done. Remaining manual steps"
cat <<NEXT

1. Install and register the runner (token from
   Settings -> Actions -> Runners -> New self-hosted runner):

     mkdir -p ~/actions-runner && cd ~/actions-runner
     curl -fsSL -o runner.tar.gz \
       https://github.com/actions/runner/releases/latest/download/actions-runner-linux-x64.tar.gz
     tar xzf runner.tar.gz
     ./config.sh --url https://github.com/YellowSquared/aoh2de-porter \
                 --token <RUNNER_TOKEN> --labels aoh2de-builder --unattended
     sudo ./svc.sh install && sudo ./svc.sh start

   The 'aoh2de-builder' label is what the workflow's runs-on targets.

2. Populate the input cache once (subsequent runs sync incrementally):

     export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=...
     export AOH2DE_S3_BUCKET=s3://your-bucket/aoh2de
     ./scripts/ci/fetch-inputs.sh

3. Add these repository secrets under Settings -> Secrets -> Actions:
     AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AOH2DE_S3_BUCKET
     AWS_S3_ENDPOINT   (only for non-AWS S3: Cloudflare R2, MinIO, ...)

Sizing note: the build needs ~4 GB Gradle heap (gradle.properties), the asset cache is
~2 GB and the built APK is another ~1.8 GB. Give the VM 8 GB RAM and 60 GB disk minimum.
NEXT

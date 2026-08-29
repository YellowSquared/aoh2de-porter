#!/usr/bin/env bash
# Populates the build inputs that are deliberately absent from git:
#   libs/game.jar   (27 MB vendored jar)
#   assets/         (~2 GB, ~125k files)
# See .gitignore for why neither is tracked.
#
# Everything lands in $AOH2DE_CACHE (default /srv/aoh2de), which lives OUTSIDE the runner
# workspace because actions/checkout runs `git clean -ffdx` before every job.
#
# Required env:
#   AOH2DE_S3_BUCKET      e.g. s3://my-bucket/aoh2de
#   AWS_ACCESS_KEY_ID / AWS_SECRET_ACCESS_KEY
# Optional env:
#   AWS_S3_ENDPOINT       custom endpoint for R2/MinIO/etc.
#   AOH2DE_CACHE          default /srv/aoh2de
#   AOH2DE_ASSETS_MODE    'archive' (default) or 'sync'
#   AOH2DE_ASSETS_ARCHIVE archive object name, default assets.tar.zst
set -euo pipefail

: "${AOH2DE_S3_BUCKET:?set AOH2DE_S3_BUCKET, e.g. s3://my-bucket/aoh2de}"
CACHE="${AOH2DE_CACHE:-/srv/aoh2de}"
MODE="${AOH2DE_ASSETS_MODE:-archive}"
ARCHIVE="${AOH2DE_ASSETS_ARCHIVE:-assets.tar.zst}"

URI="${AOH2DE_S3_BUCKET%/}"          # s3://bucket/optional/prefix
REST="${URI#s3://}"
BUCKET_NAME="${REST%%/*}"
PREFIX=""
[[ "${REST}" == */* ]] && PREFIX="${REST#*/}"

log() { printf '\n\033[1m==> %s\033[0m\n' "$*"; }

# One place decides whether a custom endpoint (R2, MinIO) is in play.
aws_cli() {
  if [[ -n "${AWS_S3_ENDPOINT:-}" ]]; then
    aws --endpoint-url "${AWS_S3_ENDPOINT}" "$@"
  else
    aws "$@"
  fi
}
aws_s3() { aws_cli s3 "$@"; }

# ETag of one object — the "did the archive change" key. Cheaper and more reliable than
# comparing sizes, and unlike a local checksum it needs no download to evaluate.
remote_etag() { aws_cli s3api head-object --bucket "${BUCKET_NAME}" --key "$1" --query ETag --output text; }

mkdir -p "${CACHE}/assets" "${CACHE}/libs" "${CACHE}/out"

# --- game.jar ---------------------------------------------------------------
# Small enough that an unconditional copy costs nothing, but the sha256 is worth logging:
# stripGameJar/patchGameJar cache on its content, so a silent swap should be visible.
log "game.jar"
aws_s3 cp "${URI}/game.jar" "${CACHE}/libs/game.jar"
sha256sum "${CACHE}/libs/game.jar" | tee "${CACHE}/libs/game.jar.sha256"

# --- assets -----------------------------------------------------------------
# 'archive' is the default because 125k individual objects make `s3 sync` spend its whole
# runtime on HEAD requests; one archive transfers at line rate.
log "assets (${MODE} mode)"
if [[ "${MODE}" == "sync" ]]; then
  aws_s3 sync "${URI}/assets/" "${CACHE}/assets/" --delete
else
  stamp="${CACHE}/assets.stamp"
  etag=$(remote_etag "${PREFIX:+${PREFIX}/}${ARCHIVE}")

  if [[ -f "${stamp}" && "$(cat "${stamp}")" == "${etag}" && -d "${CACHE}/assets/game" ]]; then
    echo "assets unchanged (${etag}) — skipping download and extract"
  else
    tmp="${CACHE}/${ARCHIVE}.part"
    aws_s3 cp "${URI}/${ARCHIVE}" "${tmp}"

    # Extract to a sibling and swap at the end. A half-extracted tree left by an
    # interrupted run would otherwise build clean and silently ship missing assets.
    rm -rf "${CACHE}/assets.new" "${CACHE}/assets.old"
    mkdir -p "${CACHE}/assets.new"
    case "${ARCHIVE}" in
      *.tar.zst)      tar --zstd -xf "${tmp}" -C "${CACHE}/assets.new" ;;
      *.tar.gz|*.tgz) tar -xzf     "${tmp}" -C "${CACHE}/assets.new" ;;
      *.zip)          unzip -q     "${tmp}" -d "${CACHE}/assets.new" ;;
      *) echo "unsupported archive format: ${ARCHIVE}" >&2; exit 1 ;;
    esac

    # Tolerate archives packed with or without a top-level assets/ directory.
    src="${CACHE}/assets.new"
    [[ -d "${CACHE}/assets.new/assets" ]] && src="${CACHE}/assets.new/assets"
    [[ -d "${src}/game" ]] || { echo "archive has no game/ directory under ${src}" >&2; exit 1; }

    mv "${CACHE}/assets" "${CACHE}/assets.old"
    mv "${src}" "${CACHE}/assets"
    rm -rf "${CACHE}/assets.old" "${CACHE}/assets.new" "${tmp}"
    echo "${etag}" > "${stamp}"
  fi
fi

log "Cache ready"
du -sh "${CACHE}/assets" "${CACHE}/libs" 2>/dev/null || true
find "${CACHE}/assets" -type f | wc -l | xargs printf 'asset files: %s\n'

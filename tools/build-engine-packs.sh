#!/usr/bin/env bash
set -euo pipefail

: "${ENGINE_VERSION_CODE:?Set ENGINE_VERSION_CODE, for example 190001}"
: "${ENGINE_VERSION_NAME:?Set ENGINE_VERSION_NAME, for example 0.19.0-1}"
: "${ENGINE_BASE_URL:?Set ENGINE_BASE_URL to the final HTTPS release directory}"
: "${ENGINE_SIGNING_PRIVATE_KEY:?Set ENGINE_SIGNING_PRIVATE_KEY to an Ed25519 PEM key}"

ENGINE_RUNTIME_API="${ENGINE_RUNTIME_API:-1}"
ENGINE_MINIMUM_VERSION_CODE="${ENGINE_MINIMUM_VERSION_CODE:-$ENGINE_VERSION_CODE}"
ENGINE_SIGNING_KEY_ID="${ENGINE_SIGNING_KEY_ID:-engine-2026-01}"
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SOURCE_DIR="$PROJECT_ROOT/build/engine-source-aars"
OUTPUT_DIR="$PROJECT_ROOT/build/engine-packs"

"$PROJECT_ROOT/gradlew" -p "$PROJECT_ROOT" copyEngineSourceAars

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
CONFIG_FILE="$OUTPUT_DIR/grabber-video-downloader.json"

printf '{\n  "enabled": true,\n  "new": true,\n  "latest_version_code": %s,\n  "version_name": "%s",\n  "minimum_version_code": %s,\n  "runtime_api": %s,\n  "packs": {\n' \
  "$ENGINE_VERSION_CODE" "$ENGINE_VERSION_NAME" \
  "$ENGINE_MINIMUM_VERSION_CODE" "$ENGINE_RUNTIME_API" > "$CONFIG_FILE"

abis=(arm64-v8a armeabi-v7a x86 x86_64)
for index in "${!abis[@]}"; do
  abi="${abis[$index]}"
  work_dir="$(mktemp -d "${TMPDIR:-/tmp}/bombitup-engine-${abi}.XXXXXX")"
  staging="$work_dir/staging"
  mkdir -p "$staging/python" "$staging/ffmpeg" "$staging/yt-dlp"

  unzip -p "$SOURCE_DIR/library.aar" "jni/$abi/libpython.zip.so" > "$work_dir/python.zip"
  unzip -p "$SOURCE_DIR/ffmpeg.aar" "jni/$abi/libffmpeg.zip.so" > "$work_dir/ffmpeg.zip"
  unzip -q "$work_dir/python.zip" -d "$staging/python"
  unzip -q "$work_dir/ffmpeg.zip" -d "$staging/ffmpeg"
  unzip -p "$SOURCE_DIR/library.aar" res/raw/ytdlp > "$staging/yt-dlp/yt-dlp"

  printf '{"schema_version":1,"version_code":%s,"version_name":"%s","runtime_api":%s,"abi":"%s"}\n' \
    "$ENGINE_VERSION_CODE" "$ENGINE_VERSION_NAME" "$ENGINE_RUNTIME_API" "$abi" \
    > "$staging/engine.json"
  printf '%s\n' \
    'Runtime payload derived from io.github.deniscerri.youtubedl-android 0.19.0.' \
    'youtubedl-android and FFmpeg licensing/source: https://github.com/deniscerri/youtubedl-android' \
    'yt-dlp licensing/source: https://github.com/yt-dlp/yt-dlp' \
    > "$staging/THIRD_PARTY_NOTICES.txt"

  find "$staging" -exec touch -t 200001010000 {} +

  installed_bytes=0
  while IFS= read -r file; do
    file_bytes="$(wc -c < "$file" | tr -d ' ')"
    installed_bytes=$((installed_bytes + file_bytes))
  done < <(find "$staging" -type f | LC_ALL=C sort)

  pack_name="downloader-engine-${ENGINE_VERSION_CODE}-${abi}.zip"
  pack_path="$OUTPUT_DIR/$pack_name"
  (
    cd "$staging"
    find . -type f | LC_ALL=C sort | zip -X -q "$pack_path" -@
  )

  download_bytes="$(wc -c < "$pack_path" | tr -d ' ')"
  sha256="$(shasum -a 256 "$pack_path" | awk '{print $1}')"
  canonical="$work_dir/signature-input.txt"
  printf 'bombitup-engine-v1\n%s\n%s\n%s\n%s\n%s\n%s\n%s\n' \
    "$ENGINE_VERSION_CODE" "$ENGINE_VERSION_NAME" "$ENGINE_RUNTIME_API" "$abi" \
    "$download_bytes" "$installed_bytes" "$sha256" > "$canonical"
  openssl pkeyutl -sign -rawin -inkey "$ENGINE_SIGNING_PRIVATE_KEY" \
    -in "$canonical" -out "$work_dir/signature.bin"
  signature="$(openssl base64 -A -in "$work_dir/signature.bin")"

  comma=','
  if [[ "$index" -eq $((${#abis[@]} - 1)) ]]; then comma=''; fi
  printf '    "%s": {"url":"%s/%s","download_bytes":%s,"installed_bytes":%s,"sha256":"%s","key_id":"%s","signature":"%s"}%s\n' \
    "$abi" "${ENGINE_BASE_URL%/}" "$pack_name" "$download_bytes" "$installed_bytes" \
    "$sha256" "$ENGINE_SIGNING_KEY_ID" "$signature" "$comma" >> "$CONFIG_FILE"
done

printf '  }\n}\n' >> "$CONFIG_FILE"
(
  cd "$OUTPUT_DIR"
  shasum -a 256 "downloader-engine-$ENGINE_VERSION_CODE-"*.zip > SHA256SUMS
)

public_key_hex="$(
  openssl pkey -in "$ENGINE_SIGNING_PRIVATE_KEY" -pubout -outform DER 2>/dev/null |
    tail -c 32 | xxd -p -c 64
)"
printf 'Engine packs: %s\nGrabber config: %s\nENGINE_SIGNING_KEY_ID=%s\nENGINE_SIGNING_PUBLIC_KEY_HEX=%s\n' \
  "$OUTPUT_DIR" "$CONFIG_FILE" "$ENGINE_SIGNING_KEY_ID" "$public_key_hex"

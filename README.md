# BOMBitUP Downloader Engine Releases

This public repository distributes signed runtime packs for BOMBitUP's Any
Video Downloader. Binary packs are attached to GitHub Releases and are not
committed to Git history.

Each release contains one pack for each supported Android ABI, a
`grabber-video-downloader.json` configuration block, and `SHA256SUMS`.

BOMBitUP verifies the declared size, SHA-256 digest, Ed25519 signature, ABI,
and runtime manifest before activating a downloaded engine.

## Source and licenses

Runtime payloads are derived from
[`io.github.deniscerri.youtubedl-android`](https://github.com/deniscerri/youtubedl-android),
which incorporates [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
[FFmpeg](https://ffmpeg.org/). Each pack includes its third-party notice.

Refer to those upstream projects for their source code and applicable license
terms. Release assets must not be modified after signing.

## Building from source

The repository contains the Android engine wrapper, its unit tests, and the
standalone pack-building script. Build the library with:

```shell
./gradlew testDebugUnitTest assembleRelease
```

Creating signed runtime packs requires an Ed25519 private key kept outside the
repository. See `tools/build-engine-packs.sh` for the required environment
variables. Never commit or upload the private key.

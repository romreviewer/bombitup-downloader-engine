# BOMBitUP Downloader Engine Releases

This public repository distributes signed runtime packs for BOMBitUP's Any
Video Downloader. Binary packs are attached to GitHub Releases and are not
committed to Git history.

Each release contains one pack for each supported Android ABI, a
`grabber-video-downloader.json` configuration block, and `SHA256SUMS`.

BOMBitUP verifies the declared size, SHA-256 digest, Ed25519 signature, ABI,
and runtime manifest before activating a downloaded engine.

## Source and licenses

The pack build script and Android integration are maintained in the
[BOMBitUP-v3 source project](https://gitlab.com/romreviewer2.0/BOMBiUP-v3).
Runtime payloads are derived from
[`io.github.deniscerri.youtubedl-android`](https://github.com/deniscerri/youtubedl-android),
which incorporates [yt-dlp](https://github.com/yt-dlp/yt-dlp) and
[FFmpeg](https://ffmpeg.org/). Each pack includes its third-party notice.

Refer to those upstream projects for their source code and applicable license
terms. Release assets must not be modified after signing.

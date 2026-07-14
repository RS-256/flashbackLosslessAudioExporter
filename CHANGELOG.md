# v0.2.0

## Audio-only export mode

- Toggle in Flashback's export dialog: `Audio-only (lossless WAV) [FLAE]` checkbox below the audio codec dropdown (en_us / ja_jp localized, persisted in `config/flae.json`)
- When enabled and `Record Audio` is on:
  - the world render and the whole FFmpeg pipeline are skipped — only the lossless wav is produced
  - the camera still tracks its keyframed/followed position, so distance attenuation and panning stay correct
  - progress UI, ESC cancellation and the export-done window keep working
  - no video file is produced: the empty temp placeholder is left for Flashback's own cleanup instead of being moved next to the wav
- Export resolution still drives the (cheap) per-frame framebuffer readback that was left intact for safety — set a small resolution for maximum speed

# v0.1.0

Initial release.

- Mirror Flashback's export audio (`AsyncFFmpegVideoWriter#encode` の `FloatBuffer`) into a lossless wav
  - 32bit float PCM (`WAVE_FORMAT_IEEE_FLOAT`), interleaved, 48000Hz
  - mono/stereo follows Flashback's `stereoAudio` export setting
  - output path: same directory / base name as the video, extension swapped to `.wav`
- Only active when `Record Audio` is enabled in the export screen
- Wav failures are logged and never interrupt Flashback's own video export
- Supported: mc1.21.11 (Flashback 0.39.5), mc26.1.2 (Flashback 0.40.0)

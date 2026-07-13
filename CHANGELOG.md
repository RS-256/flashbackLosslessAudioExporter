# v0.1.0

Initial draft.

- Mirror Flashback's export audio (`AsyncFFmpegVideoWriter#encode` の `FloatBuffer`) into a lossless wav
  - 32bit float PCM (`WAVE_FORMAT_IEEE_FLOAT`), interleaved, 48000Hz
  - mono/stereo follows Flashback's `stereoAudio` export setting
  - output path: same directory / base name as the video, extension swapped to `.wav`
- Only active when `Record Audio` is enabled in the export screen
- Wav failures are logged and never interrupt Flashback's own video export
- Supported: mc1.21.11 (Flashback 0.39.5), mc26.1.2 (Flashback 0.40.0)

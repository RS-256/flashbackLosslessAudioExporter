# flashbackLosslessAudioExporter (FLAE)

[日本語](docs/README_jp.md)

A client-side Fabric mod that exports the same deterministic audio as [Flashback](https://github.com/Moulberry/Flashback)'s video export in parallel as a **lossless WAV (32-bit float PCM, 48 kHz)** file.

FLAE simply uses a Mixin to copy the raw `FloatBuffer` passed to `AsyncFFmpegVideoWriter#encode` immediately before encoding (the result of offline rendering via `ALC_SOFT_loopback`). This means the audio is unaffected by TPS drops or lag and never passes through lossy compression. See [SPEC.md](SPEC.md) for details.

## Usage

1. Install FLAE alongside Flashback and Fabric API.
2. Enable `Record Audio` on Flashback's export screen and start the export.
3. A `.wav` file with the same base name is created next to the video output (for example, `take1.mp4` → `take1.wav`).

FLAE does nothing when `Record Audio` is disabled. If writing the WAV file fails, Flashback's video export continues unaffected.

## Audio-only export mode (v0.2.0+)

When you only need audio, FLAE can skip the entire video pipeline (world rendering and FFmpeg encoding).

Toggle this mode with the **checkbox on the export screen** (the setting is persisted in `config/flae.json`). When `Record Audio` is enabled, `Audio only (lossless WAV) [FLAE]` appears below the audio codec option.

- When enabled, exports with `Record Audio` produce only a WAV file (no video file is created).
- Camera tracking (and therefore the audio listener) remains active, so distance attenuation and panning match a normal export.
- The progress UI and ESC cancellation continue to work as usual.
- For safety, framebuffer readback is still performed. Set the export resolution to the minimum for the fastest export.

## WAV format / sample rate (v0.3.0+)

The following drop-down menus are added to the audio options on the export screen (both settings are persisted in `config/flae.json`):

- **WAV format** — 32-bit float (lossless, default), 32-bit integer, 24-bit integer, 16-bit integer, or 8-bit unsigned integer. Applies to both normal and audio-only exports. Integer formats are quantized using simple rounding without dithering.
- **WAV sample rate** — 44,100, 48,000 (native), or 96,000 Hz. Available **only in audio-only mode** (shown only when the checkbox is enabled). The OpenAL loopback device itself is reopened at the selected rate, so FLAE performs no resampling. Normal exports always use 48,000 Hz.

## Supported versions

| Minecraft | Flashback | Build script                    |
|-----------|-----------|---------------------------------|
| 1.21.11   | 0.39.5    | `build.obfuscated.gradle.kts`   |
| 26.1.2    | 0.40.0    | `build.unobfuscated.gradle.kts` |

All dependency versions are managed in `stonecutter.properties.toml` (do not add them to `gradle.properties`).

## Checklist for Minecraft / Flashback version updates (SPEC.md §7.3)

This mod uses Mixins targeting Flashback's internal classes. Check all of the following whenever adding or updating a supported version:

- [ ] Confirm that the signatures of the `AsyncFFmpegVideoWriter` constructor (`<init>(ExportSettings, String)`), `encode(NativeImage, FloatBuffer)`, `finish()`, and `close()` have not changed.
- [ ] (v0.2.0+) Confirm that `ExportJob#createVideoWriter(ExportSettings, String)` exists and that the target of the world-rendering call in `doExport` has not changed (26.1+: `ExportJob#render(RenderTarget, DeltaTracker.Timer)` / 1.21.x: `GameRenderer#render(DeltaTracker, boolean)`).
- [ ] (v0.2.0+) Confirm that `ExportJob#run` still contains exactly one `Files.move(temp, output)` call and one `createVideoWriter` call (both are anchors for `@WrapOperation`). Also confirm that the `finally` block still cleans up the temporary file with `Files.deleteIfExists(exportTempFile)`.
- [ ] (v0.2.0+) Confirm that the audio-codec call to `ImGuiHelper.enumCombo(String, Enum, Enum[])` is still unique within `StartExportWindow#render()` (it is the injection anchor for the GUI toggle), and that the shaded ImGui package name (`imgui.moulberry90`) has not changed.
- [ ] (v0.3.0+) Confirm that Flashback's `MixinAudioLibrary` still wraps `alcCreateContext` in `Library#init` and directly supplies the attributes `{FORMAT_TYPE: FLOAT, CHANNELS, FREQUENCY: 48000}` (FLAE's `MixinLibrary` wraps this from the outside with priority 1100).
- [ ] (v0.3.0+) Confirm that the `48000.0` constant used to calculate the sample count remains in `ExportJob#doExport` (the match target for `@ModifyConstant`).
- [ ] Confirm that the names and types of the `recordAudio()`, `stereoAudio()`, and `output()` fields in `ExportSettings` have not changed.
- [ ] Confirm that the relevant entries in `flashback.accesswidener` (such as `SoundManager#soundEngine`) still exist. FLAE does not reference them directly, but they are assumptions made by Flashback.
- [ ] Confirm that the sample rate is still fixed at 48,000 Hz (check whether `recorder.setSampleRate(48000)` remains hard-coded).
- [ ] Update `deps.flashback` in `stonecutter.properties.toml` to the new version's **Modrinth version ID** (not its version number). Flashback publishes the same version number multiple times for different Minecraft versions, so resolving Maven dependencies by version number can retrieve a JAR for the wrong Minecraft version. IDs are available from `https://api.modrinth.com/v2/project/flashback/version`.

---

# Stonecutter Fabric template

## Setup

1. Review the supported Minecraft versions in `settings.gradle.kts`.
   For new entries, add `versions/.../gradle.properties` with the same keys as other versions.
2. Change `mod.group`, `mod.id` and `mod.name` properties in `gradle.properties`.
3. Rename `com.example` package in `src/main/java`.
4. Rename `src/main/resources/template.mixins.json` to use your mod's id.
5. Review the `LICENSE` file. 
   See the [license decision diagram](https://docs.codeberg.org/getting-started/licensing/#license-decision-diagram) for common options.
6. Review `src/main/resources/fabric.mod.json` to have up-to-date properties.

## Template usage

- Use the `Set active project to ...` Gradle tasks to change the Minecraft version available to the classes in `src/`.
- Use the `buildAndCollect` Gradle task to store mod releases in `build/libs/`.
- To publish releases to Modrinth and CurseForge, enable `mod-publish-plugin` and the corresponding code blocks in `stonecutter.gradle.kts` and `build.gradle.kts`.
- To publish releases to a personal Maven repository, enable `maven-publish` and the corresponding code block in `build.gradle.kts`.

## Useful links

- [Stonecutter beginner's guide](https://stonecutter.kikugie.dev/wiki/start/): *spoiler: you* ***need*** *to understand how it works!*
- [Fabric Discord server](https://discord.gg/v6v4pMv): for mod development help.
- [Stonecutter Discord server](https://discord.kikugie.dev/): for Stonecutter and Gradle help.
- [How To Ask Questions - the guide](http://www.catb.org/esr/faqs/smart-questions.html): also in [video form](https://www.youtube.com/results?search_query=How+To+Ask+Questions+The+Smart+Way).

## Commonly used syntax

### Minecraft version-specific code

You can use an `if` block in source code (but not in build scripts) to apply or remove Minecraft version-specific code. 
When Stonecutter evaluates the code—for example, when Gradle is reloaded, the project is built, or `runClient` is run—code whose conditions do not match the active version (defined at `stonecutter.gradle.kts#L15`) is commented out.

```java
// some code...
//? if >=1.21.3 {
// version-specific code...
//?}
// more code...
```

ofc you can also use `else` and `else if` blocks:

```java
public static MutableComponent literal(String string) {
    //? if <=1.18.2 {
    // only uncommented and evaluated when the current version is 1.18.2 or earlier (<=1.18.2)
    /*return new TextComponent(string);
     *///?} else {
    return Component.literal(string);
    //?}
}
```

`if` blocks can be nested up to 10 levels deep:

```java
//? if <=1.18.2 {
/*public void registerCommands() {
	CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> InfinoteCommand.register(dispatcher));
}
 *///?} else {
public void registerCommands() {
	CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
		TemplateCommand.register(dispatcher, registryAccess);
		//? if >=1.20.3 {
        // only uncommented and evaluated when the current version is >= 1.20.3

        ExampleCommand.register(dispatcher);
		//?}
	});
}
//?}
```

Braces may be omitted when a version-specific block contains only one line, but keeping them is recommended.

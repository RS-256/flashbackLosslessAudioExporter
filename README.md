# flashbackLosslessAudioExporter (FLAE)

[Flashback](https://github.com/Moulberry/Flashback)のexport実行時に、映像と同じ決定論的な音声を**ロスレスWAV (32bit float PCM, 48kHz)** として並行出力するclient-side Fabric mod。

Flashbackの `AsyncFFmpegVideoWriter#encode` に渡る直前の生 `FloatBuffer`(`ALC_SOFT_loopback` によるオフラインレンダリング結果)をMixinでコピーするだけなので、TPS低下・ラグの影響を受けず、非可逆圧縮も経由しない。詳細は [SPEC.md](SPEC.md) を参照。

## 使い方

1. Flashback・Fabric APIと一緒に導入する
2. Flashbackのexport画面で `Record Audio` を有効にしてexportする
3. 動画出力パスと同じ場所に、同名の `.wav` が生成される(例: `take1.mp4` → `take1.wav`)

`Record Audio` が無効の場合は何もしない。WAV書き込みに失敗しても、Flashback本体の動画exportは巻き込まれず継続する。

## 対応バージョン

| Minecraft | Flashback | ビルドスクリプト |
|-----------|-----------|--------------------------------|
| 1.21.11   | 0.39.5    | `build.obfuscated.gradle.kts`  |
| 26.1.2    | 0.40.0    | `build.unobfuscated.gradle.kts`|

依存バージョンはすべて `stonecutter.properties.toml` で管理する(`gradle.properties` には書かない)。

## Minecraft / Flashback バージョン更新時のチェックリスト (SPEC.md §7.3)

本ModはFlashbackの内部クラスにMixinしているため、対応バージョンを追加・更新するたびに以下を確認すること。

- [ ] `AsyncFFmpegVideoWriter` のコンストラクタ(`<init>(ExportSettings, String)`)・`encode(NativeImage, FloatBuffer)`・`finish()`・`close()` のシグネチャが変わっていないか
- [ ] `ExportSettings` の `recordAudio()` / `stereoAudio()` / `output()` のフィールド名・型が変わっていないか
- [ ] `flashback.accesswidener` の該当エントリ(`SoundManager#soundEngine` 等)が引き続き存在するか(本Modは直接参照しないが、Flashback側の前提確認として)
- [ ] サンプルレートが48000Hz固定のままか(`recorder.setSampleRate(48000)` のハードコードが変わっていないか)
- [ ] `stonecutter.properties.toml` の `deps.flashback` を新バージョンの **Modrinth version ID**(version numberではない)に更新したか。Flashbackは同一version numberをMCバージョンごとに複数公開するため、version numberでのmaven解決は別MC向けjarを掴むことがある。IDは `https://api.modrinth.com/v2/project/flashback/version` で確認できる

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

## Usage
- Use `"Set active project to ..."` Gradle tasks to update the Minecraft version
  available in `src/` classes.
- Use `buildAndCollect` Gradle task to store mod releases in `build/libs/`.
- Enable `mod-publish-plugin` in `stonecutter.gradle.kts` and `build.gradle.kts`
  and the corresponding code blocks to publish releases to Modrinth and Curseforge.
- Enable `maven-publish` in `build.gradle.kts` and the corresponding code block
  to publish releases to a personal maven repository.

## Useful links
- [Stonecutter beginner's guide](https://stonecutter.kikugie.dev/wiki/start/): *spoiler: you* ***need*** *to understand how it works!*
- [Fabric Discord server](https://discord.gg/v6v4pMv): for mod development help.
- [Stonecutter Discord server](https://discord.kikugie.dev/): for Stonecutter and Gradle help.
- [How To Ask Questions - the guide](http://www.catb.org/esr/faqs/smart-questions.html): also in [video form](https://www.youtube.com/results?search_query=How+To+Ask+Questions+The+Smart+Way).

## Commonly used syntax
### minecraft version specific code
you can use `if` block in your source code (not in the build script) to apply or remove minecraft version specific code.
When the stonecutter evaluate the codes, for example; reload the gradle, building, ofc runClient or something else, 
if the current active version (defined at `stonecutter.gradle.kts#L15`) does not meet the conditions, that code is commented out in a comment block
```java
//some code...
//? if >=1.21.3 {
//version specific code...
//?}
// another some code...
```
ofc you can also use `else` and `else if` block, like this.
```java
public static MutableComponent literal(String string) {
    //? if <=1.18.2 {
    // only uncommented and can be evaluated if current version is under 1.18.2 (<=1.18.2)
    /*return new TextComponent(string);
     *///?} else {
    return Component.literal(string);
    //?}
}
```
and you can nest the `if` block up to 10 times.
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
        // only uncommented and can be evaluated if current version is >=1.18.2 && 1.20.3

        ExampleCommand.register(dispatcher);
		//?}
	});
}
//?}
```
if the version specific code is only one line you can omit the bracket, but I recommend not to omit bracket.

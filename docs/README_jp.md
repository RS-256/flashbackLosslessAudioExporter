# flashbackLosslessAudioExporter (FLAE)

[English](../README.md)

[Flashback](https://github.com/Moulberry/Flashback) のエクスポート実行時に、映像と同じ決定論的な音声を **ロスレス WAV（32-bit float PCM、48 kHz）**として並行出力するクライアント側 Fabric Mod。

Flashback の `AsyncFFmpegVideoWriter#encode` に渡る直前の生の `FloatBuffer`（`ALC_SOFT_loopback` によるオフラインレンダリング結果）を Mixin でコピーするだけなので、TPS 低下やラグの影響を受けず、非可逆圧縮も経由しない。詳細は [SPEC.md](../SPEC.md) を参照。

## 使い方

1. Flashback、Fabric API と一緒に導入する。
2. Flashback のエクスポート画面で `Record Audio` を有効にし、エクスポートする。
3. 動画出力パスと同じ場所に、同名の `.wav` が生成される（例：`take1.mp4` → `take1.wav`）。

`Record Audio` が無効の場合は何もしない。WAV の書き込みに失敗しても、Flashback 本体の動画エクスポートは影響を受けず継続する。

## 音声のみエクスポートモード（v0.2.0 以降）

音だけが必要な場合は、映像パイプライン（ワールド描画と FFmpeg エンコード）を丸ごとスキップできる。

切り替えは**エクスポート画面のチェックボックス**で行う（設定は `config/flae.json` に永続化される）。`Record Audio` を有効にすると、音声コーデックの下に `音声のみ（ロスレス WAV）[FLAE]` が表示される。

- 有効時、`Record Audio` を指定したエクスポートでは WAV のみを出力する（動画ファイルは一切生成されない）。
- カメラ（オーディオリスナー）の追従は維持されるため、距離減衰とパンは通常のエクスポートと一致する。
- 進捗 UI と ESC キーによるキャンセルはそのまま機能する。
- 安全のためフレームバッファのリードバックは残している。エクスポート解像度を最小にすると最速になる。

## WAV フォーマット／サンプルレート（v0.3.0 以降）

エクスポート画面の音声オプションに以下のプルダウンが追加される（いずれも `config/flae.json` に永続化される）。

- **WAV フォーマット** — 32-bit float（ロスレス、デフォルト）／32-bit int／24-bit int／16-bit int／8-bit unsigned。通常エクスポートと音声のみエクスポートの両方に適用される。整数フォーマットは単純な丸めで量子化される（ディザなし）。
- **WAV サンプルレート** — 44,100／48,000（ネイティブ）／96,000 Hz。**音声のみモード限定**（チェックがオンの場合のみ表示）。OpenAL ループバックデバイス自体を選択したレートで開き直すため、FLAE 側でのリサンプリングは発生しない。通常エクスポートは常に 48,000 Hz。

## 対応バージョン

| Minecraft | Flashback |
|-----------|-----------|
| 1.21.11   | 0.39.5    |
| 26.1.2    | 0.40.0    |

依存バージョンはすべて `stonecutter.properties.toml` で管理する（`gradle.properties` には書かない）。

## Minecraft／Flashback バージョン更新時のチェックリスト

本 Mod は Flashback の内部クラスに Mixin しているため、対応バージョンを追加・更新するたびに以下を確認すること。

- [ ] `AsyncFFmpegVideoWriter` のコンストラクタ（`<init>(ExportSettings, String)`）、`encode(NativeImage, FloatBuffer)`、`finish()`、`close()` のシグネチャが変わっていないか。
- [ ] （v0.2.0 以降）`ExportJob#createVideoWriter(ExportSettings, String)` が存在し、`doExport` 内のワールド描画呼び出し（26.1 以降：`ExportJob#render(RenderTarget, DeltaTracker.Timer)`／1.21.x：`GameRenderer#render(DeltaTracker, boolean)`）のターゲットが変わっていないか。
- [ ] （v0.2.0 以降）`ExportJob#run` 内の `Files.move(temp, output)` と `createVideoWriter` 呼び出しが、引き続き `run()` 内に各 1 箇所あるか（どちらも `@WrapOperation` のアンカー）。また、`finally` の `Files.deleteIfExists(exportTempFile)` による一時ファイルの削除が残っているか。
- [ ] （v0.2.0 以降）`StartExportWindow#render()` 内の音声コーデック用 `ImGuiHelper.enumCombo(String, Enum, Enum[])` 呼び出しが引き続き一意か（GUI トグルの注入アンカー）。shaded ImGui のパッケージ名（`imgui.moulberry90`）が変わっていないか。
- [ ] （v0.3.0 以降）Flashback の `MixinAudioLibrary` が `Library#init` の `alcCreateContext` をラップし、属性 `{FORMAT_TYPE: FLOAT, CHANNELS, FREQUENCY: 48000}` を直接指定する構造のままか（FLAE の `MixinLibrary` は priority 1100 でこれを外側からラップしている）。
- [ ] （v0.3.0 以降）`ExportJob#doExport` 内にサンプル数計算の `48000.0` 定数が残っているか（`@ModifyConstant` のマッチ対象）。
- [ ] `ExportSettings` の `recordAudio()`、`stereoAudio()`、`output()` のフィールド名と型が変わっていないか。
- [ ] `flashback.accesswidener` の該当エントリ（`SoundManager#soundEngine` など）が引き続き存在するか（本 Mod は直接参照しないが、Flashback 側の前提として確認する）。
- [ ] サンプルレートが 48,000 Hz 固定のままか（`recorder.setSampleRate(48000)` のハードコードが変わっていないか）。
- [ ] `stonecutter.properties.toml` の `deps.flashback` を、新バージョンの **Modrinth version ID**（version number ではない）に更新したか。Flashback は同じ version number を Minecraft バージョンごとに複数公開するため、version number による Maven 解決では別の Minecraft バージョン向け JAR を取得する場合がある。ID は `https://api.modrinth.com/v2/project/flashback/version` で確認できる。

---

# Stonecutter Fabric テンプレート

## セットアップ

1. `settings.gradle.kts` に記載された対応 Minecraft バージョンを確認する。
   新しい項目を追加する場合は、ほかのバージョンと同じキーを持つ `versions/.../gradle.properties` を追加する。
2. `gradle.properties` の `mod.group`、`mod.id`、`mod.name` プロパティを変更する。
3. `src/main/java` の `com.example` パッケージ名を変更する。
4. `src/main/resources/template.mixins.json` を Mod の ID に合わせて改名する。
5. `LICENSE` ファイルを確認する。
   一般的な選択肢については[ライセンス選択図](https://docs.codeberg.org/getting-started/licensing/#license-decision-diagram)を参照。
6. `src/main/resources/fabric.mod.json` を確認し、プロパティを最新の状態にする。

## テンプレートの使い方

- `Set active project to ...` Gradle タスクを使用し、`src/` 内のクラスで使用できる Minecraft バージョンを切り替える。
- `buildAndCollect` Gradle タスクを使用し、Mod のリリースを `build/libs/` に保存する。
- Modrinth と CurseForge にリリースを公開するには、`stonecutter.gradle.kts` と `build.gradle.kts` の `mod-publish-plugin` および対応するコードブロックを有効にする。
- 個人の Maven リポジトリにリリースを公開するには、`build.gradle.kts` の `maven-publish` および対応するコードブロックを有効にする。

## 便利なリンク

- [Stonecutter 初心者向けガイド](https://stonecutter.kikugie.dev/wiki/start/)：*ネタバレすると、その仕組みを理解することは* ***必須*** *です！*
- [Fabric Discord サーバー](https://discord.gg/v6v4pMv)：Mod 開発のヘルプ。
- [Stonecutter Discord サーバー](https://discord.kikugie.dev/)：Stonecutter と Gradle のヘルプ。
- [上手な質問の仕方 — ガイド](http://www.catb.org/esr/faqs/smart-questions.html)：[動画版](https://www.youtube.com/results?search_query=How+To+Ask+Questions+The+Smart+Way)もある。

## よく使う構文

### Minecraft バージョン固有のコード

ソースコード（ビルドスクリプトを除く）では、`if` ブロックを使って Minecraft バージョン固有のコードを適用または除外できる。Gradle の再読み込み、ビルド、`runClient` の実行などで Stonecutter がコードを評価するとき、現在のアクティブバージョン（`stonecutter.gradle.kts#L15` で定義）が条件を満たさないコードはコメントブロック内に移される。

```java
// 何らかのコード...
//? if >=1.21.3 {
// バージョン固有のコード...
//?}
// 別のコード...
```

`else` と `else if` ブロックも使用できる。

```java
public static MutableComponent literal(String string) {
    //? if <=1.18.2 {
    // 現在のバージョンが 1.18.2 以下の場合のみコメントが解除され、評価される。
    /*return new TextComponent(string);
     *///?} else {
    return Component.literal(string);
    //?}
}
```

`if` ブロックは最大 10 階層までネストできる。

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
        // 現在のバージョンが 1.20.3 以上の場合のみコメントが解除され、評価される。

        ExampleCommand.register(dispatcher);
		//?}
	});
}
//?}
```

バージョン固有のコードが 1 行だけの場合は波括弧を省略できるが、省略しないことを推奨する。

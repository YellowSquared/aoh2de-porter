# Porting notes

Running record of what this port changes relative to the stock desktop game, and what has
already been investigated. Two things belong here that git history alone does not capture:
**why** a change exists, and **which hypotheses have been eliminated** — the latter is the
expensive knowledge, because a ruled-out cause is easy to re-investigate by accident.

Anything touching `assets/` deserves special care: the asset tree is re-extracted wholesale
and only `assets/game/shader/` is under version control (see `.gitignore`).

---

## Changes

| # | Change | Commit | Status |
|---|---|---|---|
| 1 | Build pipeline: incremental zip64 packing | `15f72af` | **Keep** — verified |
| 2 | Track `assets/game/shader/` in git | `4e665aa` | **Keep** — infrastructure |
| 3 | Shaders: `mediump` → `highp` | `7bc3a5a` | **Reverted** (`b53d652`) — unconfirmed |
| 4 | ASM: neuter `HistoryManager$1.update()` | `6e0c3aa` | **Keep** — fixes the rendering corruption |
| 5 | `configuration.useGL30 = true` | — | **Reverted** — disproved the NPOT theory, kept nothing |
| 6 | Debug instrumentation in `core/src` | `05ff18b` | **Removed** — served its purpose, recoverable from history |
| 7 | SDK 26/35/36, libGDX 1.14.2, native libs unextracted | — | **Keep** — 16 KB page compliance |
| 8 | ASM: `setCatchBackKey` → `setCatchKey(Keys.BACK, …)` | — | **Keep** — required by change 7 |
| 9 | `patchGameJar` declares the patchers as an input | — | **Keep** — build correctness |
| 10 | `assets.zip` stored instead of deflated | — | **Keep** — deflate saved 4.6% |
| 11 | ASM: remove the logo-triggered `ServiceRibbon_Manager` sabotage | — | **Keep** — second image-list trap |
| 12 | Asset: strip trailing newline from the map-scale index | — | **Keep** — fixes map-resolution menu |

### 10. `assets.zip` is STORED, not deflated

Measured before changing it, because the trade only makes sense with the numbers:

| | |
|---|---|
| Asset tree | 111,209 files, 1,734,419,526 B (1.62 GB) |
| Deflated `assets.zip` | 1,654,922,325 B (1.54 GB) — **95.4% of raw** |
| Stored `assets.zip` | 1,752,169,541 B (1.63 GB) |
| APK, deflated → stored | 1,667,700,985 B → 1,764,241,958 B (**+96.5 MB**) |

The tree is almost entirely png/ogg, which are already compressed, so deflate recovers 4.6%.
That 4.6% was being paid for with an inflate pass on the first launch of every install,
forever. `entryCompression = STORED` gives it back to the APK and reduces the extractor to a
byte copy.

**It did not measurably speed up extraction — it measured slower.** Unpack of the same 111,208
files on the same AVD:

| Archive | Unpack | Free space on `/data` after |
|---|---|---|
| Deflated | 79.9 s | — |
| Deflated | 98.5 s | 2.6 G |
| **Stored** | **156.3 s** | 1.3 G (86% full) |
| Stored | **49.2 s** | 4.2 G free pre-install (110,804 files, new asset set) |

Do not read that as "STORED is slower" — the runs are not comparable. Removing inflate can only
remove CPU work, and the confound is obvious in the third column: the stored APK is 96 MB
bigger, so by the last run the 10 G partition finished at 86% full, and ext4/f2fs degrade badly
as they fill. Note also the two *identical* deflated runs differ by 23%, so this AVD's I/O noise
floor is already high.

The fourth run (49.2 s, on a device with room to spare) is the fastest of the four and the only
STORED run not made against a nearly-full partition, which supports free space being the
dominant term rather than the codec — but it also packed a different asset set, so it is still
not a controlled comparison.

The honest conclusion is that the earlier measurement stands — the unpack is syscall-bound at
~720–880 µs/file — and the codec was never the term that mattered. STORED is kept for the APK
size and the simpler extractor, not for a speed win that was not demonstrated. A real
comparison would need equal free space and several runs each.

The APK grows by 96.5 MB rather than the 79 MB the compression ratio alone implies: a stored
archive of 111k entries also carries ~17 MB of per-entry local headers that deflate's savings
were previously masking.

Two consequences worth knowing:

- The device already needs room for the APK **and** the extracted copy, so this growth lands
  where it hurts. On the test AVD it pushed `adb install` into
  `INSTALL_FAILED_INSUFFICIENT_STORAGE`; `adb shell pm trim-caches 9999G` reclaimed 2 GB and
  is the cheap fix before blaming the build. Note a failed install of an APK this size can
  also leave the package *uninstalled* after appearing to roll back, so check
  `pm list packages` rather than assuming the previous version is still there.
- `assets.zip`'s length changes, and that length *is* SplashActivity's freshness marker, so
  every existing install re-extracts once. Correct by design, not a regression.

> **Trap: AGP's incremental packaging leaves the old asset blob as dead space.** The first
> STORED build produced a **3.42 GB** APK instead of the expected ~1.76 GB. Nothing was wrong
> with the zip — `assets/assets.zip` was correctly STORED at 1,752,149,721 B, but its
> `header_offset` was **1,667,688,040**, i.e. the entire previous 1.67 GB asset entry was still
> sitting in front of it, orphaned. `packageDebug` appended the new entry rather than rewriting
> a 1.7 GB archive. The zip stays *valid* — readers go through the central directory and never
> see the gap — so nothing fails; the APK is just double size, which matters when the ceiling
> is 4 GB. Fix: delete `outputs/apk/debug/*.apk` **and**
> `intermediates/incremental/debug/packageDebug`, then rebuild. Worth checking
> `zipfile.ZipFile(apk).getinfo("assets/assets.zip").header_offset` after any change to the
> asset archive.
>
> While diagnosing this, note that **`unzip -l` lies about this APK**: it reported 111,563
> entries, because a STORED nested zip has its own central directory embedded verbatim and
> `unzip` locks onto it. Python's `zipfile` reads the real one — 101 entries. Do not conclude
> from `unzip` that the 65,535-entry limit has been breached.

**Rejected alternative: `assets.tar.lz4`.** Considered for unpack speed and dropped. At 95.4%
there is essentially no decompression work to save, and lz4 trades ratio for speed — it would
produce an archive *larger* than the deflated one while the actual cost is elsewhere: the
unpack measured 80 s and 98 s across two runs, i.e. ~720–880 µs per file, which is
`open`/`write`/`close`/inode/dirent and is identical whatever the container is. It would also
add a tar reader (not in the JDK) and an LZ4 decoder; note `lz4-java`'s JNI build ships its own
`.so`, which would walk straight back into the 16 KB alignment problem of change 7.

The remaining levers, if the ~90 s ever needs to go: parallelise the writes (the bottleneck is
syscalls and it is single-threaded today), or stop extracting altogether and serve assets from
the archive through a custom `FileHandle` — the latter also removes the ~1.6 GB second copy,
which is the real reason the device needs 2x.

### 7. minSdk 26, targetSdk 35, libGDX 1.14.2, `extractNativeLibs` dropped

Four changes that only make sense together, all in service of running natively on a 16 KB
page-size device instead of via the compatibility fallback described under
*Test-environment caveats* below.

- `targetSdkVersion 28` → `35`. The old comment here warned that R+ fails to install with
  `-124: ... resources.arsc ... uncompressed and aligned`. That belonged to the **zip64**
  branch, whose hand-rolled archive `zipalign` and `apksigner` refuse to touch; a stock AGP
  build handles it. Verified by installing, not by reasoning.
- `minSdkVersion 21` → `26`.
- `gdxVersion 1.10.0` → `1.14.2`, and `compileSdk 35` → `36` as a consequence.
- `android:extractNativeLibs="true"` removed from the manifest, with
  `packagingOptions.jniLibs.useLegacyPackaging = false` stated explicitly in
  `android/build.gradle` so the intent survives an AGP default change.

**Which version.** 1.13.0 is the first release whose natives carry `p_align=0x4000`; measured
directly from the ELF program headers of the published artifacts, since the libGDX changelog
never mentions page size at all:

| Version | `libgdx.so` min PT_LOAD `p_align` |
|---|---|
| 1.10.0 | `0x1000` (4 KB) |
| 1.13.0 and later | `0x4000` (16 KB) |

Landed on **1.14.2**, the latest release. 1.13.5 was the first pick — on the assumption that
1.14.1's `TextField.OnscreenKeyboard` rewrite and `InputMultiplexer` changes would break a jar
that cannot be recompiled — but both scans come back clean on 1.14.2, so the assumption was
wrong and there was no reason to stay behind. (1.14.2 also reverts the 1.14.1
`InputMultiplexer` change outright.) Confirmed 16 KB-aligned for **both** `gdx-platform` and
`gdx-freetype-platform` across all four ABIs, and the packaged APK passes
`zipalign -c -P 16 -v 4` on all eight `.so` entries.

**`compileSdk` had to go to 36** as a consequence: `gdx-backend-android` 1.14.1+ depends on
`androidx.core:core:1.17.0`, whose AAR metadata refuses any lower `compileSdk`. This is
independent of `targetSdk`, which stays at 35. No SDK install was needed — AGP downloaded
platform 36 and accepted the licence on its own. Staying on `compileSdk 35` means capping
libGDX at **1.14.0**, the last release that still uses `androidx.core:core:1.15.0`.

**Binary compatibility is the real risk here**, not the build — and it bit. `game.jar` is
compiled against 1.10.0 and is the one thing in this project the compiler never checks, so a
removed method surfaces only as a runtime `NoSuchMethodError`. libGDX removed
`Input.setCatchBackKey`/`setCatchMenuKey` and
`AndroidApplicationConfiguration.touchSleepTime` in this range, and the game calls the first
of those. On the first run after the upgrade:

```
FATAL EXCEPTION: GLThread
java.lang.NoSuchMethodError: No interface method setCatchBackKey(Z)V in class Lcom/badlogic/gdx/Input;
  at age.of.civilizations2.jakowski.lukasz.AoCGame.create(AoCGame.java:299)
```

Fixed by `CatchBackKeyPatcher` — see change 8 below. There is no libGDX version that avoids
this: the natives became 16 KB-aligned in exactly the release that removed the method.

To find the rest of the damage the jar was scanned rather than trusted. **Two scans are
needed, and they catch different things** — run both before moving `gdxVersion`:

1. **Call sites.** Every `Fieldref`/`Methodref`/`InterfaceMethodref` in the constant pool of
   all 7269 classes of `game-patched.jar`, filtered to `com/badlogic/gdx` owners (306 distinct
   members), resolved against an index of `gdx`, `gdx-backend-android`, `gdx-freetype` **and
   `android.jar`**, walking each class's superclass and interfaces. Catches *removed* API →
   `NoSuchMethodError`. This is what `setCatchBackKey` tripped.
2. **Abstract methods.** For every concrete game class with a libGDX type anywhere in its
   supertype chain, check that every abstract method inherited from that chain is actually
   implemented. Catches API *added* to an interface the game implements →
   `AbstractMethodError`. The call-site scan is blind to this: nothing the game calls changed,
   so every ref still resolves. This is the check that clears 1.14.1's
   `TextField.OnscreenKeyboard` refactor and the extended `Input.KeyboardHeightObserver`.

Both live in [`tools/bincompat.py`](tools/bincompat.py) — run it against
`build/patched/game-patched.jar` (the *patched* jar, since the patchers exist partly to fix
these), the three libGDX artifacts, and `android.jar`. On 1.14.2: **0 missing classes,
0 missing members, 0 unimplemented abstract methods, 0 unindexed classes reached.**

> **Trap worth recording, because it produced a confident wrong answer.** The first version of
> that scan indexed only the gdx jars and treated any class outside the index as "assume the
> member exists" — meant to wave through `java.*`/`android.*`. But *every* hierarchy walk ends
> at `java/lang/Object`, which is not in the gdx jars, so the escape hatch fired on every
> lookup and the scan reported 0 missing while `setCatchBackKey` was sitting right there. It
> even "verified" the removed methods as a negative control and still missed the call site.
> Two things make the result trustworthy now: `android.jar` is in the index (it carries the
> `java.*` stubs too), and the scan **reports every unindexed class it reached** — that count
> must be 0, or the headline number means nothing.

What the scan does *not* cover, and what a run therefore has to: reflection (the game uses
`com.badlogic.gdx.utils.Json`), and behavioural changes rather than signature changes. Two
worth knowing about if something misbehaves:

- 1.13.0: exceptions in `Gdx.app.postRunnable()` tasks are **no longer swallowed** and now
  crash the app.
- 1.13.1: `SpriteBatch` defaults to VBO instead of VertexArray on GLES2.

### 8. ASM: `setCatchBackKey` → `setCatchKey(Keys.BACK, …)`

`CatchBackKeyPatcher`, applied to every class like `FileLimitPatcher`. Two call sites in
`AoCGame.create`. The rewrite is a stack shuffle — at the call the stack is `[Input, boolean]`
and `setCatchKey` wants `[Input, int, boolean]`, so:

```
ICONST_4      // Input.Keys.BACK — stack: Input, boolean, 4
SWAP          //                   stack: Input, 4, boolean
INVOKEINTERFACE com/badlogic/gdx/Input.setCatchKey (IZ)V
```

Both values are category-1 so `SWAP` is legal, and no branches are introduced, which is why
`COMPUTE_MAXS` suffices and the original `StackMapTable` stays valid. `setCatchMenuKey` was
removed in the same release but the scan finds no reference to it in this jar, so it is
deliberately not handled rather than shipped untested.

### 9. `patchGameJar` must declare the patchers as an input

The task declared only the stripped jar as its input, so editing a patcher left it
**UP-TO-DATE**: the build reported success, the patcher never ran, and the previously patched
jar shipped unchanged. That is an unusually nasty failure mode — the symptom is "my new ASM
patch has no effect", which reads as a bug in the bytecode rather than in the build. Fixed by
adding `inputs.files buildscript.configurations.classpath` in the root `build.gradle`.

Diagnostic note for next time: **verify the APK, not the timestamps.** File mtimes across
`build/patched/`, `intermediates/` and `outputs/` were misleading here and cost time. The
decisive check is to unzip `classes*.dex` from the APK and grep for the method name, with a
string you know is present (e.g. `AoCGame`) as a positive control — a dex `method_id` cannot
exist without its name in the string pool, so absence is proof.


### 11. ASM: the logo-triggered `ServiceRibbon_Manager` sabotage

**A second copy of the image-list trap, with a different trigger.** Change 4 neutered
`HistoryManager$1.update()` and the corruption went away — until the asset set was swapped for a
modded one, at which point black-blob borders and striped menus came straight back with the
HistoryManager patch still verifiably applied (checked in the stripped jar, the patched jar and
the APK's `classes.dex`). Two independent traps, same payload shape.

The payload is laundered through three deliberately innocent names, so it mentions neither
`IMGManager` nor the logo:

```java
Core.getGL()               -> return Images.gameLogo;              // Core:9483
Images.mainMenuEdge2 = getGL();                                    // Core:1099
AoCGame.disposeImages()    -> return IMGManager.getImages();       // AoCGame:544
```

Then, at the end of `ServiceRibbon_Manager.loadSRImages()`:

```java
int oRa = IMGManager.getIMG(Images.mainMenuEdge2).getWidth()
        + IMGManager.getIMG(Images.mainMenuEdge2).getHeight();
...
if (oRa != 306 && oRa != 278 && oRa != 550) {
   AoCGame.disposeImages().remove(5);
   AoCGame.disposeImages().add((Image)AoCGame.disposeImages().get(1));
}
```

`oRa` is **the game logo's width + height**, tested against a whitelist of sanctioned logo
sizes. Removing element 5 slides every later entry down one, so each of the ~325 `Images.*`
constants resolves to its neighbour's texture; re-adding a duplicate of index 1 preserves the
list length so nothing looks obviously wrong. Identical mechanism, symptoms and silence to
change 4.

Confirmed by comparing the two asset trees:

| asset set | `UI/interface/*/game_logo.png` | w + h | result |
|---|---|---|---|
| working | 220 x 86 | **306** | whitelisted, no sabotage |
| modded | 512 x 94 | **606** | trips the guard |

306 = 220+86 is the stock logo; 550 = 512+38; 278 is the third sanctioned size.

**Two possible fixes.** `ServiceRibbonPatcher` removes the eleven payload instructions and
leaves the guard and its branch targets alone, so both arms of the `if` now do nothing — chosen
because it keeps any logo art. The zero-code alternative is to ship a logo whose width + height
lands on 306, 278 or 550; **512 x 38** keeps the new wide format and passes.


**Verified on device (2026-08-22).** Built against `assetsnew/` (the modded set, logo 512x94 ->
606, i.e. the guard *does* trip), installed on the 16 KB-page AVD, launched: main menu renders
correctly — flags in the Challenges panel, crisp text, no striped panels, no black-blob borders,
no tiled logo. The APK's `classes.dex` was checked directly: zero `disposeImages` calls remain in
`loadSRImages`.

One cosmetic remnant, and it is the proof the diagnosis was right: the guards are all still in
place, so the fingerprint still detects the modified logo — the main menu's session button reads
"Age of History 2: Definitive Edition" (the `Button_Classic_LRMain_DC` arm in
`Messages/Gift/R/Menu_Main`). The check fires; it just cannot corrupt the image list any more.
Shipping a **512 x 38** logo (550, whitelisted) silences that tell and the ~29 others without any
further bytecode patching.


### 12. Asset: trailing newline in the map-scale index file

**Symptom.** Changing the map resolution bounced straight back to the main menu, with **nothing
in logcat at all** — no exception, no error.

**Cause.** `Menu_SelectMapType_Scale`'s constructor parses the scale list with no guard:

```java
FileHandle f = FileManager.loadFile("map/" + ... + "data/scales/provinces/Age_of_Civilizations");
String[] tagsSPLITED = f.readString().split(";");
for (int i = 0; i < tagsSPLITED.length; ++i)
   tempScales.add(Integer.parseInt(tagsSPLITED[i]));   // unguarded
```

The file ended with a newline, which survives `split(";")` and reaches `parseInt`. Verified by
running the four cases rather than reasoning about them:

| file bytes | tokens | last token | `parseInt` |
|---|---|---|---|
| `1;3;4;5;7;10;30;\n` | 8 | `\n` | **throws** |
| `1;3;4;5;7;10;30\n` | 7 | `30\n` | **throws** |
| `1;3;4;5;7;10;30;` | 7 | `30` | ok |
| `1;3;4;5;7;10;30` | 7 | `30` | ok |

The constructor throws before the menu object exists, so the game falls back to the main menu.
Nothing logs it.

**Fix.** Strip the trailing newline: `printf '1;3;4;5;7;10;30;' > .../Age_of_Civilizations`.
Confirmed on device — the trailing `;` is harmless, Java's `split` drops trailing empty tokens.

**This was never an assetsnew regression.** The old, "working" tree ships the *same* file with a
trailing newline (`…30;\n`) and fails identically — its last token is `\n` instead of `30\n`.
Swapping asset sets was never going to move this bug; the old set simply had never been tested
through that menu. A survey of all 20 `Age_of_Civilizations` index files found this to be the
only one affected.

> **Two hypotheses died here first — do not re-run them.**
>
> - *Missing `map/Earta/data/DefinedScales.json`.* It is genuinely absent from **both** trees,
>   and `MapScale.initDefinedScales()` has a real latent bug in its catch block —
>   `definedScalesLength = defScales.definedScale_Default` assigns a default *index* (15) to a
>   *length* whose success-path value is `definedScales.length` (30). Supplying the file was
>   tested on device and **did not fix the symptom**, so it was reverted rather than shipped
>   alongside the real fix. The latent length bug is still there if anything ever depends on it.
> - *The `game/leaders/*` and `scenarios/*_INFO.json` exceptions in logcat.* Loud, with full
>   stack traces, and tempting. They fire on **both** asset sets during initial load and are
>   unrelated to this menu.
>
> The lesson that actually mattered: the failure logged **nothing**, and the loud exceptions in
> the log were the wrong ones. Reading the constructor beat chasing stack traces.

**`GdxAssetBase` logging is far too loud for this kind of work.** It prints
`CRITICAL: File not found` on every probe — the game legitimately probes thousands of optional
paths (`map/Earta/army_boxes/NNNN` alone runs to thousands), which buries real stack traces and
makes a monitor unusable. Filter with `grep -v "\[GdxAssetBase\] CRITICAL: File not found"`
before reading a log, or consider demoting it to a debug flag.

### Why the first search for this missed it — worth knowing

The obvious sweep is "which classes call `IMGManager.getImages()` and mutate the result". That
returns exactly one hit (`AoCGame`, a benign `clear` in `dispose()`) and looks like proof there
is no second trap. It is not: this trap calls `AoCGame.disposeImages()` instead. Searches worth
running before concluding a jar is clean:

- callers of **aliases**, not just the real accessor (`disposeImages`, `getGL`)
- the whitelist constants as **bytecode operands** — 306/278/550 are `sipush` operands and never
  appear in the constant pool, so `javap | grep` over sources finds nothing
- `ref/` decompiles only 2276 of 7269 classes, so grepping Java source silently skips two thirds
  of the jar; one of the fingerprint sites lives in `Messages/Gift/R/Menu_Main`, a second class
  named `Menu_Main` in an unrelated package, which is not decompiled at all

Scanning the bytecode for `sipush 306/278/550` plus an image measurement finds **30** classes
carrying this fingerprint. Most only swap a button label to "Age of History 2: Definitive
Edition" — branding tells, harmless. Only `ServiceRibbon_Manager` pairs the fingerprint with a
list mutation. If a third trap ever surfaces, that scan is the way to find it.

### 1. Build pipeline — incremental zip64 packing (`15f72af`)

Measured on this machine:

| Scenario | Before | After |
|---|---|---|
| No-change rebuild | 76 s | 2 s |
| One-line code change | 63 s | 11 s |
| Clean build | ~11 m 30 s | ~11 m 30 s |

Four separate problems, all in `android/build.gradle` and `gradle.properties`:

- `packZip64` carried `outputs.upToDateWhen { false }`, repacking 1.5 GB on *every* invocation.
  The justification ("directories we don't track precisely") was wrong — `inputs.files()` on a
  directory hashes the whole tree.
- `signZip64` declared no outputs, so it could never be up to date and re-wrote 1.5 GB every run.
- Packing and signing each made a full pass over the APK. Digests are now computed *during*
  packing, so there is one pass, and the unsigned intermediate no longer exists.
- Assets (~97% of the APK, rarely changed) are pre-packed once by `packZip64Assets` and copied
  into the APK **still compressed** via `addRawArchiveEntry`.

Equivalence was verified against a known-good APK built before the change: `META-INF/MANIFEST.MF`
byte-identical (same MD5, 12 166 572 bytes), all 110 165 entries identical in name/order/size,
`jarsigner -verify` reports "jar verified". Confirmed again from a fully clean build.

### 3. Shaders: `mediump` → `highp` (`7bc3a5a`, reverted)

Applied to all 9 fragment shaders while chasing the rendering corruption below, then reverted:
the working reference APK ships byte-identical shaders with `mediump` and renders correctly, so
the change diverged from known-good for no established benefit.

It did *appear* to reduce panel banding when first tested, but that measurement is worthless —
the emulator's GPU backend was switched in the same step, so the improvement is unattributable.
If this is ever revisited, change one variable at a time.

The shaders are back to their original bytes. The experiment survives in history if it is ever
worth revisiting; the precision reasoning is recorded in the `7bc3a5a` commit message.

---

## Rendering corruption on Android — SOLVED (`6e0c3aa`)

Fixed by neutering the `HistoryManager` task described below. Kept in full because the eliminated
hypotheses are the expensive part: eight plausible theories died here, and without a record any of
them is easy to start re-investigating.

### Symptoms

- Province borders render as solid black blobs, or as scattered yellow glyphs
- The "Age of History II" logo appears tiled across the map
- UI panels (main-menu title, "Challenges" box) show regular **vertical striping**
- Menu text, world-map terrain, and language-select flags all render **correctly**

The tiled-logo symptom is the most diagnostic: it means the mask shaders are sampling the
**UI/logo atlas** rather than their intended texture, and repeating it.

### Ruled out — do not re-investigate without new evidence

| Hypothesis | How it was eliminated |
|---|---|
| zip64 packaging / asset count | Reproduces at 30k assets and on a classic libGDX build |
| `mediump` shader precision | Reference APK ships **byte-identical** shaders, `mediump` included |
| `stripGameJar` substituting stock libGDX | All 1078 shared classes byte-identical to stock 1.10.0; only the 90 lwjgl3 backend classes differ, correctly excluded |
| Different shader set / different game code | Both builds reference the same 7 shaders and contain the same classes (`MapBG`, `MapBG$WorldMap_Shaders`, `CFG`) |
| Emulator host-GL translation | Corruption is **identical** under gfxstream (→Vulkan) and ANGLE (→D3D11), two unrelated implementations |
| Shader compile/link failure hidden by `pedantic=false` | Logged at runtime: `shaderAlpha`, `shaderWater3`, `shaderAlpha_Map` all `compiled=true` with empty logs |
| Texture size clamped below desktop | `GL_MAX_TEXTURE_SIZE=16384` on device — same as desktop. Also `MAX_VARYING_VECTORS=32`, `MAX_FRAGMENT_UNIFORM_VECTORS=2000`: no pressure |
| `u_maskScale` / map overlays | `MapOv.dMO()` logs `oM empty` on the main menu — no overlays are loaded at all, yet the menu is corrupt. Overlays are not involved |
| Row alignment / `GL_UNPACK_ALIGNMENT` | Logged at runtime: `GL_UNPACK_ALIGNMENT=1` (libGDX sets it), and 0 of 147 textures have rows off a 4-byte boundary |
| Stale `CFG.GAMEWIDTH/GAMEHEIGHT` | 2400x1080, matching `Gdx.graphics` **and** the backbuffer exactly. Framebuffer readbacks read the right region |
| Silent GL errors | `glGetError` drained at first draw and after every border draw: always clean |
| Exceptions swallowed by empty catches | All 20 in `ProvinceBorder` (19 of them `GdxRuntimeException`) and 9 in `Renderer` now report. **Nothing fires.** Nothing is throwing |
| **NPOT textures with `GL_REPEAT`** | 9 such textures exist and correlate suspiciously well with the corrupt elements (`line32/33/62`, `pattern*`). But forcing a GLES3 context (`useGL30 = true`, verified: "creating OpenGL ES 3.0 context") lifts the GLES2 completeness restriction entirely — **and the corruption is pixel-identical.** Not the cause |

### Debugging technique: shadowing a game class from `:core`

*(The instrumentation itself has been removed — see `05ff18b` in history to resurrect it. This is
kept as a recipe, because it is what actually cracked the bug.)*

To add logging to game code without ASM, copy the decompiled class from `ref/` into
`core/src/main/java/<same package>` and edit it as plain Java. ASM patching in `patch/` stays
reserved for real fixes.

Two classes cannot be shadowed, and it matters:

- **`IMGManager`** imports `com.codedisaster.steamworks.SteamUGC`, which `stripGameJar` removes on
  purpose — a verbatim copy will not compile. Read its public state from a separate class instead.
- **`MapBG`** is ASM-patched by `MapBGPatcher`. A `:core` shadow wins on dex order, so shadowing it
  would silently revert that fix. The same trap applies to any class `patch/` touches.

This works because of dex ordering, which is worth understanding before relying on it. **Both**
copies end up in the APK — verified: our `MapOv` lands in `classes3.dex`, the game jar's in
`classes4.dex`. Android's classloader takes the first match, and `dexDirsZ` in
`android/build.gradle` lists `mergeLibDex` (which carries `:core`) before `mergeExtDex` (the
vendored jar), so the `:core` copy wins. That order is explicit, not incidental — but it *is* a
duplicate class, so if a shadow ever seems to have no effect, check which dex won before
assuming the code is wrong. To make it robust rather than order-dependent, exclude the shadowed
class in `stripGameJar` so only one copy exists.

Keep shadow classes behaviour-identical apart from logging, and delete them when done.

### ROOT CAUSE FOUND: HistoryManager shifts IMGManager's image list on mobile

`HistoryLog/HistoryManager.java`:

```java
HISTORY_LIMIT = CFG.getIsDesktop() ? 200 : 50;        // line 37
...
public final void clearHistory() {
   CFG.timelapseManager.timelapseStatsHistory.lHistory.clear();
   if (HISTORY_LIMIT == 50) {                          // i.e. "if not desktop"
      Core.addSimpleTask(new Core.SimpleTask("127" + CFG.oR.nextInt(77)) {
         public void update() {
            IMGManager.getImages().remove(4);           // shifts every index above 4
            IMGManager.getImages().add((Image)IMGManager.getImages().get(1));
         }
      });
   }
}
```

`IMGManager.addIMG` returns `images.size() - 1`, and each of the ~325 `Images.*` static ints keeps
that index forever. Removing element 4 slides everything above it down one position, so from then
on **every one of those constants resolves to its neighbour's texture**. The list size is
preserved by appending a duplicate of index 1, which is what stops it looking obviously broken.

`HISTORY_LIMIT == 50` is precisely equivalent to "not desktop", so this fires on Android and never
on desktop — which is why identical code, assets and shaders render correctly on PC. It disposes
nothing and frees no memory, so it is not an optimisation. The randomised task name
(`"127" + nextInt(77)`) and the deferred execution suggest a deliberate anti-tamper trap.

Confirmed present in the vendored `libs/game.jar`, not just the decompile — `HistoryManager$1.update()`
contains `iconst_4; List.remove(I)` and `iconst_1; List.get(I); List.add`.

**Evidence that clinched it** — the drift detector caught the slide with texture handles, index
constant while content moved:

```
armyLeft:      was 24:h75:38x26  now 24:h76:12x26
armyBG:        was 25:h76:12x26  now 25:h77:1x26
armyMiddle:    was 26:h77:1x26   now 26:h78:21x14
army_sea:      was 27:h78:21x14  now 27:h79:3x20
```

This explains every symptom: Android-only; silent (drawing a valid wrong texture raises no GL
error, which is why all 29 catch blocks and every `glGetError` stayed clean); wrong *images*
rather than damaged pixels (the tiled logo over the map is simply another texture); and identical
under gfxstream and ANGLE because the GPU is not involved.

**Fix belongs in `patch/` as ASM.** Either neuter `HistoryManager$1.update()` to a bare `return`
(its only purpose is the sabotage), or change the non-desktop `HISTORY_LIMIT` from 50 to 51 so the
guard never matches — the history cap moving by one entry is immaterial.

### Superseded — where to look next

Everything in the *upload and sampling* path has now been measured and cleared: formats, sizes,
alignment, completeness, wrap modes, shader compilation, GL errors, exceptions. The textures
arrive intact and nothing errors — yet specific elements draw as stripes.

That points at *geometry rather than pixels*: the vertices/UVs being submitted, not the texture
being sampled. Vertical banding with correct colours is what you get when a quad is drawn with
wrong texture coordinates or wrong width, repeatedly. The elements that fail (the `pattern`-tiled
"Challenges" panel, the title overlay) are drawn by tiling a small texture across a large area,
so the next thing to instrument is the code computing those destination rectangles and UVs —
`Image.draw(...)` and whichever menu class lays out that panel — logging the actual x/y/w/h and
texture regions per draw.

Note the one measurement that has not been reconciled: the texture inventory reported
`[98] line32` as 5x1, while the border draw site reported the same index as 6x1. Textures appear
to be reloaded at different sizes during startup. Worth understanding before trusting any
size-derived reasoning.

### Discarded hypothesis: row alignment on RGB888 uploads

The evidence has moved from *how textures are sampled* to *how their pixels are uploaded*.
Shaders compile, limits are ample, and the corrupt main menu draws no overlays at all — yet
whole panels are vertically banded while text beside them is crisp.

`IMGManager` has a dedicated `loadTexture_RGB888()`. RGB888 is **3** bytes per pixel, and OpenGL's
default `GL_UNPACK_ALIGNMENT` is **4**. Whenever `width * 3` is not a multiple of 4, each row is
read starting a few bytes off from the previous one, and the error accumulates down the image —
producing exactly the observed smear/shear/banding. 4-byte RGBA textures are immune, which is
why fonts and flags look fine.

To confirm: log every texture's name, dimensions and format at load, flagging any where
`width * bytesPerPixel % 4 != 0`; then check whether the corrupt elements are exactly that set.
The fix, if confirmed, is `glPixelStorei(GL_UNPACK_ALIGNMENT, 1)` before upload, or padding the
offending textures to a multiple of 4.

### Test-environment caveats

The only AVD available (`Medium_Phone`) is a poor proxy for real hardware, and comparisons
against the reference APK on it are invalid:

- It is a **16 KB page-size** image (`google_apis_playstore_ps16k`). ~~Our app runs only because
  `extractNativeLibs="true"` earns it `PAGE_SIZE_APP_COMPAT_FLAG_LOAD_NOT_ALIGNED` — libGDX 1.10.0
  natives are 4 KB aligned.~~ No longer true as of change 7: on libGDX 1.13.5 the natives are
  16 KB aligned and mapped straight out of the APK, so the compatibility fallback is not
  involved and this AVD is now a *valid* 16 KB target rather than a lenient one.
- The **reference APK cannot run here at all**: it is arm64-v8a only, and under ARM translation the
  linker enforces the 16 KB requirement strictly — `dlopen` fails on `libgdx.so`.

So "reference works, ours is corrupt" was never a like-for-like comparison: the reference has only
ever been observed on real 4 KB-page ARM hardware. **Verifying on a real device, or on a standard
4 KB-page AVD, should come before any further code-level investigation.**

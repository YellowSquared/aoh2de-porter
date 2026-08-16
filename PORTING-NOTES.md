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
| 3 | Shaders: `mediump` → `highp` | `7bc3a5a` | **Reverted** — see below |

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

## Open issue: rendering corruption on Android

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

### Debugging technique: shadowing a game class from `:core`

To add logging to game code without ASM, copy the decompiled class from `ref/` into
`core/src/main/java/<same package>` and edit it as plain Java. ASM patching in `patch/` stays
reserved for real fixes.

This works because of dex ordering, which is worth understanding before relying on it. **Both**
copies end up in the APK — verified: our `MapOv` lands in `classes3.dex`, the game jar's in
`classes4.dex`. Android's classloader takes the first match, and `dexDirsZ` in
`android/build.gradle` lists `mergeLibDex` (which carries `:core`) before `mergeExtDex` (the
vendored jar), so the `:core` copy wins. That order is explicit, not incidental — but it *is* a
duplicate class, so if a shadow ever seems to have no effect, check which dex won before
assuming the code is wrong. To make it robust rather than order-dependent, exclude the shadowed
class in `stripGameJar` so only one copy exists.

Keep shadow classes behaviour-identical apart from logging, and delete them when done.

### Leading hypothesis (untested): row alignment on RGB888 uploads

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

- It is a **16 KB page-size** image (`google_apis_playstore_ps16k`). Our app runs only because
  `extractNativeLibs="true"` earns it `PAGE_SIZE_APP_COMPAT_FLAG_LOAD_NOT_ALIGNED` — libGDX 1.10.0
  natives are 4 KB aligned.
- The **reference APK cannot run here at all**: it is arm64-v8a only, and under ARM translation the
  linker enforces the 16 KB requirement strictly — `dlopen` fails on `libgdx.so`.

So "reference works, ours is corrupt" was never a like-for-like comparison: the reference has only
ever been observed on real 4 KB-page ARM hardware. **Verifying on a real device, or on a standard
4 KB-page AVD, should come before any further code-level investigation.**

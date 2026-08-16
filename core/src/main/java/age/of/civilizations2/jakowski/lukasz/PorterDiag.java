package age.of.civilizations2.jakowski.lukasz;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;

/**
 * DEBUG ONLY — delete along with the rest of core/src when the rendering investigation ends.
 *
 * A *new* class rather than a shadow of a game class, deliberately: shadowing means shipping a
 * duplicate that beats the vendored one on dex order, which is fine for a couple of classes but
 * a bad way to reach widely-used code. In particular IMGManager cannot be shadowed at all —
 * it imports com.codedisaster.steamworks.SteamUGC, which stripGameJar removes on purpose, so a
 * verbatim copy will not compile. Shadowing MapBG would be worse: it would silently override
 * MapBGPatcher's ASM fix.
 *
 * Everything here reads state that is already public, so nothing needs to be shadowed:
 *   - IMGManager.images  is a public static List of Image
 *   - Images             holds ~325 public static int fields, each an index into that list,
 *                        so reflection recovers a *name* for every texture
 *   - Image.getTexture() exposes the Texture, whose TextureData carries the Pixmap.Format
 */
public final class PorterDiag {
   private static final String TAG = "porter-diag";
   private static boolean dumped = false;
   private static int lastDumpedCount = 0;
   private static int dumpCount = 0;

   private PorterDiag() {
   }

   private static final Map swallowSeen = new HashMap();

   /**
    * Reports an exception that the shipped code discards into an empty catch block. These sit
    * inside per-frame draw methods, so an unconditional log would emit thousands of lines a
    * second and drown logcat; instead each distinct site reports its first occurrence in full,
    * then only at 1/10/100/1000... to show whether it is a one-off or continuous.
    *
    * A throw part-way through a draw method matters beyond the lost work: it skips whatever
    * cleanup followed it, so the batch can be left on the wrong shader, matrix or texture unit
    * for subsequent draws. That is a very plausible shape for the corruption being chased.
    */
   public static void swallowed(String site, Throwable t) {
      try {
         Integer prev = (Integer)swallowSeen.get(site);
         int n = (prev == null ? 0 : prev.intValue()) + 1;
         swallowSeen.put(site, Integer.valueOf(n));
         boolean decade = n == 1 || n == 10 || n == 100 || n == 1000 || n % 10000 == 0;
         if (decade) {
            Gdx.app.log(TAG, "!! swallowed in " + site + " (occurrence " + n + "): " + t);
            if (n == 1) {
               StackTraceElement[] st = t.getStackTrace();

               for(int i = 0; i < Math.min(6, st.length); ++i) {
                  Gdx.app.log(TAG, "      at " + st[i]);
               }
            }
         }
      } catch (Exception var6) {
      }

   }

   private static final Map borderSeen = new HashMap();
   private static Map cachedNames = null;

   /**
    * Full report on one texture used to draw a province border, emitted once per
    * (call site, image id) pair — the draw methods run per border per frame, so anything
    * unconditional would be unusable.
    *
    * Province borders are drawn by tiling a tiny line texture along the border path, e.g.
    * Images.line33 is UI/lines/line_33.png at 6x1 px. IMGManager.addIMG loads these with
    * useMipMaps=true and TextureWrap.Repeat. Under GLES2 a NPOT texture combined with REPEAT
    * wrap or mipmap filtering is *incomplete*, and an incomplete texture samples as opaque
    * black — which is exactly how the borders are rendering. Desktop GL has no such rule, so
    * this breaks only after porting. GL_OES_texture_npot lifts the restriction where present,
    * hence logging the actual state rather than assuming.
    */
   public static void borderDraw(String site, int imageId) {
      try {
         String key = site + "#" + imageId;
         if (borderSeen.containsKey(key)) {
            return;
         }

         borderSeen.put(key, Boolean.TRUE);
         if (cachedNames == null) {
            cachedNames = buildIndexNames();
         }

         String name = (String)cachedNames.get(Integer.valueOf(imageId));
         if (imageId < 0 || imageId >= IMGManager.images.size()) {
            Gdx.app.log(TAG, "BORDER " + site + " imageId=" + imageId + " OUT OF RANGE (images="
               + IMGManager.images.size() + ") — nothing valid can be drawn");
            return;
         }

         Image img = (Image)IMGManager.images.get(imageId);
         if (img == null || img.getTexture() == null) {
            Gdx.app.log(TAG, "BORDER " + site + " imageId=" + imageId + " ("
               + (name != null ? name : "?") + ") has NULL texture");
            return;
         }

         Texture tex = img.getTexture();
         Pixmap.Format fmt = tex.getTextureData() != null ? tex.getTextureData().getFormat() : null;
         int w = tex.getWidth();
         int h = tex.getHeight();
         boolean isNpot = !"POT".equals(pot(w)) || !"POT".equals(pot(h));
         boolean mip = tex.getMinFilter() != null && tex.getMinFilter().isMipMap();
         boolean clamped = tex.getUWrap() == Texture.TextureWrap.ClampToEdge
            && tex.getVWrap() == Texture.TextureWrap.ClampToEdge;
         boolean incomplete = isNpot && (mip || !clamped);
         Gdx.app.log(TAG, "BORDER " + site + " img[" + imageId + "]=" + (name != null ? name : "?")
            + " " + w + "x" + h + " " + fmt
            + " (" + pot(w) + "/" + pot(h) + ")"
            + " min=" + tex.getMinFilter() + " mag=" + tex.getMagFilter()
            + " wrap=" + tex.getUWrap() + "/" + tex.getVWrap()
            + " handle=" + tex.getTextureObjectHandle()
            + (incomplete ? "  <<< NPOT-INCOMPLETE under GLES2: samples as BLACK" : ""));
         drainGlErrors("after border " + site + " img=" + imageId);
      } catch (Exception ex) {
         Gdx.app.log(TAG, "borderDraw report failed: " + ex);
      }

   }

   /** Bytes per pixel, for the row-stride arithmetic below. */
   private static int bpp(Pixmap.Format f) {
      if (f == null) {
         return 0;
      } else if (f == Pixmap.Format.Alpha || f == Pixmap.Format.Intensity) {
         return 1;
      } else if (f == Pixmap.Format.LuminanceAlpha || f == Pixmap.Format.RGB565 || f == Pixmap.Format.RGBA4444) {
         return 2;
      } else if (f == Pixmap.Format.RGB888) {
         return 3;
      } else {
         return f == Pixmap.Format.RGBA8888 ? 4 : 0;
      }
   }

   private static String pot(int n) {
      return n > 0 && (n & n - 1) == 0 ? "POT" : "NPOT";
   }

   /** Maps each IMGManager.images index back to the Images field that names it. */
   private static Map buildIndexNames() {
      Map names = new HashMap();

      try {
         for(Field f : Images.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == Integer.TYPE) {
               f.setAccessible(true);

               try {
                  names.put(Integer.valueOf(f.getInt((Object)null)), f.getName());
               } catch (Exception var5) {
               }
            }
         }
      } catch (Exception ex) {
         Gdx.app.log(TAG, "could not reflect Images field names: " + ex);
      }

      return names;
   }

   /**
    * The point of this dump. OpenGL's default GL_UNPACK_ALIGNMENT is 4, and libGDX uploads
    * pixmaps with glTexImage2D without ever calling glPixelStorei. For any format narrower than
    * 4 bytes per pixel, a row whose byte length is not a multiple of 4 is read starting at the
    * wrong offset, and the skew accumulates down the image — which is what the vertical banding
    * and smearing look like. RGBA8888 textures are immune, which is consistent with fonts and
    * flags rendering correctly while decorative panels do not.
    *
    * So: every texture, its format, and whether its rows are 4-byte aligned. If the corrupt
    * elements are exactly the UNALIGNED ones, that is the bug.
    */
   public static void dumpTextureInventoryOnce() {
      // Images load progressively — the first call sees only ~21 of ~325, long before the border
      // line textures exist. So re-dump whenever the count has grown, capped so a game that keeps
      // loading textures cannot spam forever. Only the first pass prints the header material.
      int now = IMGManager.images != null ? IMGManager.images.size() : 0;
      if (now <= lastDumpedCount || dumpCount >= 8) {
         return;
      }

      lastDumpedCount = now;
      ++dumpCount;
      boolean first = !dumped;
      dumped = true;

      try {
         if (!first) {
            Gdx.app.log(TAG, "--- re-scan " + dumpCount + ": image count grew to " + now + " ---");
         }

         if (first) {
            dumpScreenAndGl();
            dumpLateShaders();
            drainGlErrors("at first draw");
         }

         Map names = buildIndexNames();
         int total = IMGManager.images.size();
         int unaligned = 0;
         int npot = 0;
         int risky = 0;
         Gdx.app.log(TAG, "=== texture inventory: " + total + " images ===");

         for(int i = 0; i < total; ++i) {
            try {
               Image img = (Image)IMGManager.images.get(i);
               if (img == null || img.getTexture() == null) {
                  continue;
               }

               Texture tex = img.getTexture();
               Pixmap.Format fmt = tex.getTextureData() != null ? tex.getTextureData().getFormat() : null;
               int w = tex.getWidth();
               int h = tex.getHeight();
               int b = bpp(fmt);
               boolean rowAligned = b == 0 || w * b % 4 == 0;
               if (!rowAligned) {
                  ++unaligned;
               }

               boolean isNpot = !"POT".equals(pot(w)) || !"POT".equals(pot(h));
               if (isNpot) {
                  ++npot;
               }

               // GLES2 completeness rules: a NPOT texture that uses mipmap filtering, or any
               // wrap mode other than CLAMP_TO_EDGE, is *incomplete* and samples as black or
               // undefined. Desktop GL has no such restriction, so this is exactly the class of
               // bug that only shows up after porting. IMGManager.addIMG passes useMipMaps=true.
               boolean mip = tex.getMinFilter() != null && tex.getMinFilter().isMipMap();
               boolean clamped = tex.getUWrap() == Texture.TextureWrap.ClampToEdge
                  && tex.getVWrap() == Texture.TextureWrap.ClampToEdge;
               boolean incompleteRisk = isNpot && (mip || !clamped);
               if (incompleteRisk) {
                  ++risky;
               }

               // Only the interesting ones individually; a 325-line dump per run is noise.
               if (!rowAligned || incompleteRisk) {
                  String name = (String)names.get(Integer.valueOf(i));
                  Gdx.app.log(TAG, (!rowAligned ? "UNALIGNED " : "") + (incompleteRisk ? "NPOT-INCOMPLETE " : "")
                     + "[" + i + "] " + (name != null ? name : "?")
                     + " " + w + "x" + h + " " + fmt + " rowBytes=" + w * b
                     + " (" + pot(w) + "/" + pot(h) + ")"
                     + " min=" + tex.getMinFilter() + " mag=" + tex.getMagFilter()
                     + " wrap=" + tex.getUWrap() + "/" + tex.getVWrap());
               }
            } catch (Exception var14) {
            }
         }

         Gdx.app.log(TAG, "=== summary: " + unaligned + " of " + total
            + " textures have rows not 4-byte aligned; " + npot + " are NPOT; "
            + risky + " are NPOT with mipmaps or non-clamp wrap ===");
         if (unaligned == 0) {
            Gdx.app.log(TAG, "row alignment is NOT the cause — every texture is 4-byte aligned");
         }

         if (risky == 0) {
            Gdx.app.log(TAG, "NPOT completeness is NOT the cause — no risky combinations");
         }
      } catch (Exception ex) {
         Gdx.app.log(TAG, "texture inventory failed: " + ex);
      }

   }

   /**
    * Renderer.init() runs before AoCGame builds these, so they logged as null there. By first
    * draw they exist, and their compile state matters for the same reason: pedantic is off, so
    * a failure is silent. shaderDef is the batch's default — if it were broken, everything
    * would be wrong, which is a useful thing to be able to rule out.
    */
   private static void dumpLateShaders() {
      logShader("AoCGame.shaderDef", AoCGame.shaderDef);
      logShader("AoCGame.nextPlayerTurnShdr", AoCGame.nextPlayerTurnShdr);
      logShader("AoCGame.shaderAlpha3", AoCGame.shaderAlpha3);
      logShader("Renderer.shaderAlpha", Renderer.shaderAlpha);
      logShader("Renderer.shaderWater3", Renderer.shaderWater3);
      logShader("Renderer.shaderAlpha_Map", Renderer.shaderAlpha_Map);
   }

   private static void logShader(String name, com.badlogic.gdx.graphics.glutils.ShaderProgram s) {
      try {
         if (s == null) {
            Gdx.app.log(TAG, "shader " + name + " = null");
         } else {
            String log = s.getLog();
            Gdx.app.log(TAG, "shader " + name + " compiled=" + s.isCompiled()
               + (log != null && log.trim().length() > 0 ? " log=<<" + log.trim() + ">>" : ""));
         }
      } catch (Exception ex) {
         Gdx.app.log(TAG, "shader " + name + " inspection failed: " + ex);
      }

   }

   /**
    * glGetError latches until read. Anything non-zero here means a real GL call was rejected —
    * INVALID_OPERATION/INVALID_VALUE during texture upload or draw would explain corrupt output
    * directly, and nothing in this codebase ever checks.
    */
   private static void drainGlErrors(String when) {
      try {
         int err;
         int guard = 0;
         boolean any = false;

         while((err = Gdx.gl.glGetError()) != GL20.GL_NO_ERROR && guard++ < 16) {
            any = true;
            String n;
            switch (err) {
               case GL20.GL_INVALID_ENUM: n = "GL_INVALID_ENUM"; break;
               case GL20.GL_INVALID_VALUE: n = "GL_INVALID_VALUE"; break;
               case GL20.GL_INVALID_OPERATION: n = "GL_INVALID_OPERATION"; break;
               case GL20.GL_OUT_OF_MEMORY: n = "GL_OUT_OF_MEMORY"; break;
               case GL20.GL_INVALID_FRAMEBUFFER_OPERATION: n = "GL_INVALID_FRAMEBUFFER_OPERATION"; break;
               default: n = "0x" + Integer.toHexString(err);
            }

            Gdx.app.log(TAG, "!! GL error " + when + ": " + n);
         }

         if (!any) {
            Gdx.app.log(TAG, "no pending GL errors " + when);
         }
      } catch (Exception ex) {
         Gdx.app.log(TAG, "glGetError drain failed: " + ex);
      }

   }

   /**
    * CFG.GAMEWIDTH/GAMEHEIGHT are captured once from Gdx.graphics in AoCGame. MapBG feeds them
    * straight into ScreenUtils.getFrameBufferPixmap(0, CFG.GAMEHEIGHT - h, w, h) to build the
    * minimap. On Android the surface can resize after first layout (immersive mode hides the
    * system bars), so if these have gone stale the readback region is wrong and the minimap is
    * built from the wrong pixels. Any mismatch below is a real finding.
    */
   private static void dumpScreenAndGl() {
      try {
         Gdx.app.log(TAG, "CFG.GAMEWIDTH=" + CFG.GAMEWIDTH + " CFG.GAMEHEIGHT=" + CFG.GAMEHEIGHT);
         Gdx.app.log(TAG, "Gdx.graphics=" + Gdx.graphics.getWidth() + "x" + Gdx.graphics.getHeight()
            + " backBuffer=" + Gdx.graphics.getBackBufferWidth() + "x" + Gdx.graphics.getBackBufferHeight()
            + " density=" + Gdx.graphics.getDensity());

         boolean mismatch = CFG.GAMEWIDTH != Gdx.graphics.getWidth() || CFG.GAMEHEIGHT != Gdx.graphics.getHeight();
         boolean backBufferMismatch = Gdx.graphics.getWidth() != Gdx.graphics.getBackBufferWidth()
            || Gdx.graphics.getHeight() != Gdx.graphics.getBackBufferHeight();
         if (mismatch) {
            Gdx.app.log(TAG, "!! CFG dims are STALE vs Gdx.graphics — framebuffer readbacks (minimap) read the wrong region");
         }

         if (backBufferMismatch) {
            Gdx.app.log(TAG, "!! backBuffer differs from logical size — HDPI scaling in play");
         }

         if (!mismatch && !backBufferMismatch) {
            Gdx.app.log(TAG, "screen dims are consistent — stale-dimension theory does not apply");
         }

         int[] pack = new int[1];
         java.nio.IntBuffer buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
         Gdx.gl.glGetIntegerv(GL20.GL_PACK_ALIGNMENT, buf);
         pack[0] = buf.get(0);
         buf.clear();
         Gdx.gl.glGetIntegerv(GL20.GL_UNPACK_ALIGNMENT, buf);
         Gdx.app.log(TAG, "GL_PACK_ALIGNMENT=" + pack[0] + " GL_UNPACK_ALIGNMENT=" + buf.get(0)
            + "  (unpack=4 is the default that breaks 3-byte-per-pixel rows)");
      } catch (Exception ex) {
         Gdx.app.log(TAG, "screen/GL dump failed: " + ex);
      }

   }
}

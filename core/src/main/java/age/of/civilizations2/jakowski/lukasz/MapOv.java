package age.of.civilizations2.jakowski.lukasz;

import age.of.civilizations2.jakowski.lukasz.Files.FileManager;
import age.of.civilizations2.jakowski.lukasz.GameValues.GameValues;
import age.of.civilizations2.jakowski.lukasz.Menus.Load.Menu_LoadMap;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.Json;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * DEBUG COPY — decompiled from game.jar and placed in :core so it shadows the vendored
 * class. Behaviour is unchanged; only logging is added. Delete this file to go back to
 * the shipped implementation. Do NOT put real fixes here — those belong in patch/ as ASM.
 *
 * Instrumented to chase the map-overlay corruption (striped panels, tiled logo over the
 * map). Three things are worth watching here:
 *
 *  1. u_maskScale / u_maskScaleY are derived from *texture dimensions* in lOI2(). If a
 *     texture comes back a different size on Android than on desktop — clamped by
 *     GL_MAX_TEXTURE_SIZE, or a failed load returning something else — the tile is scaled
 *     wrong and repeats across the quad. Repeated wrapping of u is exactly what vertical
 *     striping looks like.
 *  2. Both textures use TextureWrap.Repeat. GLES2 only guarantees REPEAT for
 *     power-of-two textures; NPOT + REPEAT is undefined without GL_OES_texture_npot.
 *     So the POT-ness of each texture is logged.
 *  3. dMO() wrapped its whole draw loop in `catch (Exception) {}` — completely silent.
 *     Anything throwing mid-draw would break rendering with no trace at all. That catch
 *     now logs. Same for the two swallowed catches in lO().
 */
public class MapOv {
   private static final String TAG = "porter-MapOv";
   private static boolean loggedDrawOnce = false;
   private static boolean loggedGlLimitsOnce = false;

   public List lOv = new ArrayList();
   public int iOSi = 0;
   public List oT = new ArrayList();
   public List oM = new ArrayList();

   private static String pot(int n) {
      return n > 0 && (n & n - 1) == 0 ? "POT" : "NPOT";
   }

   /** GL_MAX_TEXTURE_SIZE is the ceiling a driver silently enforces; desktop GL is far more generous. */
   private static void logGlLimitsOnce() {
      if (loggedGlLimitsOnce) {
         return;
      }

      loggedGlLimitsOnce = true;

      try {
         IntBuffer buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
         Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_SIZE, buf);
         int maxTex = buf.get(0);
         buf.clear();
         Gdx.gl.glGetIntegerv(GL20.GL_MAX_TEXTURE_IMAGE_UNITS, buf);
         int maxUnits = buf.get(0);
         Gdx.app.log(TAG, "GL_MAX_TEXTURE_SIZE=" + maxTex + " GL_MAX_TEXTURE_IMAGE_UNITS=" + maxUnits);
      } catch (Exception ex) {
         Gdx.app.log(TAG, "could not read GL limits: " + ex);
      }
   }

   public final void lO(String sFile) {
      try {
         if (FileManager.loadFile("map/" + CFG.map.getFileActiveMapPath() + "overlays/" + sFile).exists()) {
            new Config();
            Json json = new Json();
            json.setElementType(Config.class, "Overlay", Overlay.class);
            Config data = (Config)json.fromJson(Config.class, FileManager.loadFile("map/" + CFG.map.getFileActiveMapPath() + "overlays/" + sFile).reader("UTF8"));
            this.lOv = new ArrayList();

            for(Object obj : data.Overlay) {
               this.lOv.add((Overlay)obj);
            }

            this.iOSi = this.lOv.size();
            Gdx.app.log(TAG, "lO(" + sFile + "): loaded " + this.iOSi + " overlay definitions");
         } else {
            Gdx.app.log(TAG, "lO(" + sFile + "): overlay config MISSING, no overlays will draw");
            this.lOv.clear();
            this.iOSi = 0;

            try {
               this.dispose();
            } catch (Exception var7) {
            }
         }
      } catch (Exception var8) {
         Gdx.app.log(TAG, "lO(" + sFile + ") FAILED (was silently swallowed): " + var8);
         this.lOv.clear();
         this.iOSi = 0;

         try {
            this.dispose();
         } catch (Exception var6) {
         }
      }

   }

   public final boolean lOI() {
      int i = Menu_LoadMap.loadMapBG_FileID++;
      if (i < this.iOSi) {
         String tile = "map/" + CFG.map.getFileActiveMapPath() + "overlays/" + ((Overlay)this.lOv.get(i)).Tile;
         this.oT.add(new Image(IMGManager.loadTexture_RGB888(tile), Texture.TextureFilter.Linear, Texture.TextureWrap.Repeat));
         Image img = (Image)this.oT.get(this.oT.size() - 1);
         Gdx.app.log(TAG, "lOI[" + i + "] tile=" + ((Overlay)this.lOv.get(i)).Tile
            + " loaded=" + img.getWidth() + "x" + img.getHeight()
            + " (" + pot(img.getWidth()) + "/" + pot(img.getHeight()) + ", wrap=Repeat)");
         return true;
      } else {
         return false;
      }
   }

   public final boolean lOI2() {
      int i = Menu_LoadMap.loadMapBG_FileID++;
      if (i < this.iOSi) {
         this.oM.add(new Image(IMGManager.loadTexture("map/" + CFG.map.getFileActiveMapPath() + "overlays/" + (CFG.getLoadHighTextureMapOverlay() ? "high/" : "low/") + ((Overlay)this.lOv.get(i)).Mask), Texture.TextureFilter.Linear, Texture.TextureWrap.Repeat));
         ((Overlay)this.lOv.get(i)).u_maskScale = (float)((Image)this.oM.get(i)).getWidth() / ((float)((Image)this.oT.get(i)).getWidth() * ((Overlay)this.lOv.get(i)).Scale);
         ((Overlay)this.lOv.get(i)).u_maskScaleY = (float)((Image)this.oM.get(i)).getHeight() / ((float)((Image)this.oT.get(i)).getHeight() * ((Overlay)this.lOv.get(i)).Scale);

         logGlLimitsOnce();
         Image mask = (Image)this.oM.get(i);
         Image tile = (Image)this.oT.get(i);
         Overlay ov = (Overlay)this.lOv.get(i);
         // The whole scale calculation, with every input, so a wrong result can be traced to
         // whichever dimension came back unexpected. Compare these numbers against desktop.
         Gdx.app.log(TAG, "lOI2[" + i + "] " + (CFG.getLoadHighTextureMapOverlay() ? "high" : "low") + "/" + ov.Mask
            + "  mask=" + mask.getWidth() + "x" + mask.getHeight() + " (" + pot(mask.getWidth()) + "/" + pot(mask.getHeight()) + ")"
            + "  tile=" + tile.getWidth() + "x" + tile.getHeight() + " (" + pot(tile.getWidth()) + "/" + pot(tile.getHeight()) + ")"
            + "  Scale=" + ov.Scale
            + "  -> u_maskScale=" + ov.u_maskScale + " u_maskScaleY=" + ov.u_maskScaleY);
         if (ov.Scale == 0.0F || Float.isNaN(ov.u_maskScale) || Float.isInfinite(ov.u_maskScale)
            || Float.isNaN(ov.u_maskScaleY) || Float.isInfinite(ov.u_maskScaleY)) {
            Gdx.app.log(TAG, "lOI2[" + i + "] !! scale is NaN/Inf/zero-divisor — tiling will be garbage");
         }

         return true;
      } else {
         return false;
      }
   }

   public void dMO(SpriteBatch oSB, int nPosX, int nPosY, float fAlpha) {
      try {
         if (this.oM.isEmpty()) {
            if (!loggedDrawOnce) {
               loggedDrawOnce = true;
               Gdx.app.log(TAG, "dMO: oM empty — no overlays drawn at all");
            }

            return;
         }

         if (CFG.map.getMpS().getCurrSc() < GameValues.gvInGame.DRAW_OV_STOP_SCALE) {
            return;
         }

         oSB.setShader(Renderer.shaderAlpha_Map);

         boolean logThisPass = !loggedDrawOnce;
         if (logThisPass) {
            loggedDrawOnce = true;
            Gdx.app.log(TAG, "dMO: first draw, iOSi=" + this.iOSi + " oT=" + this.oT.size() + " oM=" + this.oM.size()
               + " shaderCompiled=" + Renderer.shaderAlpha_Map.isCompiled());
            String log = Renderer.shaderAlpha_Map.getLog();
            if (log != null && log.trim().length() > 0) {
               // ShaderProgram.pedantic is false, so a bad shader or a missing uniform fails
               // silently and simply renders wrong. This is the only place it surfaces.
               Gdx.app.log(TAG, "dMO: shaderAlpha_Map log: " + log.trim());
            }
         }

         for(int i = 0; i < this.iOSi; ++i) {
            oSB.setColor(new Color(1.0F, 1.0F, 1.0F, (((Overlay)this.lOv.get(i)).Alpha + (CFG.map.getMpS().getCurrSc() < 1.0F ? ((Overlay)this.lOv.get(i)).AlphaScaleZoomOut * CFG.map.getMpS().getCurrSc() : ((Overlay)this.lOv.get(i)).AlphaScaleZoomOut) + CFG.map.getMpS().getCurrSc() * ((Overlay)this.lOv.get(i)).AlphaScale) * fAlpha));
            Renderer.shaderAlpha_Map.setUniformf("u_maskScale", ((Overlay)this.lOv.get(i)).u_maskScale);
            Renderer.shaderAlpha_Map.setUniformf("u_maskScaleY", ((Overlay)this.lOv.get(i)).u_maskScaleY);
            Renderer.shaderAlpha_Map.setUniformf("u_extraColor", ((Overlay)this.lOv.get(i)).ExtraColor);
            if (logThisPass) {
               Gdx.app.log(TAG, "dMO[" + i + "] uniforms u_maskScale=" + ((Overlay)this.lOv.get(i)).u_maskScale
                  + " u_maskScaleY=" + ((Overlay)this.lOv.get(i)).u_maskScaleY
                  + " u_extraColor=" + ((Overlay)this.lOv.get(i)).ExtraColor
                  + " maskTex=" + ((Image)this.oM.get(i)).getTexture().getTextureObjectHandle()
                  + " tileTex=" + ((Image)this.oT.get(i)).getTexture().getTextureObjectHandle()
                  + " drawSize=" + CFG.map.getMpB().getWidthM() + "x" + CFG.map.getMpB().getHeightM());
            }

            ((Image)this.oM.get(i)).getTexture().bind(1);
            Gdx.gl.glActiveTexture(33984);
            ((Image)this.oT.get(i)).draw(oSB, nPosX, nPosY, CFG.map.getMpB().getWidthM(), CFG.map.getMpB().getHeightM());
            oSB.flush();
         }

         if (CFG.map.getMpC().getSecondSideOfMap()) {
            for(int i = 0; i < this.iOSi; ++i) {
               oSB.setColor(new Color(1.0F, 1.0F, 1.0F, (((Overlay)this.lOv.get(i)).Alpha + (CFG.map.getMpS().getCurrSc() < 1.0F ? ((Overlay)this.lOv.get(i)).AlphaScaleZoomOut * CFG.map.getMpS().getCurrSc() : ((Overlay)this.lOv.get(i)).AlphaScaleZoomOut) + CFG.map.getMpS().getCurrSc() * ((Overlay)this.lOv.get(i)).AlphaScale) * fAlpha));
               Renderer.shaderAlpha_Map.setUniformf("u_maskScale", ((Overlay)this.lOv.get(i)).u_maskScale);
               Renderer.shaderAlpha_Map.setUniformf("u_maskScaleY", ((Overlay)this.lOv.get(i)).u_maskScaleY);
               Renderer.shaderAlpha_Map.setUniformf("u_extraColor", ((Overlay)this.lOv.get(i)).ExtraColor);
               ((Image)this.oM.get(i)).getTexture().bind(1);
               Gdx.gl.glActiveTexture(33984);
               ((Image)this.oT.get(i)).draw(oSB, nPosX + CFG.map.getMpB().getWidthM(), nPosY, CFG.map.getMpB().getWidthM(), CFG.map.getMpB().getHeightM());
               oSB.flush();
            }
         }
      } catch (Exception var6) {
         // Original swallowed this entirely. A throw here aborts the overlay pass midway and
         // leaves the batch on a half-configured shader, which would look exactly like the
         // corruption being chased.
         Gdx.app.log(TAG, "dMO FAILED (was silently swallowed): " + var6);
      }

      oSB.setShader(AoCGame.shaderDef);
      oSB.setColor(Color.WHITE);
   }

   public void dispose() {
      try {
         for(int i = this.oT.size() - 1; i >= 0; --i) {
            ((Image)this.oT.get(i)).dispose();
         }

         this.oT.clear();
      } catch (Exception ex) {
         CFG.exceptionStack(ex);
      }

      try {
         for(int i = this.oM.size() - 1; i >= 0; --i) {
            ((Image)this.oM.get(i)).dispose();
         }

         this.oM.clear();
      } catch (Exception ex) {
         CFG.exceptionStack(ex);
      }

   }

   public static class Config {
      public String Age_of_History;
      public ArrayList Overlay;
   }

   public static class Overlay {
      public String Tile;
      public String Mask;
      public float Scale;
      public float Alpha;
      public float AlphaScale;
      public float AlphaScaleZoomOut;
      public float ExtraColor;
      public float u_maskScale;
      public float u_maskScaleY;
   }
}

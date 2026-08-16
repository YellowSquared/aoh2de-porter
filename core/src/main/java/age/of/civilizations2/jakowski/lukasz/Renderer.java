package age.of.civilizations2.jakowski.lukasz;

import age.of.civilizations2.jakowski.lukasz.Files.FileManager;
import age.of.civilizations2.jakowski.lukasz.MapA.Plagues.Nuke.KNAM;
import age.of.civilizations2.jakowski.lukasz.Z_Other.GlyphLayout_Game;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import java.util.ArrayList;
import java.util.List;
import space.earlygrey.shapedrewer.ShapeDrawer;

public class Renderer {
   public static RendererSpriteBatch oSBBorder2 = new RendererSpriteBatch();
   public static TextureRegion drawerPix;
   public static ShapeDrawer shapeDrawer;
   public static float shaderTime2;
   public static ShaderProgram shaderAlpha;
   public static ShaderProgram shaderWater3;
   public static ShaderProgram shaderAlpha_Map;
   public static Province_Border_Values provinceBorderValues = new Province_Border_Values();
   public static String charset = "";
   public static GlyphLayout_Game glyphLayout = new GlyphLayout_Game();
   public static List aNS = new ArrayList();
   public static int iANSS = 0;
   public static final Vector3 textRotatedVector3 = new Vector3(0.0F, 0.0F, 1.0F);
   private static final Matrix4 tmpMatrix = new Matrix4();
   private static final Vector3 tmpRotationAxis = new Vector3(0.0F, 0.0F, 1.0F);
   private static int numOfScissors = 0;
   public static Rectangle peekBounds = null;

   public static void init() {
      oSBBorder2.oSBR = new SpriteBatch();
      Images.pix = IMGManager.buildPix_IMG();
      Images.pix2 = IMGManager.buildPix();
      drawerPix = new TextureRegion(Images.pix.getTexture());
      shapeDrawer = new ShapeDrawer(oSBBorder2.oSBR, drawerPix);
      String flagFragment = "#ifdef GL_ES\nprecision mediump float;\n#endif\nvarying vec4 v_color;\nvarying vec2 v_texCoords;\nuniform sampler2D u_texture;\nuniform sampler2D u_texture2;\nvoid main()    \n{\n vec4 mask = texture2D(u_texture2, v_texCoords);\n gl_FragColor = vec4(mask.rgb, mask.a * (v_color.a * texture2D(u_texture, v_texCoords).a));\n}";
      String vertexShader = "attribute vec4 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\nuniform mat4 u_projTrans;\nvarying vec4 v_color;\nvarying vec2 v_texCoords;\n\nvoid main()\n{\n   v_color = a_color;\n   v_color.a = v_color.a * (255.0/254.0);\n   v_texCoords = a_texCoord0;\n   gl_Position =  u_projTrans * a_position;\n}\n";
      shaderAlpha = new ShaderProgram(vertexShader, flagFragment);
      ShaderProgram var10000 = shaderAlpha;
      ShaderProgram.pedantic = false;
      shaderAlpha.bind();
      shaderAlpha.setUniformi("u_texture", 0);
      shaderAlpha.setUniformi("u_texture2", 1);
      String defaultVertex = "attribute vec4 a_position;\nattribute vec4 a_color;\nattribute vec2 a_texCoord0;\n\nuniform mat4 u_projTrans;\n\nvarying vec4 v_color;\nvarying vec2 v_texCoords;\n\nvoid main() {\n    v_color = a_color;\n    v_texCoords = a_texCoord0;\n    gl_Position = u_projTrans * a_position;\n}";
      String testFragment = "#ifdef GL_ES\n#define LOWP lowp\nprecision mediump float;\n#else\n#define LOWP\n#endif\n\nvarying LOWP vec4 v_color;\nvarying vec2 v_texCoords;\n\n\nuniform sampler2D u_texture;\nuniform sampler2D u_texture2;\nuniform float time;\nuniform vec2 resolution;\nuniform float u_maskScale;\nuniform float u_maskScaleY;\nuniform float u_useMask;\nuniform vec2 u_maskOffset;\n\n\nconst float PI = 3.1415;\n// 速度\nconst float speed = 0.03;\nconst float speed_x = 0.06;\nconst float speed_y = 0.06;\n\n// 折射角\nconst float emboss = 0.3; \t\t// 凹凸强度\nconst float intensity = 2.4;\t// 强度\nconst int steps = 8;  \t\t\t// 波纹密度\nconst float frequency = 4.0;  \t// 频率\nconst float angle = 7.0;\n\nconst float delta = 50.0;  \t\t// 增幅（越小越激烈）\nconst float intence = 200.0;   \t// 明暗强度\n\n// 高光\nconst float reflectionCutOff = 0.012;\nconst float reflectionIntence = 80000.0;\n\nfloat col(vec2 coord)\n{\n    float delta_theta = 2.0 * PI / angle;\n    float col = 0.0;\n    float theta = 0.0;\n    for (int i = 0; i < steps; i++)\n    {\n        vec2 adjc = coord;\n        theta = delta_theta * float(i);\n        adjc.x += cos(theta)*time*speed + time * speed_x;\n        adjc.y -= sin(theta)*time*speed - time * speed_y;\n        col = col + cos((adjc.x * cos(theta) -\n            adjc.y * sin(theta)) * frequency) * intensity;\n    }\n    return cos(col);\n}\n\n\nvoid main()\n{\n    vec2 p = v_texCoords, c1 = p, c2 = p;\n    float cc1 = col(c1);\n\n    c2.x += resolution.x/delta;\n    float dx = emboss*(cc1-col(c2))/delta;\n\n    c2.x = p.x;\n    c2.y += resolution.y/delta;\n    float dy = emboss*(cc1-col(c2))/delta;\n    c1.x = c1.x +dx;\n    c1.y =  c1.y+dy;\n\n    float alpha = 1.0+dot(dx,dy)*intence;\n\n\n    vec4 col = texture2D(u_texture,c1);\n vec2 newCoords = vec2(v_texCoords.x * u_maskScale, v_texCoords.y * u_maskScaleY);\n vec4 mask = vec4(1.0, 1.0, 1.0, 1.0); \n\tmask = texture2D(u_texture2, v_texCoords);\n  gl_FragColor = vec4(col.rgb, mask.a * col.a);\n}";
      shaderWater3 = new ShaderProgram(defaultVertex, testFragment);
      shaderWater3.bind();
      shaderWater3.setUniformi("u_texture", 0);
      shaderWater3.setUniformi("u_texture2", 1);
      shaderWater3.setUniformf("u_useMask", 1.0F);
      shaderWater3.setUniformf("u_maskScale", 20.0F);
      shaderWater3.setUniformf("u_maskOffset", 0.0F, 0.0F);
      shaderAlpha_Map = new ShaderProgram(vertexShader, FileManager.loadFile("game/shader/map_overlay_fragment.glsl").readString());
      var10000 = shaderAlpha_Map;
      ShaderProgram.pedantic = false;
      shaderAlpha_Map.bind();
      shaderAlpha_Map.setUniformi("u_texture", 0);
      shaderAlpha_Map.setUniformi("u_texture2", 1);
      shaderAlpha_Map.setUniformf("u_useMask", 1.0F);
      shaderAlpha_Map.setUniformf("u_maskScale", 20.0F);
      shaderAlpha_Map.setUniformf("u_maskOffset", 0.0F, 0.0F);

      logShaderDiagnostics();
   }

   /**
    * DEBUG (see MapOv for the shadowing rationale) — this class is a copy of the vendored one
    * with logging added; behaviour is unchanged.
    *
    * ShaderProgram.pedantic is set to false above, which means a shader that fails to compile
    * or link does NOT throw — it just renders wrong, silently, forever. That is indistinguishable
    * from the corruption being chased, so the compile state of every shader is worth knowing
    * before theorising about anything else. The GLES limits are logged alongside because
    * shaderWater3 is a large inline shader with a loop and heavy trig: desktop GL has generous
    * headroom, mobile GLES2 does not, and exceeding uniform/varying limits fails at link time.
    */
   private static void logShaderDiagnostics() {
      String tag = "porter-Renderer";

      try {
         com.badlogic.gdx.Gdx.app.log(tag, "GL_VENDOR=" + com.badlogic.gdx.Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_VENDOR)
            + " GL_RENDERER=" + com.badlogic.gdx.Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_RENDERER));
         com.badlogic.gdx.Gdx.app.log(tag, "GL_VERSION=" + com.badlogic.gdx.Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_VERSION)
            + " GLSL=" + com.badlogic.gdx.Gdx.gl.glGetString(com.badlogic.gdx.graphics.GL20.GL_SHADING_LANGUAGE_VERSION));

         java.nio.IntBuffer buf = com.badlogic.gdx.utils.BufferUtils.newIntBuffer(16);
         int[][] limits = new int[][]{
            {com.badlogic.gdx.graphics.GL20.GL_MAX_TEXTURE_SIZE, 0},
            {com.badlogic.gdx.graphics.GL20.GL_MAX_TEXTURE_IMAGE_UNITS, 1},
            {com.badlogic.gdx.graphics.GL20.GL_MAX_VARYING_VECTORS, 2},
            {com.badlogic.gdx.graphics.GL20.GL_MAX_FRAGMENT_UNIFORM_VECTORS, 3},
            {com.badlogic.gdx.graphics.GL20.GL_MAX_VERTEX_UNIFORM_VECTORS, 4}
         };
         String[] names = {"MAX_TEXTURE_SIZE", "MAX_TEXTURE_IMAGE_UNITS", "MAX_VARYING_VECTORS",
            "MAX_FRAGMENT_UNIFORM_VECTORS", "MAX_VERTEX_UNIFORM_VECTORS"};

         for(int i = 0; i < limits.length; ++i) {
            buf.clear();
            com.badlogic.gdx.Gdx.gl.glGetIntegerv(limits[i][0], buf);
            com.badlogic.gdx.Gdx.app.log(tag, names[limits[i][1]] + "=" + buf.get(0));
         }
      } catch (Exception ex) {
         com.badlogic.gdx.Gdx.app.log(tag, "could not read GL info: " + ex);
      }

      logOneShader(tag, "shaderAlpha", shaderAlpha);
      logOneShader(tag, "shaderWater3", shaderWater3);
      logOneShader(tag, "shaderAlpha_Map", shaderAlpha_Map);
      logOneShader(tag, "AoCGame.shaderDef", AoCGame.shaderDef);
      logOneShader(tag, "AoCGame.nextPlayerTurnShdr", AoCGame.nextPlayerTurnShdr);
   }

   private static void logOneShader(String tag, String name, com.badlogic.gdx.graphics.glutils.ShaderProgram shader) {
      try {
         if (shader == null) {
            com.badlogic.gdx.Gdx.app.log(tag, name + " = null (never created)");
            return;
         }

         String log = shader.getLog();
         boolean ok = shader.isCompiled();
         com.badlogic.gdx.Gdx.app.log(tag, name + " compiled=" + ok
            + (log != null && log.trim().length() > 0 ? " log=<<" + log.trim() + ">>" : " log=<empty>"));
      } catch (Exception ex) {
         com.badlogic.gdx.Gdx.app.log(tag, name + " inspection failed: " + ex);
      }
   }

   public static final void setShaderWater3(SpriteBatch oSB) {
      oSB.setShader(shaderWater3);
      shaderWater3.setUniformf("time", shaderTime2);
      shaderWater3.setUniformf("resolution", new Vector2((float)IMGManager.getIMG(Images.flagBigMask).getWidth(), (float)IMGManager.getIMG(Images.flagBigMask).getHeight()));
   }

   public static final void drawBox2(SpriteBatch oSB, int nPosX, int nPosY, int nWidth, int nHeight) {
      drawBox(oSB, Images.buttonGame, nPosX, nPosY, nWidth, nHeight);
   }

   public static final void drawBox(SpriteBatch oSB, int imageID, int nPosX, int nPosY, int nWidth, int nHeight) {
      int iHCeil = (nHeight + 1) / 2;
      int iHFloor = nHeight / 2;
      Image img = IMGManager.getIMG(imageID);
      nWidth = Math.max(nWidth, img.getWidth() * 2);
      img.draw2(oSB, nPosX, nPosY, nWidth - img.getWidth(), iHCeil);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY, img.getWidth(), iHCeil, true);
      img.draw2(oSB, nPosX, nPosY + iHCeil, nWidth - img.getWidth(), iHFloor, false, true);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY + iHCeil, img.getWidth(), iHFloor, true, true);
      oSB.setColor(Color.WHITE);
   }

   public static final void drawBox3(SpriteBatch oSB, int imgID, int nPosX, int nPosY, int nWidth, int nHeight, float fAlpha) {
      int segmentWidth = 16;
      int gap = 1;
      int x = nPosX;

      for(int endX = nPosX + nWidth; x < endX; x += segmentWidth + gap) {
         int drawW = Math.min(segmentWidth, endX - x);
         drawBox4(oSB, imgID, x, nPosY, drawW, nHeight, fAlpha);
      }

   }

   public static final void drawBox4(SpriteBatch oSB, int imgID, int nPosX, int nPosY, int nWidth, int nHeight, float fAlpha) {
      int iHCeil = (nHeight + 1) / 2;
      int iHFloor = nHeight / 2;
      Image img = IMGManager.getIMG(imgID);
      nWidth = Math.max(nWidth, img.getWidth() * 2);
      img.draw2(oSB, nPosX, nPosY, nWidth - img.getWidth(), iHCeil);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY, img.getWidth(), iHCeil, true);
      img.draw2(oSB, nPosX, nPosY + iHCeil, nWidth - img.getWidth(), iHFloor, false, true);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY + iHCeil, img.getWidth(), iHFloor, true, true);
   }

   public static final void drawBox2(SpriteBatch oSB, int imgID, int nPosX, int nPosY, int nWidth, int nHeight, float fAlpha) {
      int iHCeil = (nHeight + 1) / 2;
      int iHFloor = nHeight / 2;
      Image img = IMGManager.getIMG(imgID);
      nWidth = Math.max(nWidth, img.getWidth() * 2);
      img.draw2(oSB, nPosX, nPosY, nWidth - img.getWidth(), iHCeil);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY, img.getWidth(), iHCeil, true);
      img.draw2(oSB, nPosX, nPosY + iHCeil, nWidth - img.getWidth(), iHFloor, false, true);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY + iHCeil, img.getWidth(), iHFloor, true, true);
      oSB.setColor(Color.WHITE);
   }

   public static final void drawBox2(SpriteBatch oSB, Image img, int nPosX, int nPosY, int nWidth, int nHeight, float fAlpha) {
      int iHCeil = (nHeight + 1) / 2;
      int iHFloor = nHeight / 2;
      nWidth = Math.max(nWidth, img.getWidth() * 2);
      img.draw2(oSB, nPosX, nPosY, nWidth - img.getWidth(), iHCeil);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY, img.getWidth(), iHCeil, true);
      img.draw2(oSB, nPosX, nPosY + iHCeil, nWidth - img.getWidth(), iHFloor, false, true);
      img.draw2(oSB, nPosX + nWidth - img.getWidth(), nPosY + iHCeil, img.getWidth(), iHFloor, true, true);
      oSB.setColor(Color.WHITE);
   }

   public static final void drawText(SpriteBatch oSB, String sText, int nPosX, int nPosY, Color color) {
      drawText(oSB, 0, sText, nPosX, nPosY, color);
   }

   public static final void drawText(SpriteBatch oSB, int fontID, String sText, int nPosX, int nPosY, Color color) {
      try {
         if (sText != null) {
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(color);
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, (float)nPosX, (float)(-nPosY));
         }
      } catch (Exception var7) {
         PorterDiag.swallowed("Renderer.drawText", var7);
      }

   }

   public static final void drawTextWithShadow(SpriteBatch oSB, String sText, int nPosX, int nPosY, Color color) {
      drawTextWithShadow(oSB, 0, sText, nPosX, nPosY, color);
   }

   public static final void drawTextWithShadow(SpriteBatch oSB, int fontID, String sText, int nPosX, int nPosY, Color color) {
      try {
         if (sText != null) {
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(new Color(0.0F, 0.0F, 0.0F, 0.7F));
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, (float)(nPosX - 1), (float)(-nPosY - 1));
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(color);
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, (float)nPosX, (float)(-nPosY));
         }
      } catch (Exception var7) {
         PorterDiag.swallowed("Renderer.drawTextWithShadow", var7);
      }

   }

   public static final void drawTextWithShadowAlpha(SpriteBatch oSB, String sText, int nPosX, int nPosY, Color color) {
      drawTextWithShadowAlpha(oSB, 0, sText, nPosX, nPosY, color);
   }

   public static final void drawTextWithShadowAlpha(SpriteBatch oSB, int fontID, String sText, int nPosX, int nPosY, Color color) {
      try {
         if (sText != null) {
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(new Color(0.0F, 0.0F, 0.0F, 0.1F * color.a));
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, (float)(nPosX - 1), (float)(-nPosY - 1));
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(color);
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, (float)nPosX, (float)(-nPosY));
         }
      } catch (Exception var7) {
         PorterDiag.swallowed("Renderer.drawTextWithShadowAlpha", var7);
      }

   }

   public static final void drawTextWithShadowRotated(SpriteBatch oSB, String sText, int nPosX, int nPosY, Color color, float rotate) {
      drawTextWithShadowRotated(oSB, 0, sText, nPosX, nPosY, color, rotate);
   }

   public static final void drawTextWithShadowRotated(SpriteBatch oSB, int fontID, String sText, int nPosX, int nPosY, Color color, float rotate) {
      if (sText != null) {
         Matrix4 oldTransformMatrix = oSB.getTransformMatrix().cpy();

         try {
            Matrix4 mx4Font = new Matrix4();
            mx4Font.rotate(textRotatedVector3, rotate);
            mx4Font.setTranslation((float)nPosX, (float)(-nPosY), 0.0F);
            oSB.setTransformMatrix(mx4Font);
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(new Color(0.0F, 0.0F, 0.0F, 0.7F));
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, -1.0F, -1.0F);
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(color);
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, 0.0F, 0.0F);
         } catch (Exception var12) {
         PorterDiag.swallowed("Renderer.drawTextWithShadowRotated", var12);
         } finally {
            oSB.setTransformMatrix(oldTransformMatrix);
         }
      }

   }

   public static final void drawTextRotated(SpriteBatch oSB, String sText, int nPosX, int nPosY, Color color, float rotate) {
      drawTextRotated(oSB, 0, sText, nPosX, nPosY, color, rotate);
   }

   public static final void drawTextRotated(SpriteBatch oSB, int fontID, String sText, int nPosX, int nPosY, Color color, float rotate) {
      if (sText != null) {
         Matrix4 oldTransformMatrix = oSB.getTransformMatrix().cpy();

         try {
            Matrix4 mx4Font = new Matrix4();
            mx4Font.rotate(new Vector3(0.0F, 0.0F, 1.0F), rotate);
            mx4Font.setTranslation((float)nPosX, (float)(-nPosY), 0.0F);
            oSB.setTransformMatrix(mx4Font);
            ((BitmapFont)CFG.fontMain.get(fontID)).setColor(color);
            ((BitmapFont)CFG.fontMain.get(fontID)).draw(oSB, (CharSequence)sText, 0.0F, 0.0F);
         } catch (Exception var12) {
         PorterDiag.swallowed("Renderer.drawTextRotated", var12);
         } finally {
            oSB.setTransformMatrix(oldTransformMatrix);
         }
      }

   }

   public static void aNK(int iProvinceID) {
      aNS.add(new KNAM(iProvinceID));
      iANSS = aNS.size();
   }

   public static void dNAI(SpriteBatch oSB) {
      try {
         for(int i = iANSS - 1; i >= 0; --i) {
            ((KNAM)aNS.get(i)).draw(oSB);
            if (((KNAM)aNS.get(i)).mRM) {
               aNS.remove(i);
               iANSS = aNS.size();
            }
         }
      } catch (Exception ex) {
         CFG.exceptionStack(ex);
      }

   }

   public static final synchronized void drawTextRotatedBorder(SpriteBatch oSB, String sText, int nPosX, int nPosY, Color color, float rotate) {
      if (sText != null) {
         Matrix4 oldTransformMatrix = oSB.getTransformMatrix().cpy();

         try {
            Matrix4 mx4Font = new Matrix4();
            mx4Font.rotate(textRotatedVector3, rotate);
            mx4Font.setTranslation((float)nPosX, (float)(-nPosY), 0.0F);
            oSB.setTransformMatrix(mx4Font);
            CFG.fontBorder.setColor(color);
            CFG.fontBorder.draw(oSB, (CharSequence)sText, 0.0F, 0.0F);
         } catch (Exception var11) {
         PorterDiag.swallowed("Renderer.drawTextRotatedBorder", var11);
         } finally {
            oSB.setTransformMatrix(oldTransformMatrix);
         }
      }

   }

   public static final synchronized void drawTextRotatedBorder(SpriteBatch oSB, String sText, int nPosX, int nPosY, Matrix4 mx4Font) {
      try {
         if (sText != null) {
            mx4Font.setTranslation((float)nPosX, (float)(-nPosY), 0.0F);
            oSB.setTransformMatrix(mx4Font);
            CFG.fontBorder.draw(oSB, (CharSequence)sText, 0.0F, 0.0F);
         }
      } catch (Exception var6) {
         PorterDiag.swallowed("Renderer.drawTextRotatedBorder", var6);
      }

   }

   public static final synchronized void drawTextRotatedBorder_2(SpriteBatch oSB, String sText, int nPosX, int nPosY, float rotate) {
      try {
         if (sText == null) {
            return;
         }

         tmpMatrix.idt();
         tmpMatrix.translate((float)nPosX, (float)(-nPosY), 0.0F);
         tmpMatrix.rotate(tmpRotationAxis, rotate);
         oSB.setTransformMatrix(tmpMatrix);
         CFG.fontBorder.draw(oSB, (CharSequence)sText, 0.0F, 0.0F);
      } catch (Exception var6) {
         PorterDiag.swallowed("Renderer.drawTextRotatedBorder_2", var6);
      }

   }

   public static final void clearUnclearedScissors(SpriteBatch oSB) {
      while(numOfScissors > 0) {
         clipView_End(oSB);
      }

   }

   public static final void clipViewPeek() {
      try {
         peekBounds = ScissorStack.peekScissors();
      } catch (Exception ex) {
         CFG.exceptionStack(ex);
      }

   }

   public static final void clipViewPeek_Add(SpriteBatch oSB) {
      try {
         clearUnclearedScissors(oSB);
         if (peekBounds != null) {
            ScissorStack.pushScissors(peekBounds);
            ++numOfScissors;
         }
      } catch (Exception ex) {
         CFG.exceptionStack(ex);
      }

   }

   public static final boolean clipView_Start(SpriteBatch oSB, int nPosX, int nPosY, int nWidth, int nHeight) {
      try {
         Rectangle clipBounds = new Rectangle((float)nPosX, (float)nPosY, (float)nWidth, (float)nHeight);
         oSB.flush();
         ++numOfScissors;
         return ScissorStack.pushScissors(clipBounds);
      } catch (Exception ex) {
         CFG.exceptionStack(ex);
         return false;
      }
   }

   public static final void clipView_End(SpriteBatch oSB) {
      try {
         numOfScissors = Math.max(numOfScissors - 1, 0);
         oSB.flush();
         ScissorStack.popScissors();
      } catch (Exception var2) {
         PorterDiag.swallowed("Renderer.clipView_End", var2);
      }

   }

   public static final void drawBox2(SpriteBatch oSB, int nPosX, int nPosY, int nWidth, int nHeight, float fAlpha) {
      Images.pix.draw2(oSB, nPosX, nPosY, nWidth, 1);
      Images.pix.draw2(oSB, nPosX + nWidth - 1, nPosY + 1, 1, nHeight - 2);
      Images.pix.draw2(oSB, nPosX, nPosY + 1, 1, nHeight - 2);
      Images.pix.draw2(oSB, nPosX, nPosY + nHeight - 1, nWidth, 1);
      oSB.setColor(Color.WHITE);
   }
}

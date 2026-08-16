package age.of.civilizations2.jakowski.lukasz;

import age.of.civilizations2.jakowski.lukasz.GameValues.GameValues;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.GdxRuntimeException;
import java.util.ArrayList;
import java.util.List;
import space.earlygrey.shapedrewer.JoinType;

public class ProvinceBorder {
   public Array nPath = new Array();
   private boolean civBorder = false;
   private boolean wastelandBorder = false;
   private List provBorderLine = new ArrayList();
   private int provBorderLineSize;
   private int iLineWidth = 0;
   private short withProvinceID;
   public DrawProvBorder drawProvBorder;
   public List pX = new ArrayList();
   public List pY = new ArrayList();
   private int pathLastPointX;
   private int pathLastPointY;
   public static JoinType joinType;
   public static JoinType joinType_Shadow;
   public static float lineWidth;
   public static int mapCordsPosY;
   public static float pathProvinceBorderExtraWidth;
   public static float pathProvinceBorderExtraWidth2;

   public ProvinceBorder(int withProvinceID, List nPointsX, List nPointsY) {
      if (nPointsX.size() > 0) {
         this.pathLastPointX = (Short)nPointsX.get(nPointsX.size() - 1) * CFG.map.getMpB().getMapSc3();
         this.pathLastPointY = (Short)nPointsY.get(nPointsY.size() - 1) * CFG.map.getMpB().getMapSc3();
      }

      int i = 0;

      for(int iSize = nPointsX.size() - 1; i < iSize; ++i) {
         this.provBorderLine.add(new Province_Border_Line((Short)nPointsX.get(i) * CFG.map.getMpB().getMapSc3(), (Short)nPointsY.get(i) * CFG.map.getMpB().getMapSc3(), (Short)nPointsX.get(i + 1) * CFG.map.getMpB().getMapSc3(), (Short)nPointsY.get(i + 1) * CFG.map.getMpB().getMapSc3()));
      }

      i = 0;

      for(int iSize = nPointsX.size(); i < iSize; ++i) {
         this.pX.add((Short)nPointsX.get(i));
         this.pY.add((Short)nPointsY.get(i));
      }

      this.provBorderLineSize = this.provBorderLine.size();
      this.withProvinceID = (short)withProvinceID;

      // Renamed from `i` in both loops: the decompiler emitted them shadowing the method-scope
      // `i` declared above, which javac rejects. Purely a decompilation artifact — same logic.
      for(int j = 0; j < this.provBorderLineSize; ++j) {
         this.iLineWidth += ((Province_Border_Line)this.provBorderLine.get(j)).getWidth();
      }

      for(int k = 0; k < this.provBorderLineSize; ++k) {
         this.nPath.add(new Vector2((float)((Province_Border_Line)this.provBorderLine.get(k)).getPosX(), (float)(-((Province_Border_Line)this.provBorderLine.get(k)).getPosY())));
      }

      this.nPath.add(new Vector2((float)this.pathLastPointX, (float)(-this.pathLastPointY)));
   }

   public final void updateDrawProvinceBorder(int nProvinceID) {
      try {
         try {
            if (CFG.core.getProv(this.withProvinceID).getWastelandLvl() >= 0 && CFG.core.getProv(nProvinceID).getWastelandLvl() < 0 || CFG.core.getProv(nProvinceID).getWastelandLvl() >= 0 && CFG.core.getProv(this.withProvinceID).getWastelandLvl() < 0) {
               if (!CFG.getMetProv(nProvinceID) && !CFG.getMetProv(nProvinceID)) {
                  this.wastelandBorder = false;
                  this.civBorder = false;
               } else {
                  this.wastelandBorder = true;
                  this.civBorder = true;
               }
            }
         } catch (Exception var3) {
            PorterDiag.swallowed("ProvinceBorder.unknown:L78", var3);
         }

         if (this.getIsWastelandBorder()) {
            if (this.getIsCivilizationBorder()) {
               if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
                  this.drawProvBorder = new DrawProvBorder() {
                     public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                        oSB.setColor(CFG.COLOR_PROVINCE_STRAIGHT);
                        ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
                     }
                  };
               } else {
                  this.drawProvBorder = new DrawProvBorder() {
                     public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                        oSB.setColor(CFG.COLOR_PROVINCE_STRAIGHT);
                        ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
                     }
                  };
               }
            } else {
               this.drawProvBorder = new DrawProvBorder() {
                  public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  }
               };
            }
         } else if (this.getIsCivilizationBorder()) {
            if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
               this.drawProvBorder = new DrawProvBorder() {
                  public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                     oSB.setColor(CFG.COLOR_PROVINCE_STRAIGHT);
                     ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
                  }
               };
            } else {
               this.drawProvBorder = new DrawProvBorder() {
                  public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                     oSB.setColor(CFG.COLOR_PROVINCE_STRAIGHT);
                     ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
                  }
               };
            }
         } else {
            this.updateDrawProvinceBorder_Inner(nProvinceID);
         }
      } catch (GdxRuntimeException var4) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L123", var4);
      }

   }

   public final void updateDrawProvinceBorder_OwnerAnimation(boolean newState_IsCivBorder, int nProvinceID) {
      try {
         if (this.getIsWastelandBorder()) {
            this.updateDrawProvinceBorder(nProvinceID);
         } else if (newState_IsCivBorder == this.civBorder) {
            this.updateDrawProvinceBorder(nProvinceID);
            CFG.PROVINCE_BORDER_ANIMATION_TIME.remove("" + this.getWithProvinceID() + "_" + this.iLineWidth);
         } else if (this.provBorderLineSize == 1) {
            this.civBorder = newState_IsCivBorder;
            this.updateDrawProvinceBorder(nProvinceID);
         } else if (newState_IsCivBorder) {
            CFG.PROVINCE_BORDER_ANIMATION_TIME.put("" + this.getWithProvinceID() + "_" + this.iLineWidth, System.currentTimeMillis());
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  long tempTime = CFG.getPROVINCE_BORDER_ANIMATION_TIME("" + ProvinceBorder.this.getWithProvinceID() + "_" + ProvinceBorder.this.iLineWidth);
                  float tempPerc = (float)(System.currentTimeMillis() - tempTime) / (float)GameValues.gvProvinceAnimation.PROVINCE_ANIMATION_OWNER_COLOR_INTERVAL;
                  if (tempPerc >= 1.0F) {
                     CFG.PROVINCE_BORDER_ANIMATION_TIME.remove("" + ProvinceBorder.this.getWithProvinceID() + "_" + ProvinceBorder.this.iLineWidth);
                     tempPerc = 0.99F;
                     ProvinceBorder.this.updateDrawProvinceBorder(-1);
                  }

                  ProvinceBorder.this.drawDashedBorder_PercentageWidth_Full_Straight(oSB, tempPerc, nTranslateProvincePosX, CFG.COLOR_PROVINCE_STRAIGHT, CFG.COLOR_PROVINCE_DASHED, ProvinceBorder.this.getDashedImage(), Images.pix255);
               }
            };
         } else {
            CFG.PROVINCE_BORDER_ANIMATION_TIME.put("" + this.getWithProvinceID() + "_" + this.iLineWidth, System.currentTimeMillis());
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  long tempTime = CFG.getPROVINCE_BORDER_ANIMATION_TIME("" + ProvinceBorder.this.getWithProvinceID() + "_" + ProvinceBorder.this.iLineWidth);
                  float tempPerc = (float)(System.currentTimeMillis() - tempTime) / (float)GameValues.gvProvinceAnimation.PROVINCE_ANIMATION_OWNER_COLOR_INTERVAL;
                  if (tempPerc >= 1.0F) {
                     CFG.PROVINCE_BORDER_ANIMATION_TIME.remove("" + ProvinceBorder.this.getWithProvinceID() + "_" + ProvinceBorder.this.iLineWidth);
                     tempPerc = 0.99F;
                     ProvinceBorder.this.updateDrawProvinceBorder(-1);
                  }

                  ProvinceBorder.this.drawDashedBorder_PercentageWidth_Full_Straight(oSB, tempPerc, nTranslateProvincePosX, CFG.COLOR_PROVINCE_DASHED, CFG.COLOR_PROVINCE_STRAIGHT, Images.pix255, ProvinceBorder.this.getDashedImage());
               }
            };
         }
      } catch (GdxRuntimeException var4) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L169", var4);
      }

   }

   public final void updateDrawProvinceBorder_Inner(int nProvinceID) {
      try {
         if (CFG.settingsGD.ENABLE_INNERBORDERS) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(CFG.COLOR_PROVINCE_DASHED);
                  ProvinceBorder.this.drawInnerBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               }
            };
         }
      } catch (GdxRuntimeException var3) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L189", var3);
      }

   }

   public final void updateDrawProvinceBorderSeaBySea() {
      try {
         if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(CFG.COLOR_PROVINCE_SEABYSEA);
                  ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(CFG.COLOR_PROVINCE_SEABYSEA);
                  ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
               }
            };
         }
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L211", var2);
      }

   }

   public final void updateDrawProvinceBorder_SelectedProvinces() {
      try {
         if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(0.9411765F, 0.7529412F, 0.15294118F, 1.0F));
                  ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(0.9411765F, 0.7529412F, 0.15294118F, 1.0F));
                  ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
               }
            };
         }
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L233", var2);
      }

   }

   public final void updateDrawProvinceBorder_CivRegion() {
      try {
         if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(CFG.COLOR_PROVINCE_BORDER_CIV_REGION);
                  ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(CFG.COLOR_PROVINCE_BORDER_CIV_REGION);
                  ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
               }
            };
         }
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L255", var2);
      }

   }

   public final void updateDrawProvBorder_CivilizationRegion2() {
      try {
         if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
                  ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
                  ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
               }
            };
         }
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L277", var2);
      }

   }

   public final void updateDrawProvinceBorder_Active() {
      try {
         if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
                  ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
                  ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
               }
            };
         }
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L299", var2);
      }

   }

   public final void updateDrawProvinceBorder_ActiveDashed() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               oSB.setColor(new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
               ProvinceBorder.this.drawDashedBorder(oSB, Images.line33, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), nTranslateProvincePosX);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L312", var2);
      }

   }

   public final void updateDrawProvinceBorder_Active_Percentage() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               if (ProvinceBorder.this.getIsCivilizationBorder()) {
                  ProvinceBorder.this.drawStraightBorder_PercentageWidth_Full_Straight(oSB, (100.0F - CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder) / 100.0F, nTranslateProvincePosX, new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F), CFG.COLOR_PROVINCE_STRAIGHT);
               } else {
                  ProvinceBorder.this.drawStraightBorder_PercentageWidth_Full_Dashed(oSB, (100.0F - CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder) / 100.0F, nTranslateProvincePosX, new Color(CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.r, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.g, CFG.COLOR_PROVINCE_ACTIVE_PROVINCE_BORDER.b, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F), CFG.COLOR_PROVINCE_DASHED);
               }

            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L329", var2);
      }

   }

   public final void updateDrawProvinceBorder_ActiveSea() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               oSB.setColor(new Color(0.9411765F, 0.7529412F, 0.15294118F, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
               ProvinceBorder.this.drawDashedBorder(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), nTranslateProvincePosX);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L342", var2);
      }

   }

   public final void updateDrawProvBorder_ActiveSea_Dashed() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               oSB.setColor(new Color(0.9411765F, 0.7529412F, 0.15294118F, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
               ProvinceBorder.this.drawDashedBorder(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), nTranslateProvincePosX);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L355", var2);
      }

   }

   public final void updateDrawProvBorder_ActiveSeaBySea_Percentage() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               ProvinceBorder.this.drawDashedBorder_PercentageWidth_Full_Straight(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), (100.0F - CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder) / 100.0F, nTranslateProvincePosX, new Color(0.9411765F, 0.7529412F, 0.15294118F, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F), CFG.COLOR_PROVINCE_SEABYSEA);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L367", var2);
      }

   }

   public final void updateDrawProvinceBorder_ActiveLandBySea_Percentage() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               oSB.setColor(new Color(0.9411765F, 0.7529412F, 0.15294118F, (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F));
               ProvinceBorder.this.drawDashedBorder_PercentageWidth(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), (100.0F - CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder) / 100.0F, nTranslateProvincePosX);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L380", var2);
      }

   }

   public final void updateDrawProvinceBorder_MoveUnits() {
      try {
         if (CFG.map.getMapProvBorder(CFG.map.getActiveMapIDN())) {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.6F + (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.4F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F));
                  ProvinceBorder.this.drawStraightBorder(oSB, nTranslateProvincePosX);
               }
            };
         } else {
            this.drawProvBorder = new DrawProvBorder() {
               public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
                  oSB.setColor(new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.6F + (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.4F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F));
                  ProvinceBorder.this.drawStraightBorder_Classic(oSB, nTranslateProvincePosX);
               }
            };
         }
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L402", var2);
      }

   }

   public final void updateDrawProvinceBorder_MoveUnits_Percentage() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               if (ProvinceBorder.this.getIsCivilizationBorder()) {
                  ProvinceBorder.this.drawStraightBorder_PercentageWidth_Full_Straight(oSB, CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F, nTranslateProvincePosX, new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.6F + (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.4F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F), CFG.COLOR_PROVINCE_DASHED);
               } else {
                  ProvinceBorder.this.drawStraightBorder_PercentageWidth_Full_Dashed(oSB, CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F, nTranslateProvincePosX, new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.6F + (float)CFG.core.getProvinceAnimation_Active_Data().getBorderAlpha() / 255.0F * 0.4F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F), CFG.COLOR_PROVINCE_DASHED);
               }

            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L419", var2);
      }

   }

   public final void updateDrawProvinceBorder_MoveUnits_Sea() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               oSB.setColor(new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), 0.49019608F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F));
               ProvinceBorder.this.drawDashedBorder(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), nTranslateProvincePosX);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L432", var2);
      }

   }

   public final void updateDrawProvinceBorder_MoveUnits_Percentage_LandBySea() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               oSB.setColor(new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), 0.49019608F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F));
               ProvinceBorder.this.drawDashedBorder_PercentageWidth(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F, nTranslateProvincePosX);
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L445", var2);
      }

   }

   public final void updateDrawProvinceBorder_MoveUnits_Percentage_Sea() {
      try {
         this.drawProvBorder = new DrawProvBorder() {
            public void drawPB(SpriteBatch oSB, int nTranslateProvincePosX) {
               ProvinceBorder.this.drawDashedBorder_PercentageWidth_Full_SeaBySea(oSB, Images.line44, CFG.core.getProvinceAnimation_Highlighted_Data().getLineOffset(), CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F, nTranslateProvincePosX, new Color(CFG.getColorStep(224, 250, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(206, 234, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), CFG.getColorStep(91, 4, CFG.core.getProvinceAnimation_Active_Data().getColorStepID(), 60), 0.49019608F * CFG.core.fDashedLine_Percentage_HighlitedProvinceBorder / 100.0F));
            }
         };
      } catch (GdxRuntimeException var2) {
         PorterDiag.swallowed("ProvinceBorder.drawPB:L457", var2);
      }

   }

   public static final void drawProvBorder_Prepare() {
      lineWidth = GameValues.gvProvinceBorder.PROVINCE_BORDER_WIDTH_BASE + CFG.settingsGD.BORDER_EXTRA_THICKNESS;
      if (CFG.map.getMpS().getCurrSc() < 1.0F) {
         lineWidth = Math.min(GameValues.gvProvinceBorder.PROVINCE_BORDER_WIDTH_ZOOM_IN_EXTRA_LIMIT, lineWidth / CFG.map.getMpS().getCurrSc());
      }

      lineWidth += (float)CFG.settingsGD.BORDER_EXTRA_WIDTH;
      mapCordsPosY = -CFG.map.getMpC().getPY();
      pathProvinceBorderExtraWidth = Math.min(Renderer.provinceBorderValues.MAX_BORDER_WIDTH, Math.max(Renderer.provinceBorderValues.MIN_BORDER_WIDTH, Renderer.provinceBorderValues.MAX_BORDER_WIDTH / CFG.map.getMpS().getCurrSc())) + (float)CFG.settingsGD.BORDER_EXTRA_WIDTH;
      pathProvinceBorderExtraWidth2 = Math.max(1.0F, pathProvinceBorderExtraWidth / Renderer.provinceBorderValues.BORDER_WIDTH_DIVIDE) + (float)CFG.settingsGD.BORDER_EXTRA_WIDTH;
      if (CFG.map.getMpS().getCurrSc() < Renderer.provinceBorderValues.SCALE_NONE_NONE) {
         joinType = JoinType.NONE;
         joinType_Shadow = JoinType.NONE;
      } else if (CFG.map.getMpS().getCurrSc() < Renderer.provinceBorderValues.SCALE_NONE_POINTY) {
         joinType = JoinType.NONE;
         joinType_Shadow = JoinType.POINTY;
      } else if (CFG.map.getMpS().getCurrSc() < Renderer.provinceBorderValues.SCALE_POINTY_POINTY) {
         joinType = JoinType.POINTY;
         joinType_Shadow = JoinType.POINTY;
      } else if (CFG.map.getMpS().getCurrSc() < Renderer.provinceBorderValues.SCALE_POINTY_SMOOTH) {
         joinType = JoinType.POINTY;
         joinType_Shadow = JoinType.SMOOTH;
      } else {
         joinType = JoinType.SMOOTH;
         joinType_Shadow = JoinType.SMOOTH;
      }

   }

   public final void drawStraightBorder_Classic(SpriteBatch oSB, int nTranslateProvincePosX) {
      for(int i = 0; i < this.provBorderLineSize; ++i) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), ((Province_Border_Line)this.provBorderLine.get(i)).getWidth(), IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle());
      }

   }

   public final void drawStraightBorder(SpriteBatch oSB, int nTranslateProvincePosX) {
      Renderer.shapeDrawer.setColor(oSB.getColor());
      Renderer.shapeDrawer.path2(this.nPath, lineWidth, joinType, true, (float)nTranslateProvincePosX, (float)mapCordsPosY);
   }

   public final void drawStraightBorder_PercWidth(SpriteBatch oSB, float fPercent, int nTranslateProvincePosX) {
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int i = 0;

      for(int currentWidth = 0; i < this.provBorderLineSize && currentWidth <= lineWidth; ++i) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle());
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final void drawDashedBorder_PercentageWidth_Full_Straight(SpriteBatch oSB, int iImageID, int offsetX, float fPercent, int nTranslateProvincePosX, Color activeColor, Color oldColor) {
      PorterDiag.borderDraw("drawDashedBorder_Full_Straight", iImageID);
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int iBeginDraw_ID = 0;
      int currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize; ++i) {
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         if (currentWidth >= lineWidth) {
            if (i > 0) {
               iBeginDraw_ID = i - 1;
            }
            break;
         }
      }

      oSB.setColor(oldColor);

      while(iBeginDraw_ID < this.provBorderLineSize) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getWidth(), IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getAngle());
         ++iBeginDraw_ID;
      }

      oSB.setColor(activeColor);
      currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize && currentWidth <= lineWidth; ++i) {
         IMGManager.getIMG(iImageID).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(iImageID).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(iImageID).getHeight() * CFG.PROVINCE_BORDER_DASHED_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle(), offsetX);
         offsetX += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final void drawStraightBorder_PercentageWidth_Full_Straight(SpriteBatch oSB, float fPercent, int nTranslateProvincePosX, Color activeColor, Color oldColor) {
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int iBeginDraw_ID = 0;
      int currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize; ++i) {
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         if (currentWidth >= lineWidth) {
            if (i > 0) {
               iBeginDraw_ID = i - 1;
            }
            break;
         }
      }

      oSB.setColor(oldColor);

      while(iBeginDraw_ID < this.provBorderLineSize) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getWidth(), IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getAngle());
         ++iBeginDraw_ID;
      }

      oSB.setColor(activeColor);
      currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize && currentWidth <= lineWidth; ++i) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle());
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final void drawDashedBorder_PercentageWidth_Full_Straight(SpriteBatch oSB, float fPercent, int nTranslateProvincePosX, Color activeColor, Color oldColor, int nImageIDActive, int nImageIDOld) {
      PorterDiag.borderDraw("drawDashedBorder_Full_Straight2/old", nImageIDOld);
      PorterDiag.borderDraw("drawDashedBorder_Full_Straight2", nImageIDActive);
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int iBeginDraw_ID = 0;
      int currentWidth = 0;
      int offsetX = 0;

      for(int i = 0; i < this.provBorderLineSize; ++i) {
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         if (currentWidth >= lineWidth) {
            if (i > 0) {
               iBeginDraw_ID = i - 1;
            }
            break;
         }
      }

      oSB.setColor(oldColor);

      while(iBeginDraw_ID < this.provBorderLineSize) {
         IMGManager.getIMG(nImageIDActive).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosY() - IMGManager.getIMG(nImageIDActive).getHeight(), ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getWidth(), IMGManager.getIMG(nImageIDActive).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getAngle());
         ++iBeginDraw_ID;
      }

      oSB.setColor(activeColor);
      currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize && currentWidth <= lineWidth && i < iBeginDraw_ID; ++i) {
         IMGManager.getIMG(nImageIDOld).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(nImageIDOld).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(nImageIDOld).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle(), offsetX);
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         offsetX += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final void drawStraightBorder_PercentageWidth_Full_Dashed(SpriteBatch oSB, float fPercent, int nTranslateProvincePosX, Color activeColor, Color oldColor) {
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int iBeginDraw_ID = 0;
      int currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize; ++i) {
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         if (currentWidth >= lineWidth) {
            if (i > 0) {
               iBeginDraw_ID = i - 1;
               currentWidth -= ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
               currentWidth -= ((Province_Border_Line)this.provBorderLine.get(i - 1)).getWidth();
            } else {
               currentWidth = 0;
            }
            break;
         }
      }

      oSB.setColor(oldColor);

      while(iBeginDraw_ID < this.provBorderLineSize) {
         IMGManager.getIMG(this.getDashedImage()).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosY() - IMGManager.getIMG(this.getDashedImage()).getHeight(), ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getWidth(), IMGManager.getIMG(this.getDashedImage()).getHeight() * CFG.PROVINCE_BORDER_DASHED_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getAngle(), currentWidth);
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getWidth();
         ++iBeginDraw_ID;
      }

      oSB.setColor(activeColor);
      currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize && currentWidth <= lineWidth; ++i) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle());
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final int getDashedImage() {
      return Images.line32;
   }

   public final void drawInnerBorder(SpriteBatch oSB, int nTranslateProvincePosX) {
      this.drawDashedBorder(oSB, this.getDashedImage(), 0, nTranslateProvincePosX);
   }

   public final void drawDashedBorder(SpriteBatch oSB, int iImageID, int offsetX, int nTranslateProvincePosX) {
      PorterDiag.borderDraw("drawDashedBorder", iImageID);
      for(int i = 0; i < this.provBorderLineSize; ++i) {
         IMGManager.getIMG(iImageID).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(iImageID).getHeight(), ((Province_Border_Line)this.provBorderLine.get(i)).getWidth(), IMGManager.getIMG(iImageID).getHeight() * CFG.PROVINCE_BORDER_DASHED_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle(), offsetX);
         offsetX += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final void drawDashedBorder_PercentageWidth(SpriteBatch oSB, int iImageID, int offsetX, float fPercent, int nTranslateProvincePosX) {
      PorterDiag.borderDraw("drawDashedBorder_PercentageWidth", iImageID);
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int i = 0;

      for(int currentWidth = 0; i < this.provBorderLineSize && currentWidth <= lineWidth; ++i) {
         IMGManager.getIMG(iImageID).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(iImageID).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(iImageID).getHeight() * CFG.PROVINCE_BORDER_DASHED_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle(), offsetX);
         offsetX += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final void drawDashedBorder_PercentageWidth_Full_SeaBySea(SpriteBatch oSB, int iImageID, int offsetX, float fPercent, int nTranslateProvincePosX, Color activeColor) {
      PorterDiag.borderDraw("drawDashedBorder_PercentageWidth_Full_SeaBySea", iImageID);
      int lineWidth = (int)((float)this.iLineWidth * fPercent);
      int iBeginDraw_ID = 0;
      int currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize; ++i) {
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         if (currentWidth >= lineWidth) {
            if (i > 0) {
               iBeginDraw_ID = i - 1;
            }
            break;
         }
      }

      oSB.setColor(CFG.COLOR_PROVINCE_SEABYSEA);

      while(iBeginDraw_ID < this.provBorderLineSize) {
         IMGManager.getIMG(Images.pix255).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getPosY() - IMGManager.getIMG(Images.pix255).getHeight(), ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getWidth(), IMGManager.getIMG(Images.pix255).getHeight() * CFG.PROVINCE_BORDER_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(iBeginDraw_ID)).getAngle());
         ++iBeginDraw_ID;
      }

      oSB.setColor(activeColor);
      currentWidth = 0;

      for(int i = 0; i < this.provBorderLineSize && currentWidth <= lineWidth; ++i) {
         IMGManager.getIMG(iImageID).drawO(oSB, nTranslateProvincePosX + ((Province_Border_Line)this.provBorderLine.get(i)).getPosX(), CFG.map.getMpC().getPY() + ((Province_Border_Line)this.provBorderLine.get(i)).getPosY() - IMGManager.getIMG(iImageID).getHeight(), currentWidth + ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() <= lineWidth ? ((Province_Border_Line)this.provBorderLine.get(i)).getWidth() : lineWidth - currentWidth, IMGManager.getIMG(iImageID).getHeight() * CFG.PROVINCE_BORDER_DASHED_THICKNESS, ((Province_Border_Line)this.provBorderLine.get(i)).getAngle(), offsetX);
         offsetX += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
         currentWidth += ((Province_Border_Line)this.provBorderLine.get(i)).getWidth();
      }

   }

   public final int getWithProvinceID() {
      return this.withProvinceID;
   }

   public final boolean getIsWastelandBorder() {
      return this.wastelandBorder;
   }

   public final boolean getIsCivilizationBorder() {
      return this.civBorder;
   }

   public final void setIsCivilizationBorder(boolean civilizationBorder, int iProvinceID) {
      this.civBorder = civilizationBorder;
      this.updateDrawProvinceBorder(iProvinceID);
   }

   public final void setIsCivilizationBorder_Just(boolean civilizationBorder, int iProvinceID) {
      this.civBorder = civilizationBorder;
   }

   public final void setIsCivilizationBorder_OwnerAnimation(boolean civilizationBorder, int iProvinceID) {
      this.updateDrawProvinceBorder_OwnerAnimation(civilizationBorder, iProvinceID);
      this.civBorder = civilizationBorder;
   }

   public final void setIsWastelandBorder(boolean wastelandBorder, int iProvinceID) {
      this.wastelandBorder = wastelandBorder;
      this.updateDrawProvinceBorder(iProvinceID);
   }

   static {
      joinType = JoinType.POINTY;
      joinType_Shadow = JoinType.SMOOTH;
      lineWidth = 1.0F;
      mapCordsPosY = 0;
      pathProvinceBorderExtraWidth = 1.0F;
      pathProvinceBorderExtraWidth2 = 1.0F;
   }

   public interface DrawProvBorder {
      void drawPB(SpriteBatch var1, int var2);
   }
}

package aoc.kingdoms.lukasz.jakowski.android;

import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import aoc.kingdoms.lukasz.jakowski.GdxAssetBase;

import java.io.File;

public class AndroidLauncher extends AndroidApplication {

    private static final String TAG = "AssetDebug";
    private static final String PROBE = "PointerProbe";
    private File destDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        destDir = new File(getFilesDir(), "assets");
        Log.d(TAG, "Destination Dir: " + destDir.getAbsolutePath());

        // Verify a critical file exists before launching
        File checkFile = new File(destDir, "UI/interface/XXH/buttons/menu.png");
        Log.d(TAG, "Verification check - menu.png exists: " + checkFile.exists() + " at " + checkFile.getAbsolutePath());

        updateAssetBasePath();

        AndroidApplicationConfiguration configuration = new AndroidApplicationConfiguration();
        configuration.useImmersiveMode = true;
        initialize(new AA_Game(), configuration);
        startPointerProbe();
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: Updating base path.");
        updateAssetBasePath();
        super.onResume();
    }


    // ---- TEMPORARY DIAGNOSTIC: remove once the pinch-zoom regression is located ----
    // Pinch zoom (AoCGame:1348) is gated on `Gdx.input.isTouched(1) && pointer == 0`, so it
    // needs the backend to register a SECOND pointer. libGDX 1.10.0 -> 1.14.2 left isTouched/
    // getX/getY byte-identical and kept the same touch-handler classes, so static diffing
    // cannot say whether pointer 1 is being populated at all. This polls the same state the
    // game reads and logs only on change, which answers that directly.
    private void startPointerProbe() {
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                String last = "";
                while (true) {
                    try {
                        if (Gdx.input != null) {
                            boolean p0 = Gdx.input.isTouched(0);
                            boolean p1 = Gdx.input.isTouched(1);
                            String now = p0 + "," + p1;
                            if (!now.equals(last)) {
                                last = now;
                                Log.d(PROBE, "p0=" + p0 + " (" + Gdx.input.getX(0) + "," + Gdx.input.getY(0)
                                        + ")  p1=" + p1 + " (" + Gdx.input.getX(1) + "," + Gdx.input.getY(1) + ")");
                            }
                        }
                        Thread.sleep(60);
                    } catch (InterruptedException ie) {
                        return;
                    } catch (Throwable ex) {
                        Log.e(PROBE, "probe error", ex);
                        return;
                    }
                }
            }
        }, "pointer-probe");
        t.setDaemon(true);
        t.start();
    }

    private void updateAssetBasePath() {
        if (destDir != null && destDir.exists()) {
            GdxAssetBase.baseDir = destDir.getAbsolutePath() + File.separator;
            Log.d(TAG, "GdxAssetBase.baseDir set to: " + GdxAssetBase.baseDir);
        } else {
            Log.e(TAG, "updateAssetBasePath: destDir is null or does not exist!");
        }
    }
}

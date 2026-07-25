package aoc.kingdoms.lukasz.jakowski.android;

import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import aoc.kingdoms.lukasz.jakowski.GdxAssetBase;

import java.io.File;

public class AndroidLauncher extends AndroidApplication {

    private static final String TAG = "AssetDebug";
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
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume: Updating base path.");
        updateAssetBasePath();
        super.onResume();
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

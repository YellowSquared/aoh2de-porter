package aoc.kingdoms.lukasz.jakowski.android;

import android.os.Bundle;
import android.util.Log;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import aoc.kingdoms.lukasz.jakowski.AA_Game;
import aoc.kingdoms.lukasz.jakowski.GdxAssetBase;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class AndroidLauncher extends AndroidApplication {

    private static final String TAG = "AssetDebug";
    private File destDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        destDir = new File(getFilesDir(), "assets");
        Log.d(TAG, "Destination Dir: " + destDir.getAbsolutePath());

        // 1. Unpack assets
        if (!destDir.exists()) {
            Log.d(TAG, "assets not found, starting unpack...");
            destDir.mkdirs();
            unpackZip("assets.zip", destDir);
        } else {
            Log.d(TAG, "assets already exists at: " + destDir.getAbsolutePath());
        }

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

    private void unpackZip(String zipName, File targetDir) {
        try (InputStream is = getAssets().open(zipName);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];
            int count = 0;

            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(targetDir, entry.getName());
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                    count++;
                }
                zis.closeEntry();
            }
            Log.d(TAG, "Unpack complete. Files extracted: " + count);
        } catch (Exception e) {
            Log.e(TAG, "Unpack failed!", e);
        }
    }
}

package aoc.kingdoms.lukasz.jakowski;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import java.io.File;

public class GdxAssetBase {
    // You MUST have this variable for your FileLimitPatcher to work!
    public static String baseDir = "";

    public static FileHandle getFile(String path) {
        // 1. Clean the path
        String cleanPath = path.startsWith("/") ? path.substring(1) : path;
        String fullPath = baseDir + cleanPath;

        // 2. Check the external (patched) location first
        File f = new File(fullPath);
        if (f.exists()) {
            return Gdx.files.absolute(fullPath);
        }

        // 3. Fallback to internal
        if (Gdx.files.internal(path).exists()) {
            return Gdx.files.internal(path);
        }

        // 4. Log failure but return the path so the game handles the error
        System.err.println("[GdxAssetBase] CRITICAL: File not found in external or internal: " + fullPath);
        return Gdx.files.absolute(fullPath);
    }
}

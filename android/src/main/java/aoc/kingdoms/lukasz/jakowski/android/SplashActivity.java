package aoc.kingdoms.lukasz.jakowski.android;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;
import aoc.kingdoms.lukasz.jakowski.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Launcher entry point: unpacks assets.zip on first run, then hands off to AndroidLauncher.
 *
 * The game's ~125k asset files ship as one archive so the APK stays under AGP's 65,535-entry
 * limit (see packAssetsZip in android/build.gradle); FileLimitPatcher rewrites the game's
 * Gdx.files.internal() calls to read from the copy unpacked here.
 *
 * Why this is a separate Activity rather than a branch inside AndroidLauncher: AndroidLauncher
 * extends AndroidApplication, whose lifecycle assumes initialize() ran in onCreate. Deferring
 * that call until after extraction leaves its graphics/input state null, and the first
 * onResume() then dies with "Unable to resume activity" — the Activity contract does not allow
 * skipping super.onResume() to dodge it. Keeping extraction in a plain Activity avoids the
 * problem entirely, and matches the arrangement the manifest referred to before this class was
 * lost.
 *
 * The unpack moves ~1.5 GB, so it runs on a background thread behind a progress bar: on the main
 * thread it would trip the ANR watchdog, and with no UI it is indistinguishable from a hang.
 */
public class SplashActivity extends Activity {

    private static final String TAG = "AssetDebug";
    private static final String ASSET_ARCHIVE = "assets.zip";

    /** Written last and only on success, so an interrupted unpack is never taken for a good one. */
    private static final String MARKER = ".extraction-complete";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private File destDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        destDir = new File(getFilesDir(), "assets");
        Log.d(TAG, "Destination Dir: " + destDir.getAbsolutePath());

        if (new File(destDir, MARKER).exists()) {
            Log.d(TAG, "assets already extracted, skipping unpack");
            launchGame();
            return;
        }

        // Absent marker covers both a first launch and a previous run that died part-way
        // through; either way the tree is rebuilt from scratch.
        setContentView(R.layout.extract_progress);
        final ProgressBar bar = findViewById(R.id.extract_bar);
        final TextView status = findViewById(R.id.extract_status);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = extract(bar, status);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            launchGame();
                        } else {
                            status.setText(R.string.extract_failed);
                        }
                    }
                });
            }
        }, "asset-extract").start();
    }

    private void launchGame() {
        startActivity(new Intent(this, AndroidLauncher.class));
        // Finish so back from the game exits rather than returning to a dead splash.
        finish();
    }

    /**
     * Unpacks assets.zip into destDir, reporting progress against the archive's size.
     *
     * Progress is measured in bytes rather than entries because counting entries would need a
     * full pass over the stream first — the archive is only readable sequentially out of the
     * APK, so that would double the work. aaptOptions keeps .zip uncompressed inside the APK,
     * so openFd() reports its real length.
     */
    private boolean extract(final ProgressBar bar, final TextView status) {
        File marker = new File(destDir, MARKER);
        //noinspection ResultOfMethodCallIgnored
        marker.delete();
        //noinspection ResultOfMethodCallIgnored
        destDir.mkdirs();

        long totalBytes = -1L;
        try {
            AssetFileDescriptor afd = getAssets().openFd(ASSET_ARCHIVE);
            totalBytes = afd.getLength();
            afd.close();
        } catch (IOException e) {
            // Costs only the percentage, not the unpack.
            Log.w(TAG, "could not read " + ASSET_ARCHIVE + " length; progress will be indeterminate", e);
        }

        final long total = totalBytes;
        final String totalLabel = total > 0 ? humanBytes(total) : "?";
        if (total <= 0) {
            ui.post(new Runnable() {
                @Override
                public void run() {
                    bar.setIndeterminate(true);
                }
            });
        }

        long written = 0L;
        int files = 0;
        int lastPercent = -1;
        byte[] buffer = new byte[64 * 1024];
        String destCanonical;
        try {
            destCanonical = destDir.getCanonicalPath() + File.separator;
        } catch (IOException e) {
            Log.e(TAG, "cannot resolve destination", e);
            return false;
        }

        try (InputStream is = getAssets().open(ASSET_ARCHIVE);
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());

                // Zip-slip guard: an entry named ../../x would otherwise escape destDir.
                if (!file.getCanonicalPath().startsWith(destCanonical)) {
                    Log.e(TAG, "refusing entry outside destination: " + entry.getName());
                    return false;
                }

                if (entry.isDirectory()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.mkdirs();
                } else {
                    //noinspection ResultOfMethodCallIgnored
                    file.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                            written += len;
                        }
                    }
                    files++;
                }
                zis.closeEntry();

                if (total > 0) {
                    // Bytes written out, compared against the archive's size. Most assets are
                    // png/ogg and barely compress, so the two track closely enough for a bar;
                    // clamped so it can never read over 100%.
                    final int percent = (int) Math.min(100L, written * 100L / total);
                    if (percent != lastPercent) {
                        lastPercent = percent;
                        final long done = written;
                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                bar.setProgress(percent);
                                status.setText(getString(R.string.extract_progress,
                                        percent, humanBytes(done), totalLabel));
                            }
                        });
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Unpack failed!", e);
            return false;
        }

        try {
            //noinspection ResultOfMethodCallIgnored
            marker.createNewFile();
        } catch (IOException e) {
            Log.e(TAG, "could not write completion marker", e);
            return false;
        }

        Log.d(TAG, "Unpack complete. Files extracted: " + files);
        return true;
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}

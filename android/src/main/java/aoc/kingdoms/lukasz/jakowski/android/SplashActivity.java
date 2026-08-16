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

    /**
     * Written last and only on success, so an interrupted unpack is never taken for a good one.
     * Holds the archive's byte length: an update that ships different assets changes that length,
     * which forces a re-extract. Without it the app would keep serving the previous build's files
     * forever, since a plain "done" flag cannot tell the two apart.
     */
    private static final String MARKER = ".extraction-complete";

    private final Handler ui = new Handler(Looper.getMainLooper());
    private File destDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        destDir = new File(getFilesDir(), "assets");
        Log.d(TAG, "Destination Dir: " + destDir.getAbsolutePath());

        if (isExtractedAndCurrent()) {
            Log.d(TAG, "assets already extracted and current, skipping unpack");
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

    /**
     * True only if a previous extraction finished *and* it was of this build's archive. Comparing
     * the recorded length against the current one is what makes an asset update re-extract; an
     * unreadable or absent marker simply means "extract", which is the safe direction.
     */
    private boolean isExtractedAndCurrent() {
        File marker = new File(destDir, MARKER);
        if (!marker.exists()) return false;

        long current = archiveLength();
        if (current <= 0) {
            // Length unavailable, so freshness cannot be judged. Trust the marker rather than
            // re-extracting 1.4 GB on every launch.
            Log.w(TAG, "archive length unknown; accepting existing extraction");
            return true;
        }

        try (java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(marker))) {
            String line = r.readLine();
            long recorded = line == null ? -1L : Long.parseLong(line.trim());
            if (recorded == current) return true;
            Log.d(TAG, "archive changed (" + recorded + " -> " + current + "), re-extracting");
            return false;
        } catch (Exception e) {
            Log.w(TAG, "unreadable marker, re-extracting", e);
            return false;
        }
    }

    private long archiveLength() {
        try {
            AssetFileDescriptor afd = getAssets().openFd(ASSET_ARCHIVE);
            long len = afd.getLength();
            afd.close();
            return len;
        } catch (IOException e) {
            return -1L;
        }
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

        // Clear the old tree rather than unpacking over it: a build that *removes* an asset would
        // otherwise leave the stale file behind forever, since extraction only ever writes. Cheap
        // to do — this only runs when the archive actually changed.
        if (destDir.exists()) {
            deleteRecursively(destDir);
        }
        //noinspection ResultOfMethodCallIgnored
        destDir.mkdirs();

        final long total = archiveLength();
        if (total <= 0) {
            // Costs only the percentage, not the unpack.
            Log.w(TAG, "could not read " + ASSET_ARCHIVE + " length; progress will be indeterminate");
        }

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

        try (FileOutputStream fos = new FileOutputStream(marker)) {
            fos.write(Long.toString(total).getBytes("UTF-8"));
        } catch (IOException e) {
            Log.e(TAG, "could not write completion marker", e);
            return false;
        }

        Log.d(TAG, "Unpack complete. Files extracted: " + files);
        return true;
    }

    /** Iterative rather than recursive: the asset tree is deep and this runs on a 110k-entry copy. */
    private static void deleteRecursively(File root) {
        java.util.ArrayDeque<File> stack = new java.util.ArrayDeque<>();
        java.util.ArrayDeque<File> dirs = new java.util.ArrayDeque<>();
        stack.push(root);

        while (!stack.isEmpty()) {
            File f = stack.pop();
            File[] children = f.listFiles();
            if (children != null && children.length > 0) {
                dirs.push(f);
                for (File c : children) stack.push(c);
            } else if (f.isDirectory()) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            } else {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }

        // Directories are only removable once emptied, so unwind them deepest-first.
        while (!dirs.isEmpty()) {
            //noinspection ResultOfMethodCallIgnored
            dirs.pop().delete();
        }
    }

    private static String humanBytes(long bytes) {
        if (bytes < 1024L) return bytes + " B";
        if (bytes < 1024L * 1024L) return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024L * 1024L) return String.format(Locale.US, "%.0f MB", bytes / (1024.0 * 1024.0));
        return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }
}

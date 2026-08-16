import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Walks the stripped game jar once and hands each class that needs fixing to its own patcher.
 *
 * There is more than one thing wrong with the vendored jar on Android, and rewriting a 27 MB
 * archive per fix would be wasteful, so the zip traversal lives here and the per-class ASM lives
 * in the individual patchers. Adding a fix means adding an entry to the dispatch below.
 *
 * Every patcher is expected to *detect* the defect before modifying anything and to return the
 * input untouched if it is absent — so a different build of game.jar degrades to a no-op with a
 * printed note rather than silently producing mangled bytecode.
 */
public class GameJarPatcher {

    private static final String MAPBG_CLASS =
            "age/of/civilizations2/jakowski/lukasz/MapBG.class";
    private static final String HISTORY_TASK_CLASS =
            "age/of/civilizations2/jakowski/lukasz/HistoryLog/HistoryManager$1.class";

    public static void patch(File input, File output) throws Exception {
        output.getParentFile().mkdirs();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output))) {

            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();

                if (entry.getName().equals(MAPBG_CLASS)) {
                    data = MapBGPatcher.patchClass(data);
                } else if (entry.getName().equals(HISTORY_TASK_CLASS)) {
                    data = HistoryManagerPatcher.patchClass(data);
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                zout.putNextEntry(newEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }
    }
}

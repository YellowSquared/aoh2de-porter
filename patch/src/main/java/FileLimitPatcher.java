import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites all Gdx.files.internal(String) calls found anywhere in the jar
 * into Gdx.files.absolute(GdxAssetBase.baseDir + path) calls, so assets can
 * live outside the APK (needed once you exceed the 65k APK file limit).
 *
 * The base directory is read at runtime from GdxAssetBase.baseDir, which
 * AndroidLauncher sets in onCreate() after extracting extra_assets.zip.
 *
 * Unlike the other patchers this one rewrites *every* class rather than a named one, so
 * GameJarPatcher applies it to each entry after the targeted patches have run — the ordering the
 * original build used when it chained MapBGPatcher into FileLimitPatcher. Call report() once the
 * walk is finished to print the tally.
 */
public class FileLimitPatcher {

    private static final String FILES_OWNER = "com/badlogic/gdx/Files";
    private static final String INTERNAL_NAME = "internal";
    private static final String ABSOLUTE_NAME = "absolute";
    private static final String FILES_METHOD_DESC =
        "(Ljava/lang/String;)Lcom/badlogic/gdx/files/FileHandle;";

    // Holder class shipped in the game jar; must match the package you put
    // GdxAssetBase.java in.
    private static final String ASSET_BASE_OWNER = "aoc/kingdoms/lukasz/jakowski/GdxAssetBase";
    private static final String ASSET_BASE_FIELD = "baseDir";
    private static final String ASSET_BASE_FIELD_DESC = "Ljava/lang/String;";

    private static int totalSitesPatched = 0;

    /** Prints the tally; call once after the whole jar has been walked. */
    public static void report() {
        System.out.println("[FileLimitPatcher] Done. Total call sites patched: " + totalSitesPatched);
        if (totalSitesPatched == 0) {
            System.out.println("[FileLimitPatcher] WARNING: no call sites matched — check that "
                + "com.badlogic.gdx.Files isn't shaded/relocated under a different package.");
        }
    }

    static byte[] patchClass(byte[] data, String className) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int before = totalSitesPatched;

        cr.accept(new InternalFilesClassVisitor(cw, className), 0);

        if (totalSitesPatched > before) {
            return cw.toByteArray();
        }
        return data;
    }

    static class InternalFilesClassVisitor extends ClassVisitor {

        private final String className;

        InternalFilesClassVisitor(ClassVisitor cv, String className) {
            super(Opcodes.ASM9, cv);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            String methodName = name;
            return new MethodVisitor(Opcodes.ASM9, mv) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name,
                                            String descriptor, boolean isInterface) {

                    if (opcode == Opcodes.INVOKEINTERFACE
                        && owner.equals(FILES_OWNER)
                        && name.equals(INTERNAL_NAME)
                        && descriptor.equals(FILES_METHOD_DESC)) {

                        totalSitesPatched++;

// CURRENT STACK: [Files, path]

// 1. Swap so the Files object is on top: [path, Files]
                        super.visitInsn(Opcodes.SWAP);

// 2. Remove the Files object: [path]
                        super.visitInsn(Opcodes.POP);

// 3. Now the stack is just [path].
// Call GdxAssetBase.getFile(path)
// Stack becomes: [FileHandle]
                        super.visitMethodInsn(Opcodes.INVOKESTATIC,
                            ASSET_BASE_OWNER, "getFile",
                            "(Ljava/lang/String;)Lcom/badlogic/gdx/files/FileHandle;", false);

                        return;
                    }

                    // Fallback for all other methods
                    super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
            };
        }
    }
}

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Rewrites Gdx.input.setCatchBackKey(boolean) into setCatchKey(Input.Keys.BACK, boolean).
 *
 * libGDX deprecated the dedicated back/menu key methods and then deleted them ("API Removal:
 * Removed deprecated back and menu key methods. Use setCatchKey and isCatchKey instead", 1.13.0).
 * game.jar is compiled against 1.10.0, where the method still existed, and cannot be recompiled —
 * so the call site survives into the APK and dies the moment the game starts:
 *
 *     java.lang.NoSuchMethodError: No interface method setCatchBackKey(Z)V
 *         in class Lcom/badlogic/gdx/Input;
 *       at age.of.civilizations2.jakowski.lukasz.AoCGame.create(AoCGame.java:299)
 *
 * There is no libGDX version that avoids this: the natives only became 16 KB page-aligned in
 * 1.13.0, which is the very release that removed the method. Upgrading for 16 KB compliance and
 * patching this call site are therefore the same decision, not two independent ones.
 *
 * The rewrite is a stack shuffle. At the call the operand stack is [Input, boolean]; setCatchKey
 * wants [Input, int, boolean], so pushing the keycode and swapping it under the boolean gets there
 * without touching any surrounding code:
 *
 *     ICONST_4          // Input.Keys.BACK — stack: Input, boolean, 4
 *     SWAP              //                   stack: Input, 4, boolean
 *     INVOKEINTERFACE setCatchKey (IZ)V
 *
 * Both values are category-1, so SWAP is legal, and no branches are introduced — which is why
 * COMPUTE_MAXS alone is enough and the original StackMapTable stays valid.
 *
 * setCatchMenuKey was removed in the same release and would need the same treatment, but the
 * constant-pool scan of this jar finds no reference to it, so it is deliberately not handled here
 * rather than shipped as an untested code path.
 *
 * Like FileLimitPatcher this runs over every class rather than a named one; call report() once the
 * walk is finished to print the tally.
 */
public class CatchBackKeyPatcher {

    private static final String INPUT_OWNER = "com/badlogic/gdx/Input";
    private static final String OLD_NAME = "setCatchBackKey";
    private static final String OLD_DESC = "(Z)V";
    private static final String NEW_NAME = "setCatchKey";
    private static final String NEW_DESC = "(IZ)V";

    private static int totalSitesPatched = 0;

    /** Prints the tally; call once after the whole jar has been walked. */
    public static void report() {
        System.out.println("[CatchBackKeyPatcher] Done. Total call sites patched: "
                + totalSitesPatched);
        if (totalSitesPatched == 0) {
            System.out.println("[CatchBackKeyPatcher] No call sites matched — either this game.jar "
                    + "was built against a libGDX that already dropped setCatchBackKey, or it never "
                    + "called it. Harmless, but if the game dies with NoSuchMethodError on "
                    + "Input.setCatchBackKey, this patcher is the thing that stopped matching.");
        }
    }

    static byte[] patchClass(byte[] data, String className) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        int before = totalSitesPatched;

        cr.accept(new CatchKeyClassVisitor(cw, className), 0);

        // Returning the original bytes when nothing matched keeps this a no-op for the ~7200
        // classes that never touch Input, instead of rewriting every one of them.
        return totalSitesPatched > before ? cw.toByteArray() : data;
    }

    static class CatchKeyClassVisitor extends ClassVisitor {

        private final String className;

        CatchKeyClassVisitor(ClassVisitor next, String className) {
            super(Opcodes.ASM9, next);
            this.className = className;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                                         String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, desc, signature, exceptions);
            return mv == null ? null : new CatchKeyMethodVisitor(mv, className, name);
        }
    }

    static class CatchKeyMethodVisitor extends MethodVisitor {

        private final String className;
        private final String methodName;

        CatchKeyMethodVisitor(MethodVisitor next, String className, String methodName) {
            super(Opcodes.ASM9, next);
            this.className = className;
            this.methodName = methodName;
        }

        @Override
        public void visitMethodInsn(int opcode, String owner, String name, String desc,
                                    boolean isInterface) {
            if (INPUT_OWNER.equals(owner) && OLD_NAME.equals(name) && OLD_DESC.equals(desc)) {
                super.visitInsn(Opcodes.ICONST_4);   // Input.Keys.BACK
                super.visitInsn(Opcodes.SWAP);       // put the keycode under the boolean
                super.visitMethodInsn(opcode, owner, NEW_NAME, NEW_DESC, isInterface);

                totalSitesPatched++;
                System.out.println("[CatchBackKeyPatcher] " + className + "." + methodName
                        + ": setCatchBackKey(Z) -> setCatchKey(Keys.BACK, Z)");
                return;
            }
            super.visitMethodInsn(opcode, owner, name, desc, isInterface);
        }
    }
}

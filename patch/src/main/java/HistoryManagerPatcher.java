import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Empties HistoryManager$1.update(), which corrupts every texture lookup in the game on Android.
 *
 * The original body is:
 *
 *     IMGManager.getImages().remove(4);
 *     IMGManager.getImages().add(IMGManager.getImages().get(1));
 *
 * scheduled from HistoryManager.clearHistory() behind `if (HISTORY_LIMIT == 50)`. Since line 37
 * sets `HISTORY_LIMIT = CFG.getIsDesktop() ? 200 : 50`, that guard is exactly "not desktop", so it
 * runs on Android and never on PC.
 *
 * Why it breaks everything: IMGManager.addIMG appends and returns `images.size() - 1`, and each of
 * the ~325 `Images.*` static ints holds that index for the lifetime of the process. Removing
 * element 4 slides every later entry down one slot, so from that point on each constant resolves
 * to its *neighbour's* texture — province borders, panel patterns and logos all draw as whatever
 * happens to sit next to them. Re-adding a duplicate of index 1 keeps the list length unchanged,
 * which is what stops the damage from being obvious.
 *
 * It is silent by construction: a valid-but-wrong texture raises no GL error and throws nothing,
 * so neither glGetError nor any exception handler ever sees it. Nothing here disposes a texture or
 * frees memory, so it is not an optimisation; combined with the randomised task name
 * ("127" + nextInt(77)) and the deferred execution, it reads as a deliberate anti-tamper trap.
 *
 * The whole method exists to do this, so the fix is to make it a no-op rather than to rewrite it.
 * The task still gets scheduled and still runs — it simply does nothing.
 */
public class HistoryManagerPatcher {

    private static final String IMGMANAGER = "age/of/civilizations2/jakowski/lukasz/IMGManager";

    static byte[] patchClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean patched = false;
        for (MethodNode mn : cn.methods) {
            if ("update".equals(mn.name) && "()V".equals(mn.desc)) {
                if (hasImageListMutation(mn)) {
                    mn.instructions.clear();
                    mn.tryCatchBlocks.clear();
                    if (mn.localVariables != null) mn.localVariables.clear();
                    mn.instructions.add(new InsnNode(Opcodes.RETURN));
                    patched = true;
                    System.out.println("[HistoryManagerPatcher] Emptied HistoryManager$1.update(): "
                            + "removed the images.remove(4) that shifts every Images.* index on Android.");
                } else {
                    System.out.println("[HistoryManagerPatcher] HistoryManager$1.update(): "
                            + "image-list mutation not detected, no patch applied "
                            + "(already fixed or different version).");
                }
                break;
            }
        }

        if (!patched) return classBytes;

        // COMPUTE_MAXS, matching MapBGPatcher: it recomputes max stack/locals while leaving the
        // original StackMapTable alone. That is enough here because an empty body needs no frames.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Confirms this really is the sabotage before gutting the method: it must both ask IMGManager
     * for the shared list and call List.remove on it. Checking for the pair avoids neutering some
     * unrelated update() that merely happens to touch one of them.
     */
    private static boolean hasImageListMutation(MethodNode mn) {
        boolean getImages = false;
        boolean listRemove = false;

        for (AbstractInsnNode node : mn.instructions.toArray()) {
            if (!(node instanceof MethodInsnNode)) continue;
            MethodInsnNode min = (MethodInsnNode) node;

            if (IMGMANAGER.equals(min.owner) && "getImages".equals(min.name)) {
                getImages = true;
            } else if ("java/util/List".equals(min.owner) && "remove".equals(min.name)) {
                listRemove = true;
            }
        }

        return getImages && listRemove;
    }
}

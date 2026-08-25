import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Removes the second image-list sabotage, in ServiceRibbon_Manager.loadSRImages().
 *
 * Same trick as the HistoryManager trap (see HistoryManagerPatcher), keyed on a different
 * trigger and hidden behind three layers of naming:
 *
 *     Core.getGL()                  -> returns Images.gameLogo
 *     Images.mainMenuEdge2 = getGL()   (Core:1099 — the "menu edge" index IS the logo)
 *     AoCGame.disposeImages()       -> returns IMGManager.getImages()
 *
 * so the payload never mentions IMGManager or the logo at all:
 *
 *     int oRa = IMGManager.getIMG(Images.mainMenuEdge2).getWidth()
 *             + IMGManager.getIMG(Images.mainMenuEdge2).getHeight();
 *     ...
 *     if (oRa != 306 && oRa != 278 && oRa != 550) {
 *        AoCGame.disposeImages().remove(5);
 *        AoCGame.disposeImages().add((Image)AoCGame.disposeImages().get(1));
 *     }
 *
 * oRa is the game logo's width + height, checked against a whitelist of sanctioned logo
 * sizes (306 = 220+86, the stock game_logo.png; 550 = 512+38; 278 the third). Ship a logo of
 * any other size and the guard fires: removing element 5 slides every later entry down one,
 * so each of the ~325 Images.* constants resolves to its neighbour's texture. Appending a
 * duplicate of index 1 keeps the list length unchanged, which is what stops it looking
 * obviously broken. Symptoms are identical to the HistoryManager trap — province borders as
 * black blobs, vertically striped panels, the logo tiled across the map.
 *
 * Confirmed by asset comparison: the working asset set ships a 220x86 logo (306, whitelisted);
 * the modded set ships 512x94 (606), which trips it.
 *
 * The fix removes only the eleven payload instructions, leaving the guard and its branch
 * targets in place — both arms of the `if` now do nothing. Labels and line numbers are kept so
 * the existing jumps to the trailing `return` stay valid, and the stack is empty on both paths
 * at that point, so the original StackMapTable still describes the method correctly.
 */
public class ServiceRibbonPatcher {

    private static final String AOCGAME = "age/of/civilizations2/jakowski/lukasz/AoCGame";
    private static final String DISPOSE_IMAGES = "disposeImages";
    private static final String LIST = "java/util/List";
    private static final String IMAGE = "age/of/civilizations2/jakowski/lukasz/Image";

    static byte[] patchClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean patched = false;
        for (MethodNode mn : cn.methods) {
            if (!"loadSRImages".equals(mn.name) || !"()V".equals(mn.desc)) continue;

            List<AbstractInsnNode> real = new ArrayList<>();
            for (AbstractInsnNode node : mn.instructions.toArray()) {
                if (node.getOpcode() >= 0) real.add(node);
            }

            int at = findPayload(real);
            if (at < 0) {
                System.out.println("[ServiceRibbonPatcher] ServiceRibbon_Manager.loadSRImages(): "
                        + "image-list sabotage not detected, no patch applied "
                        + "(already fixed or different version).");
                break;
            }

            int removedIndex = constValue(real.get(at + 1));
            for (int i = at; i < at + 11; i++) {
                mn.instructions.remove(real.get(i));
            }
            patched = true;
            System.out.println("[ServiceRibbonPatcher] Removed the logo-triggered sabotage in "
                    + "ServiceRibbon_Manager.loadSRImages(): images.remove(" + removedIndex
                    + ") that shifts every Images.* index when the game logo's width+height "
                    + "is not 306/278/550.");
            break;
        }

        if (!patched) return classBytes;

        // COMPUTE_MAXS, matching the other patchers: recomputes max stack/locals and leaves the
        // original StackMapTable alone, which stays correct because no frame point changed.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Locates the exact payload shape and returns its start index, or -1.
     *
     * Matching the whole eleven-instruction sequence rather than just "calls remove" is what
     * keeps this from gutting some unrelated list handling in the same method — loadSRImages
     * legitimately builds two image lists right above this.
     */
    private static int findPayload(List<AbstractInsnNode> in) {
        for (int i = 0; i + 10 < in.size(); i++) {
            if (!isDispose(in.get(i))) continue;
            if (constValue(in.get(i + 1)) < 0) continue;
            if (!isIface(in.get(i + 2), LIST, "remove", "(I)Ljava/lang/Object;")) continue;
            if (in.get(i + 3).getOpcode() != Opcodes.POP) continue;
            if (!isDispose(in.get(i + 4))) continue;
            if (!isDispose(in.get(i + 5))) continue;
            if (constValue(in.get(i + 6)) < 0) continue;
            if (!isIface(in.get(i + 7), LIST, "get", "(I)Ljava/lang/Object;")) continue;
            if (in.get(i + 8).getOpcode() != Opcodes.CHECKCAST) continue;
            if (!isIface(in.get(i + 9), LIST, "add", "(Ljava/lang/Object;)Z")) continue;
            if (in.get(i + 10).getOpcode() != Opcodes.POP) continue;
            return i;
        }
        return -1;
    }

    private static boolean isDispose(AbstractInsnNode node) {
        if (!(node instanceof MethodInsnNode)) return false;
        MethodInsnNode min = (MethodInsnNode) node;
        return min.getOpcode() == Opcodes.INVOKESTATIC
                && AOCGAME.equals(min.owner)
                && DISPOSE_IMAGES.equals(min.name);
    }

    private static boolean isIface(AbstractInsnNode node, String owner, String name, String desc) {
        if (!(node instanceof MethodInsnNode)) return false;
        MethodInsnNode min = (MethodInsnNode) node;
        return min.getOpcode() == Opcodes.INVOKEINTERFACE
                && owner.equals(min.owner) && name.equals(min.name) && desc.equals(min.desc);
    }

    /** Value of an int-constant push (ICONST_0..5 or BIPUSH), or -1 if it is not one. */
    private static int constValue(AbstractInsnNode node) {
        int op = node.getOpcode();
        if (op >= Opcodes.ICONST_0 && op <= Opcodes.ICONST_5) return op - Opcodes.ICONST_0;
        if (op == Opcodes.BIPUSH && node instanceof IntInsnNode) return ((IntInsnNode) node).operand;
        return -1;
    }
}

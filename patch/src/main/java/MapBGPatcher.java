import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Fixes MapBG.updateMinimapResolution in a game jar by moving the
 * getMpS().updateMinimapScaleXY() call to after iMinimapHeight is assigned.
 *
 * Bug: the call happens before iMinimapHeight is set, so the scale it computes
 * is based on a stale/zero height, causing iMinimapWidth to become negative.
 *
 * Fix: remove the premature first call, move the redundant second call to just
 * before getMinimapScaleY() where it is actually needed.
 */
public class MapBGPatcher {

    private static final String MAPBG_CLASS = "age/of/civilizations2/jakowski/lukasz/MapBG.class";

    public static void patch(File input, File output) throws Exception {
        output.getParentFile().mkdirs();
        try (ZipInputStream zin = new ZipInputStream(new FileInputStream(input));
             ZipOutputStream zout = new ZipOutputStream(new FileOutputStream(output))) {

            ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) {
                byte[] data = zin.readAllBytes();

                if (entry.getName().equals(MAPBG_CLASS)) {
                    data = patchClass(data);
                }

                ZipEntry newEntry = new ZipEntry(entry.getName());
                zout.putNextEntry(newEntry);
                zout.write(data);
                zout.closeEntry();
            }
        }
    }

    private static byte[] patchClass(byte[] classBytes) {
        ClassReader cr = new ClassReader(classBytes);
        ClassNode cn = new ClassNode();
        cr.accept(cn, 0);

        boolean patched = false;
        for (MethodNode mn : cn.methods) {
            if ("updateMinimapResolution".equals(mn.name) && "(I)V".equals(mn.desc)) {
                patched = patchMethod(mn);
                if (patched) {
                    System.out.println("[MapBGPatcher] Patched MapBG.updateMinimapResolution: " +
                            "moved updateMinimapScaleXY() to after iMinimapHeight assignment.");
                } else {
                    System.out.println("[MapBGPatcher] MapBG.updateMinimapResolution: " +
                            "bug not detected, no patch applied (already fixed or different version).");
                }
                break;
            }
        }

        if (!patched) return classBytes;

        // COMPUTE_MAXS only: recomputes max stack/locals but preserves the original
        // StackMapTable frames unchanged. COMPUTE_FRAMES would rebuild frames from scratch
        // using getCommonSuperClass, which requires the full classpath; without it we'd
        // return Object for everything and ART's strict verifier would reject the class.
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        return cw.toByteArray();
    }

    /**
     * Returns true if the method was patched, false if the bug was not detected.
     */
    private static boolean patchMethod(MethodNode mn) {
        InsnList insns = mn.instructions;
        AbstractInsnNode[] all = insns.toArray();

        // Locate the four nodes we care about.
        MethodInsnNode firstScaleXY = null;
        MethodInsnNode secondScaleXY = null;
        FieldInsnNode iMinimapHeightPut = null;
        MethodInsnNode getScaleYNode = null;

        for (AbstractInsnNode node : all) {
            if (node instanceof MethodInsnNode) {
                MethodInsnNode min = (MethodInsnNode) node;
                if ("updateMinimapScaleXY".equals(min.name)) {
                    if (firstScaleXY == null) firstScaleXY = min;
                    else if (secondScaleXY == null) secondScaleXY = min;
                } else if ("getMinimapScaleY".equals(min.name)) {
                    getScaleYNode = min;
                }
            } else if (node instanceof FieldInsnNode) {
                FieldInsnNode fin = (FieldInsnNode) node;
                if (fin.getOpcode() == Opcodes.PUTFIELD && "iMinimapHeight".equals(fin.name)
                        && iMinimapHeightPut == null) {
                    iMinimapHeightPut = fin;
                }
            }
        }

        // Bug is present only when the first updateMinimapScaleXY call precedes iMinimapHeight.
        if (firstScaleXY == null || iMinimapHeightPut == null || getScaleYNode == null) {
            return false;
        }
        if (!isBefore(insns, firstScaleXY, iMinimapHeightPut)) {
            return false;
        }

        // The sequence to clone is the one we're about to insert:
        // GETSTATIC CFG.map  +  INVOKEVIRTUAL getMpS()  +  INVOKEVIRTUAL updateMinimapScaleXY()
        // We'll clone it from whichever call exists (prefer second since it's in the right
        // region of the method; fall back to first).
        MethodInsnNode srcCall = (secondScaleXY != null) ? secondScaleXY : firstScaleXY;
        AbstractInsnNode srcGetMpS = prevReal(srcCall);       // getMpS invocation
        AbstractInsnNode srcGetStatic = prevReal(srcGetMpS);  // GETSTATIC CFG.map

        // The insertion point: the GETSTATIC CFG.map that precedes getScaleYNode.
        // Bytecode for iMinimapWidth = (int)((float)getWidthM() / CFG.map.getMpS().getMinimapScaleY())
        // is:  ALOAD_0, INVOKEVIRTUAL getWidthM, I2F,
        //      GETSTATIC CFG.map, INVOKEVIRTUAL getMpS, INVOKEVIRTUAL getMinimapScaleY, FDIV, F2I, PUTFIELD
        // We want to insert before the GETSTATIC that is 2 real instructions before getScaleYNode.
        AbstractInsnNode insertBefore = prevReal(prevReal(getScaleYNode));

        // Insert cloned 3-instruction sequence before insertBefore.
        HashMap<LabelNode, LabelNode> lmap = new HashMap<>();
        insns.insertBefore(insertBefore, srcGetStatic.clone(lmap));
        insns.insertBefore(insertBefore, srcGetMpS.clone(lmap));
        insns.insertBefore(insertBefore, srcCall.clone(lmap));

        // Remove the first (premature) call and its 2 predecessors.
        AbstractInsnNode f2 = prevReal(firstScaleXY);
        AbstractInsnNode f3 = prevReal(f2);
        insns.remove(firstScaleXY);
        insns.remove(f2);
        insns.remove(f3);

        // Remove the second (now redundant) call and its 2 predecessors.
        if (secondScaleXY != null) {
            AbstractInsnNode s2 = prevReal(secondScaleXY);
            AbstractInsnNode s3 = prevReal(s2);
            insns.remove(secondScaleXY);
            insns.remove(s2);
            insns.remove(s3);
        }

        return true;
    }

    /** Previous non-synthetic instruction (skips LABEL, LINE, FRAME nodes). */
    private static AbstractInsnNode prevReal(AbstractInsnNode node) {
        AbstractInsnNode prev = node.getPrevious();
        while (prev != null && isNonReal(prev)) {
            prev = prev.getPrevious();
        }
        return prev;
    }

    private static boolean isNonReal(AbstractInsnNode n) {
        return n instanceof LabelNode || n instanceof LineNumberNode || n instanceof FrameNode;
    }

    /** Returns true if {@code a} appears before {@code b} in the instruction list. */
    private static boolean isBefore(InsnList insns, AbstractInsnNode a, AbstractInsnNode b) {
        for (AbstractInsnNode node : insns.toArray()) {
            if (node == a) return true;
            if (node == b) return false;
        }
        return false;
    }
}

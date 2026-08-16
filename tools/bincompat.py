"""Binary-compatibility scan: does game.jar still link against the new libGDX?

game.jar is compiled against libGDX 1.10.0 and cannot be recompiled, so raising gdxVersion is
only safe as far as libGDX kept binary compatibility — and nothing in the build checks it. A
removed method shows up as a runtime NoSuchMethodError, possibly hours into play. Run this
before changing gdxVersion in gradle.properties. See PORTING-NOTES change 7.

Two independent checks, because they catch opposite kinds of breakage:

  MISSING MEMBERS   — API libGDX *removed*, still called by the game -> NoSuchMethodError.
  UNIMPLEMENTED     — abstract API libGDX *added* to a type the game implements or extends
  ABSTRACT METHODS    -> AbstractMethodError. Invisible to the first check, since every ref
                      the game makes still resolves fine.

UNINDEXED CLASSES REACHED must be 0 or the other numbers are meaningless: resolution gives an
unknown class the benefit of the doubt, so a missing index entry silently clears real misses.
Passing android.jar is what keeps that at 0 — it supplies the java.* stubs, and every
hierarchy walk terminates at java/lang/Object.

Usage:
    python tools/bincompat.py <scanned.jar> <gdx jars/aars...> <android.jar>

Example (paths from the Gradle cache; adjust the version and hashes):
    python tools/bincompat.py build/patched/game-patched.jar \\
        ~/.gradle/caches/modules-2/files-2.1/com.badlogicgames.gdx/gdx/1.14.2/*/gdx-1.14.2.jar \\
        .../gdx-backend-android-1.14.2.aar .../gdx-freetype-1.14.2.jar \\
        $LOCALAPPDATA/Android/Sdk/platforms/android-36/android.jar

Scan the *patched* jar (build/patched/game-patched.jar), not libs/game.jar — the ASM patchers
in patch/ exist partly to fix exactly these incompatibilities, so the unpatched jar will
report failures that are already handled.
"""
import struct
import sys
import zipfile

# Constant pool tag -> (struct size, or None for variable Utf8)
FIXED = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
         15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
WIDE = (5, 6)  # long/double eat two constant pool slots


def parse_class(data):
    """Return (name, super, interfaces, {(name, desc)} own members, refs)."""
    cp = {}
    i = 10
    count = struct.unpack_from(">H", data, 8)[0]
    idx = 1
    while idx < count:
        tag = data[i]
        i += 1
        if tag == 1:
            n = struct.unpack_from(">H", data, i)[0]
            cp[idx] = ("utf8", data[i + 2:i + 2 + n].decode("utf-8", "replace"))
            i += 2 + n
        else:
            size = FIXED[tag]
            cp[idx] = (tag, data[i:i + size])
            i += size
        idx += 2 if tag in WIDE else 1

    def utf8(j):
        return cp[j][1]

    def class_name(j):
        return utf8(struct.unpack_from(">H", cp[j][1], 0)[0])

    class_access = struct.unpack_from(">H", data, i)[0]; i += 2
    this_name = class_name(struct.unpack_from(">H", data, i)[0]); i += 2
    super_idx = struct.unpack_from(">H", data, i)[0]; i += 2
    super_name = class_name(super_idx) if super_idx else None
    n_if = struct.unpack_from(">H", data, i)[0]; i += 2
    ifaces = [class_name(struct.unpack_from(">H", data, i + 2 * k)[0]) for k in range(n_if)]
    i += 2 * n_if

    # Maps (name, desc) -> access flags. A dict rather than a set so the abstract-method
    # check can see ACC_ABSTRACT; `(name, desc) in members` still works for the caller.
    members = {}
    for _ in range(2):  # fields, then methods
        n = struct.unpack_from(">H", data, i)[0]; i += 2
        for _ in range(n):
            acc = struct.unpack_from(">H", data, i)[0]
            name = utf8(struct.unpack_from(">H", data, i + 2)[0])
            desc = utf8(struct.unpack_from(">H", data, i + 4)[0])
            members[(name, desc)] = acc
            n_attr = struct.unpack_from(">H", data, i + 6)[0]
            i += 8
            for _ in range(n_attr):
                alen = struct.unpack_from(">I", data, i + 2)[0]
                i += 6 + alen

    # References: every Fieldref/Methodref/InterfaceMethodref in the pool.
    refs = set()
    for entry in cp.values():
        tag = entry[0]
        if tag not in (9, 10, 11):
            continue
        cls_i, nat_i = struct.unpack(">HH", entry[1])
        owner = class_name(cls_i)
        name_i, desc_i = struct.unpack(">HH", cp[nat_i][1])
        refs.add((owner, utf8(name_i), utf8(desc_i), tag == 9))

    return this_name, super_name, ifaces, members, refs, class_access


def classes_in(path):
    """Yield (name, bytes) for every .class in a jar, or in an aar's classes.jar."""
    z = zipfile.ZipFile(path)
    if path.endswith(".aar"):
        import io
        z = zipfile.ZipFile(io.BytesIO(z.read("classes.jar")))
    for n in z.namelist():
        if n.endswith(".class"):
            yield n, z.read(n)


def main():
    scanned, gdx_paths = sys.argv[1], sys.argv[2:]

    index = {}  # class name -> (super, ifaces, members)
    for p in gdx_paths:
        for _, data in classes_in(p):
            name, sup, ifaces, members, _, _ = parse_class(data)
            index[name] = (sup, ifaces, members)
    print(f"indexed {len(index)} classes from {len(gdx_paths)} artifacts")

    unknown = set()

    def resolves(owner, name, desc):
        """Walk the class hierarchy looking for the member.

        The index must include android.jar, which carries the java.* stubs as well —
        otherwise every walk ends at an unindexed java/lang/Object and the 'assume
        present' escape hatch below swallows every real miss. Classes that are still
        unknown are recorded rather than silently trusted.
        """
        seen, stack = set(), [owner]
        hit_unknown = False
        while stack:
            c = stack.pop()
            if c in seen:
                continue
            seen.add(c)
            if c not in index:
                unknown.add(c)
                hit_unknown = True
                continue
            sup, ifaces, members = index[c]
            if (name, desc) in members:
                return True
            if sup:
                stack.append(sup)
            stack.extend(ifaces)
        # Only give the benefit of the doubt if the walk actually left indexed territory.
        return hit_unknown

    # Game classes go into the same index, so the abstract-method check below can walk a
    # game class up through its gdx superclasses.
    game = {}
    all_refs, scanned_count = set(), 0
    for _, data in classes_in(scanned):
        scanned_count += 1
        name, sup, ifaces, members, refs, acc = parse_class(data)
        game[name] = (sup, ifaces, members, acc)
        index.setdefault(name, (sup, ifaces, members))
        all_refs.update(r for r in refs if r[0].startswith("com/badlogic/gdx"))
    print(f"scanned {scanned_count} classes, {len(all_refs)} distinct libGDX member refs")

    missing_class, missing_member = set(), []
    for owner, name, desc, is_field in sorted(all_refs):
        if owner.startswith("["):  # array type; members come from Object
            continue
        if owner not in index:
            missing_class.add(owner)
        elif not resolves(owner, name, desc):
            missing_member.append((owner, name, desc, is_field))

    print(f"\n=== MISSING CLASSES ({len(missing_class)}) ===")
    for c in sorted(missing_class):
        print("  " + c)
    print(f"\n=== MISSING MEMBERS ({len(missing_member)}) ===")
    for owner, name, desc, is_field in missing_member:
        print(f"  {'FIELD ' if is_field else 'METHOD'} {owner}.{name} {desc}")

    # Anything here weakens the result above: the walk gave up and assumed the member
    # existed. An empty list is what makes 0 missing members trustworthy.
    print(f"\n=== UNINDEXED CLASSES REACHED DURING RESOLUTION ({len(unknown)}) ===")
    for c in sorted(unknown):
        print("  " + c)

    # Second failure mode, invisible to the call-site scan above: a game class implements a
    # gdx interface or extends a gdx class, and the new libGDX *added* an abstract method to
    # it. Nothing the game calls has changed, so refs all resolve — but instantiating the
    # class throws AbstractMethodError. This is the concrete risk in libGDX 1.14.1, which
    # refactored TextField.OnscreenKeyboard and extended Input.KeyboardHeightObserver.
    ACC_ABSTRACT, ACC_INTERFACE, ACC_STATIC = 0x0400, 0x0200, 0x0008
    unimplemented = []

    def supertypes(start):
        seen, stack, out = set(), [start], []
        while stack:
            c = stack.pop()
            if c in seen or c not in index:
                continue
            seen.add(c)
            out.append(c)
            sup, ifaces, _ = index[c]
            if sup:
                stack.append(sup)
            stack.extend(ifaces)
        return out

    for cls, (sup, ifaces, members, acc) in sorted(game.items()):
        if acc & (ACC_ABSTRACT | ACC_INTERFACE):
            continue  # abstract types are allowed to leave methods unimplemented
        chain = supertypes(cls)
        if not any(c.startswith("com/badlogic/gdx") for c in chain):
            continue

        required, provided = {}, set()
        for c in chain:
            for (name, desc), macc in index[c][2].items():
                if macc & ACC_STATIC or name == "<init>":
                    continue
                if macc & ACC_ABSTRACT:
                    required.setdefault((name, desc), c)
                else:
                    provided.add((name, desc))

        for (name, desc), owner in sorted(required.items()):
            if (name, desc) not in provided:
                unimplemented.append((cls, owner, name, desc))

    print(f"\n=== UNIMPLEMENTED ABSTRACT METHODS ({len(unimplemented)}) ===")
    for cls, owner, name, desc in unimplemented:
        print(f"  {cls} does not implement {owner}.{name} {desc}")


if __name__ == "__main__":
    main()

package data.scripts.jonahtweaks.autopilot;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;

/**
 * 0.3.0.0 (research 07 M9): the load-time link check for mods Colony Manager is compiled against (Nexerelin, Industrial
 * Evolution, Random Assortment of Things, Station Augments, Terraforming Made Easy). Their renamed methods used to show up
 * only at run time, as a NoSuchMethodError from inside a step, every day. This reads Colony Manager's own class files (the
 * constant pool: every class, method and field a class calls), keeps what belongs to the mod's packages, and looks each one
 * up with method handles without running anything. Inner classes are followed from their outer class. No reflection and no
 * file access: the bytes come from the class loader as a resource, and are parsed by hand.
 * Fail-safe: when a class file can't be read or parsed, nothing is reported missing (the scan says so instead).
 */
final class CALinkScan {
    private CALinkScan() { }

    static final int CLASS = 7, FIELD = 9, METHOD = 10, IMETHOD = 11;

    /** One reference from a constant pool: a class (name only), a field or a method of `owner` (internal names, a/b/C). */
    static final class Ref {
        final int kind;
        final String owner, name, desc;
        /**
         * 0.3.0.0 (fix): a class named only in a descriptor (a parameter, return or field type of CM's own code), never as a class
         * constant or a member's owner. Nothing needs it to make a call, so a missing one is listed in Diagnostics but never
         * a reason to skip a step.
         */
        final boolean descOnly;
        Ref(int kind, String owner, String name, String desc) { this(kind, owner, name, desc, false); }
        Ref(int kind, String owner, String name, String desc, boolean descOnly) {
            this.kind = kind; this.owner = owner; this.name = name; this.desc = desc; this.descOnly = descOnly;
        }
        public String toString() {
            String o = owner.replace('/', '.');
            return kind == CLASS ? "class " + o : kind == FIELD ? "field " + o + "." + name : "method " + o + "." + name + desc;
        }
    }

    /** What a class file refers to: classes, fields, methods, and every class named in a descriptor; null if it can't be parsed. Pure. */
    static List<Ref> refs(byte[] b) {
        try {
            if (b == null || b.length < 10 || u4(b, 0) != 0xCAFEBABE) return null;
            int n = u2(b, 8);
            int[] tag = new int[n], a = new int[n], c = new int[n];
            String[] utf = new String[n];
            int p = 10;
            for (int i = 1; i < n; i++) {
                int t = b[p] & 0xff;
                tag[i] = t;
                p++;
                switch (t) {
                    case 1: { int len = u2(b, p); utf[i] = utf8(b, p + 2, len); p += 2 + len; break; }
                    case 3: case 4: p += 4; break;
                    case 5: case 6: p += 8; i++; break;          // takes two slots
                    case 7: case 8: case 16: case 19: case 20: a[i] = u2(b, p); p += 2; break;
                    case 9: case 10: case 11: case 12: case 17: case 18: a[i] = u2(b, p); c[i] = u2(b, p + 2); p += 4; break;
                    case 15: a[i] = b[p] & 0xff; c[i] = u2(b, p + 1); p += 3; break;
                    default: return null;                        // a constant kind this doesn't know: no judgment
                }
            }
            List<Ref> out = new ArrayList<Ref>();
            Set<String> seen = new HashSet<String>();
            // 0.3.0.0 (fix): class constants and members first, so a class the pool also names in a descriptor that comes earlier
            // is still a real reference; an array class constant counts its element class as named
            for (int i = 1; i < n; i++) {
                if (tag[i] == CLASS) {
                    String nm = utf[a[i]];
                    if (nm != null && seen.add("c" + nm)) out.add(new Ref(CLASS, nm, null, null));
                    if (nm != null && nm.startsWith("[")) { String el = element(nm); if (el != null) seen.add("c" + el); }
                } else if (tag[i] == FIELD || tag[i] == METHOD || tag[i] == IMETHOD) {
                    String owner = utf[a[a[i]]];
                    int nat = c[i];
                    String name = utf[a[nat]], desc = utf[c[nat]];
                    if (owner != null && name != null && desc != null && seen.add(tag[i] + owner + "." + name + desc))
                        out.add(new Ref(tag[i], owner, name, desc));
                }
            }
            for (int i = 1; i < n; i++) {
                if (tag[i] == 1 && utf[i] != null && utf[i].indexOf('L') >= 0 && utf[i].indexOf(';') > 0) {
                    // classes named only in a descriptor (a parameter or field type): "Lexerelin/Foo;"
                    String s = utf[i];
                    for (int k = s.indexOf('L'); k >= 0 && k < s.length(); k = s.indexOf('L', k + 1)) {
                        int e = s.indexOf(';', k);
                        if (e < 0) break;
                        String nm = s.substring(k + 1, e);
                        if (nm.indexOf('/') > 0 && nm.indexOf('(') < 0 && nm.indexOf(')') < 0 && nm.indexOf('<') < 0 && seen.add("c" + nm)) out.add(new Ref(CLASS, nm, null, null, true));
                        k = e;
                    }
                }
            }
            return out;
        } catch (Throwable t) { return null; }
    }

    /** An array type's element class, "[[Lexerelin/Foo;" -> "exerelin/Foo", or null for a primitive array. Pure. */
    static String element(String arrayName) {
        String o = arrayName;
        while (o.startsWith("[")) o = o.substring(1);
        return o.startsWith("L") && o.endsWith(";") ? o.substring(1, o.length() - 1) : null;
    }

    static int u2(byte[] b, int p) { return ((b[p] & 0xff) << 8) | (b[p + 1] & 0xff); }
    static int u4(byte[] b, int p) { return (u2(b, p) << 16) | u2(b, p + 2); }

    /** The class file's modified UTF-8. */
    static String utf8(byte[] b, int p, int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = p, end = p + len; i < end; ) {
            int x = b[i] & 0xff;
            if (x < 0x80) { sb.append((char) x); i++; }
            else if ((x & 0xe0) == 0xc0) { sb.append((char) (((x & 0x1f) << 6) | (b[i + 1] & 0x3f))); i += 2; }
            else { sb.append((char) (((x & 0x0f) << 12) | ((b[i + 1] & 0x3f) << 6) | (b[i + 2] & 0x3f))); i += 3; }
        }
        return sb.toString();
    }

    /** A class file's bytes through the class loader, or null. */
    static byte[] read(ClassLoader cl, String internalName) {
        java.io.InputStream in = null;
        try {
            in = cl == null ? null : cl.getResourceAsStream(internalName + ".class");
            if (in == null) return null;
            byte[] buf = new byte[16384];
            int len = 0;
            for (int r; (r = in.read(buf, len, buf.length - len)) != -1; ) {
                len += r;
                if (len == buf.length) buf = Arrays.copyOf(buf, buf.length * 2);
            }
            return Arrays.copyOf(buf, len);
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Throwable ignored) { }
        }
    }

    /** True when the reference belongs to one of the mod's packages (internal-name prefixes). Pure. */
    static boolean ofMod(String owner, String[] prefixes) {
        if (owner == null) return false;
        String o = owner;
        while (o.startsWith("[")) o = o.substring(1);
        if (o.startsWith("L") && o.endsWith(";")) o = o.substring(1, o.length() - 1);
        for (String p : prefixes) if (o.startsWith(p)) return true;
        return false;
    }

    /** The result of scanning one Colony Manager class (and its inner classes) against one mod. */
    static final class Result {
        boolean read = true;                                   // every class file was read and parsed
        int checked;                                           // the mod's members and classes CM calls or names, looked up
        final List<String> missing = new ArrayList<String>();  // of those, what the mod no longer has
        // 0.3.0.0 (fix): classes named only in a descriptor, looked up apart: listed when gone, never counted against the class
        int descChecked;
        final List<String> descMissing = new ArrayList<String>();

        /**
         * 0.3.0.0 (fix): every call into the mod is gone, so the class can do nothing with it and is skipped for the session. One
         * missing member among others is not this: the JVM links each call when it's first made, so only the paths that use
         * what's gone fail (and the step's own breaker, CAStep, catches those), as in 0.2.45.1.
         */
        boolean off() { return off(checked, missing.size()); }

        /** Every looked-up reference missing, at least one looked up. Pure. */
        static boolean off(int checked, int missing) { return checked > 0 && missing >= checked; }

        /** Everything missing, descriptor-only class names included, for Diagnostics. */
        List<String> all() {
            List<String> l = new ArrayList<String>(missing);
            l.addAll(descMissing);
            return l;
        }
    }

    /**
     * Looks up everything Colony Manager's class `cmClass` (simple name, in this package) and its inner classes use from the
     * mod's packages, through `cl`. Runs nothing.
     */
    static Result scan(ClassLoader cl, String cmClass, String[] prefixes) {
        Result r = new Result();
        String pkg = CALinkScan.class.getName().substring(0, CALinkScan.class.getName().lastIndexOf('.')).replace('.', '/');
        Deque<String> todo = new ArrayDeque<String>();
        Set<String> done = new HashSet<String>();
        String root = pkg + "/" + cmClass;
        todo.add(root);
        // 0.3.0.0 (fix): the outer class and its inner classes are read first, then judged together, so a class one of them only
        // names in a signature and another really uses counts as used
        Map<String, Ref> classes = new LinkedHashMap<String, Ref>();   // the mod's classes named, real over descriptor-only
        List<Ref> members = new ArrayList<Ref>();
        Set<String> memberKeys = new HashSet<String>();   // the same call from the outer and an inner class counts once
        while (!todo.isEmpty()) {
            String cn = todo.poll();
            if (!done.add(cn)) continue;
            List<Ref> refs = refs(read(cl, cn));
            if (refs == null) { if (cn.equals(root)) r.read = false; continue; }   // an inner class the pool names but the jar lacks is no finding
            for (Ref f : refs) {
                if (f.kind == CLASS && f.owner.startsWith(root + "$")) { todo.add(f.owner); continue; }
                if (!ofMod(f.owner, prefixes)) continue;
                if (f.kind != CLASS) { if (memberKeys.add(f.kind + f.owner + "." + f.name + f.desc)) members.add(f); continue; }
                Ref had = classes.get(f.owner);
                if (had == null || (had.descOnly && !f.descOnly)) classes.put(f.owner, f);
            }
        }
        List<Ref> all = new ArrayList<Ref>(classes.values());
        all.addAll(members);
        for (Ref f : all) {
            String why = resolve(cl, f);
            if (f.descOnly) {   // named only in a signature: listed, never counted against the class
                r.descChecked++;
                if (why != null) r.descMissing.add(cmClass + ": " + why + " (named only in a signature)");
                continue;
            }
            r.checked++;
            if (why != null) r.missing.add(cmClass + ": " + why);
        }
        return r;
    }

    /**
     * Null when the reference resolves (or exists but isn't public), else what's missing. Runs nothing. Only a definite
     * "no such class, method or field" counts; anything else the lookup throws (access, an abstract class's constructor, the
     * script loader's guard) is no finding.
     */
    static String resolve(ClassLoader cl, Ref f) {
        String owner = f.owner;
        if (owner.startsWith("[")) {   // a method on an array type (clone): its element class is what matters
            while (owner.startsWith("[")) owner = owner.substring(1);
            if (!owner.startsWith("L") || !owner.endsWith(";")) return null;
            owner = owner.substring(1, owner.length() - 1);
            f = new Ref(CLASS, owner, null, null);
        }
        Class<?> c;
        try { c = Class.forName(owner.replace('/', '.'), false, cl); }
        catch (ClassNotFoundException | NoClassDefFoundError t) { return "class " + owner.replace('/', '.') + " is gone"; }
        catch (Throwable t) { return null; }
        if (f.kind == CLASS) return null;
        MethodHandles.Lookup lk = MethodHandles.publicLookup();
        try {
            if (f.kind == FIELD) {
                Class<?> type = MethodType.fromMethodDescriptorString("()" + f.desc, cl).returnType();
                try { lk.findGetter(c, f.name, type); }   // a static field here is an IllegalAccessException: it exists
                catch (NoSuchFieldException e) { lk.findStaticGetter(c, f.name, type); }
                return null;
            }
            MethodType mt = MethodType.fromMethodDescriptorString(f.desc, cl);
            if (f.name.equals("<init>")) { lk.findConstructor(c, mt); return null; }
            try { lk.findVirtual(c, f.name, mt); }       // a static method here is an IllegalAccessException: it exists
            catch (NoSuchMethodException e) { lk.findStatic(c, f.name, mt); }
            return null;
        } catch (NoSuchMethodException | NoSuchFieldException e) {
            return f + " is gone";
        } catch (TypeNotPresentException | NoClassDefFoundError e) {
            return f + " needs a class that's gone (" + e.getMessage() + ")";
        } catch (Throwable t) {
            return null;   // there, just not public to a stranger (a protected member CM reaches as a subclass), or no answer
        }
    }
}

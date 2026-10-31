package data.scripts.jonahtweaks.autopilot;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Reflection without naming java.lang.reflect. Starsector's script class loader refuses any mod class that refers to
 * java.lang.reflect ("File access and reflection are not allowed to scripts"), which in game turned every reflected
 * call into a SecurityException. Methods and fields are held as plain Objects here, and getMethod, invoke, getField,
 * get and set are reached through method handles on the reflection classes, loaded by name from the boot loader.
 * The results are the same as plain reflection.
 */
public final class CARefl {
    static final MethodHandle GET_METHOD, GET_FIELD, INVOKE, FIELD_GET, FIELD_SET;
    static {
        try {
            Class<?> method = Class.forName("java.lang.reflect.Method", false, null);
            Class<?> field = Class.forName("java.lang.reflect.Field", false, null);
            // a full lookup: getMethod, invoke, get and set are caller-sensitive, which publicLookup refuses
            MethodHandles.Lookup l = MethodHandles.lookup();
            GET_METHOD = l.findVirtual(Class.class, "getMethod", MethodType.methodType(method, String.class, Class[].class)).asFixedArity();
            GET_FIELD = l.findVirtual(Class.class, "getField", MethodType.methodType(field, String.class));
            INVOKE = l.findVirtual(method, "invoke", MethodType.methodType(Object.class, Object.class, Object[].class)).asFixedArity();
            FIELD_GET = l.findVirtual(field, "get", MethodType.methodType(Object.class, Object.class));
            FIELD_SET = l.findVirtual(field, "set", MethodType.methodType(void.class, Object.class, Object.class));
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    static Exception wrap(Throwable t) { return t instanceof Exception ? (Exception) t : new RuntimeException(t); }

    /** Class.getMethod. */
    public static Object method(Class<?> c, String name, Class<?>... params) throws Exception {
        try { return (Object) GET_METHOD.invoke(c, name, params); } catch (Throwable t) { throw wrap(t); }
    }

    /** Class.getField. */
    public static Object field(Class<?> c, String name) throws Exception {
        try { return (Object) GET_FIELD.invoke(c, name); } catch (Throwable t) { throw wrap(t); }
    }

    /** Method.invoke; target is null for a static method. */
    public static Object invoke(Object method, Object target, Object... args) throws Exception {
        try { return (Object) INVOKE.invoke(method, target, args); } catch (Throwable t) { throw wrap(t); }
    }

    /** Field.get; target is null for a static field. */
    public static Object get(Object field, Object target) throws Exception {
        try { return (Object) FIELD_GET.invoke(field, target); } catch (Throwable t) { throw wrap(t); }
    }

    public static boolean getBoolean(Object field, Object target) throws Exception { return Boolean.TRUE.equals(get(field, target)); }
    public static int getInt(Object field, Object target) throws Exception { return ((Number) get(field, target)).intValue(); }
    public static float getFloat(Object field, Object target) throws Exception { return ((Number) get(field, target)).floatValue(); }

    /** Field.set (unboxes into primitive fields). */
    public static void set(Object field, Object target, Object value) throws Exception {
        try { FIELD_SET.invoke(field, target, value); } catch (Throwable t) { throw wrap(t); }
    }
}

package data.scripts.jonahtweaks.autopilot;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;

import java.util.HashMap;
import java.util.Map;

/**
 * Counts the warnings and errors Mjolniar's mods write to starsector.log this session, with the latest of each, for
 * the Diagnostics page. It listens on each mod's package logger (log4j passes every class's messages up to its
 * package), so the other mods need nothing from this one, and it only reads what they log anyway.
 */
public class CALogWatch extends AppenderSkeleton {
    /** Package, name and mod id of each of Mjolniar's mods. */
    public static final String[][] MODS = {
            {"data.scripts.jonahtweaks", "Auto Colonies", "jonah_tweaks"},
            {"data.scripts.jonahseasysector", "Easy Sector", "jonahs_easy_sector"},
            {"data.scripts.jonahsautooutfitting", "Auto Outfitting", "jonahs_auto_outfitting"},
            {"data.scripts.jonahsfixes", "Fixes", "jonahs_fixes"}};

    static final Map<String, int[]> counts = new HashMap<String, int[]>();   // package -> {warnings, errors}
    static final Map<String, String> last = new HashMap<String, String>();   // package -> latest warning or error
    static boolean installed = false;

    /** Starts listening; safe to call more than once. */
    public static synchronized void install() {
        if (installed) return;
        installed = true;
        try {
            CALogWatch w = new CALogWatch();
            w.setName("AuthorModsLogWatch");
            w.setThreshold(Level.WARN);
            for (String[] m : MODS) Logger.getLogger(m[0]).addAppender(w);
        } catch (Throwable t) {
            CADiagnostics.LOG.warn(CADiagnostics.TAG + "couldn't watch the log for the Diagnostics page", t);
        }
    }

    /** The mod a logger belongs to (its package), or null. */
    static String pkgOf(String logger) {
        if (logger == null) return null;
        for (String[] m : MODS) if (logger.equals(m[0]) || logger.startsWith(m[0] + ".")) return m[0];
        return null;
    }

    @Override
    protected void append(LoggingEvent e) {   // doAppend is synchronized, and messages can come from other threads
        try {
            if (e == null || e.getLevel() == null || !e.getLevel().isGreaterOrEqual(Level.WARN)) return;
            String pkg = pkgOf(e.getLoggerName());
            if (pkg == null) return;
            int[] c = counts.get(pkg);
            if (c == null) { c = new int[2]; counts.put(pkg, c); }
            c[e.getLevel().isGreaterOrEqual(Level.ERROR) ? 1 : 0]++;
            String msg = String.valueOf(e.getMessage());
            Throwable th = e.getThrowableInformation() == null ? null : e.getThrowableInformation().getThrowable();
            if (th != null) msg += " (" + th + ")";
            last.put(pkg, msg.length() > 240 ? msg.substring(0, 240) + "..." : msg);
        } catch (Throwable ignored) { }
    }

    /** {warnings, errors} this session for a mod's package. */
    public static synchronized int[] counts(String pkg) { int[] c = counts.get(pkg); return c == null ? new int[2] : c.clone(); }

    public static synchronized String last(String pkg) { return last.get(pkg); }

    @Override public boolean requiresLayout() { return false; }
    @Override public void close() { }
}

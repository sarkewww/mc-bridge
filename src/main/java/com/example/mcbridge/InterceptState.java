package com.example.mcbridge;

public class InterceptState {
    public enum Mode { OFF, COPY, INTERCEPT }

    private static Mode mode = Mode.OFF;
    private static final ThreadLocal<Boolean> bypass = ThreadLocal.withInitial(() -> false);

    public static boolean isEnabled() {
        return mode != Mode.OFF;
    }

    public static boolean isCopyMode() {
        return mode == Mode.COPY;
    }

    public static boolean isInterceptMode() {
        return mode == Mode.INTERCEPT;
    }

    public static Mode getMode() { return mode; }

    public static void setMode(Mode m) {
        mode = m;
        Config.save();
    }

    public static void setEnabled(boolean e) {
        mode = e ? Mode.INTERCEPT : Mode.OFF;
        Config.save();
    }

    public static String toggle() {
        mode = switch (mode) {
            case OFF -> Mode.INTERCEPT;
            case COPY -> Mode.OFF;
            case INTERCEPT -> Mode.OFF;
        };
        Config.save();
        return mode == Mode.OFF ? "Chat intercept DISABLED"
                : mode == Mode.COPY ? "Chat copy mode ENABLED (send + record)"
                : "Chat intercept ENABLED (block + record)";
    }

    public static void runBypass(Runnable r) {
        bypass.set(true);
        try { r.run(); } finally { bypass.set(false); }
    }

    public static boolean isBypassing() {
        return bypass.get();
    }
}

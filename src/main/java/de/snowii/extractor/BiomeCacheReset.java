package de.snowii.extractor;

public final class BiomeCacheReset {
    private static final ThreadLocal<Boolean> RESET_NEEDED = new ThreadLocal<>();

    private BiomeCacheReset() {
    }

    public static void markNeeded() {
        RESET_NEEDED.set(Boolean.TRUE);
    }

    public static boolean consumeNeeded() {
        if (RESET_NEEDED.get() == Boolean.TRUE) {
            RESET_NEEDED.remove();
            return true;
        }
        return false;
    }
}

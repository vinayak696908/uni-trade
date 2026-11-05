package com.unitrade.service;

/**
 * Normalises user-supplied text before it is stored. Output encoding (escaping) happens in the
 * frontend, which only ever inserts user content with textContent, never innerHTML.
 */
public final class TextSanitizer {

    private TextSanitizer() {
    }

    /** Trims and removes control characters except newline and tab. */
    public static String clean(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(input.length());
        input.codePoints().forEach(cp -> {
            if (cp == '\n' || cp == '\t' || !Character.isISOControl(cp)) {
                sb.appendCodePoint(cp);
            }
        });
        return sb.toString().replace("\r", "").trim();
    }

    /** Like {@link #clean(String)} but also collapses all whitespace runs to single spaces. */
    public static String singleLine(String input) {
        String cleaned = clean(input);
        return cleaned == null ? null : cleaned.replaceAll("\\s+", " ");
    }

    /** Returns null for null/blank input, otherwise the cleaned single-line value. */
    public static String blankToNull(String input) {
        String cleaned = singleLine(input);
        return (cleaned == null || cleaned.isEmpty()) ? null : cleaned;
    }
}

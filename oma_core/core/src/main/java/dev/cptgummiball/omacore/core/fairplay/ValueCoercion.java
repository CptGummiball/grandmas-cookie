package dev.cptgummiball.omacore.core.fairplay;

import java.util.Locale;
import java.util.Optional;

/**
 * Turns a lock-table value into the Java type a config entry already holds (its current or default
 * value is the prototype). Supported: Boolean, Integer, Long, Double, String, enums. Lists and other
 * types are rejected, so an unsupported entry is reported instead of being written wrongly.
 */
public final class ValueCoercion {
    private ValueCoercion() {
    }

    public static Optional<Object> coerce(String text, Object prototype) {
        String s = text.strip();
        try {
            if (prototype instanceof Boolean) {
                String l = s.toLowerCase(Locale.ROOT);
                return l.equals("true") || l.equals("false") ? Optional.of(Boolean.valueOf(l)) : Optional.empty();
            }
            if (prototype instanceof Integer) {
                return Optional.of(Integer.valueOf(s));
            }
            if (prototype instanceof Long) {
                return Optional.of(Long.valueOf(s));
            }
            if (prototype instanceof Double) {
                return Optional.of(Double.valueOf(s));
            }
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (prototype instanceof String) {
            return Optional.of(unquote(s));
        }
        if (prototype instanceof Enum<?> e) {
            String name = unquote(s);
            for (Object constant : e.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) constant).name().equals(name)) {
                    return Optional.of(constant);
                }
            }
        }
        return Optional.empty();
    }

    /** How a config value is shown and compared: enum constants by name, everything else as text. */
    public static String display(Object value) {
        return value instanceof Enum<?> e ? e.name() : String.valueOf(value);
    }

    private static String unquote(String s) {
        return s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"") ? s.substring(1, s.length() - 1) : s;
    }
}

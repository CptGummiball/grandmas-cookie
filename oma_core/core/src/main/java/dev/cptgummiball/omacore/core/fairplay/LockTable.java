package dev.cptgummiball.omacore.core.fairplay;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pack-owned table of locked config values, shipped inside the mod jar.
 *
 * <pre>
 * # comment
 * &lt;modid&gt; &lt;COMMON|SERVER|STARTUP&gt; &lt;dotted.path&gt; = &lt;value | @default&gt;
 * </pre>
 * Paths may contain spaces and slashes inside a segment (some mods use them in section names).
 *
 * A malformed line or a second rule for the same entry rejects the whole table, so a broken table
 * can never be half applied.
 */
public final class LockTable {
    private static final Pattern LINE = Pattern.compile(
            "([a-z0-9_.-]+)\\s+([A-Za-z]+)\\s+([A-Za-z0-9_./ -]*[A-Za-z0-9_./-])\\s*=\\s*(.*)");

    private final List<LockRule> rules;

    private LockTable(List<LockRule> rules) {
        this.rules = List.copyOf(rules);
    }

    public static LockTable empty() {
        return new LockTable(List.of());
    }

    public static LockTable parse(String text) throws LockTableException {
        Map<String, LockRule> byKey = new LinkedHashMap<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].strip();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Matcher m = LINE.matcher(line);
            if (!m.matches()) {
                throw new LockTableException(i + 1, "expected '<modid> <COMMON|SERVER|STARTUP> <path> = <value>'");
            }
            ConfigSide side;
            try {
                side = ConfigSide.valueOf(m.group(2).toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new LockTableException(i + 1,
                        "side must be COMMON, SERVER or STARTUP, got '" + m.group(2) + "'");
            }
            String value = m.group(4).strip();
            if (value.isEmpty()) {
                throw new LockTableException(i + 1, "empty value");
            }
            LockRule rule = new LockRule(m.group(1), side, m.group(3), value);
            if (byKey.putIfAbsent(rule.key(), rule) != null) {
                throw new LockTableException(i + 1, "duplicate rule for " + rule.key());
            }
        }
        return new LockTable(new ArrayList<>(byKey.values()));
    }

    public List<LockRule> rules() {
        return rules;
    }

    public int size() {
        return rules.size();
    }
}

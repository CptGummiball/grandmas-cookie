package dev.cptgummiball.omacore.core.house;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reads the plain-text blueprint format:
 *
 * <pre>
 * size &lt;x&gt; &lt;y&gt; &lt;z&gt;
 * key &lt;char&gt; &lt;block state&gt;
 * layer &lt;y&gt;            followed by exactly z rows of exactly x characters
 * marker &lt;name&gt; &lt;x&gt; &lt;y&gt; &lt;z&gt; &lt;yaw&gt;
 * # comment
 * </pre>
 *
 * Every layer must appear once, every character must be in the palette (or '.'/'_'), markers must be
 * inside the bounds. Errors carry the 1-based line number; nothing is half accepted.
 */
public final class BlueprintParser {
    private BlueprintParser() {
    }

    public static Blueprint parse(String text) throws BlueprintException {
        String[] lines = text.split("\\R", -1);
        int sx = -1;
        int sy = -1;
        int sz = -1;
        Map<Character, String> palette = new LinkedHashMap<>();
        Map<String, Blueprint.Marker> markers = new LinkedHashMap<>();
        Map<Integer, char[][]> layers = new HashMap<>();
        int i = 0;
        while (i < lines.length) {
            String line = lines[i].strip();
            int lineNo = i + 1;
            i++;
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] t = line.split("\\s+");
            switch (t[0]) {
                case "size" -> {
                    if (t.length != 4) {
                        throw new BlueprintException(lineNo, "size needs x y z");
                    }
                    sx = positive(t[1], lineNo);
                    sy = positive(t[2], lineNo);
                    sz = positive(t[3], lineNo);
                }
                case "key" -> {
                    if (t.length != 3 || t[1].length() != 1) {
                        throw new BlueprintException(lineNo, "key needs one character and one block state");
                    }
                    char c = t[1].charAt(0);
                    if (c == Blueprint.AIR || c == Blueprint.KEEP || c == '#') {
                        throw new BlueprintException(lineNo, "'" + c + "' is reserved");
                    }
                    if (palette.putIfAbsent(c, t[2]) != null) {
                        throw new BlueprintException(lineNo, "duplicate key '" + c + "'");
                    }
                }
                case "layer" -> {
                    if (sx < 0) {
                        throw new BlueprintException(lineNo, "size must come before the first layer");
                    }
                    int y = number(t.length == 2 ? t[1] : "", lineNo);
                    if (y < 0 || y >= sy || layers.containsKey(y)) {
                        throw new BlueprintException(lineNo, "layer " + y + " is out of range or repeated");
                    }
                    char[][] rows = new char[sz][];
                    for (int z = 0; z < sz; z++) {
                        if (i >= lines.length) {
                            throw new BlueprintException(i, "layer " + y + " needs " + sz + " rows");
                        }
                        String row = lines[i].strip();
                        if (row.length() != sx) {
                            throw new BlueprintException(i + 1, "row must have " + sx + " characters, has " + row.length());
                        }
                        rows[z] = row.toCharArray();
                        i++;
                    }
                    layers.put(y, rows);
                }
                case "marker" -> {
                    if (t.length != 6) {
                        throw new BlueprintException(lineNo, "marker needs name x y z yaw");
                    }
                    Blueprint.Marker m = new Blueprint.Marker(number(t[2], lineNo), number(t[3], lineNo),
                            number(t[4], lineNo), (float) Double.parseDouble(t[5]));
                    if (m.x() < 0 || m.x() >= sx || m.y() < 0 || m.y() >= sy || m.z() < 0 || m.z() >= sz) {
                        throw new BlueprintException(lineNo, "marker " + t[1] + " is outside the bounds");
                    }
                    markers.put(t[1], m);
                }
                default -> throw new BlueprintException(lineNo, "unknown directive '" + t[0] + "'");
            }
        }
        if (sx < 0) {
            throw new BlueprintException(lines.length, "missing size");
        }
        char[][][] cells = new char[sy][][];
        for (int y = 0; y < sy; y++) {
            char[][] rows = layers.get(y);
            if (rows == null) {
                throw new BlueprintException(lines.length, "layer " + y + " is missing");
            }
            for (char[] row : rows) {
                for (char c : row) {
                    if (c != Blueprint.AIR && c != Blueprint.KEEP && !palette.containsKey(c)) {
                        throw new BlueprintException(lines.length, "layer " + y + " uses undefined key '" + c + "'");
                    }
                }
            }
            cells[y] = rows;
        }
        return new Blueprint(sx, sy, sz, palette, cells, markers);
    }

    private static int number(String s, int line) throws BlueprintException {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new BlueprintException(line, "not a number: '" + s + "'");
        }
    }

    private static int positive(String s, int line) throws BlueprintException {
        int n = number(s, line);
        if (n <= 0 || n > 64) {
            throw new BlueprintException(line, "size must be 1..64");
        }
        return n;
    }
}

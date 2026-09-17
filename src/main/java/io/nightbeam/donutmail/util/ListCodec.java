package io.nightbeam.donutmail.util;

import java.util.ArrayList;
import java.util.List;

public final class ListCodec {
    private static final String DELIMITER = "\u0001";

    private ListCodec() {
    }

    public static String encode(List<String> list) {
        if (list == null || list.isEmpty()) {
            return "";
        }
        return String.join(DELIMITER, list);
    }

    public static List<String> decode(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        String[] chunks = value.split(DELIMITER, -1);
        List<String> out = new ArrayList<>(chunks.length);
        for (String chunk : chunks) {
            if (!chunk.isBlank()) {
                out.add(chunk);
            }
        }
        return out;
    }
}

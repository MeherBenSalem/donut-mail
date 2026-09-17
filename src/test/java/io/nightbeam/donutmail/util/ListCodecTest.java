package io.nightbeam.donutmail.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListCodecTest {

    @Test
    void encodeDecodeRoundTrip() {
        List<String> input = List.of("a", "b", "c");
        String encoded = ListCodec.encode(input);
        assertEquals(input, ListCodec.decode(encoded));
    }

    @Test
    void decodeBlankReturnsEmpty() {
        assertTrue(ListCodec.decode("").isEmpty());
        assertTrue(ListCodec.decode(null).isEmpty());
    }

    @Test
    void encodeNullOrEmptyReturnsBlank() {
        assertEquals("", ListCodec.encode(null));
        assertEquals("", ListCodec.encode(List.of()));
    }
}

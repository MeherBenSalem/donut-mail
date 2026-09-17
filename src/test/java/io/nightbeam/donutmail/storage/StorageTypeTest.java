package io.nightbeam.donutmail.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StorageTypeTest {

    @Test
    void fromStringParsesKnownTypes() {
        assertEquals(StorageType.YAML, StorageType.fromString("yaml"));
        assertEquals(StorageType.SQLITE, StorageType.fromString("SQLITE"));
        assertEquals(StorageType.MYSQL, StorageType.fromString("MySql"));
    }
}

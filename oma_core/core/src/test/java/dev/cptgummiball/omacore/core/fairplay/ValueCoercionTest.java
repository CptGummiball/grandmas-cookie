package dev.cptgummiball.omacore.core.fairplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ValueCoercionTest {
    enum Mode { CREEPERS, ALL }

    @Test
    void coercesToThePrototypeType() {
        assertEquals(Optional.of(false), ValueCoercion.coerce(" FALSE ", true));
        assertEquals(Optional.of(64), ValueCoercion.coerce("64", 1));
        assertEquals(Optional.of(64L), ValueCoercion.coerce("64", 1L));
        assertEquals(Optional.of(0.5), ValueCoercion.coerce("0.5", 1.0));
        assertEquals(Optional.of("neoforge:default_handler"), ValueCoercion.coerce("\"neoforge:default_handler\"", "x"));
        assertEquals(Optional.of(Mode.ALL), ValueCoercion.coerce("ALL", Mode.CREEPERS));
    }

    @Test
    void rejectsWrongOrUnsupportedValues() {
        assertTrue(ValueCoercion.coerce("yes", true).isEmpty());
        assertTrue(ValueCoercion.coerce("1.5", 1).isEmpty());
        assertTrue(ValueCoercion.coerce("all", Mode.CREEPERS).isEmpty());   // enum names are exact
        assertTrue(ValueCoercion.coerce("[a]", List.of("a")).isEmpty());     // lists unsupported
    }

    @Test
    void displaysEnumsByName() {
        assertEquals("CREEPERS", ValueCoercion.display(Mode.CREEPERS));
        assertEquals("0.009999999776482582", ValueCoercion.display(0.009999999776482582));
    }
}

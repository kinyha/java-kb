package demo;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FeeTest {
    @Test void roundsUp() {
        assertEquals(0, Fee.minorUnits(0));
        assertEquals(2, Fee.minorUnits(100));
        assertEquals(3, Fee.minorUnits(101));
    }

    @Test void rejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> Fee.minorUnits(-1));
    }
}

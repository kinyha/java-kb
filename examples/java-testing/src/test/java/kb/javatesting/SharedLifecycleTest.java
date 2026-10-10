package kb.javatesting;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SharedLifecycleTest {
    int local;
    @BeforeAll void beforeAll() { System.out.println("PER_CLASS beforeAll local=" + local); }
    @BeforeEach void beforeEach() { System.out.println("PER_CLASS beforeEach local=" + local); }
    @ParameterizedTest @ValueSource(ints={1,2})
    void bothInvocationsShareTheField(int ignored) {
        assertThat(local).isLessThan(2);
        local++;
    }
    @AfterAll void afterAll() {
        assertThat(local).isEqualTo(2);
        System.out.println("PER_CLASS afterAll local=" + local);
    }
}

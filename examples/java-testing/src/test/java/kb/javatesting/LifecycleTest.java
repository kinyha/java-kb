package kb.javatesting;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class) // ONLY to make this teaching trace stable
class LifecycleTest {
    static int nextInstance;
    static int shared;
    final int instance = ++nextInstance;
    int local;
    @BeforeAll static void beforeAll() { System.out.println("lifecycle: beforeAll"); }
    @BeforeEach void beforeEach() {
        System.out.printf("beforeEach instance=%d local=%d shared=%d%n", instance, local, shared);
    }
    @Order(1) @ParameterizedTest @ValueSource(ints={7, 8})
    void eachInvocationIsANewInstance(int input) {
        assertThat(local).isZero();
        System.out.printf("body input=%d instance=%d%n", input, instance);
        local++; shared++;
    }
    @Order(2) @Test void fieldFreshButStaticRemains() {
        assertThat(local).isZero();
        assertThat(shared).isEqualTo(2);
        System.out.printf("body final instance=%d local=%d shared=%d%n", instance, local, shared);
    }
    @AfterEach void afterEach() { System.out.printf("afterEach instance=%d local=%d%n", instance, local); }
    @AfterAll static void afterAll() { System.out.println("lifecycle: afterAll"); }
}

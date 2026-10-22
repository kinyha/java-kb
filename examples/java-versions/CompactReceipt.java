// Java 25: compact source file implicitly imports java.base types, but not static IO methods.
void main() {
    IO.println(List.of("P1", "P2").reversed());
}

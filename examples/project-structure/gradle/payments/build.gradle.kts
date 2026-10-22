dependencies {
    // implementation: persistence нужен payments, но не попадает на classpath его потребителей.
    // С -Pleak демо подключает его через api, как compile-зависимость в Maven, — и app его видит.
    if (providers.gradleProperty("leak").isPresent) {
        api(project(":payments-persistence"))
    } else {
        implementation(project(":payments-persistence"))
    }
}

// Только чтобы собрать classpath для опытов темы Kotlin with Spring & JPA.
// Опыты компилируются kotlinc напрямую: так видно, какой плагин компилятора что меняет.
plugins { java }
repositories { mavenLocal(); mavenCentral() }
dependencies {
    implementation("org.springframework:spring-context:7.0.9")
    implementation("org.springframework:spring-tx:7.0.9")
    implementation("org.springframework:spring-orm:7.0.9")
    implementation("org.springframework:spring-webmvc:7.0.9")
    implementation("org.springframework:spring-test:7.0.9")
    implementation("org.hibernate.orm:hibernate-core:7.4.1.Final")
    implementation("com.h2database:h2:2.4.240")
    implementation("tools.jackson.core:jackson-databind:3.1.5")
    implementation("jakarta.servlet:jakarta.servlet-api:6.1.0")
}
// Версии, которые уже были в локальном кеше при проверке; Hibernate 7.4.1 просит чуть более новые.
configurations.all {
    resolutionStrategy.force("net.bytebuddy:byte-buddy:1.18.10", "jakarta.xml.bind:jakarta.xml.bind-api:4.0.5",
        "org.glassfish.jaxb:jaxb-runtime:4.0.9", "org.glassfish.jaxb:jaxb-core:4.0.9", "org.glassfish.jaxb:txw2:4.0.9")
}
tasks.register("cp") { doLast { println("CP=" + configurations.runtimeClasspath.get().asPath) } }

subprojects {
    apply(plugin = "java-library")
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion.set(JavaLanguageVersion.of(21)) }
}

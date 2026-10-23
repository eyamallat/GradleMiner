plugins {
    application
    java
}

repositories {
    mavenCentral()
}
configurations.all {
    exclude(group = "org.codehaus.groovy", module = "groovy")
}
dependencies {

    // Testing
    testImplementation(platform("org.junit:junit-bom:5.10.1"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Utilities
    implementation(libs.guava)
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.0")

    // Gradle API
    implementation(gradleApi())

    // OpenRewrite
    implementation("org.openrewrite:rewrite-core:8.44.0")
    implementation("org.openrewrite:rewrite-gradle:8.44.0")
    implementation("org.openrewrite:rewrite-groovy:8.44.0")

    // Kotlin parser (used by rewrite)
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:1.9.22")

    // Groovy 4 (required by rewrite-groovy)
    implementation("org.apache.groovy:groovy:4.0.30")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "org.example.App"
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
/*
application {
    mainClass = "org.example.LLM.naiveLLM"
}
*/
tasks.named<Test>("test") {
    useJUnitPlatform()
}

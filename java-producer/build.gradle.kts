import org.gradle.api.tasks.Exec

plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:4.0.0")
    implementation("io.confluent:kafka-protobuf-serializer:8.1.1")
    implementation("com.google.protobuf:protobuf-java:4.33.5")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

sourceSets {
    named("main") {
        java.srcDir(project.layout.projectDirectory.dir("generated/java"))
    }
}

val bufGenerate by tasks.registering(Exec::class) {
    group = "protobuf"
    description = "Generates protobuf classes with buf before compiling Java producer."
    workingDir = rootProject.projectDir
    commandLine("buf", "generate")
}

tasks.named("compileJava") {
    dependsOn(bufGenerate)
}

application {
    mainClass = "org.example.javaproducer.JavaProtobufProducer"
}

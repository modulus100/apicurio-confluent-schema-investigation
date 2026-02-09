import org.gradle.api.tasks.Exec

allprojects {
    repositories {
        mavenCentral()
        maven(url = "https://packages.confluent.io/maven/")
    }
}

tasks.register<Exec>("bufDepUpdate") {
    group = "protobuf"
    description = "Updates buf module dependencies."
    commandLine("buf", "dep", "update")
}

tasks.register<Exec>("bufLint") {
    group = "protobuf"
    description = "Runs buf lint checks."
    commandLine("buf", "lint")
}

tasks.register<Exec>("bufGenerate") {
    group = "protobuf"
    description = "Generates Java, Python, and Go sources from proto files."
    commandLine("buf", "generate")
}

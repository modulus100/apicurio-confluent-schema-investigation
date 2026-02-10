plugins {
    id("buildlogic.java-application-conventions")
}

dependencies {
    implementation("org.apache.kafka:kafka-clients:4.0.0")
    implementation("com.google.protobuf:protobuf-java:4.33.5")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.16")
}

sourceSets {
    named("main") {
        // Reuse generated protobuf classes from the existing Java producer module.
        java.srcDir(project.layout.projectDirectory.dir("../java-producer/generated/java"))
    }
}

application {
    mainClass = "org.example.javaproducerplain.JavaProtobufPlainProducer"
}

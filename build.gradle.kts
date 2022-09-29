import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "2.7.3"
    id("io.spring.dependency-management") version "1.0.13.RELEASE"
    kotlin("jvm") version "1.7.10"
    kotlin("plugin.spring") version "1.6.21"
    id("com.google.cloud.tools.jib") version "3.3.0"
}

group = "nl.vorhauer"
version = "1.0.0-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
    mavenLocal()
    mavenCentral()
    maven("https://repo.spring.io/milestone")
    maven("https://repo.spring.io/snapshot")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-cassandra-reactive")
    implementation("com.datastax.astra:astra-spring-boot-starter:0.3.3")
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    implementation("com.typesafe.akka:akka-persistence_2.13:2.6.20")
    implementation("com.typesafe.akka:akka-persistence-query_2.13:2.6.20")
    implementation("com.typesafe.akka:akka-cluster-tools_2.13:2.6.20")
    implementation("com.typesafe.akka:akka-persistence-cassandra_2.13:1.0.6")

    runtimeOnly("io.micrometer:micrometer-registry-datadog")
    implementation("io.netty:netty-all:4.1.79.Final")
    implementation("org.valiktor:valiktor-spring-boot-starter:0.12.0")
    implementation("org.valiktor:valiktor-core:0.12.0")
    implementation("org.valiktor:valiktor-javatime:0.12.0")
    implementation("org.owasp.encoder:encoder:1.2.3")
    implementation("io.github.microutils:kotlin-logging-jvm:2.1.23")
    implementation("org.slf4j:slf4j-simple:2.0.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("com.typesafe.akka:akka-persistence-testkit_2.13:2.6.20")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = "11"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jib {
    from {
        image = "openjdk:11"
    }
    to {
        image = "ghcr.io/jvorhauer/noviblog:latest"
        auth {
            username = System.getenv("GITHUB_USER")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
    container {
        jvmFlags = listOf("-Xms512m", "-Xmx512m")
        mainClass = "blog.Application"
        ports = listOf("8080/tcp")

    }
    setAllowInsecureRegistries(true)
}

tasks.named("jib") {
    dependsOn("test")
}

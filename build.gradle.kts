import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
        id("org.springframework.boot") version "3.0.2"
        id("io.spring.dependency-management") version "1.1.0"
        kotlin("jvm") version "1.7.22"
        kotlin("plugin.spring") version "1.7.22"
}

group = "com.example.kotlin"
version = "0.0.1-SNAPSHOT"
java.sourceCompatibility = JavaVersion.VERSION_17


repositories {
        mavenCentral()
}

dependencies {
        implementation("org.springframework.boot:spring-boot-starter")
        implementation("org.springframework.boot:spring-boot-starter-actuator")
        implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
        implementation("org.springframework.boot:spring-boot-starter-web")

        implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
        implementation("com.github.javafaker:javafaker:1.0.2")

        implementation("org.jetbrains.kotlin:kotlin-reflect")
        implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

        testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}

}

tasks.withType<Test> {
        useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "17"
	}
}

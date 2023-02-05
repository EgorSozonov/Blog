import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    kotlin("jvm") version "1.7.10"
}

group = "tech.sozonov"
version = "1.0-SNAPSHOT"
val kotlinVersion = "1.7.10"
val ktorVersion = "2.1.0"

repositories {
    mavenCentral()
}

dependencies {
    //compile fileTree(dir: 'libs', include: ['*.jar'])
    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8:$kotlinVersion")
    implementation("io.ktor:ktor-server-caching-headers:$ktorVersion")
    testImplementation(kotlin("test"))
}
buildDir = File("./_bin")

application {
    mainClass.set("io.ktor.server.netty.EngineMain")
}

sourceSets {
    main {
        java {
            this.setSrcDirs(mutableListOf("src"))
        }
        resources {
            this.setSrcDirs(mutableListOf("config", "resources"))
        }
    }
    test {
        java {
            this.setSrcDirs(mutableListOf("test"))
        }
    }
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}
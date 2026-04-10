plugins {
    id("groovy")
    id("maven-publish")
}

group = "ru.kazantsev.nsd.modules"
version = "2.2.2"

tasks.javadoc{
    options.encoding = "UTF-8"
}

tasks.test {
    failOnNoDiscoveredTests = false
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    sourceCompatibility = JavaVersion.VERSION_21
    withJavadocJar()
    withSourcesJar()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["groovy"])
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/exeki/nsmp.modules.web_api_components")
            credentials {
                username = System.getenv("GITHUB_USERNAME")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/exeki/*")
        credentials {
            username = System.getenv("GITHUB_USERNAME")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("org.apache.groovy:groovy:4.0.26")
    implementation("ru.kazantsev.nsd.sdk:global_variables:1.5.0")
}


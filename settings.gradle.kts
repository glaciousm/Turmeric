rootProject.name = "intent-healer"

include("healer-core")
include("healer-llm")
include("healer-selenium")
include("healer-cucumber")
include("healer-report")
include("healer-cli")
include("healer-example")
include("healer-testng")
include("healer-junit")

dependencyResolutionManagement {
    versionCatalogs {
        create("libs") {
            // Versions
            version("java", "17")
            version("selenium", "4.15.0")
            version("cucumber", "7.14.0")
            version("jackson", "2.16.0")
            version("slf4j", "2.0.9")
            version("logback", "1.4.14")
            version("junit", "5.10.1")
            version("mockito", "5.8.0")
            version("assertj", "3.24.2")
            version("snakeyaml", "2.2")
            version("okhttp", "4.12.0")
            version("guava", "32.1.3-jre")
            version("caffeine", "3.1.8")
            version("micrometer", "1.12.1")

            // Libraries
            library("selenium-java", "org.seleniumhq.selenium", "selenium-java").versionRef("selenium")
            library("selenium-support", "org.seleniumhq.selenium", "selenium-support").versionRef("selenium")

            library("cucumber-java", "io.cucumber", "cucumber-java").versionRef("cucumber")
            library("cucumber-junit", "io.cucumber", "cucumber-junit-platform-engine").versionRef("cucumber")
            library("cucumber-picocontainer", "io.cucumber", "cucumber-picocontainer").versionRef("cucumber")

            library("jackson-core", "com.fasterxml.jackson.core", "jackson-core").versionRef("jackson")
            library("jackson-databind", "com.fasterxml.jackson.core", "jackson-databind").versionRef("jackson")
            library("jackson-annotations", "com.fasterxml.jackson.core", "jackson-annotations").versionRef("jackson")
            library("jackson-dataformat-yaml", "com.fasterxml.jackson.dataformat", "jackson-dataformat-yaml").versionRef("jackson")
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").versionRef("jackson")

            library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("logback-classic", "ch.qos.logback", "logback-classic").versionRef("logback")

            library("snakeyaml", "org.yaml", "snakeyaml").versionRef("snakeyaml")
            library("okhttp", "com.squareup.okhttp3", "okhttp").versionRef("okhttp")
            library("guava", "com.google.guava", "guava").versionRef("guava")
            library("caffeine", "com.github.ben-manes.caffeine", "caffeine").versionRef("caffeine")
            library("micrometer-core", "io.micrometer", "micrometer-core").versionRef("micrometer")
            library("micrometer-registry-prometheus", "io.micrometer", "micrometer-registry-prometheus").versionRef("micrometer")

            library("junit-jupiter", "org.junit.jupiter", "junit-jupiter").versionRef("junit")
            library("junit-platform-suite", "org.junit.platform", "junit-platform-suite").version("1.10.1")
            library("mockito-core", "org.mockito", "mockito-core").versionRef("mockito")
            library("mockito-junit-jupiter", "org.mockito", "mockito-junit-jupiter").versionRef("mockito")
            library("assertj-core", "org.assertj", "assertj-core").versionRef("assertj")

            // Bundles
            bundle("jackson", listOf("jackson-core", "jackson-databind", "jackson-annotations", "jackson-dataformat-yaml", "jackson-datatype-jsr310"))
            bundle("cucumber", listOf("cucumber-java", "cucumber-junit", "cucumber-picocontainer"))
            bundle("testing", listOf("junit-jupiter", "mockito-core", "mockito-junit-jupiter", "assertj-core"))
            bundle("logging", listOf("slf4j-api", "logback-classic"))
            bundle("selenium", listOf("selenium-java", "selenium-support"))
        }
    }
}

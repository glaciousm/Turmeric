plugins {
    java
}

dependencies {
    implementation(project(":healer-core"))
    implementation(project(":healer-selenium"))
    implementation(project(":healer-llm"))
    implementation(project(":healer-report"))

    implementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    implementation("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    implementation("org.seleniumhq.selenium:selenium-java:4.15.0")
    implementation("org.slf4j:slf4j-api:2.0.9")

    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

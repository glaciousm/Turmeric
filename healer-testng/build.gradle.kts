plugins {
    java
}

dependencies {
    implementation(project(":healer-core"))
    implementation(project(":healer-selenium"))
    implementation(project(":healer-llm"))
    implementation(project(":healer-report"))

    implementation("org.testng:testng:7.8.0")
    implementation("org.seleniumhq.selenium:selenium-java:4.15.0")
    implementation("org.slf4j:slf4j-api:2.0.9")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testImplementation("org.mockito:mockito-core:5.7.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.7.0")
    testImplementation("org.assertj:assertj-core:3.24.2")
}

tasks.test {
    useJUnitPlatform()
}

import java.io.ByteArrayOutputStream

plugins {
    id("java-gradle-plugin")
    id("maven-publish")
    id("org.jetbrains.kotlin.jvm") version "1.4.21"
    id("com.gradle.plugin-publish") version "0.12.0"
}

group = "dev.hworblehat"
version = "0.1.0"

pluginBundle {
    website = "https://github.com/hWorblehat/gradlecumber"
    vcsUrl = "https://github.com/hWorblehat/gradlecumber"
    tags = listOf("cucumber", "BDD", "featureTest", "test", "gherkin")
}

val gradlecumberPlugin by gradlePlugin.plugins.creating {
    id = "dev.hworblehat.gradlecumber"
    displayName = "Gradlecumber Cucumber Plugin"
    description = "Define, run and analyse BDD feature test suites using Cucumber"
    implementationClass = "dev.hworblehat.gradlecumber.GradlecumberPlugin"
}

repositories {
    jcenter()
}

val testGlue by sourceSets.creating
val kotestVersion = "4.3.+"
fun kotest(lib: String): String = "io.kotest:kotest-$lib:$kotestVersion"

configurations {

}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom:$embeddedKotlinVersion"))
    api("io.cucumber:messages:[13,)")
    api("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("io.github.microutils:kotlin-logging:[2,3)")

    val testGlueImplementation = testGlue.implementationConfigurationName

    testGlueImplementation(platform("org.jetbrains.kotlin:kotlin-bom:$embeddedKotlinVersion"))
    testGlueImplementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testGlueImplementation("io.cucumber:cucumber-java8:6.+")
    testGlueImplementation(kotest("assertions-core"))

    testImplementation(kotest("runner-junit5"))
    testImplementation(kotest("assertions-core"))
    testImplementation("commons-io:commons-io:2.+")
    testImplementation("io.mockk:mockk:1.+")
}

tasks.withType<Test> {
    useJUnitPlatform()

    inputs.files(configurations.named(testGlue.runtimeClasspathConfigurationName))
    inputs.files(testGlue.output)

    doFirst {
        systemProperty("gradlecumber.test.gluecp", testGlue.runtimeClasspath.asPath)
        systemProperty("gradlecumber.test.pluginName", gradlecumberPlugin.id)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    kotlinOptions {
        languageVersion = "1.4"
        jvmTarget = "1.8"
    }
}

val messageSamplesRoot = layout.buildDirectory.dir("messageSamples")
val messageSamples by tasks.registering {
    group = "build"
    description = "Generate Cucumber Messages sample files"
}
val messageSamplesFiles = files(messageSamplesRoot) {
    builtBy(messageSamples)
}

sourceSets.test {
    resources.srcDir(messageSamplesFiles)
}

arrayOf(
    "singlePassingScenario" to arrayOf("passing"),
    "singleFailingScenario" to arrayOf("failing"),
    "singleUnimplementedScenario" to arrayOf("unimplemented"),
    "1Failing1Passing1Unimplemented" to arrayOf("passing", "failing", "unimplemented"),
    "2RulesWith1ErrorInEach" to arrayOf("rule"),
    "2RulesWithBackground" to arrayOf("background"),
    "1ScenarioWithVariables" to arrayOf("variables"),
    "scenarioOutlineWith3Examples" to arrayOf("outline"),
    "scenarioWithDataTable" to arrayOf("table"),
    "scenarioWithFailingHook" to arrayOf("hook")
).forEach { (name, features) ->

    val outputFile = messageSamplesRoot.map { it.file("messageSamples/$name.ndjson") }
    val inputFiles = features.map { file("src/test/resources/dummyFeatures/$it.feature") }

    val runCucumber = tasks.register("runCucumberSample${name.capitalize()}", JavaExec::class) {
        inputs.files(configurations.named(testGlue.runtimeClasspathConfigurationName))
        inputs.files(testGlue.output)
        inputs.files(inputFiles)

        outputs.file(outputFile)

        main = "io.cucumber.core.cli.Main"
        classpath(testGlue.runtimeClasspath)
        isIgnoreExitValue = true
        args(*inputFiles.map { it.path }.toTypedArray())

        standardOutput = ByteArrayOutputStream()

        doFirst {
            val reportFile = outputFile.get().asFile
            reportFile.parentFile.mkdirs()
            args("--plugin", "message:${reportFile.path}")
        }
    }

    messageSamples.configure {
        dependsOn(runCucumber)
    }

}

publishing {
    repositories {
        maven {
            name = "localFileRepository"
            url = uri("$buildDir/local-repository")
        }
    }
}

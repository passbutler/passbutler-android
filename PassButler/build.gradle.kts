plugins {
    id("com.android.application")

    id("org.jetbrains.kotlin.android")

    id("de.mannodermaus.android-junit5")
}

android {
    sourceSets.all {
        java.srcDir("./src/$name/kotlin/")
    }

    compileSdk = 30

    compileOptions {
        // Required minimum Java 8 for OkHttp3 used in Retrofit
        kotlinOptions.jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "de.passbutler.app"
        minSdk = 26
        targetSdk = 30
        versionCode = gitCommitCount()
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("Long", "BUILD_TIMESTAMP", "${System.currentTimeMillis()}L")
        buildConfigField("String", "BUILD_REVISION_HASH", "\"${gitCommitHashShort()}\"")
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            isDebuggable = true
            isMinifyEnabled = false

            manifestPlaceholders["usesCleartextTraffic"] = true
        }

        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

            manifestPlaceholders["usesCleartextTraffic"] = false
        }
    }
}

dependencies {
    implementation(project(":PassButlerCommon"))

    // Kotlin
    val kotlinVersion = "1.5.20"
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")

    // Kotlin Coroutines for Android
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.5.1")

    // TinyLog logger
    val tinylogVersion = "2.3.2"
    implementation("org.tinylog:tinylog-api-kotlin:$tinylogVersion")
    implementation("org.tinylog:tinylog-impl:$tinylogVersion")

    // SQLDelight
    implementation("com.squareup.sqldelight:android-driver:1.5.1")

    // Google Material theme/components
    implementation("com.google.android.material:material:1.4.0")

    // Android widgets
    implementation("androidx.appcompat:appcompat:1.3.1")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.0")

    // Android Lifecycle and ViewModel components
    implementation("androidx.lifecycle:lifecycle-extensions:2.2.0")

    val lifecycleVersion = "2.3.1"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    implementation("androidx.fragment:fragment-ktx:1.3.6")

    // Android Biometrics framework
    implementation("androidx.biometric:biometric:1.1.0")

    // Android Preference framework
    implementation("androidx.preference:preference:1.1.1")

    // JUnit 5
    val junitVersion = "5.7.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    // Mockk.io
    testImplementation("io.mockk:mockk:1.12.0")

    // UI tests
    val espressoVersion = "3.4.0"
    androidTestImplementation("androidx.test.espresso:espresso-core:$espressoVersion")
    androidTestImplementation("androidx.test.espresso:espresso-contrib:$espressoVersion")
    androidTestImplementation("androidx.test.ext:junit-ktx:1.1.3")
}

fun gitCommitCount(): Int {
    return "git rev-list HEAD --count".executeCommand().toInt()
}

fun gitCommitHashShort(): String {
    return "git rev-parse --short HEAD".executeCommand()
}

fun String.executeCommand(workingDir: File = file("./")): String {
    val commandParts = split(" ")
    val commandProcess = ProcessBuilder(*commandParts.toTypedArray())
        .directory(workingDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .redirectError(ProcessBuilder.Redirect.PIPE)
        .start()

    commandProcess.waitFor(5, TimeUnit.SECONDS)

    return commandProcess.inputStream.bufferedReader().readText().trim()
}

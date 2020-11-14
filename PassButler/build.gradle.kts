plugins {
    id("com.android.application")

    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.android.extensions")
    id("org.jetbrains.kotlin.kapt")

    id("de.mannodermaus.android-junit5")
}

android {
    sourceSets.all {
        java.srcDir("./src/$name/kotlin/")
    }

    compileSdkVersion(29)

    compileOptions {
        // Required minimum Java 8 for OkHttp3 used in Retrofit
        kotlinOptions.jvmTarget = "1.8"
    }

    buildFeatures {
        viewBinding = true
    }

    defaultConfig {
        applicationId = "de.passbutler.app"
        minSdkVersion(26)
        targetSdkVersion(29)
        versionCode = gitCommitCount()
        versionName = "1.0.0"

        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"

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
    val kotlinVersion = "1.3.72"
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlinVersion")

    // Kotlin Coroutines for Android
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.3.7")

    // TinyLog logger
    val tinylogVersion = "2.1.2"
    implementation("org.tinylog:tinylog-api-kotlin:$tinylogVersion")
    implementation("org.tinylog:tinylog-impl:$tinylogVersion")

    // SQLDelight
    implementation("com.squareup.sqldelight:android-driver:1.4.0")

    // Google Material theme/components
    implementation("com.google.android.material:material:1.2.0-beta01")

    // Android widgets
    implementation("androidx.appcompat:appcompat:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.1.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.constraintlayout:constraintlayout:2.0.0-beta8")

    // Android Lifecycle and ViewModel components
    val lifecycleVersion = "2.2.0"
    implementation("androidx.lifecycle:lifecycle-extensions:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVersion")

    implementation("androidx.fragment:fragment-ktx:1.2.5")

    // Since no Java 8 is used this makes no sense
    //noinspection LifecycleAnnotationProcessorWithJava8
    kapt("androidx.lifecycle:lifecycle-compiler:2.2.0")

    // Android Biometrics framework
    implementation("androidx.biometric:biometric:1.0.1")

    // Android Preference framework
    implementation("androidx.preference:preference:1.1.1")

    // JUnit 5
    val junitVersion = "5.6.2"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junitVersion")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junitVersion")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junitVersion")

    // Mockk.io
    testImplementation("io.mockk:mockk:1.10.0")
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

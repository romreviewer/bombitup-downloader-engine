plugins {
    id("com.android.library") version "8.5.2"
    id("org.jetbrains.kotlin.android") version "1.9.24"
}

android {
    namespace = "com.romreviewer.bombitup.downloader.engine"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions { jvmTarget = "11" }
}

val engineLauncherAars by configurations.creating

dependencies {
    engineLauncherAars("io.github.deniscerri.youtubedl-android:library:0.19.0") {
        isTransitive = false
    }
    engineLauncherAars("io.github.deniscerri.youtubedl-android:ffmpeg:0.19.0") {
        isTransitive = false
    }
    implementation("com.google.code.gson:gson:2.10.1")
    testImplementation("junit:junit:4.13.2")
}

val generatedJniDir = layout.buildDirectory.dir("generated/engineLaunchers/jniLibs")

val extractEngineLaunchers by tasks.registering(Sync::class) {
    from({ engineLauncherAars.files.map { zipTree(it) } }) {
        include("jni/**/libpython.so")
        include("jni/**/libffmpeg.so")
        include("jni/**/libffprobe.so")
        include("jni/**/libqjs.so")
        eachFile {
            relativePath = RelativePath(
                true,
                *relativePath.segments.drop(1).toTypedArray()
            )
        }
        includeEmptyDirs = false
    }
    into(generatedJniDir)
}

tasks.register<Copy>("copyEngineSourceAars") {
    from(engineLauncherAars)
    into(layout.buildDirectory.dir("engine-source-aars"))
    rename { name ->
        when {
            name.startsWith("library-") -> "library.aar"
            name.startsWith("ffmpeg-") -> "ffmpeg.aar"
            else -> name
        }
    }
}

android.sourceSets.getByName("main").jniLibs.srcDir(generatedJniDir)
tasks.configureEach {
    if (name.startsWith("merge") && name.endsWith("JniLibFolders")) {
        dependsOn(extractEngineLaunchers)
    }
}

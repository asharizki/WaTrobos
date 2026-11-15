import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Locale
import java.util.Properties
import kotlin.time.Duration.Companion.milliseconds

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.kspPlugin)
}

val localBuild = providers.gradleProperty("localBuild").orNull == "true"
val signingProperties = Properties().apply {
    rootProject.file("signing.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}

val gitHash: String = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().uppercase(Locale.ROOT).take(8).ifBlank { "UNKNOWN" } }.getOrElse("UNKNOWN")

android {
    namespace = "com.wmods.wppenhacer"
    //noinspection GradleDependency
    compileSdk = 37
    ndkVersion = "28.2.13676358"

    flavorDimensions += "version"

    productFlavors {
        create("whatsapp") {
            dimension = "version"
            applicationIdSuffix = if (localBuild) ".dev" else ""
            if (localBuild) resValue("string", "app_name", "WaTrobos Dev")
            isDefault = true
        }
        create("business") {
            dimension = "version"
            applicationIdSuffix = ".w4b"
            resValue("string", "app_name", "WaTrobos Business")
        }
    }

    defaultConfig {
        applicationId = "id.asharizki.watrobos"
        minSdk = 28
        //noinspection OldTargetApi
        targetSdk = 34
        versionCode = 10017
        versionName = if (localBuild) "1.0.12-dev ($gitHash)" else "1.0.12"
        multiDexEnabled = true

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        signingConfigs.create("config") {
            val androidStoreFile = project.findProperty("androidStoreFile") as String?
                ?: signingProperties.getProperty("androidStoreFile")
            if (!androidStoreFile.isNullOrEmpty()) {
                storeFile = rootProject.file(androidStoreFile)
                storePassword = project.findProperty("androidStorePassword") as String?
                    ?: signingProperties.getProperty("androidStorePassword")
                keyAlias = project.findProperty("androidKeyAlias") as String?
                    ?: signingProperties.getProperty("androidKeyAlias")
                keyPassword = project.findProperty("androidKeyPassword") as String?
                    ?: signingProperties.getProperty("androidKeyPassword")
            }
        }

        ndk {
            abiFilters.add("armeabi-v7a")
            abiFilters.add("arm64-v8a")
        }

        buildConfigField("Boolean", "RESET_ON_INSTALL", "true")
        // Set these to the WaTrobos repository when its public URL is available.
        buildConfigField("String", "RELEASES_API_URL", "\"${providers.gradleProperty("releasesApiUrl").orNull.orEmpty()}\"")
        buildConfigField("String", "RELEASES_PAGE_URL", "\"${providers.gradleProperty("releasesPageUrl").orNull.orEmpty()}\"")

    }

    packaging {
        resources {
            excludes += "META-INF/*.kotlin_module"
            excludes += "META-INF/AL2.0"
            excludes += "META-INF/LGPL2.1"
            excludes += "META-INF/LICENSE*"
            excludes += "META-INF/NOTICE*"
            excludes += "okhttp3/**"
            excludes += "kotlin/**"
            excludes += "org/**"
            excludes += "**.properties"
            excludes += "**.bin"
        }

        jniLibs {
            useLegacyPackaging = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildTypes {

        debug {
            isMinifyEnabled = project.hasProperty("minify") && project.findProperty("minify").toString().toBoolean()
            //noinspection NotShrinkingResources
            isShrinkResources = false
            signingConfig =
                if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig =
                if (signingConfigs["config"].storeFile != null) signingConfigs["config"] else signingConfigs["debug"]
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
        aidl = true
        resValues = true
    }


    lint {
        disable += "SelectedPhotoAccess"
        baseline = file("lint-baseline.xml")
    }

    androidResources.additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x64")
    // Ship only the languages the app is translated to; libraries bring ~90 others
    androidResources.localeFilters += listOf(
        "en", "ar", "de", "es", "fr", "id", "in", "it", "iw", "pt", "ru", "tr", "zh"
    )

}

androidComponents {
    onVariants { variant ->
        val appName = when (variant.flavorName) {
            "business" -> "WaTrobos-Business"
            else -> "WaTrobos"
        }
        variant.outputs.forEach { output ->
            output.outputFileName.set(output.versionName.map { "$appName-$it.apk" })
        }

        // Hotfix: YukiHookAPI's KSP generates META-INF/xposed/*, so the java resource
        // merge task must run after it or the entrypoint is missing.
        val cap = variant.name.replaceFirstChar { c ->
            if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
        }
        val kspTask = "ksp${cap}Kotlin"
        tasks.matching { it.name == "merge${cap}JavaResource" }.configureEach {
            dependsOn(kspTask)
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.media3:media3-transformer:1.8.0")
    implementation("androidx.media3:media3-effect:1.8.0")
    testImplementation("junit:junit:4.13.2")
    implementation(libs.colorpicker)
    implementation(libs.dexkit)
    implementation(libs.flatbuffers)
    compileOnly(libs.libxposed.api)
    implementation(platform(libs.yukihook.bom))
    implementation(libs.yukihook.core)
    implementation(libs.yukihook.runtime.libxposed)
    implementation(libs.yukihook.extension.kavaref)
    implementation(libs.kavaref.core)
    implementation(libs.kavaref.extension)
    ksp(libs.yukihook.compiler)
    ksp(libs.androidx.room.compiler)

    implementation(libs.core)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.documentfile)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.room.runtime)
    implementation(libs.rikkax.appcompat)
    implementation(libs.rikkax.core)
    implementation(libs.material)
    implementation(libs.rikkax.material)
    implementation(libs.rikkax.material.preference)
    implementation(libs.rikkax.widget.borderview)
    implementation(libs.jstyleparser)
    implementation(libs.okhttp)
    implementation(libs.filepicker)
    implementation(libs.betterypermissionhelper)
    implementation(libs.bcpkix.jdk18on)
    implementation(libs.arscblamer)
    implementation(libs.markwon.core)
    implementation(libs.androidx.datastore.preferences)
}


configurations.all {
    exclude("androidx.appcompat", "appcompat")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk7")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
}

tasks.configureEach {
    if (this is JavaCompile) options.encoding = "UTF-8"
    if (name.endsWith("ReleaseArtProfile")) {
        enabled = false
    }
}

interface InjectedExecOps {
    @get:Inject val execOps: ExecOperations
}


afterEvaluate {
    listOf("installWhatsappDebug", "installBusinessDebug").forEach { taskName ->
        tasks.findByName(taskName)?.doLast {
            runCatching {
                val injected = project.objects.newInstance<InjectedExecOps>()
                runBlocking {
                    delay(1000.milliseconds)
                    injected.execOps.exec {
                        commandLine(
                            "adb",
                            "shell",
                            "am",
                            "force-stop",
                            project.properties["debug_package_name"]?.toString()
                        )
                    }
                    delay(3000.milliseconds)
                    injected.execOps.exec {
                        commandLine(
                            "adb",
                            "shell",
                            "am",
                            "start",
                            "-n",
                            "$(cmd package resolve-activity --brief ${project.properties["debug_package_name"]} | tail -n 1)"
                        )
                    }
                }
            }
        }
    }
}

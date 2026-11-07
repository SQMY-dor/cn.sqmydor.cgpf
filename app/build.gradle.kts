plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "cn.sqmydor.cgpf"
    compileSdk = 37
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "cn.sqmydor.cgpf"
        minSdk = 29
        targetSdk = 37
        versionCode = 120
        versionName = "1.2.0"
    }

    signingConfigs {
        // Optional release key via env (CI). Falls back to the debug key.
        create("release") {
            val ks = System.getenv("CGPF_KEYSTORE")
            if (ks != null && file(ks).exists()) {
                storeFile = file(ks)
                storePassword = System.getenv("CGPF_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("CGPF_KEY_ALIAS")
                keyPassword = System.getenv("CGPF_KEY_PASSWORD")
            } else {
                initWith(getByName("debug"))
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs["release"]
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            merges += "META-INF/xposed/*"
            excludes += listOf(
                "kotlin/**", "DebugProbesKt.bin", "META-INF/*.version",
                "META-INF/*.kotlin_module", "META-INF/androidx/**", "META-INF/com/**",
                "META-INF/services/**", "META-INF/LICENSE*", "META-INF/NOTICE*",
            )
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    compileOnly(libs.libxposed.api)
    implementation(libs.libxposed.service)
    implementation(libs.activity.compose)
    implementation(libs.miuix.ui)
    implementation(libs.miuix.preference)
}

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("maven-publish")
}

android {
    compileSdk = 36

    defaultConfig {
        minSdk = 21
        targetSdk = 36
        multiDexEnabled = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    kotlinOptions {
        jvmTarget = "1.8"
    }

    namespace = "com.funsol.iap.billing"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.android.billingclient:billing:8.0.0")

    // Room DB
    kapt("androidx.room:room-compiler:2.7.2")
    implementation("androidx.room:room-ktx:2.7.2")
    implementation("androidx.room:room-runtime:2.7.2")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])
                groupId = "com.github.Funsol-Projects"
                artifactId = "Funsol-Billing-Helper"
                version = "v2.0.6"
            }
            create<MavenPublication>("debug") {
                from(components["debug"])
                groupId = "com.github.Funsol-Projects"
                artifactId = "Funsol-Billing-Helper"
                version = "v2.0.6"
            }
        }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Numéro de version = numéro du build GitHub (permet d'installer les mises à jour par-dessus)
val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.salatuk.adhan"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.salatuk.adhan"
        minSdk = 26
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    signingConfigs {
        create("release") {
            // Clé fixe : toutes les versions ont la même signature, donc les mises à jour s'installent par-dessus.
            storeFile = file("salatuk.jks")
            storePassword = "salatuk-adhan"
            keyAlias = "salatuk"
            keyPassword = "salatuk-adhan"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

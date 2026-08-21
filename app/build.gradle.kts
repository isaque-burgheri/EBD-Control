plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.ebd.controle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ebd.controle"
        minSdk = 26
        targetSdk = 35
        // versionCode: número que o Android usa para reconhecer atualização — sempre
        // incrementar a cada APK distribuído. versionName: o que o humano lê.
        versionCode = 2
        versionName = "3.1"
        vectorDrawables { useSupportLibrary = true }
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        // Gera a classe BuildConfig, de onde a tela de Configurações lê a versão.
        // No AGP 8 isto vem desligado por padrão.
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.activity:activity-compose:1.9.3")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.8.3")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Sincronização (HTTP). O Apps Script devolve JSON solto e o merge é feito à mão
    // com org.json, então OkHttp puro basta — Retrofit e Gson estavam declarados e
    // nunca foram usados.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Sincronização automática (agendamento em segundo plano + ciclo de vida do app)
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("androidx.lifecycle:lifecycle-process:2.8.6")

    debugImplementation("androidx.compose.ui:ui-tooling")
}

import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

/*
 * Chave de assinatura do release.
 *
 * O .jks e as senhas ficam FORA do repositorio: `keystore.properties` esta no
 * .gitignore. Sem esse arquivo o build de release sai sem assinatura em vez de
 * quebrar, para quem so compila debug nao precisar da chave.
 *
 * Assinar o release com chave propria e o que garante que a proxima versao instala
 * por cima: a chave de debug e descartavel e muda de maquina para maquina, e foi
 * isso que obrigou a desinstalar o app na virada da 3.2 para a 3.3.
 */
val arquivoChave = rootProject.file("keystore.properties")
val chaveConfigurada = arquivoChave.exists()
val propsChave = Properties().apply {
    if (chaveConfigurada) arquivoChave.inputStream().use { load(it) }
}

android {
    namespace = "com.ebd.controle"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ebd.controle"
        minSdk = 26
        targetSdk = 35
        // Fonte única da versão. Mude só esta linha a cada APK distribuído.
        val versao = "3.5"

        // versionName é o que o humano lê; versionCode é o inteiro que o Android usa
        // para reconhecer atualização e precisa sempre crescer. Calcular um a partir do
        // outro (3.3 -> 303) elimina o erro de subir o nome e esquecer o código — foi o
        // que deixou o código em 3 enquanto o nome já estava em 3.2.
        versionName = versao
        versionCode = versao.split(".").let { partes ->
            require(partes.size == 2) { "versionName deve ser MAIOR.MENOR (ex.: 3.3), veio \"$versao\"" }
            partes[0].toInt() * 100 + partes[1].toInt()
        }
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (chaveConfigurada) {
            create("release") {
                storeFile = file(propsChave.getProperty("storeFile"))
                storePassword = propsChave.getProperty("storePassword")
                keyAlias = propsChave.getProperty("keyAlias")
                keyPassword = propsChave.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            if (chaveConfigurada) signingConfig = signingConfigs.getByName("release")
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
    // SavedStateHandle: a aba de pontos guarda nele o rascunho de marcações, que
    // precisa sobreviver à rotação e à troca de item na barra inferior.
    implementation("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.8.6")
    // collectAsStateWithLifecycle: para a coleta pausar no onStop em vez de
    // manter os Flows do Room ativos com o app em segundo plano.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
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

import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

fun resolvedSetting(environmentName: String, propertyName: String): String =
    providers.environmentVariable(environmentName)
        .orElse(providers.gradleProperty(propertyName))
        .orNull
        ?.trim()
        .orEmpty()

val developmentKeystoreSource = rootProject.file("signing/school-development.jks.b64")
val developmentKeystore = rootProject.file("build/signing/school-development.jks")
val updatePublicKeySource = rootProject.file("signing/school-update-development-public.der.b64")
val developmentCertificateSource = rootProject.file("signing/school-development.cert.sha256")

check(developmentKeystoreSource.isFile) {
    "缺少固定开发签名源文件：${developmentKeystoreSource.path}"
}
check(updatePublicKeySource.isFile) {
    "缺少更新清单验证公钥：${updatePublicKeySource.path}"
}
check(developmentCertificateSource.isFile) {
    "缺少固定开发签名证书指纹：${developmentCertificateSource.path}"
}

if (!developmentKeystore.isFile || developmentKeystore.length() == 0L) {
    developmentKeystore.parentFile.mkdirs()
    val encoded = developmentKeystoreSource.readText(Charsets.UTF_8).filterNot(Char::isWhitespace)
    developmentKeystore.writeBytes(Base64.getDecoder().decode(encoded))
}

val resolvedVersionCode = providers.environmentVariable("SCHOOL_VERSION_CODE")
    .orNull
    ?.toIntOrNull()
    ?: 26
val resolvedVersionName = providers.environmentVariable("SCHOOL_VERSION_NAME")
    .orNull
    ?.takeIf(String::isNotBlank)
    ?: "0.22.0"
val updatePublicKey = updatePublicKeySource.readText(Charsets.UTF_8).filterNot(Char::isWhitespace)
val developmentCertificate = developmentCertificateSource.readText(Charsets.UTF_8)
    .lowercase()
    .filter(Char::isLetterOrDigit)

val firebaseProjectId = resolvedSetting("SCHOOL_FIREBASE_PROJECT_ID", "schoolFirebaseProjectId")
val firebaseApplicationId = resolvedSetting("SCHOOL_FIREBASE_APPLICATION_ID", "schoolFirebaseApplicationId")
val firebaseApiKey = resolvedSetting("SCHOOL_FIREBASE_API_KEY", "schoolFirebaseApiKey")
val firebaseSenderId = resolvedSetting("SCHOOL_FIREBASE_SENDER_ID", "schoolFirebaseSenderId")
val firebaseUpdateTopic = resolvedSetting("SCHOOL_FIREBASE_UPDATE_TOPIC", "schoolFirebaseUpdateTopic")
    .ifBlank { "school_dev_update" }
val courseManifestUrl = resolvedSetting("SCHOOL_COURSE_MANIFEST_URL", "schoolCourseManifestUrl")
    .ifBlank {
        "https://github.com/MajorTomMan/School/releases/download/course-latest/manifest.json"
    }
val updatePushEnabled = listOf(
    firebaseProjectId,
    firebaseApplicationId,
    firebaseApiKey,
    firebaseSenderId,
).all(String::isNotBlank)

android {
    namespace = "com.majortomman.school"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.majortomman.school"
        minSdk = 26
        targetSdk = 36
        versionCode = resolvedVersionCode
        versionName = resolvedVersionName
        buildConfigField(
            "String",
            "UPDATE_MANIFEST_URL",
            "https://github.com/MajorTomMan/school/releases/download/dev-latest/update-manifest.json".asBuildConfigString(),
        )
        buildConfigField(
            "String",
            "UPDATE_SIGNATURE_URL",
            "https://github.com/MajorTomMan/school/releases/download/dev-latest/update-manifest.sig".asBuildConfigString(),
        )
        buildConfigField("String", "COURSE_MANIFEST_URL", courseManifestUrl.asBuildConfigString())
        buildConfigField("String", "UPDATE_PUBLIC_KEY_BASE64", updatePublicKey.asBuildConfigString())
        buildConfigField("String", "DEVELOPMENT_CERT_SHA256", developmentCertificate.asBuildConfigString())
        buildConfigField("boolean", "UPDATE_PUSH_ENABLED", updatePushEnabled.toString())
        buildConfigField("String", "FCM_PROJECT_ID", firebaseProjectId.asBuildConfigString())
        buildConfigField("String", "FCM_APPLICATION_ID", firebaseApplicationId.asBuildConfigString())
        buildConfigField("String", "FCM_API_KEY", firebaseApiKey.asBuildConfigString())
        buildConfigField("String", "FCM_SENDER_ID", firebaseSenderId.asBuildConfigString())
        buildConfigField("String", "FCM_UPDATE_TOPIC", firebaseUpdateTopic.asBuildConfigString())

        if (updatePushEnabled) {
            // FirebaseInitProvider reads these resources when Google Play services starts the process
            // for a background data message. No google-services.json is stored in the repository.
            resValue("string", "google_app_id", firebaseApplicationId)
            resValue("string", "google_api_key", firebaseApiKey)
            resValue("string", "gcm_defaultSenderId", firebaseSenderId)
            resValue("string", "project_id", firebaseProjectId)
        }
    }

    signingConfigs {
        create("schoolDevelopment") {
            // 仅用于公开开发版和预览版，保证本地、PR 与主分支 APK 可相互覆盖安装。
            storeFile = developmentKeystore
            storePassword = "android"
            keyAlias = "schooldev"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("schoolDevelopment")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        // Gradle 与单元测试使用 Java 21；Android 产物继续保持 Java 17 字节码基线。
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.15.0")
    val roomVersion = "2.8.4"

    implementation(composeBom)
    implementation(firebaseBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.work:work-runtime-ktx:2.11.2")
    implementation("com.google.firebase:firebase-messaging")

    // 最新 Lucene Kuromoji 负责日语形态素切分、词性、原形、读音和活用信息。
    implementation("org.apache.lucene:lucene-analysis-kuromoji:10.5.0")

    ksp("androidx.room:room-compiler:$roomVersion")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    debugImplementation("androidx.compose.ui:ui-tooling")
}

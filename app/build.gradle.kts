import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/*
 * 后端地址不再硬编码在 Kotlin 源码里。
 *
 * - 默认值 = 模拟器访问宿主机的固定地址（10.0.2.2）；
 * - 真机调试用命令行参数覆盖，不改代码：
 *     ./gradlew :app:assembleDebug -PapiBaseUrl=http://192.168.1.10:3000/
 * - 也可以写进根目录的 gradle.properties（apiBaseUrl=...）长期生效。
 */
val apiBaseUrl: String = (project.findProperty("apiBaseUrl") as String?) ?: "http://10.0.2.2:3000/"

android {
    namespace = "com.zhuhai.medicine"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.zhuhai.medicine"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // 后端站点根地址（带尾斜杠，Retrofit 要求 baseUrl 以 / 结尾）
        buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
    }

    signingConfigs {
        create("release") {
            /*
             * 正式签名信息放在仓库根目录的 keystore.properties（已 gitignore）：
             *   storeFile=../keystore.jks
             *   storePassword=...
             *   keyAlias=...
             *   keyPassword=...
             * 文件不存在时下面会自动退回 debug 签名。
             */
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties()
                propsFile.inputStream().use { input -> props.load(input) }
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            /*
             * 没有配置正式签名时退回 debug 签名。
             * 否则产物是 app-release-unsigned.apk —— 装不上、也交不出去，
             * 「release 能不能跑」这种问题在面试现场才暴露就晚了。
             */
            signingConfig = if (rootProject.file("keystore.properties").exists()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }

            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // 生成 BuildConfig，ApiClient 里用 BuildConfig.DEBUG 区分 debug / release
        buildConfig = true
    }

    testOptions {
        unitTests {
            // JVM 单元测试里 android.util.Log 等框架方法没有实现，
            // 默认直接抛 "Method i in android.util.Log not mocked"。
            // 打开这个开关后它们返回默认值（Log 返回 0），业务代码可以照常打日志。
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // 网络层
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    // ViewModel 的分页逻辑跑在 viewModelScope（Dispatchers.Main）里，
    // JVM 单测要用 Dispatchers.setMain 替换主线程调度器
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
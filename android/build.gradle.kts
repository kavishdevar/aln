plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.crowdin.platform:gradle-plugin:1.3.0")
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

tasks.register("crowdinUpload") {
    doLast {
        exec {
            commandLine("crowdin", "upload", "sources")
        }
    }
}

tasks.register("crowdinDownload") {
    doLast {
        exec {
            commandLine("crowdin", "download")
        }
    }
}

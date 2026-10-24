plugins {
    id("com.android.application") version "9.0.1" apply false
    // AGP 9.0.1에 내장된 Kotlin(2.2.10)과 같은 버전이어야 한다
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}

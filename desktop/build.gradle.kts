import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.junrar)
}

compose.desktop {
    application {
        mainClass = "ir.comicreader.fa.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ComicKhan"
            packageVersion = "1.0.0"
            description = "خوانندهٔ کمیک (CBZ/ZIP/CBR/RAR)"
        }
    }
}

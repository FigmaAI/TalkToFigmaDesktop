import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JvmVendorSpec

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    id("org.openjfx.javafxplugin") version "0.0.14"
}

javafx {
    // JavaFX version - explicitly set to avoid using project version
    version = "21.0.2"  // Use specific version for better compatibility
    modules("javafx.controls", "javafx.media", "javafx.swing")
    // Liberica Full JDK is used, so JavaFX is built-in
    configuration = "compileOnly"
}

group = "kr.co.metadata.mcp"
version = "1.0.5" // App version

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.BELLSOFT)
    }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
        freeCompilerArgs.add("-opt-in=kotlin.RequiresOptIn")
    }
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.java-websocket:Java-WebSocket:1.5.3")
    implementation("com.typesafe:config:1.4.2")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.15.2")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // MCP Kotlin SDK (includes Ktor dependencies)
    implementation("io.modelcontextprotocol:kotlin-sdk:0.5.0")
    
    // JSON serialization (updated to latest stable version)
    implementation(libs.kotlin.serialization.json)
    
    // Coroutines (updated to latest stable version)
    implementation(libs.kotlinx.coroutines.core)
    
    // Logging with color and JSON support
    implementation("io.github.microutils:kotlin-logging:2.1.23")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    // removed jansi library completely (solved signing issue)
    implementation("net.logstash.logback:logstash-logback-encoder:7.4") // JSON logging support
    
    // HTTP client for analytics
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    
    // JSON support for analytics
    implementation("org.json:json:20231013")

    // Test Dependencies
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
}

tasks.processResources {
    val propertiesFile = File(sourceSets.main.get().resources.srcDirs.first(), "version.properties")
    propertiesFile.parentFile.mkdirs()
    propertiesFile.writeText("version=${project.version}")
}

tasks.test {
    useJUnitPlatform()
}

compose.desktop {
    application {
        mainClass = "kr.co.metadata.mcp.AppKt"
        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Pkg, // For App Store
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Exe
            )
            packageName = "Cursor Talk to Figma desktop"
            packageVersion = project.version.toString()
            
            // Include JRE for self-contained application
            includeAllModules = true

            macOS {
                bundleID = "kr.co.metadata.mcp.talktofigma"
                dockName = "Cursor Talk to Figma desktop"
                iconFile.set(project.file("src/main/resources/icon.icns"))
                
                // Fix LSApplicationCategoryType issue
                appCategory = "public.app-category.developer-tools"
                
                // Set minimum macOS version to 12.0 to allow arm64-only builds
                minimumSystemVersion = "12.0"

                buildTypes.release.proguard {
                    obfuscate.set(false)
                    configurationFiles.from(project.file("proguard-rules.pro"))
                }
                
                // Optimized JVM arguments
                jvmArgs(
                    "-Dapple.awt.enableTemplateImages=true",
                    "-Xms64m",
                    "-Xmx512m",
                    "-XX:+UseG1GC",
                    "-XX:+UseStringDeduplication"
                )

                // Add required Info.plist key for export compliance
                infoPlist {
                    "ITSAppUsesNonExemptEncryption" to false
                }

                // 공통 entitlements 파일 사용 (App Store와 Developer ID 모두 호환)
                entitlementsFile.set(project.rootProject.file("entitlements.plist"))
                
                if (System.getenv("BUILD_FOR_APP_STORE") == "true") {
                    // App Store build setting
                    println("Configuring for App Store distribution... manual signing")
                    signing {
                        sign.set(false)
                    }
                
                } else {
                    // Developer ID build setting (default)
                    val signingEnabled = project.properties["signingEnabled"]?.toString()?.toBoolean() ?: true
                    println("Configuring for Developer ID distribution (Signing enabled: $signingEnabled)")

                    if (signingEnabled) {
                        signing {
                            sign.set(true)
                            identity.set(System.getenv("SIGNING_IDENTITY") ?: "Developer ID Application: JooHyung Park (ZQC7QNZ4J8)")
                        }
                        
                        // Developer ID distribution requires Notarization
                        notarization {
                            appleID.set(System.getenv("APPLE_ID"))
                            password.set(System.getenv("APPLE_PASSWORD")) // App-Specific Password
                            teamID.set(System.getenv("APPLE_TEAM_ID"))
                        }
                    }
                }
            }

            windows {
                menuGroup = "Cursor Talk to Figma desktop"
                upgradeUuid = "FCDFDD35-04EB-4698-89F5-3CCAB516B324"
                iconFile.set(project.file("src/main/resources/icon.ico"))
                // Console app (set to false to hide console window)
                console = false
            }
            
            // Linux is not supported
        }
    }
}

tasks.register("printVersion") {
    // Store version during configuration phase
    val versionValue = project.version.toString()
    
    doLast {
        println(versionValue)
    }
}
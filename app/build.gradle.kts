plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
}

group = "kr.co.metadata.mcp"
version = "1.0.0" // App version

kotlin {
    jvmToolchain(21)
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
    implementation("org.fusesource.jansi:jansi:2.4.0") // ANSI color support
    implementation("net.logstash.logback:logstash-logback-encoder:7.4") // JSON logging support

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
            packageName = "TalkToFigmaDesktop"
            packageVersion = project.version.toString()
            
            // Include JRE for self-contained application
            includeAllModules = true

            macOS {
                bundleID = "kr.co.metadata.mcp.talktofigma"
                dockName = "TalkToFigma Desktop"
                iconFile.set(project.file("src/main/resources/icon.icns"))
                jvmArgs("-Dapple.awt.enableTemplateImages=true")

                signing {
                    sign.set(true)
                    identity.set(System.getenv("SIGNING_IDENTITY"))
                }

                if (System.getenv("BUILD_FOR_APP_STORE") == "true") {
                    // App Store용 프로비저닝 프로필 설정 (프로젝트 루트에 위치해야 함)
                    provisioningProfile.set(project.rootProject.file("TalkToFigma_App_Store.provisionprofile"))
                    runtimeProvisioningProfile.set(project.rootProject.file("TalkToFigma_App_Store.provisionprofile"))

                    // App Store용 entitlements 파일 설정 (프로젝트 루트에 위치해야 함)
                    entitlementsFile.set(project.rootProject.file("entitlements-appstore.plist"))
                } else {
                    // Developer ID 배포용 entitlements 파일 설정 (프로젝트 루트에 위치해야 함)
                    entitlementsFile.set(project.rootProject.file("entitlements.plist"))

                    // Developer ID 배포 시에만 Notarization 설정 필요
                    notarization {
                        appleID.set(System.getenv("APPLE_ID"))
                        password.set(System.getenv("APPLE_PASSWORD")) // App-Specific Password
                        teamID.set(System.getenv("APPLE_TEAM_ID"))
                    }
                }
            }

            windows {
                menuGroup = "TalkToFigmaDesktop"
                upgradeUuid = "FCDFDD35-04EB-4698-89F5-3CCAB516B324"
                iconFile.set(project.file("src/main/resources/icon.ico"))
                // Console app (set to false to hide console window)
                console = false
            }
            
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
            }
        }
    }
}

tasks.register("printVersion") {
    doLast {
        println(project.version)
    }
}
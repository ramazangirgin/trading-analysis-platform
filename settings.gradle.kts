pluginManagement {
    includeBuild("build-logic")
    repositories {
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        // Node.js distributions for :frontend (node-gradle plugin). Declared here because
        // FAIL_ON_PROJECT_REPOS forbids the plugin from adding it itself.
        ivy {
            name = "Node.js"
            setUrl("https://nodejs.org/dist/")
            patternLayout {
                artifact("v[revision]/[artifact](-v[revision]-[classifier]).[ext]")
            }
            metadataSources {
                artifact()
            }
            content {
                includeModule("org.nodejs", "node")
            }
        }
    }
}

rootProject.name = "trading-analysis-platform"

include(
    ":backend",
    ":backend:bff:api",
    ":backend:bff:impl",
    ":backend:orchestration",
    ":backend:domain:analysis:core",
    ":backend:domain:analysis:adapter",
    ":backend:domain:report:core",
    ":backend:domain:report:adapter",
    ":backend:domain:catalog:core",
    ":backend:domain:catalog:adapter",
    ":backend:domain:settings:core",
    ":backend:domain:settings:adapter",
    ":frontend",
)

// Deployable artifacts live under artifact/, while project paths stay short:
// :backend:bff:api -> artifact/backend/bff/api, :frontend -> artifact/frontend.
// artifact/ta-runner is a separate Python build and is not part of this one.
fun placeUnderArtifact(project: ProjectDescriptor) {
    project.projectDir = file("artifact" + project.path.replace(':', '/'))
    project.children.forEach(::placeUnderArtifact)
}
rootProject.children.forEach(::placeUnderArtifact)

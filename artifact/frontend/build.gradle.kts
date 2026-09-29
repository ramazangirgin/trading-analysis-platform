import com.github.gradle.node.pnpm.task.PnpmTask

plugins {
    base
    alias(libs.plugins.node)
}

description = "Vue 3 frontend, bundled into the backend jar as static resources"

node {
    // Node and pnpm are downloaded into the build: no global installation needed.
    download.set(true)
    // The Node.js repository is declared in settings.gradle.kts.
    distBaseUrl.set(null as String?)
    version.set(libs.versions.node)
    pnpmVersion.set(libs.versions.pnpm)
}

val sources = listOf("src", "public", "index.html", "package.json", "pnpm-lock.yaml",
    "vite.config.ts", "tsconfig.json", "tsconfig.node.json", "env.d.ts")

val pnpmBuild = tasks.register<PnpmTask>("pnpmBuild") {
    description = "Type-checks and builds the frontend into dist/"
    dependsOn(tasks.pnpmInstall)
    args.set(listOf("run", "build"))
    inputs.files(sources)
    outputs.dir("dist")
}

val pnpmLint = tasks.register<PnpmTask>("pnpmLint") {
    description = "Runs ESLint and Prettier checks"
    dependsOn(tasks.pnpmInstall)
    args.set(listOf("run", "lint"))
    inputs.files(sources + listOf("eslint.config.js", ".prettierrc.json", ".prettierignore"))
    outputs.upToDateWhen { true }
}

val pnpmTest = tasks.register<PnpmTask>("pnpmTest") {
    description = "Runs the Vitest suite"
    dependsOn(tasks.pnpmInstall)
    args.set(listOf("run", "test"))
    inputs.files(sources)
    outputs.upToDateWhen { true }
}

// Vite dev server with hot reload; proxies /api to bootRun (see vite.config.ts). Runs until stopped.
tasks.register<PnpmTask>("pnpmDev") {
    description = "Starts the Vite dev server on http://localhost:5173"
    dependsOn(tasks.pnpmInstall)
    args.set(listOf("run", "dev"))
}

tasks.assemble { dependsOn(pnpmBuild) }
tasks.check { dependsOn(pnpmLint, pnpmTest) }
tasks.clean { delete("dist") }

// Consumed by :backend, which copies it into classpath:/static.
val dist = configurations.create("dist") {
    isCanBeConsumed = true
    isCanBeResolved = false
}

artifacts {
    add(dist.name, layout.projectDirectory.dir("dist")) {
        builtBy(pnpmBuild)
    }
}

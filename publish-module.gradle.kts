apply(plugin = "maven-publish")
apply(plugin = "signing")

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets["main"].java.srcDirs)
    from(sourceSets["main"].kotlin.srcDirs)
}

val javadocJar by tasks.registering(Jar::class) {
    from(tasks.named("javadoc"))
    archiveClassifier.set("javadoc")
}

val publishGroupId: String by project
val publishArtifactId: String by project
val publishArtifactName: String by project
val publishVersion: String by project

group = publishGroupId
version = publishVersion

afterEvaluate {
    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("release") {
                groupId = publishGroupId
                artifactId = publishArtifactId
                version = publishVersion

                from(components["java"])

                artifact(sourcesJar)
                artifact(javadocJar)

                pom {
                    name.set(publishArtifactName)
                    description.set("ADB Relay JVM - relay ADB connections over network.")
                    url.set("https://github.com/cuongnv126/adb-relay-jvm")
                    licenses {
                        license {
                            name.set("Apache License 2.0")
                            url.set("https://github.com/cuongnv126/adb-relay-jvm/blob/main/LICENSE")
                        }
                    }
                    developers {
                        developer {
                            id.set("cuongnv")
                            name.set("Cuong V. Nguyen")
                            email.set("cuongnv126@gmail.com")
                        }
                    }
                    scm {
                        connection.set("scm:git:github.com/cuongnv126/adb-relay-jvm.git")
                        developerConnection.set("scm:git:ssh://github.com/cuongnv126/adb-relay-jvm.git")
                        url.set("https://github.com/cuongnv126/adb-relay-jvm/tree/main")
                    }
                }
            }
        }
    }

    configure<SigningExtension> {
        useInMemoryPgpKeys(
            rootProject.ext["signing.keyId"].toString(),
            rootProject.ext["signing.key"].toString(),
            rootProject.ext["signing.password"].toString(),
        )
        sign(extensions.getByType<PublishingExtension>().publications)
    }
}

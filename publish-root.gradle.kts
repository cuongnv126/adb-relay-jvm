ext["sonatypeStagingProfileId"] = ""
ext["ossrhUsername"] = ""
ext["ossrhPassword"] = ""
ext["signing.keyId"] = ""
ext["signing.password"] = ""
ext["signing.key"] = ""

val secretPropsFile = rootProject.file("local.properties")
if (secretPropsFile.exists()) {
    val p = java.util.Properties()
    secretPropsFile.inputStream().use { p.load(it) }
    p.forEach { name, value -> ext[name.toString()] = value }
} else {
    ext["ossrhUsername"] = System.getenv("OSSRH_USERNAME")
    ext["ossrhPassword"] = System.getenv("OSSRH_PASSWORD")
    ext["sonatypeStagingProfileId"] = System.getenv("SONATYPE_STAGING_PROFILE_ID")
    ext["signing.keyId"] = System.getenv("SIGNING_KEY_ID")
    ext["signing.password"] = System.getenv("SIGNING_PASSWORD")
    ext["signing.key"] = System.getenv("SIGNING_KEY")
}

nexusPublishing {
    repositories {
        sonatype {
            stagingProfileId.set(rootProject.ext["sonatypeStagingProfileId"].toString())
            username.set(rootProject.ext["ossrhUsername"].toString())
            password.set(rootProject.ext["ossrhPassword"].toString())
            nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
            snapshotRepositoryUrl.set(uri("https://s01.oss.sonatype.org/content/repositories/snapshots/"))
        }
    }
}

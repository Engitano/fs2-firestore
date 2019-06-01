import sbt.configs

val majorVersion = SettingKey[String]("major version")
val minorVersion = SettingKey[String]("minor version")
val patchVersion = SettingKey[Option[String]]("patch version")

Global / majorVersion := "0"
Global / minorVersion := "1"
Global / patchVersion := Some("0")

val writeVersion = taskKey[Unit]("Writes the version to version.txt")
writeVersion := {
  IO.write(baseDirectory.value / "version.txt", (`fs2-firestore`  / version).value)
}

lazy val `fs2-firestore` = (project in file("."))
  .configs(IntegrationTest)
  .settings(
    Common(),
    name := "fs2-firestore",
    version := s"${majorVersion.value}.${minorVersion.value}${patchVersion.value.fold("")(p => s".$p")}",
    resolvers ++= Dependencies.resolvers(),
    libraryDependencies ++= Dependencies(),
    bintrayOrganization := Some("engitano"),
    bintrayPackageLabels := Seq("firestore", "fs2"),
    coverageExcludedPackages := "<empty>;com.google.*;",
    Defaults.itSettings ++ headerSettings(IntegrationTest) ++ automateHeaderSettings(IntegrationTest),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"),
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full),
    parallelExecution in IntegrationTest := false,
  )

addCommandAlias("fullBuild",";clean;coverage;test;it:test;coverageReport")
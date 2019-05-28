import sbt.configs

val majorVersion = SettingKey[String]("major version")
val minorVersion = SettingKey[String]("minor version")
val patchVersion = SettingKey[Option[String]]("patch version")

Global / majorVersion := "0"
Global / minorVersion := "1"
Global / patchVersion := Some("0")

val writeVersion = taskKey[Unit]("Writes the version to version.txt")
writeVersion := {
  IO.write((baseDirectory.value / "version.txt"), (`fs2-firestore`  / version).value)
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
    coverageMinimum := 90.0,
    coverageFailOnMinimum := true,
    Defaults.itSettings ++ headerSettings(IntegrationTest) ++ automateHeaderSettings(IntegrationTest),
    addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8"),
      PB.targets in Compile := Seq(
          scalapb.gen(flatPackage = true) -> (sourceManaged in Compile).value
      ),
    Compile / PB.includePaths := Seq(
      target.value / "protobuf_external",
      baseDirectory.value / "googleapis",
    ),
    Compile / PB.protoSources := Seq(
      baseDirectory.value / "googleapis" / "google" / "firestore" / "v1",
      baseDirectory.value / "googleapis" / "google" / "firestore" / "admin" / "v1"
    ),
    scalapbCodeGeneratorOptions += CodeGeneratorOption.FlatPackage,
    Compile / PB.targets := scalapbCodeGenerators.value,
    addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
.enablePlugins(Fs2Grpc)

addCommandAlias("fullBuild",";clean;coverage;test;it:test;coverageReport")
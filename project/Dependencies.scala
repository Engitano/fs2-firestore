import sbt._

object Dependencies {

  def resolvers(): Seq[MavenRepository] = Seq(
    Resolver.sonatypeRepo("releases"),
    Resolver.bintrayRepo("engitano", "maven")
  )

  def apply(): Seq[ModuleID] = Seq(
    "co.fs2"               %% "fs2-core"                        % "1.0.2",
    "org.typelevel"        %% "cats-effect"                     % "1.1.0",
    "com.thesamet.scalapb" %% "scalapb-runtime-grpc"            % scalapb.compiler.Version.scalapbVersion exclude ("com.google.api.grpc", "proto-google-common-protos"),
    "com.thesamet.scalapb" %% "scalapb-runtime"                 % scalapb.compiler.Version.scalapbVersion % "protobuf",
    "com.chuusai"          %% "shapeless"                       % "2.3.3",
    "com.engitano"         %% "google-common-protos-scala"      % "0.1.8",
    "io.grpc"              % "grpc-auth"                        % "1.20.0",
    "com.google.auth"      % "google-auth-library-oauth2-http"  % "0.12.0",
    "io.grpc"              % "grpc-netty-shaded"                % "1.20.0" % "it",
    "org.scalatest"        %% "scalatest"                       % "3.0.5" % "test, it",
    "com.whisk"            %% "docker-testkit-scalatest"        % "0.9.8" % "it",
    "com.whisk"            %% "docker-testkit-impl-spotify"     % "0.9.8" % "it",
    "com.whisk"            %% "docker-testkit-impl-docker-java" % "0.9.8" % "it"
  )
}

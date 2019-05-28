import sbt.Keys._
import sbt.URL

object Common {
  def apply() = Seq(
    scalaVersion := "2.12.8",
    organization := "com.engitano",
    organizationName := "Engitano",
    startYear := Some(2019),
    licenses += ("MIT", new URL("http://opensource.org/licenses/MIT"))

  )
  
}

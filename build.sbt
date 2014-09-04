name := "akka-mapreduce"

organization := "com.akrivos.code"

version := "0.0.1"

scalaVersion := "2.11.2"

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8", "-target:jvm-1.7")

resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies ++= {
  val akkaV = "2.3.5"
  Seq(
    "com.typesafe.akka" %% "akka-actor"   % akkaV,
    "com.typesafe.akka" %% "akka-testkit" % akkaV   % Test,
    "com.typesafe.akka" %% "akka-slf4j"   % akkaV,
    "org.scalatest"     %% "scalatest"    % "2.2.1" % Test
  )
}

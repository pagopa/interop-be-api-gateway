import scala.sys.process._
import scala.util.matching.Regex

/**
 * It computes the project version starting from git repository information
 * A release branch should be in the form MAJOR.MINOR.x and the resulting version is computed in the following way:
 *   1) if there are no tags associated to this branch in the form vMAJOR.MINOR.BUILD the resulting version is MAJOR.MINOR.1-SNAPSHOT
 *   2) if there are tags then the BUILD number is computed as the max among all the tags build numbers incremented by one and
 *      the resulting version is MAJOR.MINOR.COMPUTEDBUILD-SNAPSHOT
 * A tag is expected to be in the form vMAJOR.MINOR.BUILD and the resulting version is MAJOR.MINOR.BUILD
 * Otherwise the resulting version is 0.0.0
 */
object ComputeVersion {

  val gitOutput: String = ("git symbolic-ref -q --short HEAD" #|| "git describe --tags --exact-match" #|| "git rev-parse --short HEAD").lineStream_!.head

  val releaseBranch: Regex = "([0-9]\\d*)\\.(\\d+)\\.([x])".r

  val tag: Regex = "v([0-9]\\d*)\\.(\\d+)\\.(\\d+)".r

  lazy val version: String =  gitOutput match {
    case tag(major, minor, build) =>
      s"$major.$minor.$build"
    case releaseBranch(major, minor, _*) =>
      val out = s"""git tag --list v$major.$minor*""".lineStream_!.toList
      if (out.isEmpty)
        s"$major.$minor.1-SNAPSHOT"
      else {
        val lastBuildVersion = out.map(_.split("\\.")(2)).map(_.toInt).foldLeft(0)(Math.max)
        s"$major.$minor.${(lastBuildVersion + 1).toString}-SNAPSHOT"
      }
    case _ =>
      "0.0.0"
  }
}

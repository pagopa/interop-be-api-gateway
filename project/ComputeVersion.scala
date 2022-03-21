import scala.sys.process._
import scala.util.matching.Regex

/** Computes the project version using git repository info:
  * - checked out on a tag with a name like $major.$minor.$patch | version = ${major}.${minor}.${patch}
  * - on the HEAD of a branch                                    | version = ${branchName}-SNAPSHOT
  * - else                                                       | version = ${shortCommitHash}-SNAPSHOT
  */
object ComputeVersion {

  private val branchNameIfHead: String = "git symbolic-ref -q --short HEAD" // same as git branch --show-current
  private val thisCommitTag: String    = "git describe --tags --exact-match"
  private val shortCommitHash: String  = "git rev-parse --short HEAD"
  private val gitOutput: String        = (thisCommitTag #|| branchNameIfHead #|| shortCommitHash).lineStream_!.head

  val tag: Regex = "^([0-9]+)\\.([0-9]+)\\.([0-9]+)$".r

  val version: String = gitOutput match {
    case tag(major, minor, patch) => s"$major.$minor.$patch"
    case x                        => s"$x-SNAPSHOT"
  }

}

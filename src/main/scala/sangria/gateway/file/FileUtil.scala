package sangria.gateway.file

import better.files.File

object FileUtil {
  def loadFiles(files: Seq[File], globs: Seq[String]) = {
    val foundFiles = files.flatMap(file ⇒ globs.flatMap(glob ⇒ file.glob(glob, includePath = false))).toSet.toVector.sortBy((file: File) ⇒ file.name)

    foundFiles.filterNot(_.isDirectory).map(f ⇒ f → f.lines.mkString("\n"))
  }
}

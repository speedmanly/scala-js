/* Scala.js compiler
 * Copyright 2013 LAMP/EPFL
 * @author  Sébastien Doeraene
 */

package scala.scalajs.compiler

import scala.tools.nsc._
import scala.tools.nsc.io.AbstractFile
import scala.reflect.internal.pickling.PickleBuffer

import java.io.{ File, PrintWriter, BufferedOutputStream, FileOutputStream }

/** Send JS ASTs to files
 *
 *  @author Sébastien Doeraene
 */
trait GenJSFiles extends SubComponent { self: GenJSCode =>
  import global._
  import jsAddons._

  def genJSFile(cunit: CompilationUnit, sym: Symbol, tree: js.Tree) {
    val outfile = getUniqueFileFor(cunit, sym, ".js", true)
    val output = bufferedPrintWriter(outfile)
    var sourceMapFile: File = null
    var sourceMapOutput: PrintWriter = null
    try {
      val printer =
        if (true) { // TODO Some option
          // With source map
          sourceMapFile = getUniqueFileFor(cunit, sym, ".js.map", true)
          sourceMapOutput = bufferedPrintWriter(sourceMapFile)
          new JSTreePrinterWithSourceMap(output, sourceMapOutput,
              outfile.getName)
        } else {
          // Without source map
          new JSTreePrinter(output)
        }

      printer.printTopLevelTree(tree)

      if (sourceMapFile ne null) {
        printer.print(s"//@ sourceMappingURL=${sourceMapFile.getName}")
        printer.println()
      }

      printer.close()
    } finally {
      output.close()
      if (sourceMapOutput ne null)
        sourceMapOutput.close()
    }
  }

  private def bufferedPrintWriter(file: File) =
    new PrintWriter(new BufferedOutputStream(new FileOutputStream(file)))

  private def getUniqueFileFor(cunit: CompilationUnit, sym: Symbol,
      suffix: String, withOrderingPrefix: Boolean) = {
    val file = getFileFor(cunit, sym, suffix, withOrderingPrefix)

    if (withOrderingPrefix && !file.exists) {
      /* Remove files with the same name but a different ordering prefix.
       * This needs to be done only when the file does not exist. If it exists,
       * then all the files to be deleted now were deleted when it was created.
       */
      val otherFiles = file.getParentFile.listFiles()
      if (otherFiles ne null) {
        for (otherFile <- otherFiles if sameNameUpToPrefix(otherFile, file)) {
          otherFile.delete()
        }
      }
    }

    file
  }

  private def getFileFor(cunit: CompilationUnit, sym: Symbol,
      suffix: String, withOrderingPrefix: Boolean) = {
    val baseDir: AbstractFile =
      settings.outputDirs.outputDirFor(cunit.source.file)

    val pathParts = sym.fullName.split("[./]")
    val dir = (baseDir /: pathParts.init)(_.subdirectoryNamed(_))

    var filename = pathParts.last
    if (withOrderingPrefix) {
      filename = getOrderingPrefixFor(sym) + filename
      if (sym.isModuleClass && !sym.isImplClass)
        filename = filename + nme.MODULE_SUFFIX_STRING
    }

    new File(dir.file, filename + suffix)
  }

  private def getOrderingPrefixFor(sym: Symbol): String = {
    val ordering = sym.ancestors.count(!_.isInterface) + 1
    "%04d-" format ordering
  }

  private def sameNameUpToPrefix(lhs: File, rhs: File) = {
    // Assume rhs is a name with a prefix
    val lhsName = lhs.getName
    val rhsName = rhs.getName
    (lhsName.length > 4 &&
        lhsName.substring(4) == rhsName.substring(4) &&
        lhsName.substring(0, 4).forall(Character.isDigit))
  }
}

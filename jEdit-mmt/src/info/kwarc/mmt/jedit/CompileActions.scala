package info.kwarc.mmt.jedit

import org.gjt.sp.jedit._

import errorlist._

import info.kwarc.mmt.api._
import archives.source
import utils._
import utils.FileConversion._

/** This class is factored out from MMTPlugin to structure the code better.
 * It collects all compilation-related functionality. */
class CompileActions(mmtplugin: MMTPlugin) {
   private val errorSource = mmtplugin.errorSource
   private val controller = mmtplugin.controller
   private def log(msg: String) {controller.report("jedit-compile", msg)}
   
   /** compiles a path (may be a directory) in an archive */
   def compile(archive: String, path: List[String]) {
      log("compile" + " " + archive + " " + path.mkString("","/",""))
      val arch = controller.backend.getArchive(archive).getOrElse(return)
      // call build method on the respective archive
      controller.handle(frontend.ArchiveBuild(archive, "mmt-omdoc", archives.Build, path, Nil))
   }
   /** compiles a buffer or directory */
   def compile(file: String) {
      controller.backend.getArchives find {a => file.startsWith((a/source).toString)} match {
         case None =>
           log("not compiling buffer/directory " + file)
         case Some(a) =>
           log("compiling buffer/directory " + file)
           val path = File(file.substring(a.root.toString.length + 8)).segments
           compile(a.id, path)
      }
   }
   def compileCurrent(view: View) {
      val buffer = view.getBuffer
      buffer.save(view, null)
      io.VFSManager.waitForRequests // wait until buffer is saved
      compile(buffer.getPath)
   }
   def compileSelected(view: View, brw: browser.VFSBrowser) {
      val files = brw.getSelectedFiles
      files foreach {vfsfile =>
         val file = vfsfile.getPath
         // save if the file is open
         if (vfsfile.getType == io.VFSFile.DIRECTORY) {
            val buffer = jEdit.getBuffer(file)
            if (buffer != null) {
               buffer.save(view, null)
               io.VFSManager.waitForRequests // wait until buffer is saved
            }
         }
         compile(file)
      }
   }
}
package info.kwarc.mmt.stex

import java.nio.charset.{Charset, MalformedInputException}

import info.kwarc.mmt.api.archives._
import info.kwarc.mmt.api.utils.{File, FilePath}
import info.kwarc.mmt.stex.STeXUtils._

case class STeXStructure(smslines: List[String], deps: List[Dependency]) {
  def join(s2: STeXStructure): STeXStructure =
    if (s2.smslines.isEmpty && s2.deps.isEmpty) this
    else STeXStructure(s2.smslines ++ smslines, s2.deps ++ deps)
}


/**
  * Created by maeder on 11.11.15.
  */
trait STeXAnalysis {
  self: TraversingBuildTarget =>

  protected def logSuccess(f: FilePath) {
    logResult("success " + f)
  }

  protected def logFailure(f: FilePath) {
    logResult("failure " + f)
  }

  def mkDep(a: Archive, ar: String, fp: FilePath): Option[Dependency] = {
    val root = a.root.up.up / ar
    controller.addArchive(root)
    controller.backend.getArchive(root) match {
      case None =>
        log("missing archive " + ar + " for " + fp)
        None
      case Some(arch) => Some(mkFileDep(arch, fp))
    }
  }

  def mkFileDep(archive: Archive, filePath: FilePath): Dependency =
    FileBuildDependency("tex-deps", archive, filePath)

  def mhRepos(a: Archive, r: String, b: String): Option[Dependency] = {
    val fp = entryToPath(b)
    val optRepo = Option(r).map(getArgMap)
    optRepo match {
      case Some(m) => mkDep(a, m.getOrElse("mhrepos", archString(a)), fp)
      case None => Some(mkFileDep(a, fp))
    }
  }

  protected def toKeyDep(d: Dependency, key: String): Dependency = d match {
    case FileBuildDependency(_, ar, fp) => FileBuildDependency(key, ar, fp)
    case fd => fd
  }

  protected def matchPathAndRep(a: Archive, line: String): Option[Dependency] =
    line match {
      case beginModnl(_, _, b) => Some(mkFileDep(a, entryToPath(b)))
      case includeGraphics(_, _, b) => Some(PhysicalDependency(File(b)))
      case importOrUseModule(r) => getArgMap(r).get("load").map(f => PhysicalDependency(File(f).setExtension(".sms")))
      case mhinputRef(_, r, b) =>
        val fp = entryToPath(b)
        Option(r) match {
          case Some(id) => mkDep(a, id, fp)
          case None => Some(mkFileDep(a, fp))
        }
      case tikzinput(_, r, b) => mhRepos(a, r, b).map(toKeyDep(_, "tikzsvg"))
      case guse(r, b) => mkDep(a, r, entryToPath(b))
      case useMhProblem(_, r, b) => createMhImport(a, r, b).flatMap(_.deps).headOption.map(toKeyDep(_, "tikzsvg"))
      case includeMhProblem(_, r, b) => mhRepos(a, r, b)
      case _ => None
    }

  private def mkImport(a: Archive, r: String, p: String, s: String, ext: String): String =
    "\\importmodule[load=" + a.root.up.up + "/" + r + "/source/" + p + ",ext=" + ext + "]" + s + "%"

  private def mkMhImport(a: Archive, r: String, p: String, s: String): STeXStructure =
    STeXStructure(List(mkImport(a, r, p, s, "sms")), mkDep(a, r, entryToPath(p)).toList.map(toKeyDep(_, "sms")))

  private def mkGImport(a: Archive, r: String, p: String): STeXStructure =
    STeXStructure(List(mkImport(a, r, p, "{" + p + "}", "tex"), "\\mhcurrentrepos{" + r + "}%"),
      mkDep(a, r, entryToPath(p)).toList)

  // line list is reversed!

  private def splitArgs(r: String): List[List[String]] = r.split(",").toList.map(_.split("=").toList)

  private def mkArgMap(ll: List[List[String]]): Map[String, String] = {
    var m: Map[String, String] = Map.empty
    ll.foreach {
      case List(k, v) => m += ((k, v))
      case _ =>
    }
    m
  }

  private def getArgMap(r: String): Map[String, String] = mkArgMap(splitArgs(r))

  private def createMhImport(a: Archive, r: String, b: String): List[STeXStructure] = {
    val m = getArgMap(r)
    m.get("path").toList.map(p => mkMhImport(a, m.getOrElse("repos", archString(a)), p, b))
  }

  private def createGImport(a: Archive, r: String, p: String): STeXStructure = {
    Option(r) match {
      case Some(id) =>
        mkGImport(a, id, p)
      case None =>
        mkGImport(a, archString(a), p)
    }
  }

  private def createImport(r: String, p: String): STeXStructure =
    STeXStructure(List("\\importmodule[" + r + "]{" + p + "}%"), Nil)

  /** create sms file */
  def createSms(a: Archive, inFile: File, outFile: File) {
    val source = readSourceRebust(inFile)
    val w = new StringBuilder
    val STeXStructure(smsLines, _) = mkSTeXStructure(a, source.getLines)
    if (smsLines.nonEmpty) File.write(outFile, smsLines.reverse.mkString("", "\n", "\n"))
    else log("no sms content")
  }

  def mkSTeXStructure(a: Archive, lines: Iterator[String]): STeXStructure = {
    var struct = STeXStructure(Nil, Nil)
    def join(s: STeXStructure) = {
      struct = struct.join(s)
    }
    lines.foreach { line =>
      val l = stripComment(line).trim
      val verbIndex = l.indexOf("\\verb")
      if (verbIndex <= -1) {
        val sl = matchSmsEntry(a, l)
        sl.foreach(join)
        if (key != "sms") {
          val od = matchPathAndRep(a, l)
          od.foreach(d => join(STeXStructure(Nil, List(d))))
        }
      }
    }
    struct
  }

  def matchSmsEntry(a: Archive, line: String): List[STeXStructure] = {
    line match {
      case importMhModule(r, b) =>
        createMhImport(a, r, b)
      case gimport(_, r, p) =>
        List(createGImport(a, r, p))
      case smsGStruct(_, r, _, p) =>
        List(createGImport(a, r, p))
      case smsMhStruct(r, _, p) =>
        createMhImport(a, r, p)
      case smsSStruct(r, _, p) =>
        List(createImport(r, p))
      case smsViewsig(r, _, f, t) =>
        val m = getArgMap(r)
        val fr = m.getOrElse("fromrepos", archString(a))
        val tr = m.getOrElse("torepos", archString(a))
        List(mkGImport(a, fr, f), mkGImport(a, tr, t))
      case smsViewnl(_, r, p) =>
        List(createGImport(a, archString(a), p))
      case smsMhView(r, _, f, t) =>
        val m = getArgMap(r)
        var ofp = m.get("frompath")
        var otp = m.get("topath")
        val fr = m.getOrElse("fromrepos", archString(a))
        val tr = m.getOrElse("torepos", archString(a))
        (ofp, otp) match {
          case (Some(fp), Some(tp)) =>
            List(mkMhImport(a, fr, fp, f), mkMhImport(a, tr, tp, t))
          case _ => Nil
        }
      case smsView(r, f, t) =>
        val m = getArgMap(r)
        val ofr = m.get("from")
        val otr = m.get("to")
        (ofr, otr) match {
          case (Some(fr), Some(tr)) =>
            List(createImport(fr, f), createImport(tr, t))
          case _ => Nil
        }
      case _ if smsRegs.findFirstIn(line).isDefined => List(STeXStructure(List(line + "%"), Nil))
      case _ => Nil
    }
  }
}

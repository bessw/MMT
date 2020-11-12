package info.kwarc.mmt.mizar.newxml

import info.kwarc.mmt.api.utils._
import info.kwarc.mmt.mizar.newxml.syntax.Text_Proper

object Main {
  def makeParser = new info.kwarc.mmt.api.utils.XMLToScala("info.kwarc.mmt.mizar.newxml.syntax")

  def main(args: Array[String]) {
    val parser = makeParser
    val files= args.map(arg=>File(arg))
    var troublemakers:List[String] = List.empty
    var troubles:List[String] = List.empty
    var parsedFiles:List[syntax.Text_Proper] = List.empty
    files.foreach { art =>
      try {
        val parsedArt = parser(art).asInstanceOf[syntax.Text_Proper]
        parsedFiles::= parsedArt
      } catch {
        case e: BacktrackableExtractError =>
          troublemakers::=art.toFilePath.toString()
          val issue = "Backtrackable extract error while parsing file " + art.toFilePath.toString() +": "+ e.msg
          troubles::= issue
          println(issue)
        case e: FatalExtractError =>
          troublemakers::=art.toFilePath.toString()
          val issue = "Fatal extract error while parsing file " + art.toFilePath.toString() +": "+ e.msg
          troubles::= issue
          println(issue)
      }
      println("Done parsing file "+art.toFilePath.toString()+".")
    }
    troublemakers = troublemakers.reverse
    troubles = troubles.reverse
    parsedFiles = parsedFiles.reverse
    def prettyPrintList(sep:String, l:List[String]) = l match {
      case Nil => ""
      case List(f) => f
      case hd :: tl => tl.foldLeft(hd)((s, t) => s + sep + t)
    }
    if (!troubles.isEmpty) {
      println("We encountered the following issues during parsing: " + prettyPrintList("\n", troubles))
      println("There were issues while parsing the following files: " + prettyPrintList(" ", troublemakers))
    }
    val translatedArticles = parsedFiles map {text_proper:Text_Proper => translator.articleTranslator.translateArticle(text_proper)}
  }
}
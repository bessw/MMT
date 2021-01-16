package info.kwarc.mmt.mizar.newxml.translator

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.documents._
import info.kwarc.mmt.api.utils._
import info.kwarc.mmt.api.symbols._
import info.kwarc.mmt.api.modules._
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.notations._
import info.kwarc.mmt.api.presentation._
import info.kwarc.mmt.api.uom.SimplificationUnit

import info.kwarc.mmt.mizar.newxml._
import foundations._
import mmtwrapper.Mizar

import scala.collection._

object TranslationController {


  var controller = {
    val c = new frontend.Controller
    //       c.setFileReport(File("mizar.log"))
    //       c.setCheckNone //c.setFoundChecker(new libraries.DefaultFoundation(controller.report))
    c
  }

  var query : Boolean = false

  //new frontend.Controller(libraries.NullChecker, new FileReport(new java.io.File("mizar.log")))

  //set during translation
  var currentAid : String = null
  var currentDoc : Document = null
  var currentThy : Theory = null
  var currentOutputBase : DPath = null

  def currentBaseThy : Option[MPath] = Some(mmtwrapper.Mizar.MizarPatternsTh)
  def currentBaseThyFile = File("/home/user/Erlangen/MMT/content/MathHub/MMT/LATIN2/source/foundations/mizar/"+mmtwrapper.Mizar.MizarPatternsTh.name.toString+".mmt")
  def localPath : LocalName = LocalName(currentAid.toLowerCase())
  def currentThyBase : DPath = TranslationController.currentOutputBase / localPath
  def currentTheoryPath : MPath = {
    val res = currentThyBase ? localPath
    assert (res == currentThy.path)
    res
  }
  def getTheoryPath(aid: String) = (TranslationController.currentOutputBase / aid.toLowerCase()) ? aid.toLowerCase()
  def currentSource : String = mmtwrapper.Mizar.mathHubBase + "/source/" + currentAid.toLowerCase() + ".miz"

  def makeDocument() = {
    val doc = new Document(currentThyBase, documents.FileLevel)
    controller.add(doc)
    currentDoc = doc
    doc
  }
  def makeTheory() = {
    val thy = new Theory(currentThyBase, localPath, currentBaseThy, Theory.noParams, Theory.noBase)
    controller.add(thy)
    currentThy = thy
    thy
  }
  def endMake() = {
    controller.endAdd(currentThy)
    val doc = currentThy.asDocument
    currentDoc = doc
    controller.add(currentDoc)
    controller.endAdd(currentDoc)
  }

  def add(e: NarrativeElement) : Unit = {
    controller.add(e)
  }
  def add(m: Module) : Unit = {
    controller.add(m)
  }
  def add(e : Declaration) : Unit = {
    val eC = complify(e)
    //println(controller.presenter.asString(eC))
    controller.add(eC)
  }
  private def complify(d: Declaration) = {
    val rules = RuleSet.collectRules(controller, Context(mmtwrapper.Mizar.MizarPatternsTh))
    foundations.IntroductionRule.allRules.foreach {rules.declares(_)}
    val complifier = controller.complifier(rules).toTranslator()
    try {
      d.translate(complifier,Context.empty)
    } catch {case e: Exception =>
      println("error while complifying instance " + d.path)
      d
    }
  }

  def makeConstant(n: LocalName, t: Term) : Constant =
    Constant(OMMOD(currentTheoryPath), n, Nil, Some(t), None, None)
  def makeConstant(n: LocalName, tO: Option[Term], dO: Option[Term])(implicit notC:NotationContainer = NotationContainer.empty()) : Constant =
    Constant(OMMOD(currentTheoryPath), n, Nil, tO, dO, None)

  def simplifyTerm(tm:objects.Term): objects.Term = {
    val su = SimplificationUnit(Context.empty,true,false)
    val rules = RuleSet.collectRules(controller, su.context)
    controller.simplifier.objectLevel(tm,su, rules)
  }

  def inferType(tm:objects.Term, ctx: Context = Context.empty): objects.Term = {
    checking.Solver.infer(controller, ctx, tm, None).getOrElse(Mizar.any)
  }
}
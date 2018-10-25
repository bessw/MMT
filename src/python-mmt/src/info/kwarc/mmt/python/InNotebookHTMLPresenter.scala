package info.kwarc.mmt.python

import info.kwarc.mmt.api._
import modules._
import symbols._
import objects._
import presentation._

class InNotebookHTMLPresenter(oP: ObjectPresenter) extends Presenter(oP) {
  val key = "notebook-presenter"
  def apply(e : StructuralElement, standalone: Boolean = false)(implicit rh : RenderingHandler) {
     val htmlRh = utils.HTML(s => rh(s))
     val ps = new PresentationScope(htmlRh, rh)
     ps(e)
  }
  
  /** local class so that we can import htmlRh and build HTML programmatically */
  private class PresentationScope(htmlRh: utils.HTML, rh : RenderingHandler) {
     import htmlRh._
     def apply(e: StructuralElement) {
        e match {
          case thy: DeclaredTheory =>
            doKeyword("theory")
            doName(thy.name)
            thy.meta foreach {m =>
              doOperator(":")
              doPath(m)
            }
            // always empty in a notebook
          case c: Constant =>
            doName(c.name)
            c.tp foreach {t =>
              doOperator(":")
              doTerm(t)
            }
            c.df foreach {t =>
              doOperator("=")
              doTerm(t)
            }
          case Include(_, from, args) =>
            doKeyword("include")
            doPath(from)
        }
     }
     
     def doName(l: LocalName) {
        span("name") {
          text(l.toString)
        }
     }
     def doPath(p: Path) {
       span("uri", attributes = List(HTMLAttributes.href -> p.toPath)) {
         text(p.toString)
       }
     }
     def doTerm(t: Term) {
       oP(t, None)(rh)
     }
     def doKeyword(k: String) {
       span("keyword") {
         text("theory")
       }
     }
     def doOperator(s: String) {
        span("operator") {
          text(":")
        }
     }
  }
}
 
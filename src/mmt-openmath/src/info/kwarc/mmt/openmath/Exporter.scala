package info.kwarc.mmt.openmath

import info.kwarc.mmt.api._
import info.kwarc.mmt.api.archives.BuildTask
import modules._
import objects._
import documents._
import presentation._

object OpenMath {
   /** a non-standard content dictionary declaring one symbol for the type of each literal, i.e., int, float, string, bytearray */
   val omNamespace = DPath(utils.URI("http://www.openmath.org/cd"))
   val cd = omNamespace ? "OpenMath"

   val OMI = cd ? "OMI"
   val OMF = cd ? "OMF"
   val OMSTR = cd ? "OMSTR"

   /** the symbol for equality that is used for definitions */
   val equals = omNamespace ? "relation1.ocd" ? "eq"
   /** the symbol for the is-a relation that is used for type annotations */
   val isA = omNamespace ? "set1" ? "in"
}

/** An exporter that creates an OpenMath CD for every theory */
class Exporter extends archives.Exporter {
  /** use "openmath" as the key of this build target */
  val key = "openmath"

  /** override the default file extension with "ocd" */
  override val outExt = "ocd"

  /** We are only interested in CDs, so we skip documents */
  def exportDocument(doc: documents.Document, bt: BuildTask) {}

  /** This could be used to export a table of contents */
  def exportNamespace(dp: DPath, bt: BuildTask, namespaces: List[BuildTask], modules: List[BuildTask]) {}

  /** OpenMath does not support views, so we skip them */
  def exportView(view: View, bf: BuildTask) {}

  /** flatten a theory and export it as a CD */
  def exportTheory(thy: Theory, bf: BuildTask) {
    // simplification flattens a theory, this removes in particular all named imports
    // controller is MMT's main object, it is available to every extension
    controller.simplifier(thy)

    // build an XML element using Scala's built-in XML syntax
    val cd = <CD>
     <CDComment>Generated by MMT</CDComment>
     <CDName>{thy.name.toString}</CDName>
     <CDBase>{thy.parent.toString}</CDBase>
     <CDURL>{thy.path.toString}</CDURL>
     {getDescription(thy)}
     {
       thy.getDeclarations.flatMap {
           case c: symbols.Constant =>
             <CDDefinition>
               <Name>{c.name.toString}</Name>
               <Role>application</Role>
               {getDescription(c)}
               {c.tp.toList.map {tp =>
                 // add an FMP "c isA tp" if c has a type
                 getFMP(c.path, tp, OpenMath.isA)
               }}
               {c.df.toList.map {df =>
                 // add an FMP "c equals df" if c has a definition
                 getFMP(c.path, df, OpenMath.equals)
               }}
           </CDDefinition>

           case _ => Nil
       }
     }
    </CD>

    // write the XML element to the exorter's output stream
    rh(cd)
  }

  /** retrieves the description from MMT metadata and turns it into a <Description> element */
  private def getDescription(se: StructuralElement) = NarrativeMetadata.description.get(se) match {
     case Some(desc) => <Description>{desc}</Description>
     case None => Nil
  }

  /** builds an FMP element for a symbol's type or definition
   *  @param path the symbol URI
   *  @param t the type/definition
   *  @param oper the operator to use to combine the two */
  private def getFMP(path: GlobalName, t: Term, oper: GlobalName) = {
    val fmp = OMA(OMS(oper), List(OMS(path), t))
    <FMP>{OpenMathPresenter.asXML(fmp)}</FMP>
  }
}

/** translates MMT objects into OpenMath objects in XML encoding
 *  This is straightforward except for a few case where MMT objects go beyond OpenMath.
 */
object OpenMathPresenter extends ObjectPresenter {
   def apply(o: Obj, origin: Option[CPath] = None)(implicit rh : RenderingHandler) {
     // recursively translate to XML, wrap in OMOBJ, and write to output stream
     rh(<OMOBJ>{applyRec(o)}</OMOBJ>)
   }

   private def applyRec(o: Obj): scala.xml.Node = {
     o match {
       case OMS(p) => <OMS name={p.name.toPath} cd={p.module.name.toPath} cdbase={p.doc.toString}/>
       case OMV(n) => <OMV name={n.toPath}/>
       case OMA(f, args) => <OMA>{(f::args).map(e => applyRec(e))}</OMA>
       case OMBIND(binder, context, scope) =>
         <OMBIND>{applyRec(binder)}{applyContext(context)}{applyRec(scope)}</OMBIND>
       case l: OMLITTrait =>
          l.synType match {
            case OMS(OpenMath.OMI) => <OMI>{l.toString}</OMI>
            case OMS(OpenMath.OMF) => <OMF>{l.toString}</OMF>
            case OMS(OpenMath.OMSTR) => <OMSTR>{l.toString}</OMSTR>
            case _ =>
              // default case for MMT literals that are not allowed in OpenMath
              <OMSTR>{l.toString}</OMSTR>
          }
     }
   }
   private def applyContext(con: Context): scala.xml.Node = {
     <OMBVAR>{con.map(applyVarDecl)}</OMBVAR>
   }
   private def applyVarDecl(vd: VarDecl): scala.xml.Node = {
     val omv = <OMV name={vd.name.toPath}/>
     val typeAttribution = vd.tp.toList.flatMap {tp => List(OMS(OpenMath.isA), tp)}
     val defAttribution  = vd.df.toList.flatMap {df => List(OMS(OpenMath.equals), df)}
     val keyValList = (typeAttribution ::: defAttribution).map(o => applyRec(o))
     if (keyValList.isEmpty)
       omv
     else
       <OMATTR><OMATP>{keyValList}</OMATP>{omv}</OMATTR>
   }
}


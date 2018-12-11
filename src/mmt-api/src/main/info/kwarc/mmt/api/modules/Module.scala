package info.kwarc.mmt.api.modules
import info.kwarc.mmt.api._
import symbols._
import objects._

/**
 * MMT modules, unifies theories and views
 *
 * @param parent the namespace of this module, by default the [[Path]] of the parent document
 * @param name the name of the module
 */
abstract class Module(val parent : DPath, val name : LocalName) extends ModuleOrLink {
   def path: MPath = parent ? name
   def toTerm = OMMOD(path)
   def superModule: Option[MPath] = if (name.length > 1) Some(parent ? name.init) else None
   //def parameters : Context
   def translate(newNS: DPath, newName: LocalName, translator: Translator, context : Context): Module
}

/**
 * A [[ContentElement]] that that is defined via a module
 */
trait ModuleWrapper extends ContentElement {
  def module : Module
  def getComponents = module.getComponents
  def getDeclarations = module.getDeclarations
  // bend over metadata pointer
  this.metadata = module.metadata
  //default
  def toNode = module.toNode
  override def toString = module.toString
}

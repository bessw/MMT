package info.kwarc.mmt.api.frontend.actions

import info.kwarc.mmt.api.GeneralError
import info.kwarc.mmt.api.frontend.{Controller, actions}
import info.kwarc.mmt.api.utils.MMTSystem

import scala.collection.mutable.ListBuffer
import scala.util.Try

// TODO: Add a `help` action

/** Shared base class for Actions for printing something */
sealed abstract class PrintAction extends ActionImpl {}

/** print all loaded knowledge items to STDOUT in text syntax */
case object PrintAll extends PrintAction {
  def apply(controller: Controller): Unit = {
    controller.report("response", "\n" + controller.library.toString)
  }
  def toParseString = "printAll"
}
object PrintAllCompanion extends ActionObjectCompanionImpl[PrintAll.type]("print all loaded knowledge items to STDOUT in text syntax", "printAll")

/** print all loaded knowledge items to STDOUT in XML syntax */
case object PrintAllXML extends PrintAction {
  def apply(controller: Controller): Unit = {
    controller.report("response", "\n" + controller.library.getModules.map(_.toNode).mkString("\n"))
  }
  def toParseString = "printXML"
}
object PrintAllXMLCompanion extends ActionObjectCompanionImpl[PrintAllXML.type]("print all loaded knowledge items to STDOUT in xml syntax", "printXML")

/** print all configuration entries to STDOUT */
case object PrintConfig extends PrintAction {
  def apply(controller: Controller) : Unit = controller.report("response", controller.getConfigString())
  def toParseString = "printConfig"
}
object PrintConfigCompanion extends ActionObjectCompanionImpl[PrintConfig.type]("print all configuration to stdout", "printConfig")

case class HelpAction(topic: String) extends PrintAction {
  // list of all known help Topics
  private def helpTopics : List[String] = (
    MMTSystem.getResourceList("/help-text/").flatMap({
      case s: String if s.endsWith(".txt") => Some(s.stripSuffix(".txt"))
      case _ => None
    }) ::: ActionCompanion.all.flatMap(_.keywords).distinct ::: List("topics")
    ).sorted

  /** gets dynamically generated help entries */
  private def getDynamicHelp(topic: String) : Option[String] = topic match {
    case "" => getHelpText("help")
    case "topics" =>
      val lines = new ListBuffer[String]()
      lines += "Type 'help <topic>' for more information about a specific topic. "
      lines += ""
      helpTopics.map(lines +=)
      Some(lines.mkString("\n"))
    case _ => None
  }

  /** gets the (static) help text for a given topic or None */
  private def getHelpText(topic: String) : Option[String] = if(topic.matches("[A-Za-z_-]+")) {
    Try(MMTSystem.getResourceAsString("/help-text/" + topic + ".txt")).toOption
  } else {
    None
  }

  /** gets the help text for a given action or None */
  private def getActionHelp(action: String) : Option[String] = {
    val topics = ActionCompanion.find(action)
    if(topics.isEmpty){
      None
    } else {
      Some(topics.distinct.map(ac => ac.mainKeyword + ": " + ac.helpText).mkString("\n"))
    }
  }


  def apply(controller: Controller): Unit = {
    val topicActual = topic.trim

    // try and get a string that represents help
    getDynamicHelp(topicActual).getOrElse(getHelpText(topicActual).getOrElse(getActionHelp(topicActual).getOrElse(""))) match {
      case "" => controller.report("response", "No help on '" + topic + "' available")
      case s: String => controller.report("response", s)
    }
  }
  def toParseString: String = "help " + topic
}
object HelpActionCompanion extends ActionCompanionImpl[HelpAction]("print help about a given topic", "help") {
  import Action._
  override def parserActual(implicit state: ActionState): actions.Action.Parser[HelpAction] = (strMaybeQuoted *) ^^ { s => HelpAction(s.mkString(" ")) }
}

/** utility methods for handling [[PrintAction]]s */
trait PrintActionHandling {
  self: Controller =>

  /** returns a string expressing the current configuration */
  def getConfigString(): String = state.config.toString
}
package info.kwarc.mmt.leo.AgentSystem.MMTSystem

import info.kwarc.mmt.api.frontend.Controller
import info.kwarc.mmt.api.{Rule, modules}
import info.kwarc.mmt.api.objects._
import info.kwarc.mmt.api.symbols.Constant
import info.kwarc.mmt.leo.AgentSystem.{Change, Listener, Agent}
import info.kwarc.mmt.api.uom.Lambda

/**
 * Created by Mark on 7/23/2015.
 */


abstract class MMTAgent(implicit controller: Controller,oLP:String) extends Agent {


  override type BBType = MMTBlackboard

  override val name: String = "GoalAgent"

  lazy val presentObj = blackboard.get.presentObj
  lazy val rules = blackboard.get.rules

  lazy val invertibleBackward = blackboard.get.invertibleBackward
  lazy val invertibleForward = blackboard.get.invertibleForward
  lazy val searchBackward = blackboard.get.searchBackward
  lazy val searchForward = blackboard.get.searchForward
  lazy val searchTerms = blackboard.get.searchTerms
  lazy val transitivityRules = blackboard.get.transitivityRules

  lazy val goalSection = blackboard.get.goalSection
  def goal = goalSection.data
  lazy val factSection = blackboard.get.factSection
  def facts = factSection.data


}

class SearchBackwardAgent(implicit controller: Controller,oLP:String) extends MMTAgent {
  override val priority=1

  override val name =  "SearchBackwardAgent"
  def wantToSubscribeTo = List(blackboard.get.factSection)
  override val interests = List("ADD")

  def addTask(g:Goal) = taskSet+=new SearchBackwardTask(this,g)

  override def respond() = {
    log("responding to: " + mailbox.length + " message(s)")
    readMail.foreach{
      case Change(s,data,flag) => addTask(blackboard.get.goalSection.data)
      case _ if blackboard.get.cycle==0  => addTask(blackboard.get.goalSection.data)
      case _ =>
    }
    if (taskSet.isEmpty) log("NO TASKS FOUND") else log("Found "+taskSet.size+" task(s)")
  }
}

class SearchForwardAgent(implicit controller: Controller,oLP:String) extends MMTAgent {

  override val name =  "SearchForwardAgent"
  def wantToSubscribeTo = List(blackboard.get.factSection)
  override val interests = Nil

  def addTask() = taskSet += new SearchForwardTask(this)

  override def respond() = {
    log("responding to: " + mailbox.length + " message(s)")
    if (!goal.isFinished) {addTask()}
    if (taskSet.isEmpty) log("NO TASKS FOUND") else log("Found "+taskSet.size+" task(s)")
  }

}


class TermGenerationAgent(implicit controller: Controller,oLP:String) extends MMTAgent {

  override val name =  "TermGeneratingAgent"
  def wantToSubscribeTo = List(blackboard.get.factSection)
  override val interests = List("ADD")

  def addTask() = taskSet+=new TermGenerationTask(this) //TODO add Term generation to agent system
  //def addTask() = ???

  override def respond() = {
    log("responding to: " + mailbox.length + " message(s)")
    if (!goal.isFinished) {addTask()}
    if (taskSet.isEmpty) log("NO TASKS FOUND") else log("Found "+taskSet.size+" task(s)")
  }

}

class TransitivityAgent(implicit controller: Controller,oLP:String) extends MMTAgent {

  override val name =  "TermGeneratingAgent"
  def wantToSubscribeTo = List(blackboard.get.factSection)
  override val interests = List("ADD") //TODO make it interested in the addition of relation shaped facts

  def addTask() = taskSet+=new TransitivityTask(this) //TODO add transitivity to agent system
  //def addTask() = ???

  override def respond() = {
    log("responding to: " + mailbox.length + " message(s)")
    if (!goal.isFinished) {addTask()}
    if (taskSet.isEmpty) log("NO TASKS FOUND") else log("Found "+taskSet.size+" task(s)")
  }

}

abstract class NormalizingRule extends Rule{}

abstract class NormalizingAgent(implicit controller: Controller,oLP:String) extends MMTAgent {

  override val name = "NormalizingAgent"

  def wantToSubscribeTo = List(blackboard.get.goalSection)

  //TODO possibly class of beta and eta
  lazy val normalizingRules = blackboard.get.rules.get(classOf[NormalizingRule])

  def allRulesPresent: Boolean = ??? //TODO add way to determine if there are rules for all of the symbols

  override val interests = List("ADD") //TODO make it interested in the addition of relation shaped facts

}

abstract class SimplifyingAgent(implicit controller: Controller,oLP:String) extends MMTAgent {}




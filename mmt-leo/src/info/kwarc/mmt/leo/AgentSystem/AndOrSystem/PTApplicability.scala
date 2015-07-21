package info.kwarc.mmt.leo.AgentSystem.AndOrSystem

import info.kwarc.mmt.leo.AgentSystem._

/**
 * Created by Mark on 7/21/2015.
 */

trait PTApplicability extends RuleTask{
  override def isApplicable[BB<:Blackboard](bb:BB):Boolean ={
    bb match {
      case b:AndOrBlackboard[_] =>
        b.proofSection.isApplicable(this)
      case _ => throw new IllegalArgumentException("Not a valid blackboard type")
    }
  }
}



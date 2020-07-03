package info.kwarc.mmt.frameit

import info.kwarc.mmt.api.{DPath, GlobalName, LocalName, MPath, NamespaceMap, Path}
import info.kwarc.mmt.api.utils.{File, URI}

object Archives {

  val frameworldIdentifier = "FrameIT/frameworld"

  def getPaths(rootDir: File): List[File] = {
    val mathhubRoot = rootDir / "MathHub"
    List(
      mathhubRoot / "MMT" / "urtheories",
      mathhubRoot / "MMT" / "LFX",
      mathhubRoot / "MitM" / "core",
      mathhubRoot / "MitM" / "Foundation",
      mathhubRoot / "FrameIT" / "frameworld"
    )
  }

  object MetaSymbols {
    val LFtype: GlobalName = Path.parseS("http://cds.omdoc.org/urtheories?Typed?type", NamespaceMap.empty)
    val jder: GlobalName = Path.parseS("http://mathhub.info/MitM/Foundation?Logic?ded", NamespaceMap.empty)
    val jdoteq: GlobalName = Path.parseS("http://mathhub.info/MitM/Foundation?Logic?eq", NamespaceMap.empty)
    val proofSketch: GlobalName = Path.parseS("http://mathhub.info/MitM/Foundation?InformalProofs?proofsketch", NamespaceMap.empty)
  }

  object Frameworld {
    val FactCollection: MPath = Path.parseM("http://mathhub.info/FrameIT/frameworld?FactCollection", NamespaceMap.empty)
  }

}

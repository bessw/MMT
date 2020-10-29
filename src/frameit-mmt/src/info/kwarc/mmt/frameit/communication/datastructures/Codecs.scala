package info.kwarc.mmt.frameit.communication.datastructures

import info.kwarc.mmt.api.objects.Term
import info.kwarc.mmt.api.{GlobalName, MPath, NamespaceMap, Path}
import info.kwarc.mmt.frameit.business.datastructures.FactReference
import info.kwarc.mmt.frameit.communication.datastructures.DataStructures.{SCheckingError, SDynamicScrollApplicationInfo, SFact, SGeneralFact, SInvalidScrollAssignment, SMiscellaneousError, SNonTotalScrollApplication, SScroll, SScrollApplication, SScrollAssignments, SValueEqFact}
import info.kwarc.mmt.frameit.communication.datastructures.SOMDoc.{OMDocBridge, SFloatingPoint, SInteger, SOMA, SOMS, SRawOMDoc, SString, STerm}
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.{deriveConfiguredDecoder, deriveConfiguredEncoder}
import io.circe.{CursorOp, Decoder, DecodingFailure, Encoder, HCursor, Json, JsonObject}

import scala.util.Try

/**
  * All the JSON codecs for [[DataStructures]] and [[SOMDoc]]
  *
  *
  * ## WARNINGS
  *
  * - Be cautious in using companion objects for abstract class/case classes for which you would like to derive
  * io.circe codecs!! See [[https://gitter.im/circe/circe?at=5f8ea822270d004bcfdb28e9]]
  *
  * - Invariant of whole frameit-mmt code: io.circe.generic.{auto,semiauto} and io.circe.generic.extras.{auto,semiauto}
  *   imports should only occur in this file (Codecs.scala).
  *
  *   Reasons:
  *     - compilation is much faster when editing server endpoints (say in [[ConcreteServerEndpoints]]) if the
  *       implicit-heavy codec derivation is encapsulated (and thus cached by the compiler from previous runs)
  *       in another file (Codecs.scala)
  *     - Abstract over io.circe which is brittle to use as a human being.
  *
  * ## Things I wish I knew about io.circe myself
  *
  * - io.circe.generic.semiauto.derive{Encoder, Decoder} is something completely unrelated to
  *   io.circe.generic.extras.semiauto.derive{Encoder, Decoder}.
  *   Precisely because of this confusion arising from the name clash, the latter is deprecated.
  *
  * - You can derive "configure" codecs by having an implicit configuration in scope and using
  *   io.circe.generic.extras.semiauto.deriveConfigured{Encoder,Decoder}.
  *   You do *not* need a @ConfiguredJsonCodec annotation for this on the case classes for which you want to derive
  *   the codecs. See [[https://gitter.im/circe/circe?at=5f9934f1f2fd4f60fc3b4539]].
  *
  * - [[io.circe.generic.extras.semiauto.deriveConfiguredEncoder deriveConfiguredEncoder]] and
  *   [[io.circe.generic.extras.semiauto.deriveConfiguredDecoder deriveConfiguredDecoder]] will fail
  *   if there is no *or* more than one implicit [[Configuration]] in scope. In both cases, you will get the
  *   *same* error message (due to Scala 2 being limited in that regard, no fault of io.circe).
  *   Hence, if we want to use different configurations for different case classes, then we need to encapsulate
  *   the [[Configuration]] objects. This is what this file does via the use of ''object config'', see the code
  *   to learn more about that pattern.
  */
private[communication] object Codecs {
  /**
    * Create a Circe [[Configuration]] encoding case classes as objects with their name (subject to rewriteMap)
    * in a "kind" discriminator field.
    * @param rewriteMap With this map, you can control how specific case classes should be referred to in the
    *                   "kind" field.
    *
    * @see [[SOMDocCodecs.config]] for an example
    */
  private def kindedJsonConfig(rewriteMap: Map[Class[_], String]): Configuration = {
    val map = rewriteMap.map { case (key, value) => (key.getSimpleName, value) }
    Configuration.default.withDiscriminator("kind").copy(transformConstructorNames = oldCtorName => {
      map.getOrElse(oldCtorName, oldCtorName)
    })
  }

  object PathCodecs {
    implicit val mpathEncoder: Encoder[MPath] = Encoder.encodeString.contramap[MPath](_.toString)
    implicit val mpathDecoder: Decoder[MPath] = Decoder.decodeString.emapTry { str => {
      Try(Path.parseM(str, NamespaceMap.empty))
    }
    }

    implicit val globalNameEncoder: Encoder[GlobalName] = Encoder.encodeString.contramap[GlobalName](_.toString)
    implicit val globalNameDecoder: Decoder[GlobalName] = Decoder.decodeString.emapTry { str => {
      Try(Path.parseS(str, NamespaceMap.empty))
    }
    }
  }

  object SOMDocCodecs {
    import io.circe.generic.extras.semiauto._

    private[datastructures] object config {
      implicit val somdocConfig: Configuration = kindedJsonConfig(Map(
        classOf[SOMA] -> "OMA",
        classOf[SOMS] -> "OMS",
        classOf[SFloatingPoint] -> "OMF",
        classOf[SString] -> "OMSTR",
        classOf[SInteger] -> "OMI",
        classOf[SRawOMDoc] -> "RAW"
      ))
    }

    // vvvvvvv CAREFUL WHEN REMOVING IMPORTS (IntelliJ might wrongly mark them as unused)
    import config._
    // ^^^^^^^ END
    implicit val stermEncoder: Encoder[STerm] = deriveConfiguredEncoder
    implicit val stermDecoder: Decoder[STerm] = deriveConfiguredDecoder

    implicit val termDecoder: Decoder[Term] = (c: HCursor) => {
      stermDecoder(c).map(OMDocBridge.decode)
    }

    implicit val termEncoder: Encoder[Term] = (tm: Term) => {
      stermEncoder(OMDocBridge.encode(tm))
    }
  }

  object DataStructureCodecs {
    object FactCodecs {
      // vvvvvvv CAREFUL WHEN REMOVING IMPORTS (IntelliJ might wrongly mark them as unused)
      import PathCodecs._
      import SOMDocCodecs._
      // ^^^^^^^ END

      private[datastructures] object config {
        implicit val factConfig: Configuration = kindedJsonConfig(Map(
          classOf[SGeneralFact] -> "general",
          classOf[SValueEqFact] -> "veq"
        ))
      }

      // vvvvvvv CAREFUL WHEN REMOVING IMPORTS (IntelliJ might wrongly mark them as unused)
      import config._
      // ^^^^^^^ END

      implicit val sfactEncoder: Encoder[SFact] = deriveConfiguredEncoder
      implicit val sfactDecoder: Decoder[SFact] = deriveConfiguredDecoder

      // deliberately use generic.semiauto.derive{Encoder,Decoder}, not the ones from extras.generic.semiauto!
      implicit val factReferenceEncoder: Encoder[FactReference] = io.circe.generic.semiauto.deriveEncoder
      implicit val factReferenceDecoder: Decoder[FactReference] = io.circe.generic.semiauto.deriveDecoder

      /*val knownFactEncoder: Encoder[SFact with SKnownFact] = (knownFact: SFact with SKnownFact) => {
        // just add `uri: ...` field to encoded fact
        Json.fromJsonObject(
          // assumption: facts are encoded as objects
          sfactEncoder(knownFact).asObject.getOrElse(???).add("uri", globalNameEncoder(knownFact.ref.uri))
        )
      }*/

      // No knownFactDecoder (not needed yet)
    }

    // re-export implicits (implicitness isn't transitive in Scala)
    implicit val sfactEncoder: Encoder[SFact] = FactCodecs.sfactEncoder
    implicit val sfactDecoder: Decoder[SFact] = FactCodecs.sfactDecoder
    // implicit val knownFactEncoder: Encoder[SFact with SKnownFact] = FactCodecs.knownFactEncoder

    // vvvvvvv CAREFUL WHEN REMOVING IMPORTS (IntelliJ might wrongly mark them as unused)
    import PathCodecs._
    import SOMDocCodecs._

    import io.circe.generic.auto._
    import io.circe.generic.semiauto._
    // ^^^^^^^ END

    implicit val factReferenceEncoder: Encoder[FactReference] = deriveEncoder
    implicit val factReferenceDecoder: Decoder[FactReference] = deriveDecoder
    implicit val scrollApplicationDecoder: Decoder[SScrollApplication] = deriveDecoder
    implicit val scrollEncoder: Encoder[SScroll] = deriveEncoder

    // [[SScrollAssignments]] codecs
    //
    private val originalScrollAssignmentsEncoder = deriveEncoder[SScrollAssignments]
    private val originalScrollAssignmentsDecoder = deriveDecoder[SScrollAssignments]

    implicit val scrollAssignmentsEncoder: Encoder[SScrollAssignments] = assignments => {
      originalScrollAssignmentsEncoder(assignments).hcursor
        .downField("assignments")
        .values
        .map(values => Json.arr(values.toSeq : _*))
        .getOrElse(throw new Exception("This should not occur, did you change the signature/field names of SScrollAssignments?"))
    }
    implicit val scrollAssignmentsDecoder: Decoder[SScrollAssignments] = (c: HCursor) => {
      originalScrollAssignmentsDecoder(
        Json.fromJsonObject(JsonObject.singleton("assignments", c.value)).hcursor
      )
    }

    private object CheckingError {
      private[datastructures] object config {
        implicit val errorConfig: Configuration = kindedJsonConfig(Map(
          classOf[SInvalidScrollAssignment] -> "invalidAssignment",
          classOf[SNonTotalScrollApplication] -> "nonTotal",
          classOf[SMiscellaneousError] -> "unknown"
        ))
      }
      // vvvvvvv CAREFUL WHEN REMOVING IMPORTS (IntelliJ might wrongly mark them as unused)
      import config._
      // ^^^^^^^ END

      val checkingErrorEncoder: Encoder[SCheckingError] = deriveConfiguredEncoder
      // no decoder for [[SCheckingError]] needed at the moment
    }

    implicit val checkingErrorEncoder: Encoder[SCheckingError] = CheckingError.checkingErrorEncoder

    implicit val dynamicScrollInfoEncoder: Encoder[SDynamicScrollApplicationInfo] = deriveEncoder
    implicit val dynamicScrollInfoDecoder: Decoder[SDynamicScrollApplicationInfo] = deriveDecoder
  }
}

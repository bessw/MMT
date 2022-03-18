package info.kwarc.mmt.api.uom

import info.kwarc.mmt.api._
import objects._
import modules._
import symbols._

import Theory._

/**
 * a theory written directly in Scala
 */
abstract class RealizedTheory(mt: Option[MPath]) extends Theory(null, null, mt, noParams, noBase) with SemanticObject {
   // getClass only works inside the body, i.e., after initializing the super class
   // so we make the constructor arguments null and override afterwards
   // this will fail if one of the arguments is accessed during initialization of the superclass
   override val parent = mpath.parent
   override val name = mpath.name

   /** the body of this theory
    *
    *  this is maintained lazily so that it can be built by the initializer of inheriting classes
    *  see the classes generated by [[uom.ScalaExporter]] for examples
    */
   private var _lazyBody : List[() => Unit] = Nil
   /** takes a argument and adds it to the _lazyBody */
   protected def declare(b: => Unit) {
      _lazyBody ::= (() => b)
   }
   /**
    * creates the actual body of this class from the body
    */
   override def init {
      try {
         _lazyBody.reverseMap {b => b()}
      } finally {
         _lazyBody = Nil // make sure, nothing gets added twice
      }
   }
}


/**
 * a model of an MMT theory written in Scala
 */
abstract class RealizationInScala extends RealizedTheory(None) {
   //override val parent = GenericScalaExporter.scalaToDPath(getClass.getPackage.getName)
   /*override val name = {
      val cls = getClass
      var n = cls.getName.substring(cls.getPackage.getName.length+1)
      if (n.endsWith("$"))
         n = n.substring(0,n.length-1)
      LocalName(n)
   }*/

   /** the modelled theory */
   val _domain: TheoryScala

   /** the MMT URI of the modelled theory */
   lazy val _path = _domain._path
   /** the name of the modelled theory */
   lazy val _name = _domain._name
   
   /** the HOAS apply operators in applications */
   val under: List[GlobalName]

   declare {add(symbols.PlainInclude(_path, path))}

   protected def realizes(b: => Unit) {
      declare {b}
   }

   /**
    * adds a [[RuleConstant]] realizing r.head as r to this model
    * @param r a BreadthRule for n-ary operators and an AbbrevRule for nullary operators
    */
   def rule(r: SyntaxDrivenRule) {
     rule(r, "realize")
   }
   /**
    * adds a [[RuleConstant]] whose name is derived from the head of a rule
    */
   private def rule(r: SyntaxDrivenRule, tag: String) {
      val rc = {
        val name = r.head.name / tag
        val tp = OMS(r.head)
        symbols.RuleConstant(toTerm, name, tp, Some(r)) //TODO nicer type
      }
      add(rc)
   }

   private var _axioms: List[(String, () => Term, Term => Boolean)] = Nil
   def _assert(name: String, term: () => Term, assertion: Term => Boolean) {_axioms ::= ((name, term, assertion))}
   def _test(controller: frontend.Controller, log: String => Unit) {
      _axioms.foreach {
         case (n, tL, a) =>
           log("test case " + n)
           try {
             val t = tL()
             //log("term: " + controller.presenter.asString(t))
             val tS = controller.simplifier(t, SimplificationUnit(Context(_path), false, true))
             //log("simplified: " + controller.presenter.asString(tS))
             val result = a(tS)
             log((if (result) "PASSED" else "FAILED") + "\n")
           } catch {
             case e: Error => log("error :" + e.toString + "\n")
           }
      }
   }


   // ******************* helper functions to create realizations in human-written subclasses

   private val invertTag = "invert"

   /**
    * adds a rule for implementing a type
    */
   def universe(rt: RealizedType) {
      rule(rt)
   }
   def universe(synType: GlobalName)(semType: SemanticType) {
      universe(new RealizedType(OMS(synType), semType))
   }

   /** look up the realized type for a given operator */
   protected def getRealizedType(synType: GlobalName): RealizedType = {
     getDeclarations foreach {
       case rc: RuleConstant => rc.df match {
         case Some(rt: RealizedType) if rt.synType == OMS(synType) => return rt
         case _ =>
       }
       case _ =>
     }
     throw AddError(this, s"realization for syntactic type $synType not found while trying to add a rule")
   }

   /** adds a rule for implementing a function symbol (argument and return types must have been added previously) */
   def function(op:GlobalName, aTypesN: List[GlobalName], rTypeN: GlobalName)(fun: FunctionN) {
     if (aTypesN.length != fun.arity) {
         throw AddError(this, "function realizing " + op + " of arity " + aTypesN.length + " has wrong arity " + fun.arity)
     }
     val rType = getRealizedType(rTypeN)
     val aTypes = aTypesN map {n => getRealizedType(n)}
     if (fun.arity == 0) {
       val lit = rType of fun.app(Nil)
       val ar = new AbbrevRule(op, lit)
       rule(ar)
       val inv = new InverseOperator(op) {
           def unapply(l: OMLIT) = {
              if (l == lit) Some(Nil)
              else None
           }
       }
       rule(inv, invertTag)
     } else {
        val synTp = SynOpType(under, aTypes.map(_.synType), rType.synType)
        val semOp = new SemanticOperator(aTypes.map(_.semType) =>: rType.semType) {
          def apply(args: List[Any]) = fun.app(args)
        }
        val semTp = semOp.getTypes.head
        val ro = new RealizedOperator(op, synTp, semOp, semTp)
        rule(ro)
     }
   }

   /** typed variant, experimental, not used by ScalaExporter yet */
   def functionT[U,V](op:GlobalName, argType1: RepresentedRealizedType[U], rType: RepresentedRealizedType[V])(comp: U => V) {
      val synTp = SynOpType(under, List(argType1.synType), rType.synType)
      val semOp = new SemanticOperator(List(argType1.semType) =>: rType.semType) {
        def apply(args: List[Any]) = args(0) match {
            case argType1.semType(x) => comp(x)
            case _ => throw ImplementationError("illegal arguments")
        }
      }
      val semTp = semOp.getTypes.head
      val ro = new RealizedOperator(op, synTp, semOp, semTp)
      rule(ro)
   }

   /** the partial inverse of a unary operator */
   def inverse(op: GlobalName, aTypeN: GlobalName, rTypeN: GlobalName)(comp: Any => Option[Any]) {
     val rType = getRealizedType(rTypeN)
     val List(aType) = List(aTypeN) map {n => getRealizedType(n)}
     val inv = new InverseOperator(op) {
        def unapply(l: OMLIT) = l match {
            case rType(y) => comp(y) match {
               case Some(x) => Some(List(aType of x))
               case None => None
            }
            case _ => None
        }
      }
      rule(inv, invertTag)
   }
   /** the partial inverse of an n-ary operator */
   def inverse(op: GlobalName, aTypesN: List[GlobalName], rTypeN: GlobalName)(fun: InvFunctionN) {
      if (aTypesN.length != fun.arity) {
         throw AddError(this, "function realizing " + op + " of arity " + aTypesN.length + " has wrong arity " + fun.arity)
      }
      val rType = getRealizedType(rTypeN)
      val aTypes = aTypesN map {n => getRealizedType(n)}
      val inv = new InverseOperator(op) {
         def unapply(l: OMLIT) = l match {
            case rType(y) => fun.app(y) match {
               case Some(xs) =>
                 if (xs.length != aTypes.length)
                   None
                 else {
                   val argsV = (aTypes zip xs) map {case (aT,x) => aT of x}
                   Some(argsV)
                 }
               case None => None
            }
            case _ => None
         }
      }
      rule(inv, invertTag)
   }
}

/** a flexary function, used by [[RealizationInScala]] */
class FunctionN private (val arity: Int, val app: List[Any] => Any)
object FunctionN {
   implicit def from0(f: () => Any) = new FunctionN(0, l => f())
   implicit def from1(f: Any => Any) = new FunctionN(1, l => f(l(0)))
   implicit def from2(f: (Any,Any) => Any) = new FunctionN(2, l => f(l(0),l(1)))
   implicit def from3(f: (Any,Any,Any) => Any) = new FunctionN(3, l => f(l(0),l(1),l(2)))
   implicit def from4(f: (Any,Any,Any,Any) => Any) = new FunctionN(4, l => f(l(0),l(1),l(2),l(3)))
   implicit def from5(f: (Any,Any,Any,Any,Any) => Any) = new FunctionN(5, l => f(l(0),l(1),l(2),l(3),l(4)))
   implicit def from6(f: (Any,Any,Any,Any,Any,Any) => Any) = new FunctionN(6, l => f(l(0),l(1),l(2),l(3),l(4),l(5)))
   implicit def from7(f: (Any,Any,Any,Any,Any,Any,Any) => Any) = new FunctionN(7, l => f(l(0),l(1),l(2),l(3),l(4),l(5),l(6)))
   implicit def from8(f: (Any,Any,Any,Any,Any,Any,Any,Any) => Any) = new FunctionN(8, l => f(l(0),l(1),l(2),l(3),l(4),l(5),l(6),l(7)))
}

/** inverse of a flexary function, used by [[RealizationInScala]] */
class InvFunctionN private (val arity: Int, val app: Any => Option[List[Any]])
object InvFunctionN {
   implicit def from1(f: Any => Option[Any]) = new InvFunctionN(1, l => f(l).map(u => List(u)))
   implicit def from2(f: Any => Option[(Any,Any)]) = new InvFunctionN(2, l => f(l).map(u => List(u._1,u._2)))
}

trait TheoryScala {
   val _base : DPath
   val _name : LocalName
   lazy val _path = _base ? _name
}

trait ConstantScala {
   val parent: MPath
   val name: String
   lazy val path: GlobalName = parent ? name
   lazy val term = objects.OMID(path)
}

class UnaryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(arg: Term) = path(arg)
   def unapply(t: Term) = t match {
      case OMA(OMS(this.path), List(a)) => Some(a)
      case _ => None
   }
}
class BinaryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(arg1: Term, arg2: Term) = path(arg1, arg2)
   def unapply(t: Term) = t match {
      case OMA(OMS(this.path), List(a1, a2)) => Some((a1,a2))
      case _ => None
   }
}

class TernaryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(arg1: Term, arg2: Term, arg3: Term) = path(arg1, arg2, arg3)
   def unapply(t: Term) = t match {
      case OMA(OMS(this.path), List(a1, a2, a3)) => Some((a1,a2,a3))
      case _ => None
   }
}

class FouraryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(arg1: Term, arg2: Term, arg3: Term, arg4: Term) = path(arg1, arg2, arg3, arg4)
   def unapply(t: Term) = t match {
      case OMA(OMS(this.path), List(a1, a2, a3, a4)) => Some((a1,a2,a3,a4))
      case _ => None
   }
}

class FlexaryConstantScala(val parent: MPath, val name: String) extends ConstantScala {
   def apply(args: Term*) = path(args:_*)
   def unapplySeq(t: Term): Option[Seq[Term]] = t match {
      case OMA(OMS(this.path), args) => Some(args)
      case _ => None
   }
}

trait ViewScala extends TheoryScala

object ConstantScala {
   implicit def constantToTerm(c: ConstantScala) = c.term
}

trait DocumentScala {
   private var realizations: List[RealizationInScala] = Nil
   private var documents : List[DocumentScala] = Nil
   def addRealization(r: RealizationInScala) {
      realizations ::= r
   }
   def addDocument(d: DocumentScala) {
      documents ::= d
   }
   def test(controller: frontend.Controller, log: String => Unit) {
      documents.foreach {_.test(controller, log)}
      realizations.foreach {_._test(controller, log)}
   }
}


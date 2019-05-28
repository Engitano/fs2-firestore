package com.engitano.fs2firestore.queries

import shapeless.{ReprTypes, SingletonTypeUtils}

import scala.reflect.macros.whitebox

@macrocompat.bundle
class QueryMacros(val c: whitebox.Context) extends SingletonTypeUtils with ReprTypes {
  import c.universe._

  def mkWitness(sTpe: Type, s: Tree): Tree = {
    q"""
      _root_.shapeless.Witness.mkWitness[$sTpe]($s.asInstanceOf[$sTpe])
    """
  }

  def mkOps(field: String,  w: Type): Tree = {
    val name = TypeName(c.freshName("anon$"))

    q"""
      {
        final class $name extends ColumnOps {
          type Col = $w
          val columnName = $field
        }
        new $name
      }
    """
  }

  def buildOps(s: Tree): Tree  = (s.tpe, s) match {
    case (SymTpe, LiteralSymbol(sym)) =>
      mkOps(sym, SingletonSymbolType(sym))
  }
}
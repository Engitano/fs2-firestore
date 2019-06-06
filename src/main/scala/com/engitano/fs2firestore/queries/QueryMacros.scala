/*
 * Copyright (c) 2019 Engitano
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.engitano.fs2firestore.queries

import shapeless.{HList, ReprTypes, SingletonTypeUtils}

import scala.reflect.macros.whitebox

@macrocompat.bundle
class QueryMacros(val c: whitebox.Context) extends SingletonTypeUtils with ReprTypes {
  import c.universe._

  def mkWitness(sTpe: Type, s: Tree): Tree = {
    q"""
      _root_.shapeless.Witness.mkWitness[$sTpe]($s.asInstanceOf[$sTpe])
    """
  }

  def mkOps(field: String,  w: Type, r: Type): Tree = {
    val name = TypeName(c.freshName("anon$"))

    q"""
      {
        final class $name extends com.engitano.fs2firestore.queries.syntax.ColumnOps {
          type R = $r
          type Col = $w
          val columnName = $field
        }
        new $name
      }
    """
  }

  def buildOps[Repr <: HList : WeakTypeTag](s: Tree): Tree  = (s.tpe, s) match {
    case (SymTpe, LiteralSymbol(sym)) =>
      mkOps(sym, SingletonSymbolType(sym), weakTypeOf[Repr])
  }
}
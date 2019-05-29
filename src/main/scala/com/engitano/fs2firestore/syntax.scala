package com.engitano.fs2firestore

import shapeless.{::, BasisConstraint, HList, HNil, NotContainsConstraint}
import shapeless.labelled.FieldType
import shapeless.ops.record.Keys

import scala.annotation.implicitNotFound

object syntax {

  implicit def pathHelpersFromConfig(cfg: FirestoreConfig) = new PathHelpers(cfg)

  private[fs2firestore] class PathHelpers(cfg: FirestoreConfig) {
    def rootDb = s"projects/${cfg.project}/databases/${cfg.database}"

    def rootDocuments = s"projects/${cfg.project}/databases/${cfg.database}/documents"

    def collectionGroupPath(collectionId: String) =
      s"$rootDb/collectionGroups/$collectionId"

    def collectionPath(collectionId: String) =
      s"${rootDocuments}/$collectionId"

    def collectionPath[T: CollectionFor]: String = collectionPath(CollectionFor[T].collectionName)

    def documentName[T: CollectionFor : IdFor](t: T): String =
      documentName[T](IdFor[T].getId(t))

    def documentName[T: CollectionFor](id: String): String = documentName(CollectionFor[T].collectionName, id)

    def documentName(collectionId: String, id: String) =
      s"$rootDocuments/$collectionId/$id"
  }

  trait ImplicitHelpers {
    implicit def defaultHasProperty[ColRepr <: HList, K, V](implicit ev: BasisConstraint[FieldType[K, V] :: HNil, ColRepr]) =
      new HasProperty[ColRepr, K, V] {}

    implicit def defaultHasKey[ColRepr <: HList, TKeys <: HList, K](implicit ke: Keys.Aux[ColRepr, TKeys], ev: BasisConstraint[K :: HNil, TKeys]) =
      new HasKey[ColRepr, K] {}

    implicit def defaultNotHasKey[Keys <: HList,  K](implicit ev: NotContainsConstraint[Keys, K]) =
      new NotHasKey[Keys, K] {}
  }

  @implicitNotFound("""Implicit not found: HasProperty[ColRepr, K, V]
Cannot prove that your collection type contains a property with key ${K} with of value type: ${V}.
Check query keys and types.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
  sealed abstract class HasProperty[ColRepr, K, V] protected ()

  @implicitNotFound("""Implicit not found: HasKey[ColRepr, K, V]
Cannot prove that your collection type contains a property with key ${K}.
Check query key.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
  sealed abstract class HasKey[ColRepr, K] protected ()

  @implicitNotFound("""Implicit not found: NotHasKey[ColRepr, K, V]
Cannot prove that your collection does not already contains a property with key ${K}.
Check all index keys.
Check that you have imported com.engitano.fs2firestore.queries.syntax._""")
  sealed abstract class NotHasKey[Keys, K] protected ()


}

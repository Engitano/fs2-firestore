package com.engitano.fs2firestore

object Marshalling extends marshalling

trait marshalling
  extends LowPriorityValueMarshallers
  with LowPriorityDocumentMarshallers
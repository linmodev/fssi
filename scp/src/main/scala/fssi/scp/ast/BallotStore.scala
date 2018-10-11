package fssi
package scp
package ast

import bigknife.sop._
import bigknife.sop.macros._
import bigknife.sop.implicits._

import fssi.scp.types._

/** save ballot protocol processing status
  */
@sp trait BallotStore[F[_]] {
  // ballot state maintainence
  // current phase, 𝜑
  def getCurrentPhase(nodeId: NodeID, slotIndex: BigInt): P[F, Ballot.Phase]                         // 𝜑
  def getCurrentBallot[A <: Value](nodeId: NodeID, slotIndex: BigInt): P[F, Option[Ballot[A]]]       // b
  def getPreparedBallot[A <: Value](nodeId: NodeID, slotIndex: BigInt): P[F, Option[Ballot[A]]]      // p
  def getPreparedPrimeBallot[A <: Value](nodeId: NodeID, slotIndex: BigInt): P[F, Option[Ballot[A]]] // p'
  def getLowestBallot[A <: Value](nodeId: NodeID, slotIndex: BigInt): P[F, Option[Ballot[A]]]        // c, commit
  def getHighestBallot[A <: Value](nodeId: NodeID, slotIndex: BigInt): P[F, Option[Ballot[A]]]       // h
  def getNextValueBallot[A <: Value](nodeId: NodeID, slotIndex: BigInt): P[F, Option[Ballot[A]]]     // z mValueOverride
  def getLatestBallotMessages(): P[F, Map[NodeID, Message]]                                          // M

  def getLatestBallotMessage(nodeId: NodeID): P[F, Option[Message]] = getLatestBallotMessages().map(_.get(nodeId))

  // temp state
  def getLastEmittedEnvelope(nodeId: NodeID): P[F, Option[Envelope]]
  def updateLastEmmitedEnvelop(nodeId: NodeID, envelope: Envelope): P[F, Unit]
  def getHeardFromQuorum(nodeId: NodeID, slotIndex: BigInt): P[F, Boolean]
  def updateHeardFromQuorum(nodeId: NodeID, slotIndex: BigInt, heard: Boolean): P[F, Unit]

  def isInExternalizePhase(nodeId: NodeID, slotIndex: BigInt): P[F, Boolean] =
    getCurrentPhase(nodeId, slotIndex).map(x =>
      (x != Ballot.preparePhase) && (x != Ballot.confirmPhase))

  /** update local state
    */
  def needUpdateBallot[A <: Value](newBallot: Ballot[A],
                                   nodeId: NodeID,
                                   slotIndex: BigInt): P[F, Boolean]

  def updateCurrentBallot[A <: Value](newBallot: Ballot[A], nodeId: NodeID, slotIndex: BigInt): P[F, Unit]
  def updateLatestBallotMessage(nodeId: NodeID, message: Message): P[F, Unit]

  def checkCurrentStateInvariants(): P[F, Either[Throwable, Unit]]

  // based on current state , create envelope to emit
  def buildPrepareMessage[A <: Value](): P[F, Message.Prepare[A]]
  def buildConfirmMessage[A <: Value](): P[F, Message.Confirm[A]]
  def buildExternalizeMessage[A <: Value](): P[F, Message.Externalize[A]]
}

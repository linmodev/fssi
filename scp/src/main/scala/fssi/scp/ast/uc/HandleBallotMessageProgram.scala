package fssi.scp
package ast
package uc

import types._
import components._

import bigknife.sop._
import bigknife.sop.implicits._

trait HandleBallotMessageProgram[F[_]] extends SCP[F] with BumpStateProgram[F] {
  import model.nodeService._
  import model.nodeStore._
  import model.applicationService._
  import model.logService._

  def handleBallotMessage(nodeId: NodeID,
                          slotIndex: SlotIndex,
                          previousValue: Value,
                          envelope: Envelope[Message.BallotMessage]): SP[F, Boolean] = {

    //
    val statement = envelope.statement
    val message   = statement.message
    for {
      values   <- valuesFromBallotMessage(statement.message)
      validity <- validateValues(nodeId, slotIndex, values)
      _        <- info(s"[$nodeId][$slotIndex] application checked ballot message: $validity")
      r <- ifM(validity == Value.Validity.Invalid, false) {
        for {
          phase  <- currentBallotPhase(nodeId, slotIndex)
          commit <- currentCommitBallot(nodeId, slotIndex)
          invalidEnvelope = phase == Ballot.Phase.Externalize && commit.isDefined && commit.get.value != message.workingBallot.value
          _ <- ifThen(invalidEnvelope)(removeEnvelope(nodeId, slotIndex, envelope))
          _ <- ifThen(phase != Ballot.Phase.Externalize)(
            advanceSlot(nodeId, slotIndex, previousValue, statement))
        } yield !invalidEnvelope
      }
    } yield r
  }

  def advanceSlot(nodeId: NodeID,
                  slotIndex: SlotIndex,
                  previousValue: Value,
                  statement: Statement[Message.BallotMessage]): SP[F, Unit] = {

    //attemp bump to the latest un-accepted ballot
    // we can got a set of counters from envelopes: [3,5,6,7,9,10]
    // find a smallest,unaccepted counter, but is greater than currentBallot's counter.
    def isCounterAccepted(n: Int): SP[F, Boolean] = {
      for {
        aheadNodes <- nodesAheadBallotCounter(nodeId, slotIndex, n)
        q          <- isVBlocking(nodeId, aheadNodes)
        _          <- info(s"[$nodeId][$slotIndex] accepted counter($n): $q")
      } yield q
    }
    def isCounterNotAccepted(n: Int): SP[F, Boolean] = isCounterAccepted(n).map(!_)
    def attemptBump: SP[F, Boolean] = {
      for {
        phase <- currentBallotPhase(nodeId, slotIndex)
        result <- ifM(phase == Ballot.Phase.Externalize, false) {
          for {
            counters <- allCountersFromBallotEnvelopes(nodeId, slotIndex)
            r0 <- ifM(counters.isEmpty, false) {
              for {
                b <- currentBallot(nodeId, slotIndex)
                targetCounter = b.map(_.counter).getOrElse(0)
                r00 <- ifM(counters.head < targetCounter, false) {
                  for {
                    r000 <- ifM(isCounterNotAccepted(targetCounter), false.pureSP[F]) {
                      //(result, found not accepted):(Boolean, Boolean)
                      counters
                        .foldLeft((false, false).pureSP[F]) { (acc, n) =>
                          for {
                            pre <- acc
                            r0000 <- ifM(pre._2, pre) {
                              for {
                                notAccepted <- isCounterNotAccepted(n)
                                r00000 <- ifM(notAccepted.pureSP[F],
                                              abandonBallot(nodeId, slotIndex, previousValue, n)
                                                .map((_, true)))(acc)
                              } yield r00000
                            }
                          } yield r0000
                        }
                        .map(_._1)
                    }
                  } yield r000
                }
              } yield r00
            }
          } yield r0
        }
      } yield result
    }
    // return bumped, but loop until return false
    def attemptBumpMore: SP[F, Boolean] = {
      def _loop(acc: Boolean): SP[F, Boolean] =
        for {
          _ <- info(s"[$nodeId][$slotIndex] attemp bump the smallest un-accepted ballot")
          b <- attemptBump
          _ <- ifM(b.pureSP[F], attemptBump)(false.pureSP[F])
        } yield acc || b

      _loop(false)
    }

    for {
      _                <- currentMessageLevelUp(nodeId, slotIndex)
      _                <- info(s"[$nodeId][$slotIndex] attemp accept prepare")
      prepareAccepted  <- attemptAcceptPrepare(nodeId, slotIndex, previousValue, statement)
      _                <- info(s"[$nodeId][$slotIndex] attemp confirm prepare")
      prepareConfirmed <- attemptConfirmPrepare(nodeId, slotIndex, previousValue, statement)
      _                <- info(s"[$nodeId][$slotIndex] accept commit prepare")
      commitAccepted   <- attemptAcceptCommit(nodeId, slotIndex, previousValue, statement)
      _                <- info(s"[$nodeId][$slotIndex] confirm commit prepare")
      commitConfirmed  <- attemptConfirmCommit(nodeId, slotIndex, previousValue, statement)
      didWork = prepareAccepted || prepareConfirmed || commitAccepted || commitConfirmed
      bumped <- ifM(currentMessageLevel(nodeId, slotIndex).map(_ == 1), attemptBumpMore)(
        false.pureSP[F])
      _ <- ifThen(didWork || bumped) {
        for {
          unEmitted <- currentUnEmittedBallotMessage(nodeId, slotIndex)
          _         <- ifThen(unEmitted.isDefined)(emit(nodeId, slotIndex, previousValue, unEmitted.get))
        } yield ()
      }
      _ <- currentMessageLevelDown(nodeId, slotIndex)
    } yield ()
  }

  def attemptAcceptPrepare(nodeId: NodeID,
                           slotIndex: SlotIndex,
                           previousValue: Value,
                           hint: Statement[Message.BallotMessage]): SP[F, Boolean]
  def attemptConfirmPrepare(nodeId: NodeID,
                            slotIndex: SlotIndex,
                            previousValue: Value,
                            hint: Statement[Message.BallotMessage]): SP[F, Boolean]
  def attemptAcceptCommit(nodeId: NodeID,
                          slotIndex: SlotIndex,
                          previousValue: Value,
                          hint: Statement[Message.BallotMessage]): SP[F, Boolean]
  def attemptConfirmCommit(nodeId: NodeID,
                           slotIndex: SlotIndex,
                           previousValue: Value,
                           hint: Statement[Message.BallotMessage]): SP[F, Boolean]
}

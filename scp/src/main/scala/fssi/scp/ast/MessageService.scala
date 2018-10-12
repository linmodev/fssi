package fssi
package scp
package ast

import bigknife.sop._
import bigknife.sop.macros._
import bigknife.sop.implicits._

import fssi.scp.types._

@sp trait MessageService[F[_]] {
  /** create nominate message envelope
    */
  def createNominationEnvelop[A <: Value](nodeId: NodeID,
                                          slotIndex: BigInt,
                                          quorumSet: QuorumSet,
                                          votes: Set[A],
                                          accepted: Set[A]): P[F, Envelope]

  def createBallotEnvelope(nodeId: NodeID, slotIndex: BigInt, quorumSet: QuorumSet, message: Message): P[F, Envelope]

  def emitEnvelope(envelope: Envelope): P[F, Unit]

  def verifySign(envelope: Envelope): P[F, Boolean]
  def hasBeenTampered(envelope: Envelope): P[F, Boolean] = verifySign(envelope).map(!_)

  /** check the statement to see if it's sane
    * @param receiver current node, which recieved message involving the statement, if the statement's sender is self, 
    *     for Message.Prepare, the ballot's counter can be 0; in other situations , 0 is not allowed.
    * @see BallotProtocol.cpp, line 247 BallotProtocol::isStatementSane(SCPStatement const& st, bool self)
    */
  def isStatementSane(receiver: NodeID, statement: Statement): P[F, Boolean]

  /** get working ballot. get b.
    * @see BallotProtocol.cpp line 1580, BallotProtocol::getWorkingBallot(SCPStatement const& st)
    */
  def getWorkingBallot[A <: Value](message: Message): P[F, Option[Ballot[A]]] // current ballot, b

  /** computes a list of candidate values that may have been prepared
    * @see BallotProtocol.cpp line 677
    */
  def getPrepareCandidates[A <: Value](message: Message): P[F, Set[Ballot[A]]]

  /** sort ballots
    */
  def sortBallots[A <: Value](ballots: Vector[Ballot[A]]): P[F, Vector[Ballot[A]]]

  /** compare two ballots a and b, if a > b return 1(or anything > 0) 
    *                              else if a < b return -1 (or anything < 0)
    *                              else return 0
    */
  def compareBallots[A <: Value](a: Ballot[A], b: Ballot[A]): P[F, Int]

  /** is two ballot compatible?
    */
  def isCompatible[A <: Value](a: Ballot[A], b: Ballot[A]): P[F, Boolean]

}

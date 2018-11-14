package fssi
package interpreter
import fssi.ast.Consensus
import fssi.interpreter.Setting.CoreNodeSetting
import fssi.interpreter.scp.{BlockValue, SCPEnvelope, SCPSupport}
import fssi.scp._
import fssi.scp.types.SlotIndex
import fssi.types.{ReceiptSet, TransactionSet}
import fssi.types.base.{Hash, Timestamp, WorldState}
import fssi.types.biz.Node.ConsensusNode
import fssi.types.biz.{Block, ConsensusAuxMessage, Receipt, Transaction}
import utils._

class ConsensusHandler
    extends Consensus.Handler[Stack]
    with SCPSupport
    with UnsignedBytesSupport
    with LogSupport {

  override def initialize(node: ConsensusNode): Stack[Unit] = Stack { setting =>
    setting match {
      case coreNodeSetting: CoreNodeSetting =>
        val consensusConfig                                   = coreNodeSetting.config.consensusConfig
        implicit val scpSetting: fssi.scp.interpreter.Setting = resolveSCPSetting(consensusConfig)
        Portal.initialize
      case _ =>
    }
  }

  override def destroy(): Stack[Unit] = Stack {}

  override def tryAgree(transaction: Transaction,
                        receipt: Receipt,
                        lastDeterminedBlock: Block,
                        currentWorldState: WorldState): Stack[Unit] = Stack {
    case coreNodeSetting: CoreNodeSetting =>
      val consensusSetting                                  = coreNodeSetting.config.consensusConfig
      implicit val scpSetting: fssi.scp.interpreter.Setting = resolveSCPSetting(consensusSetting)
      val nodeId                                            = scpSetting.nodeId
      val chainId                                           = coreNodeSetting.config.chainId
      val height                                            = lastDeterminedBlock.height + 1
      val slotIndex                                         = SlotIndex(height)
      val preWorldState                                     = lastDeterminedBlock.curWorldState
      val transactions                                      = TransactionSet(transaction)
      val receipts                                          = ReceiptSet(receipt)
      val timestamp                                         = Timestamp(System.currentTimeMillis())
      val block = Block(height,
                        chainId,
                        preWorldState,
                        currentWorldState,
                        transactions,
                        receipts,
                        timestamp,
                        Hash.empty)
      val hash       = Hash(crypto.hash(calculateUnsignedBlockBytes(block)))
      val blockValue = BlockValue(block.copy(hash = hash))
      Portal.handleRequest(nodeId, slotIndex, BlockValue(lastDeterminedBlock), blockValue)
      log.debug(s"try to agree block value: $blockValue")
    case _ =>
  }

  override def processMessage(message: ConsensusAuxMessage,
                              lastDeterminedBlock: Block): Stack[Unit] = Stack {
    case coreNodeSetting: CoreNodeSetting =>
      message match {
        case SCPEnvelope(envelope) =>
          log.debug(s"handling scp envelope: $envelope")
          val consensusSetting = coreNodeSetting.config.consensusConfig
          implicit val scpSetting: fssi.scp.interpreter.Setting =
            resolveSCPSetting(consensusSetting)
          val nodeId        = envelope.statement.from
          val slotIndex     = envelope.statement.slotIndex
          val previousValue = BlockValue(lastDeterminedBlock)
          Portal.handleEnvelope(nodeId, slotIndex, envelope, previousValue)
          log.debug(s"handled scp envelope: $envelope")
      }
  }
}

object ConsensusHandler {
  val instance = new ConsensusHandler

  trait Implicits {
    implicit val consensusHandler: ConsensusHandler = instance
  }
}

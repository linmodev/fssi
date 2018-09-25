package fssi
package tool
package object handler {
  object createAccount             extends CreateAccountHandler
  object createChain               extends CreateChainHandler
  object createTransferTransaction extends CreateTransferTransactionHandler
  object createDeployTransaction   extends CreateDeployTransactionHandler
  object createRunTransaction      extends CreateRunContractTransactionHandler
  object compileContract           extends CompileContractHandler

}

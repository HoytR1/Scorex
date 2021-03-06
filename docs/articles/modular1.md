On the Way to a Modular Cryptocurrency, Part 1: Generic Block Structure
========================================================================

The Protocols Mess Problem
--------------------------

In a code of a today's cryptocurrency logical parts are very couply tied making codebase hard to understand
  and change. This series of articles shows how the problem could be solved by introducing separate inter-changeable 
  injectable modules. Preliminary article ["The Architecture Of A Cryptocurrency"](components.md) describes 
  possible modules in a cryptocurrency design. 
  
This article, the first in the series describes how a block structure and a block-related functionality 
could be defined agnostic to implementation details of two separate modules, consensus-related and transaction-related.
Code snippets using Scala language are provided.


Generic Block Structure
-------------------------------------

A block consists of: 

1. Pointer to previous block
2. Consensus-related data, e.g. nonce & difficulty target for Bitcoin, generation signature & base target for Nxt.
3. Transactions, the most valuable part of a block for users. Some transactions- or state-related data could
be also included(e.g. Merkle tree root hash for transactions or whole state after block application)
4. Additional useful information: block structure version, timestamp etc 
5. Signature(s)

(Please note [in Bitcoin there's no a signature of a block](https://en.bitcoin.it/wiki/Protocol_documentation#block), 
while we're going to add it to make things more generic) 

Making a new cryptocurrency usually means to replace (2) or (3) or both with something new. So to have an ability to 
make experiments fast we need to introduce some flexible and modular approach to a block structure and corresponding functions.
    
In the first place, we are going to introduce generic *block field* concept wrapping any kind of data with a possibility 
of serialization into json & binary form:

```scala        
abstract class BlockField[T] {
  val name: String
  val value: T

  def json: JsObject
  def bytes: Array[Byte]
}
```

Then we can stack up blockfields into *block*, also introducing abstract ConsensusDataType & TransactionDataType types as
well as abstract references to ConsensusModule & TransactionModule *modules*, where a *module* is a functional 
interface to be replaced with a concrete implementation then:

```scala    
trait Block {
  type ConsensusDataType
  type TransactionDataType
  
  implicit val consensusModule: ConsensusModule[ConsensusDataType]
  implicit val transactionModule: TransactionModule[TransactionDataType]
        
  val consensusDataField: BlockField[ConsensusDataType]
  val transactionDataField: BlockField[TransactionDataType]                 
      
  val versionField: ByteBlockField
  val timestampField: LongBlockField
  val referenceField: BlockIdField
  val signerDataField: SignerDataBlockField    
  ...
}
``` 
      
What both modules could have in common? Well, they are parsing data of a type they are parametrized with, producing
 a blockfield based on data and providing genesis block data details. Let's extract this functionality into the common
 concept:

```scala           
trait BlockProcessingModule[BlockPartDataType] {
   def parseBlockData(bytes: Array[Byte]): BlockField[BlockPartDataType]
   def formBlockData(data: BlockPartDataType): BlockField[BlockPartDataType]
   def genesisData: BlockField[BlockPartDataType]       
}
```

Having this common ground, let's define consensus and transaction interfaces.           
      
Consensus Module
-----------------

What can we do with consensus-related data from a block?

* check whether a block is valid from module's point of view, i.e. whether a block was generated by a right 
kind of participant in a right way

* get block generator(s)(let's not forget about multiple generators possibility, see e.g.   
                           Meni Rosenfeld's Proof-of-Activity proposal http://eprint.iacr.org/2014/452.pdf)
                           
* calculate block generators rewards 

* get a score of a block. Score equals to 1 in case of longest chain rule or some calculated value relative to 
difficulty in case of cumulative difficulty to be used to select best blockchain out of many possible options  
             
Also, we can add a function to generate a block here, taking private key owner(to sign a block) and transaction module
(to form transactional part of a block) as parameters

Considering all the functions, we can encode the interface now:  

```scala
trait ConsensusModule[ConsensusBlockData] extends BlockProcessingModule[ConsensusBlockData] {
  def isValid[TT](block: Block, history: History, state: State)(implicit transactionModule: TransactionModule[TT]): Boolean
  def generators(block: Block): Seq[Account]
  def feesDistribution(block: Block): Map[Account, Long]        
  def blockScore(block: Block, history: History)(implicit transactionModule: TransactionModule[_]): BigInt
  def generateNextBlock[TT](account: PrivateKeyAccount)(implicit transactionModule: TransactionModule[TT]): Future[Option[Block]]
}
```

Transaction Module
------------------
    
We are going to consider a transactional part of a cryptocurrency, the most useful for an end user. An user isn't 
using blockchain directly, querying some state instead:
         
* There's some initial state of the world stated in the first block of a chain( *genesis block* )
* Then each block carries transactions which are atomic world state modifiers
    
Probably [State monad](https://en.wikibooks.org/wiki/Haskell/Understanding_monads/State) could be helpful here, but for 
start(as we are rewriting existing project not using a true functional approach) the state interface is: 

```scala       
trait State {
  def processBlock(block: Block, reversal: Boolean): Unit
}
```
    
Please note no any querying functions are listed in the basic trait, as we are going to make state design stackable. 
For example, if it's possible for a cryptocurrency to support balance querying for an arbitrary account following trait 
   could be mixed with the basic one: 

```scala            
trait BalanceSheet {
  def balance(address: String, confirmations: Int): Long
}
```
                  
to have a concrete interface to be implemented by a cryptocurrency like
      
```scala
trait LagonakiState extends State with BalanceSheet with AccountTransactionsHistory
```
    
In addition to state a history is to be stored as well(to send it to another peer for a reconstruction of a state, at
  least). Please note, history could be in a different form than the blockchain, for example, a blocktree could be
   explicitly stored, or blockchain with addition of block uncles as Ethereum does. I'm not going to provide History interface code here,
    but you can [find it online](https://github.com/ConsensusResearch/Scorex-Lagonaki/blob/master/scorex-basics/src/main/scala/scorex/transaction/History.scala).
      
So a transactional module contains references to concrete implementations of state and history, and few functions able to:
       
* check whether a block is valid from module's point of view(so whether all transactions within a block and transactions 
metadata e.g. Merkle tree root hash are valid)
* extract transactions from a block
* get transactions from unconfirmed pool and add corresponding metadata(on forming a new block) 
* clear duplicates from unconfirmed pool(on getting a block from the network) 
     
     
     
The code reflecting requirements above is:
                    
```scala            
trait TransactionModule[TransactionBlockData] extends BlockProcessingModule[TransactionBlockData]{
              
  val state: State
  val history: History

  def isValid(block: Block): Boolean   
  def transactions(block: Block): Seq[Transaction]            
  def packUnconfirmed(): TransactionBlockData   
  def clearFromUnconfirmed(data: TransactionBlockData): Unit  
  ...
}
```
       
       
The Concrete Implementation
---------------------------

To develop a concrete blockchain-powered product a developer needs to provide concrete implementations of state, history,
consensus & transactions modules to glue them together then in an application. 

To see how that's done in Scorex Lagonaki, take a look into [LagonakiApplication.scala](https://github.com/ConsensusResearch/Scorex-Lagonaki/blob/master/src/main/scala/scorex/app/LagonakiApplication.scala).
While Scorex is the name of an abstract framework, Lagonaki is the name of concrete implementation wiring together:

* SimpleTransactionModule operating with just a sequence of simplest token transfer transactions without any metadata
* Two 100% Proof-of-Stake consensus module implementations, one is Nxt-like, other is Qora-like. It's possible to replace 
one consensus algorithm with another with just a single setting in application.conf.


Further Work
------------

The resulting application wiring together modules is much leaner than before. Some work could be done further though:

* [stackable user APIs when a module provides it's own part of API implementation](modular2.md)  
* stackable P2P protocol

// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._

import cachematic.util.scala.isPowerOfTwo
import cachematic.store.Store
import cachematic.datatypes.{Tag, MainMemoryAddress}

object ParallelCompareState extends ChiselEnum {
  val sIdle, sTagCompare, sComplete = Value
}

/**
  * Parallel tag and data store fetch module (meant for small caches).
  * Hit status and data available on the next clock cycle.
  * TODO: add parametrizeable "wait" delay to account for sense amplifiers and bus charging of the selected SRAM technology.  
  */
class ParallelCompare(numSets: Int, numWays: Int, blockSize: Int, wordSize: Int, private val mmAddrType: MainMemoryAddress) extends Module {
  require(isPowerOfTwo(numSets),    "Number of sets must be positive and a power of 2")
  require(numWays > 0,              "Number of ways must be a positive number")
  require(isPowerOfTwo(blockSize),  "Cache block/line size must be positive and a power of 2")
  require(isPowerOfTwo(wordSize),   "Word size must be positive and a power of 2")

  val widthIdMatchedTag = log2Ceil(numWays)

  val io = IO(new Bundle {
    val mmAddr = Input(mmAddrType.cloneType)

    val isHit = Output(Bool())
    val dataOut = Output(UInt(wordSize.W))
  })

  // Datatypes for convenience
  val tagStoreEntry = new Tag(mmAddrType.numTagBits)
  val dataStoreEntry = Vec(blockSize, UInt(wordSize.W))

  // Tag store control signals
  val enTagStore = Bool()
  val isWriteTagStore = Bool()
  val dataInTagStore = Wire(Vec(numWays, tagStoreEntry))
  val dataOutTagStore = Wire(Vec(numWays, tagStoreEntry))

  // Data store control signals
  val enDataStore = Bool()
  val isWriteDataStore = Bool()
  val dataInDataStore = Wire(Vec(numWays, dataStoreEntry))
  val dataOutDataStore = Wire(Vec(numWays, dataStoreEntry))

  // Submodules
  val tagStore = Module(new Store(numSets, 0, Vec(numWays, tagStoreEntry)))
  val tagMatch = Module(new TagMatch(numWays, tagStoreEntry))
  val dataStore = Module(new Store(numSets, 0, Vec(numWays, dataStoreEntry)))
  val wordMatch = Module(new WordMatch(numWays, blockSize, wordSize, mmAddrType.numBlockOffsetBits))

  tagStore.in.bits.addr := io.mmAddr.setId
  dataStore.in.bits.addr := io.mmAddr.setId

  tagMatch.io.tag := io.mmAddr.tag
  tagMatch.io.vecTags := tagStore.out.bits.dataOut

  wordMatch.io.idWay := tagMatch.io.idWay
  wordMatch.io.vecWords := dataStore.out.bits.dataOut
  wordMatch.io.blockOffset := io.mmAddr.blockOffset

  // TODO: Connect both stores to the control signals of the FSM
  // io.isHit := tagMatch.io.isHit
  // io.dataOut := blockMatch.io.wordOut
}

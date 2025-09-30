// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._
import cachematic.util.scala.isPowerOfTwo
import cachematic.store.Store
import cachematic.datatypes.{Tag, MainMemoryAddress}


object SerialCompareState extends ChiselEnum {
  val sIdle, sTagCompare, sComplete = Value
}

class SerialCompare(numSets: Int, numWays: Int, blockSize: Int, wordSize: Int, private val mmAddrType: MainMemoryAddress) extends Module {
  require(isPowerOfTwo(numSets),    "Number of sets must be positive and a power of 2")
  require(numWays > 0,              "Number of ways must be a positive number")
  require(isPowerOfTwo(blockSize),  "Cache block/line size must be positive and a power of 2")
  require(isPowerOfTwo(wordSize),   "Word size must be positive and a power of 2")

  val widthIdMatchedTag = log2Ceil(numWays)

  val io = IO(new Bundle {
    val mmAddr = Input(mmAddrType.cloneType)
    val valid = Input(Bool())

    val isHit = Output(Bool())
    val dataOut = Output(UInt(wordSize.W))
  })

  // Datatypes for convenience
  val tagStoreEntry = new Tag(mmAddr.numTagBits)
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
  val tagStore = Module(new Store(numSets, numWays, tagStoreEntry))
  val tagMatch = Module(new TagMatch(numWays, tagStoreEntry))
  val dataStore = Module(new Store(numSets, numWays, dataStoreEntry))
  val blockMatch = Module(new WordMatch(numWays, blockSize, wordSize, mmAddr.numBlockOffsetBits))

  // Connect both stores to the set field of the requested main memory address
  tagStore.io.addr := io.mmAddr.setId
  dataStore.io.addr := io.mmAddr.setId

  tagMatch.io.tag := io.mmAddr.tag
  tagMatch.io.vecTags := tagStore.io.dataOut

  blockMatch.io.idWay := tagMatch.io.idWay
  blockMatch.io.vecWords := dataStore.io.dataOut
  blockMatch.io.blockOffset := io.mmAddr.blockOffset

  // io.dataOut := DontCare
  // io.isHit := tagMatch.io.isHit
  // io.dataOut := blockMatch.io.wordOut

/**
  * FSM Controls enable signals to both stores for retreiving cache entries
  */
  val stateReg = RegInit(sIdle)
  val completeReg = RegInit(true.B)

  // State transitions
  switch (stateReg) {
    is (sIdle) {
      when (completeReg && io.valid) {
        stateReg := sTagCompare
      }
    }
    is (sTagCompare) {
      when () { // hit

      } .elsewhen () { // miss

      }

  }

  // State logic
  switch (stateReg) {
    is (sIdle) {
      when (io.in.valid) {
        requestReg := io.in.bits
        inReadyReg := false.B
      }
    }
    is (sPending) {

    }
    is (sReturn) {

    }
  }
}

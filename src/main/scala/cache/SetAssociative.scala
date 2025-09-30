// See README.md for license details.

package cachematic.cache

import chisel3._
import chisel3.util._
import cachematic.util.scala.isPowerOfTwo
import cachematic.store.{TagStore, DataStore}

object CacheControllerState extends ChiselEnum {
  val sIdle, sPending, sInvalidate, sFetch, sReturn = Value
}

/**
  * Has decoupled interface for initiating read/write, and for data output
  * TODO: Factor out different submodules into reusable building blocks for other caches
  */
class SetAssociative(mmSize: Int, blockSize: Int, wordSize: Int, numSets: Int, numWays: Int) extends Module {
  require(mmSize > 0,                         "Main memory size must be a positive number")
  require(isPowerOfTwo(blockSize),            "Cache block/line size must be positive and a power of 2")
  require(isPowerOfTwo(wordSize),             "Word size must be positive and a power of 2")
  require(isPowerOfTwo(numSets),              "Number of sets must be positive and a power of 2")
  require(numWays > 0,                        "Number of ways must be a positive number")
  require(numSets*numWays*blockSize < mmSize, "Cache size must be smaller than the size of addressable main memory")

  val cacheSize = numSets * numWays * blockSize
  val numAddrBits = log2Ceil(mmSize)
  val numBlockOffsetBits = log2(blockSize)
  val numSetBits = log2(numSets)
  val numTagBits = numAddrBits - (numSetBits + numBlockOffsetBits)

  val io = IO(new Bundle {
    val in = Flipped(Decoupled(new CacheInterface(new MainMemoryAddress(numTagBits, numSetBits, numBlockOffsetBits))))
    val out = Decoupled(UInt(wordSize.W))
  })

  val inReadyReg = RegInit(true.B)
  val outValidReg = RegInit(false.B)
  val requestReg = Reg(new CacheInterface(new MainMemoryAddress(numTagBits, numSetBits, numBlockOffsetBits)))
  
  val requestAddr = requestReg.addr

  // TODO:
  val cache = Module(new SerialCompare())

/**
  * FSM
  */
  val stateReg = RegInit(sIdle)

  // State transitions
  switch (stateReg) {
    is (sIdle) {
      when (io.in.valid) {
        stateReg := sPending
      }
    }
    is (sPending) {
      when () { // hit

      } .elsewhen () { // miss

      }
    }
    is (sReturn) {
      when (io.out.ready) {
        stateReg := sIdle
      }
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

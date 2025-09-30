// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._
import cachematic.util.scala.isPowerOfTwo

// TODO: Replace the SyncReadMem module with actual parametrized black-box Verilog OpenRAM module, once defined
// TODO: Add the functionality to mask inputs to write individual byte or word to memory
class Store[T <: Data](numSets: Int, numWays: Int, private val dataType: T) extends Module {
  require(isPowerOfTwo(numSets),  "Number of sets must be positive and a power of 2")
  require(numWays > 0,            "Number of ways must be a positive number")

  val io = IO(new Bundle {
    val addr = Input(UInt(numSets.W))
    val en = Input(Bool())
    val isWrite = Input(Bool())
    val dataIn = Input(Wire(Vec(numWays, dataType.cloneType)))
    val dataOut = Output(Wire(Vec(numWays, dataType.cloneType)))
  })

/**
  * Tag/data store, single-port R/W
  */
  // NOTE: Reads the whole set. Emulates function of SRAM, not construction
  val store = SyncReadMem(numSets, Wire(Vec(numWays, dataType.cloneType)))

  // Returns K-way elements
  io.dataOut := store.readWrite(io.addr, io.dataIn, io.en, io.isWrite)
}

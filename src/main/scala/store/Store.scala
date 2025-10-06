// See README.md for license details.

package cachematic.store

import chisel3._
import chisel3.util._

import cachematic.util.scala.isPowerOfTwo

object StoreState extends ChiselEnum {
  val sIdle, sAccess, sValid, sDone = Value
}

/**
  * Decoupled SRAM interfacing module with parametrizeable delay to match memory technology spec
  *   TODO: Replace the SyncReadMem module with actual parametrized black-box Verilog OpenRAM module, once defined
  *   TODO: Add the functionality to mask inputs to write individual byte or word to memory
  *   TODO: Enable burst transactions (R/W without Idle and Hold state in between if new data is available)
  */
class Store[T <: Data](numSets: Int, numWays: Int, delay: Int, private val dataType: T) extends Module {
  require(isPowerOfTwo(numSets),  "Number of sets must be positive and a power of 2")
  require(numWays > 0,            "Number of ways must be a positive number")
  require(delay > 0,              "SRAM access delay spec must be a positive number")

  import StoreState._

  val in = IO(Flipped(Decoupled(new Bundle {
    val addr = UInt(log2Ceil(numSets).W)
    val isWrite = Bool()
    val dataIn = Vec(numWays, dataType.cloneType)
  })))
  val out = IO(Decoupled(new Bundle {
    val dataOut = Vec(numWays, dataType.cloneType)
  }))

/**
  * Tag/data store, single-port R/W
  *   NOTE: Reads the whole set. Emulates function of SRAM, not construction -> replace with actual SRAM technology
  *     [OpenRAM]: https://escholarship.org/content/qt8x19c778/qt8x19c778_noSplash_b2b3fbbb57f1269f86d0de77865b0691.pdf
  */
  val store = SyncReadMem(numSets, Vec(numWays, dataType.cloneType))

/**
  * FSM (Moore)
  */
  val stateReg = RegInit(sIdle)
  val numDelayBits = if (delay > 1) log2Ceil(delay) else 1
  val delayCounterReg = RegInit(0.U(numDelayBits.W))

  // State transitions
  switch (stateReg) {
    is (sIdle) {
      when (in.valid === true.B) {
        stateReg := sAccess
      }
    }
    is (sAccess) {
      when ((delayCounterReg === (delay-1).U(numDelayBits.W)) && ~in.bits.isWrite) { // read operation -> latch memory output
        stateReg := sValid
      } .elsewhen ((delayCounterReg === (delay-1).U(numDelayBits.W)) && in.bits.isWrite) { // write operation
        stateReg := sDone
      }
    }
    is (sValid) {
      when (out.ready === true.B) { // CPU ready to receive dataOut immediately
        stateReg := sIdle
      } .otherwise { // CPU reads latched dataOut when CPU is ready
        stateReg := sDone
      }
    }
    is (sDone) { // latched memory output
      when (out.ready === true.B) {
        stateReg := sIdle
      }
    }
  }

  // Output logic
  // Memory output is latched until upstream module asserts successful read of the data
  val en = WireDefault(false.B)
  val dataOutReg = Reg(Vec(numWays, dataType.cloneType))
  in.ready := WireDefault(true.B)
  out.valid := WireDefault(false.B)
  out.bits.dataOut := DontCare

  // Returns K-way elements
  when (en) {
    val rwPort = store(in.bits.addr)
    when (in.bits.isWrite) {
      rwPort := in.bits.dataIn
    } .otherwise {
      dataOutReg := rwPort
    }
  }

  switch (stateReg) {
    is (sIdle) {
      delayCounterReg := 0.U
    }
    is (sAccess) {
      delayCounterReg := delayCounterReg + 1.U
      en := true.B
      in.ready := false.B
    }
    is (sValid) {
      out.bits.dataOut := dataOutReg
      in.ready := false.B
      out.valid := true.B
    }
    is (sDone) {
      out.bits.dataOut := dataOutReg
      in.ready := false.B
      out.valid := true.B
    }
  }
}

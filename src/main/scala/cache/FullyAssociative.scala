// See README.md for license details.

package cachematic.cache

import chisel3._
import chisel3.stage.ChiselStage

/**
  * Compute GCD using subtraction method.
  * Subtracts the smaller from the larger until register y is zero.
  * value in register x is then the GCD
  */
class FullyAssociative extends Module {
  val io = IO(new Bundle {
    val in = Input(UInt(4.W))
    val out = Output(UInt(4.W))
  })
  val reg = RegInit(0.U)

  reg := io.in
  io.out := reg
}

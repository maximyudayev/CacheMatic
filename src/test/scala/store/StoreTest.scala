// See README.md for license details.

package cachematic.store

import scala.math._
import org.scalatest.flatspec.AnyFlatSpec

import chisel3._
import chiseltest.simulator.WriteVcdAnnotation
import chiseltest._

/**
  * Unit test of decoupled tag/data store R/W operations, with internal specified delay
  *
  * 8-set, 3-way, single-port SRAM with {6,2,1} clock cycles of latency, and blocks of 2x 8-bit words
  *
  * From a terminal shell use:
  * {{{
  * sbt 'testOnly cachematic.store.StoreTest'
  * }}}
  *
  * TODO: implement test vectors for burst reads/writes
  */
class StoreTest extends AnyFlatSpec with ChiselScalatestTester {
  def step(dut: Store[_ <: Data], n: Int = 1) = {
    dut.clock.step(n)
  }

  def startup(dut: Store[_ <: Data]) = {
    dut.in.bits.isWrite.poke(false.B)
    dut.in.valid.poke(false.B)
    dut.out.ready.poke(false.B)
    dut.out.valid.expect(false.B)
    step(dut)
  }

  def checkIdle(dut: Store[_ <: Data]) = {
    dut.in.ready.expect(true.B)
    dut.out.valid.expect(false.B)
  }

  def initRead(dut: Store[_ <: Data], addr: Int, addr_width: Int) = {
    dut.in.bits.addr.poke(addr.U(addr_width.W))
    dut.in.bits.isWrite.poke(false.B)
    dut.in.valid.poke(true.B)
    dut.out.ready.poke(false.B)
  }

  def initWrite(dut: Store[_ <: Data], addr: Int, addr_width: Int) = {
    dut.in.bits.addr.poke(addr.U(addr_width.W))
    dut.in.bits.isWrite.poke(true.B)
    dut.in.valid.poke(true.B)
    dut.out.ready.poke(false.B)
  }

  def checkOutputValid(dut: Store[_ <: Data]) = {
    dut.in.ready.expect(false.B)
    dut.out.valid.expect(true.B)
  }

  def checkDelayedOutput(dut: Store[_ <: Data], delay: Int) = {
    for (cycles <- 0 until delay) {
      step(dut)
      dut.out.valid.expect(false.B)
    }
    step(dut)
    checkOutputValid(dut)
  }

  def acknowledgeOutput(dut: Store[_ <: Data]) = {
    dut.in.valid.poke(false.B)
    dut.out.ready.poke(true.B)
    step(dut)
  }

  behavior of "Store"

  it should "read data from memory and latch it until CPU is ready" in {
    for (isCpuWaiting <- List(false, true)) {
      for (delay <- List(6, 2, 1)) {
        test(new Store(
          depth = 8,
          delay = delay,
          dataType = Vec(3, Vec(2, UInt(8.W)))
        )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          startup(dut)

          // Should be in Idle
          checkIdle(dut)
          step(dut)

          // Initiate read operation
          initRead(dut, 5, 3)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, delay)

          // Wait in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)
        }
      }
    }
  }

  it should "write data to memory and wait until CPU acknowledgement" in {
    for (isCpuWaiting <- List(false, true)) {
      for (delay <- List(6, 2, 1)) {
        test(new Store(
          depth = 8,
          delay = delay,
          dataType = Vec(3, Vec(2, UInt(8.W)))
        )).withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          startup(dut)

          // Should be in Idle
          checkIdle(dut)
          step(dut)

          // Initiating write operation
          for (way <- 0 until 3) {
            for (block <- 0 until 2) {
              dut.in.bits.dataIn(way)(block).poke(way.U(8.W))
            }
          }
          initWrite(dut, 5, 3)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, delay)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)

          // Initiating read operation
          initRead(dut, 5, 3)

          // Should become valid after internal delay, marked by `out.valid` stalls the CPU
          checkDelayedOutput(dut, delay)

          // Wait in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            step(dut, 3)
            checkOutputValid(dut)
          }

          // Acknowledge output reading
          acknowledgeOutput(dut)

          // Should be in Idle again
          checkIdle(dut)
        }
      }
    }
  }
}

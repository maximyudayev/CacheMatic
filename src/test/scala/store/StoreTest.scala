// See README.md for license details.

package cachematic.store

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import chiseltest.simulator.WriteVcdAnnotation
import chiseltest._
import scala.math._

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

  behavior of "Store" 
  
  it should "read data from memory and latch it until CPU is ready" in {
    for (isCpuWaiting <- List(false, true)) {
      for (delay <- List(6, 2, 1)) {
        test(new Store(numSets = 8, numWays = 3, delay = delay, dataType = Vec(2, UInt(8.W))))
          .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          dut.in.valid.poke(false.B)
          dut.in.bits.isWrite.poke(false.B)
          dut.out.ready.poke(false.B)
          dut.out.valid.expect(false.B)
          dut.clock.step()

          // Should be in Idle
          dut.in.ready.expect(true.B)
          dut.out.valid.expect(false.B)
          dut.clock.step()

          // Initiating read operation
          dut.in.bits.addr.poke(5.U(3.W))
          dut.in.bits.isWrite.poke(false.B)
          dut.in.valid.poke(true.B)

          // Internal delay, marked by `out.valid` stalls the CPU
          for (cycles <- 0 until delay) {
            dut.clock.step()
            dut.out.valid.expect(false.B)
          }

          // Enters Valid state after known delay
          dut.clock.step()
          dut.in.ready.expect(false.B)
          dut.out.valid.expect(true.B)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            for (cycles <- 0 until 3) {
              dut.clock.step()
            }
            dut.out.valid.expect(true.B)
            dut.in.ready.expect(false.B)
          }

          dut.in.valid.poke(false.B)
          dut.out.ready.poke(true.B)

          // Enters Idle again
          dut.clock.step()
          dut.in.ready.expect(true.B)
          dut.out.valid.expect(false.B)
        }
      }
    }
  }

  it should "write data to memory and wait until CPU acknowledgement" in {
    for (isCpuWaiting <- List(false, true)) {
      for (delay <- List(6, 2, 1)) {
        test(new Store(numSets = 8, numWays = 3, delay = delay, dataType = Vec(2, UInt(8.W))))
          .withAnnotations(Seq(WriteVcdAnnotation)) { dut =>

          // Startup
          dut.in.valid.poke(false.B)
          dut.in.bits.isWrite.poke(false.B)
          dut.out.ready.poke(false.B)
          dut.out.valid.expect(false.B)
          dut.clock.step()

          // Should be in Idle
          dut.in.ready.expect(true.B)
          dut.out.valid.expect(false.B)
          dut.clock.step()

          // Initiating write operation
          for (way <- 0 until 3) {
            for (block <- 0 until 2) {
              dut.in.bits.dataIn(way)(block).poke(way.U(8.W))
            }
          }
          dut.in.bits.addr.poke(5.U(3.W))
          dut.in.bits.isWrite.poke(true.B)
          dut.in.valid.poke(true.B)

          // Internal delay, marked by `out.valid` stalls the CPU
          for (cycles <- 0 until delay) {
            dut.clock.step()
            dut.out.valid.expect(false.B)
          }

          // Enters Valid state after known delay
          dut.clock.step()
          dut.in.ready.expect(false.B)
          dut.out.valid.expect(true.B)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            for (cycles <- 0 until 3) {
              dut.clock.step()
            }
            dut.out.valid.expect(true.B)
            dut.in.ready.expect(false.B)
          }

          dut.in.valid.poke(false.B)
          dut.out.ready.poke(true.B)

          // Initiating read operation
          dut.clock.step()
          dut.in.ready.expect(true.B)
          dut.out.valid.expect(false.B)
          dut.in.bits.addr.poke(5.U(3.W))
          dut.in.bits.isWrite.poke(false.B)
          dut.in.valid.poke(true.B)
          dut.out.ready.poke(false.B)

          // Internal delay, marked by `out.valid` stalls the CPU
          for (cycles <- 0 until delay) {
            dut.clock.step()
            dut.out.valid.expect(false.B)
          }

          // Enters Valid state after known delay
          dut.clock.step()
          dut.in.ready.expect(false.B)
          dut.out.valid.expect(true.B)

          // Waits in Done state with latched data until CPU is ready
          if (!isCpuWaiting) {
            for (cycles <- 0 until 3) {
              dut.clock.step()
            }
            dut.out.valid.expect(true.B)
            dut.in.ready.expect(false.B)
          }

          dut.in.valid.poke(false.B)
          dut.out.ready.poke(true.B)

          // Enters Idle again
          dut.clock.step()
          dut.in.ready.expect(true.B)
          dut.out.valid.expect(false.B)
        }
      }
    }
  }
}

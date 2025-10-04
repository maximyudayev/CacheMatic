package cachematic

import chisel3._
import chisel3.util._
import cachematic.types._

class DCacheIO(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  val addr      = Input(UInt(addrWidth.W))
  val readEn    = Input(Bool())
  val writeEn   = Input(Bool())
  val writeData = Input(UInt(dataWidth.W))
  val ready     = Output(Bool())
  val readData  = Output(UInt(dataWidth.W))
}

class DCache(cacheSizeBytes: Int, blockSizeBytes: Int, addrWidth: Int = 32, dataWidth: Int = 32) extends Module {
  val io = IO(new DCacheIO(addrWidth, dataWidth))

  val cache = Module(new DirectMappedCache(cacheSizeBytes, blockSizeBytes))

  // inputs
  cache.io.address   := io.addr
  cache.io.readEn    := io.readEn
  cache.io.writeEn   := io.writeEn
  cache.io.writeData := io.writeData

  //  outputs
  io.readData := cache.io.readData
  io.ready    := cache.io.hit // or use a stall/valid
}

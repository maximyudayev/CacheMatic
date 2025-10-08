// See README.md for license details.

package cachematic.cache

import chisel3._
import chisel3.util._

class DirectMapped(val cacheSizeBytes: Int, val blockSizeBytes: Int) extends Module {
  val addrWidth = 32
  val dataWidth = 32

  val io = IO(new Bundle {
    val address = Input(UInt(addrWidth.W))
    val wdata   = Input(UInt(dataWidth.W))
    val wen     = Input(Bool())
    val rdata   = Output(UInt(dataWidth.W))
    val valid   = Output(Bool())
    val hit     = Output(Bool())
    val miss    = Output(Bool())
  })

  val numLines   = cacheSizeBytes / blockSizeBytes
  val indexBits  = log2Ceil(numLines)
  val offsetBits = log2Ceil(blockSizeBytes)

  val index = io.address(offsetBits + indexBits - 1, offsetBits)
  val tag   = io.address(addrWidth - 1, offsetBits + indexBits)

  val validArray = RegInit(VecInit(Seq.fill(numLines)(false.B)))
  val tagArray   = RegInit(VecInit(Seq.fill(numLines)(0.U((addrWidth - indexBits - offsetBits).W))))
  val dataArray  = RegInit(VecInit(Seq.fill(numLines)(0.U(dataWidth.W))))

  val hitWire  = validArray(index) && (tagArray(index) === tag)
  val missWire = !hitWire

  when(io.wen && hitWire) {
    dataArray(index) := io.wdata
  }.elsewhen(missWire) {
    validArray(index) := true.B
    tagArray(index)   := tag
    dataArray(index)  := 0.U // or could simulate memory load later
  }

  io.rdata := dataArray(index)
  io.valid := validArray(index)
  io.hit   := hitWire
  io.miss  := missWire
}

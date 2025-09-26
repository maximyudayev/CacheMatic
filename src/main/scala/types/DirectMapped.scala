package cachematic.types

import chisel3._
import chisel3.util._

class DirectMappedCache(val cacheSizeBytes: Int, val blockSizeBytes: Int) extends Module {
  val addrWidth = 32 // assuming 32bit addr

  val io = IO(new Bundle {
    val address = Input(UInt(addrWidth.W))

    val hit     = Output(Bool())
    val miss    = Output(Bool())
  })

  // parameters
  val numLines   = cacheSizeBytes / blockSizeBytes
  val indexBits  = log2Ceil(numLines) // // bits used to select which cache line to access.
  val offsetBits = log2Ceil(blockSizeBytes)

  // Extract index and tag address
  val index = io.address(offsetBits + indexBits - 1, offsetBits) 
  val tag   = io.address(addrWidth - 1, offsetBits + indexBits)

  // Valid bits + tags for each line (cache)
  val validArray = RegInit(VecInit(Seq.fill(numLines)(false.B)))
  val tagArray   = RegInit(VecInit(Seq.fill(numLines)(0.U((addrWidth - indexBits - offsetBits).W))))

  // Compute hit/miss(compare tag,and valid) based on current state
  val hitWire  = validArray(index) && (tagArray(index) === tag)
  val missWire = !hitWire

  // Update cache only on miss for next cycle
  when(missWire) {
    validArray(index) := true.B
    tagArray(index)   := tag
  }

  // Register outputs:hit/miss come a cycle after request
  val hitReg  = RegNext(hitWire, init = false.B)
  val missReg = RegNext(missWire, init = false.B)

  io.hit  := hitReg
  io.miss := missReg
}

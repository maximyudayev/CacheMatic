// package cachematic

// import chisel3._
// import chisel3.util._
// import cachematic.types._

// class ICacheIO(val addrWidth: Int, val dataWidth: Int) extends Bundle {
//   val addr     = Input(UInt(addrWidth.W))
//   val valid    = Input(Bool()) // request valid
//   val ready    = Output(Bool()) // cache ready
//   val instr    = Output(UInt(dataWidth.W))
// }

// class ICache(cacheSizeBytes: Int, blockSizeBytes: Int, addrWidth: Int = 32, dataWidth: Int = 32) extends Module {
//   val io = IO(new ICacheIO(addrWidth, dataWidth))

//   val cache = Module(new DirectMappedCache(cacheSizeBytes, blockSizeBytes))
//     //  inputs
//   cache.io.address := io.addr
//   cache.io.readEn  := io.valid
//   cache.io.writeEn := false.B
//   cache.io.writeData := 0.U

//   // outputs
//   io.instr := cache.io.readData
//   io.ready := cache.io.hit
// }

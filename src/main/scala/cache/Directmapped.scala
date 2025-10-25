// See README.md for license details.

package cachematic.cache

import chisel3._
import chisel3.util._

/**
 * Defines the type of cache to be instantiated.
 */
object CacheType extends ChiselEnum {
  val Instruction, Data = Value
}

/**
 * IO Bundle for the Memory-facing side of the cache.
 * Has separate channels for read and write requests (write-through).
 */
class MemIO(val addrWidth: Int, val dataWidth: Int) extends Bundle {
  // Read channel
  val read_req = Decoupled(UInt(addrWidth.W)) // Memory read request (block-aligned address)
  val read_resp = Flipped(Decoupled(UInt(dataWidth.W)))

  // Write channel (for write-through)
  val write_req = Decoupled(new Bundle {
    val addr = UInt(addrWidth.W)
    val data = UInt(dataWidth.W)
  })
}

class DirectMapped(
  val cacheType: CacheType.Type, 
  val cacheSizeBytes: Int, 
  val blockSizeBytes: Int
) extends Module {
  val addrWidth = 32
  val dataWidth = 32

  val io = IO(new Bundle {
    // CPU-facing interface
    // Flipped(Decoupled(...)) creates a ready/valid interface for inputs
    val cpuReq = Flipped(Decoupled(new MemRequestIO))
    // Decoupled(...) creates a ready/valid interface for outputs
    val cpuRsp = Decoupled(new MemResponseIO)
    // Memory-facing interface
    val mem = new MemIO(addrWidth, dataWidth)
  })

  val numLines   = cacheSizeBytes / blockSizeBytes
  val wordsPerBlock = blockSizeBytes / (dataWidth / 8)
  val indexBits  = log2Ceil(numLines)
  val offsetBits = log2Ceil(blockSizeBytes)
  val tagsBits   = addrWidth - indexBits - offsetBits

  // --- State Machine ---
  val s_IDLE :: s_READ_HIT :: s_WRITE_HIT :: s_MEM_REQ_START :: s_MEM_RESP_WAIT :: s_MEM_REQ_NEXT :: Nil = Enum(6)
  val state = RegInit(s_IDLE)

  // CPU request decoding
  val cpuReqAddr = io.cpuReq.bits.addrRequest
  val cpuReqTag   = cpuReqAddr(addrWidth - 1, offsetBits + indexBits)
  val cpuReqIndex = cpuReqAddr(offsetBits + indexBits - 1, offsetBits)
  val word_offset = cpuReqAddr(offsetBits - 1, log2Ceil(dataWidth / 8))
  val cacheLineBaseAddr = Cat(cpuReqAddr(addrWidth - 1, offsetBits), 0.U(offsetBits.W))

  val validArray = RegInit(VecInit(Seq.fill(numLines)(false.B)))
  val tagArray   = RegInit(VecInit(Seq.fill(numLines)(0.U(tagsBits.W))))
  // val dataArray  = RegInit(VecInit(Seq.fill(numLines)(0.U(dataWidth.W))))
  val dataArray  = Reg(Vec(numLines, Vec(wordsPerBlock, UInt(dataWidth.W))))
  
  // --- Regs for handling misses ---
  val missIsWrite = RegInit(false.B)
  val missAddr    = RegInit(0.U(addrWidth.W))
  val missWData   = RegInit(0.U(dataWidth.W))

  // --- Regs for handling multi-word read miss responses ---
  // counter for words (in a block) received from memory
  val missWordCounter = RegInit(0.U((log2Ceil(wordsPerBlock)+1).W))
  val missIndex = missAddr(offsetBits + indexBits - 1, offsetBits)
  val missTag   = missAddr(addrWidth - 1, offsetBits + indexBits)
  val missWordOffset = missAddr(offsetBits - 1, log2Ceil(dataWidth / 8))
  
  // --- Regs for registered outputs
  // val cpuReqReadyReg        = RegInit(true.B)
  // val memReadRespReadyReg   = RegInit(true.B)
  val cpuRspValidReg        = RegInit(false.B)
  val cpuRspDataReg         = RegInit(0.U(dataWidth.W))
  val memReadReqValidReg    = RegInit(false.B)
  val memReadReqAddrReg     = RegInit(0.U(addrWidth.W))
  val memWriteReqValidReg   = RegInit(false.B)
  val memWriteReqAddrReg    = RegInit(0.U(addrWidth.W))
  val memWriteReqDataReg    = RegInit(0.U(dataWidth.W))

  val isHit  = validArray(cpuReqIndex) && (tagArray(cpuReqIndex) === cpuReqTag)
  // val missWire = !hitWire
  val isWrite = io.cpuReq.bits.isWrite && (cacheType == CacheType.Data).B

  // --- Default signals assignments ---
  // io.cpuReq.ready := cpuReqReadyReg
  io.cpuReq.ready := (state === s_IDLE)
  // io.mem.read_resp.ready := memReadRespReadyReg
  io.mem.read_resp.ready := true.B  // always ready to accept memory responses

  io.cpuRsp.valid := cpuRspValidReg
  io.cpuRsp.bits.dataResponse := cpuRspDataReg
  io.mem.read_req.valid := memReadReqValidReg
  io.mem.read_req.bits := memReadReqAddrReg
  io.mem.write_req.valid := memWriteReqValidReg
  io.mem.write_req.bits.addr := memWriteReqAddrReg
  io.mem.write_req.bits.data := memWriteReqDataReg

  // Default regs values
  // cpuReqReadyReg := true.B
  // memReadRespReadyReg := true.B
  cpuRspValidReg := false.B
  cpuRspDataReg  := 0.U
  memReadReqValidReg := false.B
  memReadReqAddrReg  := 0.U
  memWriteReqValidReg := false.B
  memWriteReqAddrReg  := 0.U
  memWriteReqDataReg  := 0.U

  switch(state) {
    is(s_IDLE) {
      when(io.cpuReq.valid && io.cpuReq.ready) {
        when (isHit) {
          when (isWrite) {
            state := s_WRITE_HIT
            // Write Hit:
            // update cache line
            dataArray(cpuReqIndex)(word_offset) := io.cpuReq.bits.dataRequest
            // write through to memory
            memWriteReqValidReg := true.B
            memWriteReqAddrReg := cpuReqAddr
            memWriteReqDataReg := io.cpuReq.bits.dataRequest
          } .otherwise {
            state := s_READ_HIT
            // Read Hit:
            // return data immediately
            cpuRspValidReg := true.B
            cpuRspDataReg := dataArray(cpuReqIndex)(word_offset)
          }
        } .otherwise {
          // Write miss (write-allocate policy) and Read miss handled the same way
          // Send read request to memory, starting from first word of the cache line
          state := s_MEM_REQ_START
          // cpuReqReadyReg := false.B // stall CPU until miss is handled
          memReadReqValidReg := true.B
          memReadReqAddrReg := cacheLineBaseAddr
          missWordCounter := 0.U // reset word counter
          // save miss request info for later use
          missIsWrite := isWrite
          missAddr := cpuReqAddr
          missWData := io.cpuReq.bits.dataRequest
        }
      }
    }
    is(s_READ_HIT) {
      when (io.cpuRsp.valid && io.cpuRsp.ready) {
        state := s_IDLE
      }
    }
    is(s_WRITE_HIT) {
      when (io.mem.write_req.valid && io.mem.write_req.ready) {
        state := s_IDLE
      }
    }
    is(s_MEM_REQ_START) {
      // send read request from cache to memory
      when(io.mem.read_req.valid && io.mem.read_req.ready) {
        state := s_MEM_RESP_WAIT
        memReadReqValidReg := false.B // deassert read request valid
        // memReadRespReadyReg := true.B // ready to receive memory response
      }

    }
    // wait for memory response
    is(s_MEM_RESP_WAIT) {
      // wait for memory response with the requested cache line
      when(io.mem.read_resp.valid && io.mem.read_resp.ready) {
        // store the received word into the cache line
        dataArray(missIndex)(missWordCounter) := io.mem.read_resp.bits

        when(missWordCounter === (wordsPerBlock-1).U) {
          // last word received
          // update cpuReqTag and valid bit of the cache line
          validArray(missIndex) := true.B
          tagArray(missIndex)   := missTag

          // handle specific read miss/ write miss
          when(missIsWrite) {
            // Write miss: 
            // update cache line with the missed write data
            dataArray(missIndex)(missWordOffset) := missWData
            // write through to memory
            memWriteReqValidReg := true.B
            memWriteReqAddrReg := missAddr
            memWriteReqDataReg := missWData
          } .otherwise {
            // Read miss:
            // return the requested data to CPU
            val cpuRspData = Mux(missWordOffset === (wordsPerBlock - 1).U,
                     io.mem.read_resp.bits,
                     dataArray(missIndex)(missWordOffset))
            cpuRspValidReg := true.B
            cpuRspDataReg := cpuRspData
          }
          state := s_IDLE
        } .otherwise {
          // not the last word yet, increment word counter and move to next state
          state := s_MEM_REQ_NEXT
          missWordCounter := missWordCounter + 1.U
          // send read request for next word in the cache line
          memReadReqValidReg := true.B
          memReadReqAddrReg := cacheLineBaseAddr + ((missWordCounter + 1.U) << log2Ceil(dataWidth / 8).U)
          // memReadRespReadyReg := false.B // deassert ready until next word is requested
        }
      }
    }
    is(s_MEM_REQ_NEXT) {
      // send read request for next word in the cache line
      when(io.mem.read_req.valid && io.mem.read_req.ready) {
        state := s_MEM_RESP_WAIT
        memReadReqValidReg := false.B // deassert read request valid
        // memReadRespReadyReg := true.B // ready to receive memory response
      }
    }

  }
}

import chisel3._
import chisel3.util._
import chiseltest._
import org.scalatest.flatspec.AnyFlatSpec

import cachematic.policies.coherence.SnoopUnit

/**
 * Unit tests for the SnoopUnit module.
 * Test for ICache example: 8KB cache, 64B lines (128 lines total).
 * nOffsetBits = 6, nLinesLog2 = 7, nTagBits = 19
 */
class SnoopUnitTest extends AnyFlatSpec with ChiselScalatestTester {
  
  // Define the common parameters used across all tests
  val lineSizeBytes = 64
  val numLines = 128

  // Define two addresses that map to the same index but have different tags
  // Index = 1 (Binary: 0000001), Offset = 0 (Binary: 000000)
  // Tag is the remaining 19 bits.
  // Address structure: [Tag (19)][Index (7)][Offset (6)]
  val TEST_INDEX = 1
  
  // Tag 0 (0x0)
  val ADDR_TAG_0 = "h0000_0040".U(32.W) 
  val TAG_0 = 0.U(p.nTagBits.W)

  // Tag 1 (0x1)
  val ADDR_TAG_1 = "h0002_0040".U(32.W) 
  val TAG_1 = 1.U(p.nTagBits.W)

  behavior of "SnoopUnit"

  it should "initialize and output valid_in when no snoop request is active" in {
    test(new SnoopUnit(lineSizeBytes, numLines)) { c =>
      println("\n--- Test: No Snoop Active ---")
      
      // Initialize cache metadata: Set line 1 as valid with TAG_0
      val initialValid = VecInit(Seq.tabulate(numLines)(i => if (i == TEST_INDEX) true.B else false.B))
      val initialTags = VecInit(Seq.tabulate(numLines)(i => if (i == TEST_INDEX) TAG_0 else 0.U))

      c.io.tags_in.poke(initialTags)
      c.io.valid_in.poke(initialValid)
      c.io.snoop.valid.poke(false.B)
      c.io.snoop.bits.poke(ADDR_TAG_1) // Poke random snoop address

      c.clock.step(1)

      // Expected: Output should match input valid vector exactly
      c.io.next_valid_out.expect(initialValid)
      println("  -> Valid output matches input when snoop is inactive.")
    }
  }

  it should "invalidate a cache line on a successful snoop hit" in {
    test(new SnoopUnit(lineSizeBytes, numLines)) { c =>
      println("\n--- Test: Successful Snoop Hit (Invalidation) ---")
      
      // Setup: Line TEST_INDEX is valid with TAG_0
      val initialValid = VecInit(Seq.tabulate(numLines)(i => if (i == TEST_INDEX) true.B else false.B))
      val initialTags = VecInit(Seq.tabulate(numLines)(i => if (i == TEST_INDEX) TAG_0 else 0.U))

      c.io.tags_in.poke(initialTags)
      c.io.valid_in.poke(initialValid)
      
      // Snoop with the exact address corresponding to TAG_0
      c.io.snoop.valid.poke(true.B)
      c.io.snoop.bits.poke(ADDR_TAG_0)
      
      c.clock.step(1)

      // Expected: The next_valid_out vector should have the bit at TEST_INDEX cleared.
      val expectedValid = initialValid.clone()
      expectedValid(TEST_INDEX) := false.B
      
      c.io.next_valid_out.expect(expectedValid)
      println(s"  -> Line ${TEST_INDEX} successfully invalidated on snoop hit.")
    }
  }
  
  it should "not invalidate a line if tags mismatch (Snoop Miss)" in {
    test(new SnoopUnit(lineSizeBytes, numLines)) { c =>
      println("\n--- Test: Snoop Miss (Tag Mismatch) ---")

      // Setup: Line TEST_INDEX is valid with TAG_0
      val initialValid = VecInit(Seq.tabulate(numLines)(i => if (i == TEST_INDEX) true.B else false.B))
      val initialTags = VecInit(Seq.tabulate(numLines)(i => if (i == TEST_INDEX) TAG_0 else 0.U))
      
      c.io.tags_in.poke(initialTags)
      c.io.valid_in.poke(initialValid)
      
      // Snoop with an address corresponding to TAG_1 (same index, different tag)
      c.io.snoop.valid.poke(true.B)
      c.io.snoop.bits.poke(ADDR_TAG_1)
      
      c.clock.step(1)

      // Expected: The next_valid_out vector should remain unchanged (no invalidation)
      c.io.next_valid_out.expect(initialValid)
      println("  -> Line was not invalidated due to tag mismatch.")
    }
  }
  
  it should "not invalidate a line if the line is already invalid (Snoop Miss)" in {
    test(new SnoopUnit(lineSizeBytes, numLines)) { c =>
      println("\n--- Test: Snoop Miss (Line Already Invalid) ---")

      // Setup: Line TEST_INDEX is invalid
      val initialValid = VecInit(Seq.tabulate(numLines)(i => false.B))
      val initialTags = VecInit(Seq.tabulate(numLines)(i => TAG_0)) // Tag is irrelevant if invalid
      
      c.io.tags_in.poke(initialTags)
      c.io.valid_in.poke(initialValid)
      
      // Snoop with ADDR_TAG_0
      c.io.snoop.valid.poke(true.B)
      c.io.snoop.bits.poke(ADDR_TAG_0)
      
      c.clock.step(1)

      // Expected: The next_valid_out vector should remain all false
      c.io.next_valid_out.expect(initialValid)
      println("  -> Line was not invalidated as it was already invalid.")
    }
  }
}
// See README.md for license details.
package cachematic.util.scala

def isPowerOfTwo(n: Int): Boolean = n > 0 && (n & (n - 1)) == 0

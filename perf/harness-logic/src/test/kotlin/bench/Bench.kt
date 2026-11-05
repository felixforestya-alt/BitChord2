package bench

import java.lang.management.ManagementFactory

/**
 * A/B timing with warm-up; time and allocation per operation on the calling thread. Rounds
 * alternate which side goes first (ABBA), so neither gains from going second; the median and the
 * best round are both reported.
 */
object Bench {
    private val mx = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean

    class Side(val name: String, val body: () -> Unit)
    class Result(val name: String, val nsPerOp: Double, val bestNsPerOp: Double, val bytesPerOp: Double)

    fun compare(title: String, ops: Int, a: Side, b: Side, warmup: Int = 8, rounds: Int = 31): Pair<Result, Result> {
        repeat(warmup) { repeat(ops) { a.body() }; repeat(ops) { b.body() } }
        val ta = DoubleArray(rounds); val tb = DoubleArray(rounds)
        val aa = DoubleArray(rounds); val ab = DoubleArray(rounds)
        for (r in 0 until rounds) {
            if (r % 2 == 0) {
                measure(ops, a.body).let { ta[r] = it.first; aa[r] = it.second }
                measure(ops, b.body).let { tb[r] = it.first; ab[r] = it.second }
            } else {
                measure(ops, b.body).let { tb[r] = it.first; ab[r] = it.second }
                measure(ops, a.body).let { ta[r] = it.first; aa[r] = it.second }
            }
        }
        val ra = Result(a.name, median(ta), ta.min(), median(aa))
        val rb = Result(b.name, median(tb), tb.min(), median(ab))
        val change = (rb.nsPerOp - ra.nsPerOp) / ra.nsPerOp * 100
        val bestChange = (rb.bestNsPerOp - ra.bestNsPerOp) / ra.bestNsPerOp * 100
        println("BENCH $title")
        println("BENCH   ${ra.name.padEnd(10)} median ${"%,12.0f".format(ra.nsPerOp)} ns/op  best ${"%,12.0f".format(ra.bestNsPerOp)}  ${"%,10.0f".format(ra.bytesPerOp)} B/op")
        println("BENCH   ${rb.name.padEnd(10)} median ${"%,12.0f".format(rb.nsPerOp)} ns/op  best ${"%,12.0f".format(rb.bestNsPerOp)}  ${"%,10.0f".format(rb.bytesPerOp)} B/op  (median ${"%+.1f".format(change)}%, best ${"%+.1f".format(bestChange)}%)")
        return ra to rb
    }

    private fun measure(ops: Int, body: () -> Unit): Pair<Double, Double> {
        val a0 = mx.currentThreadAllocatedBytes
        val t0 = System.nanoTime()
        repeat(ops) { body() }
        val t1 = System.nanoTime()
        val a1 = mx.currentThreadAllocatedBytes
        return (t1 - t0).toDouble() / ops to (a1 - a0).toDouble() / ops
    }

    private fun median(values: DoubleArray): Double = values.sorted()[values.size / 2]
}

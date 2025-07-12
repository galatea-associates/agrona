# Performance Tuning Guide

A comprehensive guide to optimizing Agrona applications for maximum performance, covering CPU optimization strategies, memory allocation patterns, JVM tuning parameters, benchmarking methodologies, and platform-specific considerations.

## Table of Contents

1. [Performance Philosophy](#performance-philosophy)
2. [CPU Optimization Strategies](#cpu-optimization-strategies)
3. [Memory Allocation Patterns](#memory-allocation-patterns)
4. [JVM Tuning Parameters](#jvm-tuning-parameters)
5. [Benchmarking with JMH](#benchmarking-with-jmh)
6. [Profiling Techniques](#profiling-techniques)
7. [Platform-Specific Optimizations](#platform-specific-optimizations)
8. [Performance Measurement Strategies](#performance-measurement-strategies)
9. [Common Performance Pitfalls](#common-performance-pitfalls)
10. [Performance Regression Detection](#performance-regression-detection)

---

## Performance Philosophy

### Zero-Copy Operations
Agrona's fundamental performance principle centers on eliminating unnecessary memory copying operations. The library provides unified buffer abstractions that work directly with underlying memory regions—whether heap arrays, NIO ByteBuffers, or off-heap memory addresses—without intermediate copying.

**Core Zero-Copy Patterns:**
- DirectBuffer and MutableDirectBuffer interfaces for unified memory access
- UnsafeBuffer implementation for direct memory manipulation
- Memory-mapped file operations for persistent storage without copying
- Ring buffer implementations for message passing without allocation

> Source: `/agrona/src/main/java/org/agrona/concurrent/UnsafeBuffer.java`

### Lock-Free Concurrency Model
All concurrent data structures in Agrona implement lock-free algorithms to eliminate thread blocking and provide predictable latency characteristics. The library leverages atomic operations and memory ordering semantics to coordinate between threads without mutual exclusion.

**Lock-Free Design Benefits:**
- Eliminates thread blocking and context switching overhead
- Provides predictable latency under load
- Scales linearly with CPU cores
- Avoids priority inversion and deadlock scenarios

> Source: `/agrona/src/main/java/org/agrona/concurrent/OneToOneConcurrentArrayQueue.java`

### Cache-Conscious Architecture
Agrona explicitly optimizes for CPU cache behavior through strategic memory layout, cache-line alignment, and false sharing prevention. The library uses 64-byte cache-line padding to isolate frequently accessed data structures.

```java
// Example: Cache-line padding in BackoffIdleStrategy
abstract class BackoffIdleStrategyPrePad {
    byte p000, p001, ..., p063; // 64-byte padding
}
```

> Source: `/agrona/src/main/java/org/agrona/concurrent/BackoffIdleStrategy.java:21-27`

---

## CPU Optimization Strategies

### Idle Strategy Selection

The choice of idle strategy significantly impacts CPU utilization and latency characteristics. Agrona provides several implementations optimized for different scenarios:

#### BusySpinIdleStrategy - Ultra-Low Latency
Best for applications requiring absolute minimum latency at the cost of high CPU usage.

```java
IdleStrategy idleStrategy = BusySpinIdleStrategy.INSTANCE;

// Usage pattern for lowest latency
while (isRunning) {
    int workCount = doWork();
    idleStrategy.idle(workCount);
}
```

**Characteristics:**
- **Latency**: Sub-microsecond response times
- **CPU Usage**: 100% - monopolizes CPU core
- **Power Consumption**: High - no power management
- **Use Cases**: HFT trading systems, real-time control systems

> Source: `/agrona/src/main/java/org/agrona/concurrent/BusySpinIdleStrategy.java:47-55`

#### BackoffIdleStrategy - Balanced Performance
Implements a three-phase backoff approach: spin → yield → park with exponential backoff.

```java
// Default configuration
IdleStrategy idleStrategy = new BackoffIdleStrategy();

// Custom tuning for specific requirements
IdleStrategy customStrategy = new BackoffIdleStrategy(
    50L,           // maxSpins - spin iterations before yielding
    10L,           // maxYields - yield calls before parking
    1000L,         // minParkPeriodNs - initial park duration
    1_000_000L     // maxParkPeriodNs - maximum park duration
);
```

**Phase Behavior:**
1. **Spinning Phase**: Uses `Thread.onSpinWait()` for CPU optimization hints
2. **Yielding Phase**: Calls `Thread.yield()` to allow other threads
3. **Parking Phase**: Uses `LockSupport.parkNanos()` with exponential backoff

> Source: `/agrona/src/main/java/org/agrona/concurrent/BackoffIdleStrategy.java:127-144`

#### YieldingIdleStrategy - CPU Cooperative
Reduces CPU usage while maintaining reasonable responsiveness through thread yielding.

```java
IdleStrategy idleStrategy = YieldingIdleStrategy.INSTANCE;
```

**Characteristics:**
- **Latency**: Low - typically microsecond range
- **CPU Usage**: Medium - cooperatively shares CPU
- **Throughput**: Good for moderate load scenarios

> Source: `/agrona/src/main/java/org/agrona/concurrent/YieldingIdleStrategy.java:43-51`

#### SleepingIdleStrategy - Power Efficient
Minimizes power consumption through controlled sleep periods.

```java
// Default 1μs sleep period
IdleStrategy idleStrategy = new SleepingIdleStrategy();

// Custom sleep duration
IdleStrategy customSleep = new SleepingIdleStrategy(
    10_000L  // 10μs sleep period
);
```

> Source: `/agrona/src/main/java/org/agrona/concurrent/SleepingIdleStrategy.java:45-58`

### Thread.onSpinWait() Optimization

Modern JVMs optimize `Thread.onSpinWait()` calls by issuing CPU-specific instructions (e.g., PAUSE on x86) that improve power efficiency and hyperthreading performance during busy-wait loops.

```java
// Agrona's implementation (though deprecated, shows the pattern)
public static void onSpinWait() {
    if (ON_SPIN_WAIT_ENABLED) {
        Thread.onSpinWait();
    }
}
```

**CPU-Specific Benefits:**
- **x86/x64**: PAUSE instruction reduces power consumption and improves hyperthreading
- **ARM**: YIELD instruction provides similar benefits
- **Power/SPARC**: Architecture-specific optimizations

> Source: `/agrona/src/main/java/org/agrona/hints/ThreadHints.java:52-58`

### Timer Slack Optimization (Linux)

Linux coalesces timer events within a 50μs window by default. For sub-50μs timing requirements, adjust the timer slack:

```bash
# Set timer slack to 10μs for specific thread
echo 10000 > /proc/[PID]/timerslack_ns

# Verify current setting
cat /proc/[PID]/timerslack_ns
```

> Source: `/agrona/src/main/java/org/agrona/concurrent/BackoffIdleStrategy.java:108-117`

---

## Memory Allocation Patterns

### Zero-Allocation Steady State

Agrona applications should achieve zero allocation during steady-state operation after warmup. All objects should be pre-allocated during initialization.

#### Buffer Pre-allocation Strategy

```java
// Pre-allocate aligned direct buffers
ByteBuffer directBuffer = BufferUtil.allocateDirectAligned(1024, 64);
UnsafeBuffer buffer = new UnsafeBuffer(directBuffer);

// Verify alignment for optimal performance
buffer.verifyAlignment();
```

**Alignment Benefits:**
- **Cache Line Alignment**: Reduces cache misses
- **SIMD Optimization**: Enables vectorized operations
- **Memory Bus Efficiency**: Optimal memory controller utilization

> Source: `/agrona/src/main/java/org/agrona/BufferUtil.java:27`

#### Object Pooling Patterns

```java
// Example: Ring buffer with pre-allocated messages
public class MessagePool {
    private final Object[] pool;
    private int index = 0;
    
    public MessagePool(int size) {
        pool = new Object[size];
        for (int i = 0; i < size; i++) {
            pool[i] = new Message(); // Pre-allocate
        }
    }
    
    public Object acquire() {
        return pool[index++ % pool.length];
    }
}
```

### Cache-Line Padding Implementation

Prevent false sharing by ensuring hot data structures occupy separate cache lines:

```java
// Example from BackoffIdleStrategy
abstract class BackoffIdleStrategyPrePad {
    // 64 bytes of padding to prevent false sharing
    byte p000, p001, p002, p003, p004, p005, p006, p007;
    // ... continuing to p063
}
```

**Cache Line Constants:**
```java
public static final int CACHE_LINE_LENGTH = 64;  // Standard cache line size
```

> Source: `/agrona/src/main/java/org/agrona/BitUtil.java:70`

### Memory Layout Optimization

#### Power-of-Two Sizing
Use power-of-two sizes for efficient modulo operations via bit masking:

```java
// Efficient capacity calculation
int capacity = BitUtil.findNextPositivePowerOfTwo(requestedSize);

// Fast modulo operation (capacity must be power of 2)
int index = hashCode & (capacity - 1);
```

> Source: `/agrona/src/main/java/org/agrona/BitUtil.java:124-127`

#### Alignment Utilities
```java
// Align values to specific boundaries
int aligned = BitUtil.align(value, BitUtil.CACHE_LINE_LENGTH);

// Verify power-of-two for optimal performance
if (BitUtil.isPowerOfTwo(capacity)) {
    // Use fast bit-mask operations
}
```

> Source: `/agrona/src/main/java/org/agrona/BitUtil.java:156-159`

---

## JVM Tuning Parameters

### Essential JVM Flags for Agrona Applications

#### Unsafe API Access
```bash
# Enable Unsafe API access (required for Agrona)
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/java.nio=ALL-UNNAMED
```

#### Garbage Collection Optimization
```bash
# Low-latency GC configuration
-XX:+UseG1GC                           # G1 for predictable pause times
-XX:MaxGCPauseMillis=1                 # Target 1ms pause times
-XX:+UnlockExperimentalVMOptions       # Enable experimental options
-XX:+UseZGC                            # Alternative: ZGC for sub-1ms pauses

# Memory management
-Xms8g -Xmx8g                          # Fixed heap size (avoid expansion)
-XX:NewRatio=1                         # 50% young generation
-XX:+AlwaysPreTouch                    # Pre-touch memory pages
```

#### Compilation Optimization
```bash
# HotSpot compiler optimizations
-XX:+UnlockDiagnosticVMOptions         # Enable diagnostic options
-XX:+DebugNonSafepoints               # Better profiling support
-XX:+PrintGCApplicationStoppedTime     # Monitor pause times

# Prevent deoptimization in tight loops
-XX:CompileCommand=dontinline,org.agrona.concurrent.BusySpinIdleStrategy::idle
```

#### System Properties for Agrona
```bash
# Disable bounds checking for maximum performance
-Dagrona.disable.bounds.checks=true

# Disable spin-wait hints if causing issues
-Dorg.agrona.hints.disable.onSpinWait=true

# Enable additional system introspection
-Dagrona.enable.extended.system.checks=true
```

> Source: `/agrona/src/main/java/org/agrona/hints/ThreadHints.java:36`

#### CPU Affinity and NUMA Optimization
```bash
# Pin JVM to specific CPU cores
taskset -c 0-3 java [JVM_OPTIONS] Application

# NUMA node binding
numactl --cpunodebind=0 --membind=0 java [JVM_OPTIONS] Application
```

---

## Benchmarking with JMH

### JMH Configuration for Agrona Applications

Agrona includes comprehensive JMH benchmarks demonstrating optimal configuration patterns:

```java
@Fork(value = 3, jvmArgsPrepend = "-Dagrona.disable.bounds.checks=true")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@State(Scope.Benchmark)
public class PerformanceBenchmark {
    
    @Benchmark
    public long measureOperation() {
        return performCriticalOperation();
    }
}
```

> Source: `/agrona-benchmarks/src/main/java/org/agrona/concurrent/ClockBenchmark.java:36-41`

### Clock Performance Benchmarking

Understanding time measurement overhead is crucial for latency-sensitive applications:

```java
// Different clock implementations with varying overhead
@Benchmark public long systemNanoTime()     { return System.nanoTime(); }
@Benchmark public long systemCurrentTimeMillis() { return System.currentTimeMillis(); }
@Benchmark public long systemNanoClock()    { return SystemNanoClock.INSTANCE.nanoTime(); }
@Benchmark public long epochClock()         { return SystemEpochClock.INSTANCE.time(); }
```

**Typical Performance Characteristics:**
- `System.nanoTime()`: ~20-30ns overhead
- `System.currentTimeMillis()`: ~10-15ns overhead  
- Agrona clocks: ~5-10ns overhead with caching

> Source: `/agrona-benchmarks/src/main/java/org/agrona/concurrent/ClockBenchmark.java:58-95`

### Memory Operation Benchmarking

```java
@Param({ "1", "2", "4", "16", "50", "99", "128", "1024" })
private int length;

@Param({ "ARRAY", "DIRECT" })
private BufferType bufferType;

@Benchmark
public void setMemory() {
    unsafeBuffer.setMemory(index, length, (byte)0x5A);
}
```

> Source: `/agrona-benchmarks/src/main/java/org/agrona/concurrent/SetMemoryBenchmark.java:44-46`

### Running Benchmarks

```bash
# Execute specific benchmark class
java -cp agrona-benchmarks.jar org.openjdk.jmh.Main ClockBenchmark

# Profile allocations
java -cp agrona-benchmarks.jar org.openjdk.jmh.Main \
  -prof gc \
  ClockBenchmark

# CPU profiling with detailed output
java -cp agrona-benchmarks.jar org.openjdk.jmh.Main \
  -prof perf \
  -jvmArgs "-XX:+UnlockDiagnosticVMOptions -XX:+DebugNonSafepoints" \
  ClockBenchmark
```

---

## Profiling Techniques

### Built-in Performance Monitoring

#### System Introspection
```java
// Detect runtime environment for optimization
String osName = SystemUtil.osName();
String osArch = SystemUtil.osArch();
long pid = SystemUtil.getPid();

// Platform-specific optimizations
if (SystemUtil.isLinux() && SystemUtil.isX64Arch()) {
    // Apply Linux x64-specific optimizations
}
```

> Source: `/agrona/src/main/java/org/agrona/SystemUtil.java:77-142`

#### Thread State Analysis
```java
// Generate thread dumps for deadlock analysis
StringBuilder sb = new StringBuilder();
SystemUtil.threadDump(sb);
System.out.println(sb.toString());

// Detect debugger attachment (impacts performance)
if (SystemUtil.isDebuggerAttached()) {
    System.out.println("Warning: Debugger attached - performance will be degraded");
}
```

> Source: `/agrona/src/main/java/org/agrona/SystemUtil.java:200-218`

### External Profiling Tools

#### Linux perf Integration
```bash
# Profile CPU usage with call graphs
perf record -g java [JVM_OPTIONS] Application
perf report

# Monitor cache misses
perf stat -e cache-misses,cache-references java [JVM_OPTIONS] Application

# Profile memory access patterns
perf mem record java [JVM_OPTIONS] Application
perf mem report
```

#### Flight Recorder Configuration
```bash
# Enable JFR for continuous profiling
-XX:+FlightRecorder
-XX:StartFlightRecording=duration=60s,filename=profile.jfr

# Custom JFR settings for low overhead
-XX:FlightRecorderOptions=settings=profile
```

#### Async Profiler Integration
```bash
# Profile allocation patterns
java -jar async-profiler.jar -e alloc -d 30 -f profile.html [PID]

# CPU profiling with call stacks
java -jar async-profiler.jar -e cpu -d 30 -f cpu-profile.html [PID]
```

---

## Platform-Specific Optimizations

### Linux Optimizations

#### Kernel Parameters
```bash
# Disable CPU frequency scaling
echo performance > /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor

# Reduce context switching overhead
echo 1 > /proc/sys/kernel/sched_migration_cost_ns

# Optimize network stack for low latency
echo 1 > /proc/sys/net/core/busy_poll
echo 1 > /proc/sys/net/core/busy_read
```

#### Memory Management
```bash
# Disable transparent huge pages (THP) for predictable allocation
echo never > /sys/kernel/mm/transparent_hugepage/enabled

# Configure NUMA policy
echo 0 > /proc/sys/vm/zone_reclaim_mode
```

#### Process Scheduling
```bash
# Set real-time scheduling for critical threads
chrt -f 99 java [JVM_OPTIONS] Application

# CPU isolation for dedicated cores
# Add isolcpus=2,3 to kernel boot parameters
# Then bind application to isolated cores
taskset -c 2,3 java [JVM_OPTIONS] Application
```

### Windows Optimizations

#### High-Resolution Timers
```java
// Enable high-resolution timers in Windows
System.setProperty("java.util.concurrent.ForkJoinPool.common.threadFactory",
    "java.util.concurrent.ForkJoinPool$DefaultForkJoinWorkerThreadFactory");
```

#### Process Priority
```bash
# Set high priority class
start /HIGH java [JVM_OPTIONS] Application

# Set real-time priority (use with caution)
start /REALTIME java [JVM_OPTIONS] Application
```

### Architecture-Specific Considerations

#### x86/x64 Optimizations
- Leverage PAUSE instruction via `Thread.onSpinWait()`
- Utilize prefetch instructions for predictable access patterns
- Align data structures to 64-byte cache lines

#### ARM64 Optimizations
- Use YIELD instruction equivalent through `Thread.onSpinWait()`
- Consider different cache line sizes (may be 32 or 128 bytes)
- Optimize for weaker memory ordering model

---

## Performance Measurement Strategies

### Latency Measurement Patterns

#### High-Resolution Timing
```java
// Use Agrona's nano clocks for precise measurement
NanoClock clock = SystemNanoClock.INSTANCE;

long startTime = clock.nanoTime();
performOperation();
long endTime = clock.nanoTime();

long latencyNs = endTime - startTime;
```

> Source: `/agrona/src/main/java/org/agrona/concurrent/NanoClock.java`

#### Percentile Tracking
```java
// Simple latency histogram
public class LatencyTracker {
    private final long[] measurements = new long[1000000];
    private int index = 0;
    
    public void record(long latencyNs) {
        measurements[index++ % measurements.length] = latencyNs;
    }
    
    public void printPercentiles() {
        Arrays.sort(measurements);
        System.out.printf("50th: %d ns\n", measurements[(int)(measurements.length * 0.5)]);
        System.out.printf("99th: %d ns\n", measurements[(int)(measurements.length * 0.99)]);
        System.out.printf("99.9th: %d ns\n", measurements[(int)(measurements.length * 0.999)]);
    }
}
```

### Throughput Measurement

#### Message Rate Calculation
```java
public class ThroughputCounter {
    private final AtomicLong messageCount = new AtomicLong();
    private final long startTime = System.nanoTime();
    
    public void increment() {
        messageCount.incrementAndGet();
    }
    
    public double getMessagesPerSecond() {
        long elapsedNs = System.nanoTime() - startTime;
        long count = messageCount.get();
        return (count * 1_000_000_000.0) / elapsedNs;
    }
}
```

### Resource Utilization Monitoring

#### Memory Usage Tracking
```java
// Monitor allocation pressure
MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();

long usedMemory = heapUsage.getUsed();
long maxMemory = heapUsage.getMax();
double utilizationPercent = (usedMemory * 100.0) / maxMemory;
```

#### CPU Usage Monitoring
```java
// Track CPU utilization
ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
long cpuTime = threadBean.getCurrentThreadCpuTime();
long userTime = threadBean.getCurrentThreadUserTime();
```

---

## Common Performance Pitfalls

### Memory Management Issues

#### 1. Boxing in Hot Paths
```java
// BAD: Boxing overhead
List<Integer> list = new ArrayList<>();
for (int i = 0; i < 1000000; i++) {
    list.add(i); // Auto-boxing creates Integer objects
}

// GOOD: Use primitive collections
IntArrayList list = new IntArrayList();
for (int i = 0; i < 1000000; i++) {
    list.addInt(i); // No boxing overhead
}
```

#### 2. Unnecessary Allocation in Loops
```java
// BAD: Object creation in hot loop
for (int i = 0; i < iterations; i++) {
    String result = new String("data"); // Unnecessary allocation
    process(result);
}

// GOOD: Reuse objects
String reusableString = "data";
for (int i = 0; i < iterations; i++) {
    process(reusableString);
}
```

### Concurrency Pitfalls

#### 1. False Sharing
```java
// BAD: Fields accessed by different threads in same cache line
class SharedData {
    volatile long producerCounter;  // Updated by producer
    volatile long consumerCounter;  // Updated by consumer - false sharing!
}

// GOOD: Separate cache lines with padding
class SharedData {
    volatile long producerCounter;
    
    // 64-byte padding to prevent false sharing
    long p1, p2, p3, p4, p5, p6, p7;
    
    volatile long consumerCounter;
}
```

#### 2. Inappropriate Idle Strategy
```java
// BAD: BusySpinIdleStrategy in low-priority background thread
IdleStrategy strategy = BusySpinIdleStrategy.INSTANCE; // Wastes CPU

// GOOD: Use appropriate strategy for the use case
IdleStrategy strategy = new BackoffIdleStrategy(); // Balanced approach
```

### JVM Configuration Issues

#### 1. Variable Heap Size
```bash
# BAD: Variable heap causes allocation overhead
-Xms1g -Xmx8g

# GOOD: Fixed heap size for predictable performance
-Xms8g -Xmx8g
```

#### 2. Missing Unsafe Access
```bash
# BAD: Missing module access causes fallback to slower APIs
java Application

# GOOD: Proper module access enables Unsafe optimizations
java --add-opens java.base/jdk.internal.misc=ALL-UNNAMED Application
```

---

## Performance Regression Detection

### Automated Performance Testing

#### Benchmark Integration in CI/CD
```bash
#!/bin/bash
# performance-test.sh

# Run baseline benchmarks
java -jar agrona-benchmarks.jar \
  -rf json \
  -rff baseline-results.json \
  ClockBenchmark

# Run current benchmarks  
java -jar current-benchmarks.jar \
  -rf json \
  -rff current-results.json \
  ClockBenchmark

# Compare results (threshold: 5% regression)
python compare-benchmarks.py \
  --baseline baseline-results.json \
  --current current-results.json \
  --threshold 0.05
```

#### Performance Gate Implementation
```java
@Test
public void performanceRegressionTest() {
    long iterations = 1000000;
    long startTime = System.nanoTime();
    
    for (int i = 0; i < iterations; i++) {
        performCriticalOperation();
    }
    
    long endTime = System.nanoTime();
    long avgLatencyNs = (endTime - startTime) / iterations;
    
    // Fail if average latency exceeds threshold
    long maxAllowedLatencyNs = 100; // 100ns threshold
    assertThat(avgLatencyNs).isLessThan(maxAllowedLatencyNs);
}
```

### Continuous Monitoring

#### Metrics Collection
```java
public class PerformanceMetrics {
    private final Histogram latencyHistogram = new Histogram();
    private final Counter messageCounter = new Counter();
    
    public void recordOperation(long latencyNs) {
        latencyHistogram.record(latencyNs);
        messageCounter.increment();
        
        // Alert if 99th percentile exceeds threshold
        if (latencyHistogram.getPercentile(99.0) > 1000) {
            alertHighLatency();
        }
    }
}
```

#### System Resource Monitoring
```bash
# Monitor system metrics
sar -u 1 10  # CPU utilization
sar -r 1 10  # Memory utilization  
sar -n DEV 1 10  # Network utilization

# Monitor JVM metrics
jstat -gc [PID] 250ms  # GC statistics
jstat -compiler [PID]  # Compilation statistics
```

### Performance Alerting

#### Threshold-Based Alerts
```java
public class PerformanceMonitor {
    private static final long LATENCY_THRESHOLD_NS = 1000; // 1μs
    private static final double ERROR_RATE_THRESHOLD = 0.01; // 1%
    
    public void checkThresholds() {
        if (getCurrentLatency() > LATENCY_THRESHOLD_NS) {
            alertLatencyViolation();
        }
        
        if (getErrorRate() > ERROR_RATE_THRESHOLD) {
            alertErrorRateViolation();
        }
    }
}
```

---

## Conclusion

Performance optimization in Agrona applications requires a systematic approach combining careful algorithm selection, proper JVM configuration, platform-specific tuning, and continuous measurement. The key principles are:

1. **Choose appropriate idle strategies** based on latency vs. CPU usage requirements
2. **Eliminate allocations** in steady-state operation through pre-allocation and object reuse
3. **Configure JVM parameters** to enable Unsafe API access and optimize garbage collection
4. **Measure continuously** using JMH benchmarks and production metrics
5. **Monitor for regressions** through automated testing and alerting

By following these guidelines and leveraging Agrona's high-performance primitives, applications can achieve sub-microsecond latencies with predictable performance characteristics.

---

## References

- [Agrona Core Library](/agrona/src/main/java/org/agrona/)
- [Idle Strategy Implementations](/agrona/src/main/java/org/agrona/concurrent/)
- [JMH Benchmarks](/agrona-benchmarks/src/main/java/org/agrona/)
- [System Utilities](/agrona/src/main/java/org/agrona/SystemUtil.java)
- [Technical Architecture](/docs/architecture/system-design.md)
- [Buffer Management Guide](/docs/guides/buffer-operations.md)
- [Concurrent Programming Guide](/docs/guides/concurrent-programming.md)
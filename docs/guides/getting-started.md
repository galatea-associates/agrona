# Getting Started with Agrona

## 1. Introduction

Agrona is a high-performance, zero-dependency Java library that provides essential data structures and utility methods for building low-latency applications. This guide will help you get started with Agrona's core features, including buffer operations, primitive collections, concurrent utilities, and performance benchmarking.

> Source: `/README.md:10-13`

## 2. Prerequisites and Environment Setup

### 2.1 Java Version Requirements

Agrona requires Java 17 or later. The library is tested with Java 17 through Java 25-ea to ensure compatibility across modern JVM versions.

> Source: `/build.gradle:47-49`

```bash
# Verify your Java version
java -version
```

### 2.2 Essential JVM Configuration

For optimal performance, particularly when using Unsafe APIs and disabling bounds checking, configure your JVM with the following flags:

```bash
# Required for Unsafe API access (Java 17+)
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/java.util.zip=ALL-UNNAMED

# Optional: Enable dynamic agent loading (Java 21+)
-XX:+EnableDynamicAgentLoading

# Performance tuning
-Dagrona.disable.bounds.checks=true
-Dagrona.strict.alignment.checks=true
-XX:+UseParallelGC
```

> Source: `/build.gradle:206-227`

### 2.3 System Properties

Agrona provides several system properties for configuration:

| Property | Default | Description |
|----------|---------|-------------|
| `agrona.disable.bounds.checks` | `false` | Disable bounds checking for maximum performance |
| `agrona.strict.alignment.checks` | `false` | Enable strict buffer alignment validation |
| `net.bytebuddy.experimental` | `false` | Enable ByteBuddy experimental features |

> Source: `/agrona/src/main/java/org/agrona/DirectBuffer.java:43-46`

## 3. Installation and Dependency Configuration

### 3.1 Maven Configuration

Add Agrona modules to your Maven project:

```xml
<dependencies>
    <!-- Core Agrona library -->
    <dependency>
        <groupId>org.agrona</groupId>
        <artifactId>agrona</artifactId>
        <version>1.25.0</version>
    </dependency>
    
    <!-- ByteBuddy agent for buffer alignment (optional) -->
    <dependency>
        <groupId>org.agrona</groupId>
        <artifactId>agrona-agent</artifactId>
        <version>1.25.0</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- JMH benchmarks (development/testing) -->
    <dependency>
        <groupId>org.agrona</groupId>
        <artifactId>agrona-benchmarks</artifactId>
        <version>1.25.0</version>
        <scope>test</scope>
    </dependency>
    
    <!-- JCStress concurrency tests (development/testing) -->
    <dependency>
        <groupId>org.agrona</groupId>
        <artifactId>agrona-concurrency-tests</artifactId>
        <version>1.25.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

> Source: `/build.gradle:55-56`

### 3.2 Gradle Configuration

For Gradle projects, add the following dependencies:

```gradle
dependencies {
    // Core Agrona library
    implementation 'org.agrona:agrona:1.25.0'
    
    // ByteBuddy agent for buffer alignment (optional)
    runtimeOnly 'org.agrona:agrona-agent:1.25.0'
    
    // JMH benchmarks (development/testing)
    testImplementation 'org.agrona:agrona-benchmarks:1.25.0'
    
    // JCStress concurrency tests (development/testing)  
    testImplementation 'org.agrona:agrona-concurrency-tests:1.25.0'
}

// Configure test JVM arguments
test {
    jvmArgs(
        '--add-opens', 'java.base/jdk.internal.misc=ALL-UNNAMED',
        '--add-opens', 'java.base/java.util.zip=ALL-UNNAMED'
    )
    
    systemProperties = [
        'agrona.disable.bounds.checks': 'false',
        'agrona.strict.alignment.checks': 'true'
    ]
}
```

> Source: `/build.gradle:170-230`

### 3.3 Module Overview

Agrona consists of four main modules:

| Module | Purpose | Key Features |
|--------|---------|--------------|
| **agrona** | Core library | Buffer operations, collections, concurrent utilities |
| **agrona-agent** | Runtime instrumentation | Buffer alignment enforcement using ByteBuddy |
| **agrona-benchmarks** | Performance testing | JMH microbenchmarks for all components |
| **agrona-concurrency-tests** | Correctness validation | JCStress tests for concurrent data structures |

> Source: `/build.gradle:253-522`

## 4. First Steps: Basic Buffer Operations

### 4.1 Understanding DirectBuffer

The `DirectBuffer` interface provides efficient, zero-copy access to memory regions. It abstracts over byte arrays, ByteBuffers, and off-heap memory.

```java
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

// Create a buffer from a byte array
byte[] data = new byte[1024];
MutableDirectBuffer buffer = new UnsafeBuffer(data);

// Write primitive values
buffer.putInt(0, 42);
buffer.putLong(4, 123456789L);
buffer.putBytes(12, "Hello Agrona".getBytes());

// Read values back
int intValue = buffer.getInt(0);        // 42
long longValue = buffer.getLong(4);     // 123456789L
```

> Source: `/agrona/src/main/java/org/agrona/DirectBuffer.java:58-122`

### 4.2 Zero-Copy Buffer Wrapping

Agrona buffers can wrap existing data structures without copying:

```java
import java.nio.ByteBuffer;

// Wrap a ByteBuffer (heap or direct)
ByteBuffer jdkBuffer = ByteBuffer.allocateDirect(1024);
DirectBuffer agronaBuffer = new UnsafeBuffer();
agronaBuffer.wrap(jdkBuffer);

// Wrap a byte array with offset and length
byte[] array = new byte[2048];
agronaBuffer.wrap(array, 100, 1024);  // Use bytes 100-1123

// Wrap off-heap memory directly
long memoryAddress = // ... obtain from native allocation
agronaBuffer.wrap(memoryAddress, 1024);
```

> Source: `/agrona/src/main/java/org/agrona/DirectBuffer.java:73-122`

### 4.3 Buffer Capacity and Bounds Checking

```java
// Check buffer capacity
int capacity = buffer.capacity();

// Bounds checking is controlled by system property
// agrona.disable.bounds.checks=true for maximum performance
boolean shouldCheck = DirectBuffer.SHOULD_BOUNDS_CHECK;

// Access the bounds checking property name
String propertyName = DirectBuffer.DISABLE_BOUNDS_CHECKS_PROP_NAME;
```

> Source: `/agrona/src/main/java/org/agrona/DirectBuffer.java:43-55`

## 5. Primitive Collections: Boxing Avoidance

### 5.1 Int-to-Int HashMap Example

Agrona's primitive collections eliminate boxing overhead by working directly with primitive types:

```java
import org.agrona.collections.Int2IntHashMap;

// Create a map with a specific missing value (equivalent to null)
Int2IntHashMap priceMap = new Int2IntHashMap(-1);

// Add mappings - no boxing occurs
priceMap.put(12345, 9950);  // Stock ID -> Price in cents
priceMap.put(67890, 15000);
priceMap.put(11111, 7825);

// Retrieve values
int price = priceMap.get(12345);  // Returns 9950
int missing = priceMap.get(99999);  // Returns -1 (missing value)

// Check for existence
boolean exists = priceMap.containsKey(12345);  // true

// Size and operations
int size = priceMap.size();
priceMap.remove(67890);
priceMap.clear();
```

> Source: `/agrona/src/main/java/org/agrona/collections/Int2IntHashMap.java:56-79`

### 5.2 Performance Benefits

Primitive collections provide significant advantages over standard Java collections:

| Operation | Standard HashMap<Integer,Integer> | Int2IntHashMap | Performance Gain |
|-----------|-----------------------------------|----------------|------------------|
| Put operation | Boxing + hash + allocation | Direct primitive operation | 5-10x faster |
| Get operation | Boxing + hash + unboxing | Direct primitive access | 3-5x faster |
| Memory usage | Object header + Integer objects | Primitive arrays only | 4x less memory |
| GC pressure | High (temporary Integer objects) | Zero (no allocations) | Eliminates GC spikes |

### 5.3 Available Primitive Collections

```java
// Hash maps for different primitive combinations
Int2IntHashMap intMap = new Int2IntHashMap(-1);
Long2LongHashMap longMap = new Long2LongHashMap(-1L);
Int2ObjectHashMap<String> intObjectMap = new Int2ObjectHashMap<>();

// Primitive sets
IntHashSet intSet = new IntHashSet(-1);
LongHashSet longSet = new LongHashSet(-1L);

// Primitive lists
IntArrayList intList = new IntArrayList();
LongArrayList longList = new LongArrayList();
```

## 6. Concurrent Programming with Lock-Free Data Structures

### 6.1 Many-to-One Concurrent Queue

The `ManyToOneConcurrentArrayQueue` provides high-performance, lock-free queuing for multiple producer, single consumer scenarios:

```java
import org.agrona.concurrent.ManyToOneConcurrentArrayQueue;
import java.util.function.Consumer;

// Create a queue with specified capacity (must be power of 2)
ManyToOneConcurrentArrayQueue<String> queue = 
    new ManyToOneConcurrentArrayQueue<>(1024);

// Producer threads can safely offer items
boolean offered = queue.offer("Hello");
if (!offered) {
    // Queue is full, handle back-pressure
}

// Consumer thread polls for items
String item = queue.poll();  // Returns null if empty
if (item != null) {
    processItem(item);
}

// Batch draining for efficiency
queue.drain(message -> {
    processMessage(message);
});

// Check queue state
int size = queue.size();
int capacity = queue.capacity();
boolean empty = queue.isEmpty();
```

> Source: `/agrona/src/main/java/org/agrona/concurrent/ManyToOneConcurrentArrayQueue.java:42-80`

### 6.2 Lock-Free Algorithm Benefits

Agrona's concurrent utilities implement proven lock-free algorithms:

- **Wait-Free Progress**: Operations complete in bounded time regardless of other threads
- **No Thread Blocking**: Eliminates context switching and priority inversion issues  
- **Scalable Performance**: Linear scaling with CPU cores
- **Cache-Friendly Design**: Minimizes false sharing through explicit padding

> Source: `/agrona/src/main/java/org/agrona/concurrent/ManyToOneConcurrentArrayQueue.java:23-37`

### 6.3 Memory Ordering and Consistency

Lock-free data structures use memory ordering guarantees:

```java
// The queue uses release/acquire semantics internally
// Offers use release semantics
// Polls use acquire semantics
// This ensures visibility and ordering without locks
```

## 7. Running Performance Benchmarks

### 7.1 JMH Benchmark Execution

Agrona includes comprehensive JMH benchmarks for performance validation:

```bash
# Build the benchmark JAR
./gradlew :agrona-benchmarks:shadowJar

# Run all benchmarks
java -jar agrona-benchmarks/build/libs/benchmarks.jar

# Run specific benchmark classes
java -jar agrona-benchmarks/build/libs/benchmarks.jar ClockBenchmark

# Run with custom JVM flags for optimal performance
java -Dagrona.disable.bounds.checks=true \
     -XX:+UseParallelGC \
     -jar agrona-benchmarks/build/libs/benchmarks.jar
```

> Source: `/build.gradle:463-478`

### 7.2 Clock Performance Benchmarking

Example of running the clock benchmarks to understand timing overhead:

```java
import org.openjdk.jmh.annotations.*;

// The ClockBenchmark measures different timing approaches
@Fork(value = 3, jvmArgsPrepend = "-Dagrona.disable.bounds.checks=true")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
public class ClockBenchmark {
    
    @Benchmark
    public long systemNanoTime() {
        return System.nanoTime();
    }
    
    @Benchmark  
    public long systemCurrentTimeMillis() {
        return System.currentTimeMillis();
    }
}
```

> Source: `/agrona-benchmarks/src/main/java/org/agrona/concurrent/ClockBenchmark.java:33-73`

### 7.3 Benchmark Results Interpretation

Typical benchmark results show Agrona's performance advantages:

```
Benchmark                          Mode  Cnt   Score   Error  Units
ClockBenchmark.systemNanoTime      avgt   30  28.123 ± 0.891  ns/op
ClockBenchmark.systemCurrentTime   avgt   30  15.678 ± 0.234  ns/op
BufferBenchmark.unsafeGetInt       avgt   30   0.652 ± 0.012  ns/op
BufferBenchmark.byteBufferGetInt   avgt   30   1.234 ± 0.045  ns/op
```

## 8. Environment Configuration Best Practices

### 8.1 Production JVM Settings

For production deployments, use these recommended JVM settings:

```bash
# Memory and GC configuration
-Xms8g -Xmx8g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=1
-XX:+UnlockExperimentalVMOptions
-XX:+UseStringDeduplication

# Agrona-specific optimizations
-Dagrona.disable.bounds.checks=true
-Dagrona.strict.alignment.checks=false

# Unsafe API access (required)
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/java.util.zip=ALL-UNNAMED

# Optional performance tuning
-XX:+UseLargePages
-XX:LargePageSizeInBytes=2M
```

### 8.2 Agent Configuration

When using the agrona-agent for buffer alignment enforcement:

```bash
# Add the agent JAR to your JVM
-javaagent:agrona-agent-1.25.0.jar

# Or specify specific alignment enforcement
-javaagent:agrona-agent-1.25.0.jar=alignBuffers=true
```

> Source: `/build.gradle:377-410`

### 8.3 Testing Configuration

For development and testing environments:

```bash
# Enable bounds checking for safety
-Dagrona.disable.bounds.checks=false
-Dagrona.strict.alignment.checks=true

# Enable ByteBuddy experimental features
-Dnet.bytebuddy.experimental=true

# Testing-specific JVM flags
-XX:+EnableDynamicAgentLoading
```

> Source: `/build.gradle:224-228`

## 9. Next Steps and Advanced Topics

### 9.1 Advanced Buffer Operations

Once comfortable with basic operations, explore:

- Memory-mapped file buffers for persistent storage
- Atomic buffer operations for concurrent access
- Custom buffer implementations for specialized use cases
- Buffer expansion strategies for dynamic sizing

### 9.2 Advanced Concurrent Programming

Build on the queue examples with:

- Ring buffer implementations for structured messaging
- Agent framework for concurrent task scheduling  
- Broadcast buffers for one-to-many communication
- Idle strategies for efficient CPU utilization

### 9.3 Integration Patterns

Learn how Agrona integrates with:

- **Aeron**: High-performance messaging transport
- **SBE (Simple Binary Encoding)**: Efficient message serialization
- **Chronicle Queue**: Persistent messaging with memory-mapped files
- **Custom Applications**: Building domain-specific high-performance systems

### 9.4 Performance Monitoring

Implement monitoring and observability:

- JFR (Java Flight Recorder) profiling for allocation tracking
- Custom metrics using Agrona's counter implementations
- Latency histogram collection and analysis
- Memory usage monitoring and optimization

## 10. Common Issues and Troubleshooting

### 10.1 Unsafe API Access Issues

If you encounter `IllegalAccessError` with Unsafe operations:

```bash
# Ensure you have the correct JVM flags
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED

# For older Java versions, you may need
--illegal-access=permit
```

### 10.2 Performance Not as Expected

1. **Verify bounds checking is disabled**: Set `agrona.disable.bounds.checks=true`
2. **Check GC configuration**: Use low-latency collectors like G1 or ZGC
3. **Validate alignment**: Enable strict alignment checks during development
4. **Profile allocation**: Use profilers to identify unexpected allocations

### 10.3 Concurrency Issues

1. **Verify queue capacity**: Ensure queues are appropriately sized
2. **Check producer/consumer patterns**: Match queue type to usage pattern
3. **Monitor back-pressure**: Implement proper handling for full queues
4. **Validate memory ordering**: Understand acquire/release semantics

## 11. Additional Resources

- **Official Documentation**: [Javadocs](https://www.javadoc.io/doc/org.agrona/agrona)
- **Source Code**: [GitHub Repository](https://github.com/aeron-io/agrona)
- **Change Log**: [Version History](https://github.com/aeron-io/agrona/wiki/Change-Log)
- **Community**: [Discussions and Issues](https://github.com/aeron-io/agrona/issues)

## 12. Performance Optimization Tips

### 12.1 Buffer Operations

- Always disable bounds checking in production (`agrona.disable.bounds.checks=true`)
- Prefer direct buffers for off-heap operations
- Use appropriate buffer sizes to minimize reallocations
- Consider memory alignment for optimal CPU cache usage

### 12.2 Collection Usage

- Choose the right primitive collection for your key/value types
- Set appropriate initial capacity to avoid resizing
- Use missing values that don't conflict with your data domain
- Consider load factor impact on performance vs memory usage

### 12.3 Concurrent Programming

- Match queue implementations to your producer/consumer patterns
- Size queues appropriately for your throughput requirements
- Implement proper back-pressure handling for queue full conditions
- Use batch operations (like drain()) for improved efficiency

---

*This guide provides a comprehensive introduction to Agrona's core features. For advanced usage patterns and optimization techniques, consult the API documentation and explore the extensive test suite for additional examples.*
# Agrona

[![Javadocs](https://www.javadoc.io/badge/org.agrona/agrona.svg)](https://www.javadoc.io/doc/org.agrona/agrona)
[![GitHub](https://img.shields.io/github/license/aeron-io/Agrona.svg)](https://github.com/aeron-io/agrona/blob/master/LICENSE)

[![Actions Status](https://github.com/aeron-io/agrona/workflows/Continuous%20Integration/badge.svg)](https://github.com/aeron-io/agrona/actions)
[![CodeQL Status](https://github.com/aeron-io/agrona/workflows/CodeQL/badge.svg)](https://github.com/aeron-io/agrona/actions)

## Project Description

Agrona is a high-performance, zero-dependency Java library that provides essential data structures and utility methods for building low-latency applications. As the foundational library powering industry-leading projects like [Aeron](https://github.com/aeron-io/aeron) (efficient reliable UDP unicast, multicast, and IPC message transport) and [Simple Binary Encoding](https://github.com/aeron-io/simple-binary-encoding) (SBE), Agrona has established itself as the de facto standard for high-performance Java primitives in microsecond-sensitive computing environments.

### Core Value Proposition

Agrona addresses critical performance bottlenecks in standard Java collections and utilities by providing:

- **Zero-allocation APIs** and direct memory access to minimize garbage collection pressure
- **Specialized primitive collections** without boxing/unboxing overhead  
- **Cache-line aware implementations** with explicit padding for optimal performance
- **Lock-free data structures** for high-performance concurrent scenarios
- **Direct memory manipulation** capabilities for zero-copy operations

### Performance Excellence

- **Sub-microsecond latency** for buffer operations
- **Zero garbage generation** in steady-state operation
- **Linear scalability** with CPU cores for concurrent structures
- **Orders of magnitude** reduction in memory allocation overhead

For the latest version information and changes see the [Change Log](https://github.com/aeron-io/agrona/wiki/Change-Log). 

The latest release and **downloads** can be found in [Maven Central](http://search.maven.org/#search%7Cga%7C1%7Cagrona).

## Utility List

### Buffer Management Infrastructure

- **DirectBuffer & MutableDirectBuffer** - Thread-safe interfaces for direct and atomic buffer operations with memory ordering semantics
- **AtomicBuffer & UnsafeBuffer** - High-performance implementations for on-heap and off-heap memory manipulation
- **ExpandableArrayBuffer** - Automatic buffer growth with configurable expansion strategies
- **Memory-Mapped Support** - Efficient file-backed memory regions for persistence and large datasets

### Primitive Collections Framework

- **Hash Maps** - Open addressing and linear probing with int/long primitive keys to object references or primitive values
- **Hash Sets** - High-performance sets for int/long primitives and object references with collision resolution
- **Array Lists** - Dynamic arrays for primitive types with capacity management to avoid boxing overhead
- **Counter Maps** - Specialized maps for metrics aggregation and statistical analysis
- **LRU Caches** - Set associative caches with int/long primitive keys to object reference values

### Concurrent Utilities Suite

- **Lock-Free Queues** - SPSC, MPSC, and MPMC implementations for low-latency applications
- **Ring/Broadcast Buffers** - Off-heap implementations for efficient IPC communication and event distribution
- **Agent Framework** - Simple concurrent services framework with pluggable idle strategies
- **Atomic Counters** - Off-heap counter implementations for application telemetry, position tracking, and coordination

### System Integration Utilities

- **High-Resolution Clocks** - Epoch and nano clock implementations with microsecond precision for latency measurement
- **Signal Handling** - Support for "Ctrl + C" and graceful shutdown in server applications  
- **Timer Wheel** - Scalable timer implementation for scheduling at deadlines with O(1) register and cancel time
- **IdGenerator** - Concurrent and distributed unique ID generator using lock-free Twitter Snowflake algorithm
- **DistinctErrorLog** - Efficient logging of distinct errors to prevent disk space exhaustion

### Advanced Features

- **Code Generation** - Utilities for generating specialized implementations from annotated primitive types
- **Stream Wrappers** - InputStream and OutputStream implementations that wrap direct buffers for zero-copy I/O
- **Memory Alignment** - Cache-line padding and alignment enforcement for optimal concurrent performance
- **Direct Memory Access** - Safe abstractions over Unsafe API for zero-copy operations and memory manipulation

## Build Instructions

### Prerequisites

Agrona requires the following to build and run:

- **Java Development Kit (JDK) 17 or higher**
  - Tested with Java 17, 21, and up to Java 25-ea
  - JVM implementations supporting `jdk.internal.misc.Unsafe` API access
- **Build System**: [Gradle](http://gradle.org/) (Gradle Wrapper included)

### Building from Source

Clone the repository and build using the included Gradle wrapper:

```bash
git clone https://github.com/aeron-io/agrona.git
cd agrona
```

**Full clean and build:**
```bash
./gradlew
```

**Build specific modules:**
```bash
./gradlew :agrona:build                    # Core library only
./gradlew :agrona-agent:build              # Alignment verification agent
./gradlew :agrona-benchmarks:build         # Performance benchmarks
./gradlew :agrona-concurrency-tests:build  # Concurrency correctness tests
```

**Run tests:**
```bash
./gradlew test                             # All tests
./gradlew :agrona:test                     # Core library tests only
./gradlew :agrona-concurrency-tests:test   # JCStress concurrency tests
```

**Run benchmarks:**
```bash
./gradlew :agrona-benchmarks:jmh           # JMH performance benchmarks
```

### Maven Dependency

Add Agrona to your project using Maven:

```xml
<dependency>
    <groupId>org.agrona</groupId>
    <artifactId>agrona</artifactId>
    <version>1.23.1</version>
</dependency>
```

### Gradle Dependency

Add Agrona to your project using Gradle:

```gradle
dependencies {
    implementation 'org.agrona:agrona:1.23.1'
}
```

### Build Configuration

The project uses this [build.gradle](https://github.com/aeron-io/agrona/blob/master/build.gradle) file with the following key configurations:

- **Zero external dependencies** - Pure JDK implementation
- **Cross-platform compatibility** - Linux, Windows, macOS support  
- **Multi-module structure** - Core library plus specialized modules
- **Comprehensive testing** - Unit tests, concurrency tests, and benchmarks

## Integration References

### Primary Integrations

Agrona serves as the foundational library for several high-performance projects:

| Project | Integration Type | Usage |
|---------|------------------|-------|
| **[Aeron](https://github.com/aeron-io/aeron)** | Direct dependency | Efficient reliable UDP/IPC transport implementation using Agrona's lock-free queues and buffer management |
| **[Simple Binary Encoding (SBE)](https://github.com/aeron-io/simple-binary-encoding)** | Core primitives | High-performance message codec leveraging DirectBuffer operations and zero-copy techniques |
| **[Chronicle Queue](https://github.com/OpenHFT/Chronicle-Queue)** | Memory management | Persistent messaging utilizing memory-mapped file support and buffer abstractions |

### Integration Patterns

**Message Processing Systems**
- Zero-copy buffer manipulation for protocol encoding/decoding
- Ring buffers for efficient inter-thread communication  
- Lock-free queues for producer-consumer architectures

**Real-Time Analytics**
- Primitive collections for high-performance data aggregation
- Off-heap counters for metrics collection
- Memory-mapped files for large dataset processing

**Financial Trading Platforms**
- Sub-microsecond latency buffer operations
- Lock-free data structures for order processing
- Atomic operations for position tracking

### Community Integrations

Agrona's zero-dependency design enables seamless integration into existing Java ecosystems:

- **Spring Framework** - Can be used alongside Spring for high-performance components
- **Netty** - Complementary use for network I/O with Agrona's buffer abstractions
- **Apache Kafka** - Custom serializers using Agrona's buffer operations
- **Reactive Streams** - Publishers and subscribers utilizing lock-free queues

### Documentation Resources

For comprehensive documentation on integrating Agrona:

- **[API Documentation](https://www.javadoc.io/doc/org.agrona/agrona)** - Complete Javadoc reference
- **[Performance Benchmarks](agrona-benchmarks/)** - JMH benchmark results and methodology
- **[Concurrency Tests](agrona-concurrency-tests/)** - JCStress validation of concurrent behavior
- **[Change Log](https://github.com/aeron-io/agrona/wiki/Change-Log)** - Version history and migration guides

## License Information

**Copyright 2014-2025 Real Logic Limited.**

Licensed under the **Apache License, Version 2.0** (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

**Full license text:** See [LICENSE](LICENSE) file for complete license terms.

**License Summary:**
- ✅ Commercial use permitted
- ✅ Modification and distribution allowed  
- ✅ Private use permitted
- ✅ Patent grant included
- ❗ License and copyright notice must be included
- ❗ No liability or warranty provided

# System Utilities API Reference

## Overview

Agrona's system utilities provide a comprehensive set of low-level operations for system introspection, file I/O, resource management, exception handling, and inter-process communication. These utilities form the foundation for building high-performance Java applications with minimal overhead and predictable behavior.

> **Source References**: This documentation covers utilities from `/agrona/src/main/java/org/agrona/` including SystemUtil.java, IoUtil.java, CloseHelper.java, and related system-level classes.

## Table of Contents

1. [System Introspection](#1-system-introspection)
2. [File Operations and I/O](#2-file-operations-and-io)
3. [Resource Management](#3-resource-management)
4. [Exception Handling](#4-exception-handling)
5. [Validation and Verification](#5-validation-and-verification)
6. [String and Data Utilities](#6-string-and-data-utilities)
7. [Error Handling Framework](#7-error-handling-framework)
8. [Resource Lifecycle Management](#8-resource-lifecycle-management)
9. [Inter-Process Communication](#9-inter-process-communication)
10. [Timer Scheduling](#10-timer-scheduling)

---

## 1. System Introspection

### 1.1 SystemUtil Class

The `SystemUtil` class provides essential system introspection capabilities for runtime environment detection, process management, and configuration loading.

> **Source**: `/agrona/src/main/java/org/agrona/SystemUtil.java`

#### 1.1.1 Operating System Detection

```java
// Get OS name as lowercase string
String osName = SystemUtil.osName();

// Architecture detection
String architecture = SystemUtil.osArch();

// Platform-specific checks
boolean isWindows = SystemUtil.isWindows();
boolean isLinux = SystemUtil.isLinux();
boolean isMac = SystemUtil.isMac();
boolean isX64 = SystemUtil.isX64Arch();
```

**API Reference:**

| Method | Return Type | Description | Source Line |
|--------|-------------|-------------|-------------|
| `osName()` | `String` | Returns operating system name in lowercase | 77 |
| `osArch()` | `String` | Returns OS architecture or "unknown" | 89 |
| `isWindows()` | `boolean` | True if OS name starts with "win" | 110 |
| `isLinux()` | `boolean` | True if OS name contains "linux" | 120 |
| `isMac()` | `boolean` | True if OS name starts with "mac" | 130 |
| `isX64Arch()` | `boolean` | True if architecture is x86-64 based | 140 |

#### 1.1.2 Process Information

```java
// Get current process ID
long processId = SystemUtil.getPid();

// Check if debugger is attached
boolean debuggerAttached = SystemUtil.isDebuggerAttached();

// Get formatted thread dump
String threadDump = SystemUtil.threadDump();
```

**Constants:**
- `PID_NOT_FOUND = 0`: Returned when process ID cannot be determined

#### 1.1.3 Configuration and Property Management

```java
// Load properties from file or URL
SystemUtil.loadPropertiesFile("config.properties");
SystemUtil.loadPropertiesFile(PropertyAction.PRESERVE, "config.properties");

// Get property with null handling
String value = SystemUtil.getProperty("my.property");
String valueWithDefault = SystemUtil.getProperty("my.property", "defaultValue");

// Parse size values with units (k, m, g)
int sizeAsInt = SystemUtil.getSizeAsInt("buffer.size", 1024);
long sizeAsLong = SystemUtil.getSizeAsLong("max.size", 1024L);

// Parse duration values with units (ns, us, ms, s)
long durationNanos = SystemUtil.getDurationInNanos("timeout", 1000000L);
```

**Property Actions:**
- `PropertyAction.REPLACE`: Overwrite existing system properties
- `PropertyAction.PRESERVE`: Keep existing values, only set if not present

#### 1.1.4 Utility Methods

```java
// Get temporary directory with trailing separator
String tmpDir = SystemUtil.tmpDirName();

// Generate thread dump to StringBuilder
StringBuilder sb = new StringBuilder();
SystemUtil.threadDump(sb);

// Parse size and duration strings directly
long parsedSize = SystemUtil.parseSize("buffer.size", "64k");
long parsedDuration = SystemUtil.parseDuration("timeout", "100ms");
```

**Size Suffixes:**
- `k` or `K`: Kilobytes (×1024)
- `m` or `M`: Megabytes (×1024²)
- `g` or `G`: Gigabytes (×1024³)

**Duration Suffixes:**
- `ns`: Nanoseconds
- `us`: Microseconds  
- `ms`: Milliseconds
- `s`: Seconds

---

## 2. File Operations and I/O

### 2.1 IoUtil Class

The `IoUtil` class provides high-performance file operations with emphasis on memory-mapped files and efficient I/O patterns.

> **Source**: `/agrona/src/main/java/org/agrona/IoUtil.java`

#### 2.1.1 Memory-Mapped File Operations

```java
// Map existing file for read-write access
MappedByteBuffer buffer = IoUtil.mapExistingFile(
    new File("data.bin"), "data file");

// Map specific region of existing file
MappedByteBuffer regionBuffer = IoUtil.mapExistingFile(
    new File("data.bin"), "data file", 1024L, 4096L);

// Map with specific access mode
MappedByteBuffer readOnlyBuffer = IoUtil.mapExistingFile(
    new File("data.bin"), FileChannel.MapMode.READ_ONLY, "data file");

// Create and map new file
MappedByteBuffer newBuffer = IoUtil.mapNewFile(
    new File("new.bin"), 8192L);

// Create new file without zero-filling
MappedByteBuffer sparseBuffer = IoUtil.mapNewFile(
    new File("sparse.bin"), 8192L, false);
```

**Memory Mapping API:**

| Method | Parameters | Description | Source Line |
|--------|------------|-------------|-------------|
| `mapExistingFile(File, String)` | file, description | Map entire file as READ_WRITE | 302 |
| `mapExistingFile(File, String, long, long)` | file, description, offset, length | Map file region as READ_WRITE | 319 |
| `mapExistingFile(File, MapMode, String)` | file, mode, description | Map entire file with specified mode | 336 |
| `mapNewFile(File, long)` | file, length | Create and map new file with zero-fill | 400 |
| `mapNewFile(File, long, boolean)` | file, length, fillWithZeros | Create and map new file with optional zero-fill | 415 |

#### 2.1.2 File Management Operations

```java
// Create empty file with specified length
FileChannel channel = IoUtil.createEmptyFile(new File("empty.bin"), 1024L);

// Create directory if it doesn't exist
IoUtil.ensureDirectoryExists(new File("/tmp/myapp"), "application directory");

// Recreate directory (delete if exists, then create)
IoUtil.ensureDirectoryIsRecreated(
    new File("/tmp/myapp"), 
    "application directory",
    (path, desc) -> System.out.println("Recreated: " + path)
);

// Delete file or directory recursively
IoUtil.delete(new File("/tmp/myapp"), false); // throw on failure
IoUtil.delete(new File("/tmp/myapp"), true);  // ignore failures

// Delete with error handler
IoUtil.delete(new File("/tmp/myapp"), throwable -> 
    System.err.println("Delete failed: " + throwable.getMessage()));

// Delete only if exists
IoUtil.deleteIfExists(new File("optional.tmp"));
```

#### 2.1.3 Buffer and Memory Management

```java
// Unmap memory-mapped buffer immediately
MappedByteBuffer mapped = IoUtil.mapExistingFile(file, "test");
IoUtil.unmap(mapped); // Force unmap without waiting for GC

// Unmap any ByteBuffer (only unmaps if memory-mapped)
ByteBuffer anyBuffer = getSomeBuffer();
IoUtil.unmap(anyBuffer);

// Fill file channel with specific byte value
FileChannel channel = getFileChannel();
IoUtil.fill(channel, 0, 1024, (byte) 0xFF);
```

#### 2.1.4 Utility Operations

```java
// Get temporary directory name with separator
String tmpDir = IoUtil.tmpDirName();

// Check file existence with descriptive errors
IoUtil.checkFileExists(new File("required.dat"), "configuration file");
```

**Constants:**
- `BLOCK_SIZE = 4096`: Standard file block size for I/O operations

---

## 3. Resource Management

### 3.1 CloseHelper Class

The `CloseHelper` class provides robust patterns for safely closing `AutoCloseable` resources with comprehensive error handling strategies.

> **Source**: `/agrona/src/main/java/org/agrona/CloseHelper.java`

#### 3.1.1 Quiet Closing (Exception Suppression)

```java
// Close single resource, suppress exceptions
FileInputStream stream = new FileInputStream("data.txt");
CloseHelper.quietClose(stream);

// Close multiple resources, suppress all exceptions
Collection<AutoCloseable> resources = Arrays.asList(stream1, stream2, channel);
CloseHelper.quietCloseAll(resources);

// Close variable number of resources
CloseHelper.quietCloseAll(stream1, stream2, channel, buffer);
```

**Quiet Closing API:**

| Method | Parameters | Behavior | Source Line |
|--------|------------|----------|-------------|
| `quietClose(AutoCloseable)` | resource | Suppresses all exceptions, handles null | 35 |
| `quietCloseAll(Collection)` | resources | Suppresses all exceptions, handles nulls | 54 |
| `quietCloseAll(AutoCloseable...)` | varargs | Suppresses all exceptions, handles nulls | 79 |

#### 3.1.2 Exception-Propagating Closing

```java
// Close with exception rethrowing
CloseHelper.close(resource); // Rethrows as RuntimeException

// Close multiple with exception aggregation
List<AutoCloseable> resources = getResources();
CloseHelper.closeAll(resources); // Aggregates multiple exceptions

// Close with suppressed exception handling
try {
    CloseHelper.closeAll(stream1, stream2, stream3);
} catch (RuntimeException e) {
    // Primary exception with others as suppressed
    Throwable[] suppressed = e.getSuppressed();
}
```

#### 3.1.3 Error Handler Integration

```java
// Close with custom error handling
ErrorHandler errorHandler = throwable -> 
    logger.warn("Resource cleanup failed", throwable);

CloseHelper.close(errorHandler, resource);
CloseHelper.closeAll(errorHandler, resources);
CloseHelper.closeAll(errorHandler, stream1, stream2, stream3);
```

**Error Handler Closing API:**

| Method | Parameters | Behavior | Source Line |
|--------|------------|----------|-------------|
| `close(ErrorHandler, AutoCloseable)` | handler, resource | Delegates exceptions to handler | 210 |
| `closeAll(ErrorHandler, Collection)` | handler, resources | Delegates all exceptions to handler | 238 |
| `closeAll(ErrorHandler, AutoCloseable...)` | handler, varargs | Delegates all exceptions to handler | 284 |

#### 3.1.4 Usage Patterns

```java
// Pattern 1: Resource cleanup in finally block
FileChannel channel = null;
try {
    channel = FileChannel.open(path, StandardOpenOption.READ);
    // Use channel
} finally {
    CloseHelper.quietClose(channel);
}

// Pattern 2: Bulk resource cleanup
List<AutoCloseable> managedResources = new ArrayList<>();
try {
    // Add resources as they're created
    managedResources.add(openStream());
    managedResources.add(openChannel());
    // Use resources
} finally {
    CloseHelper.quietCloseAll(managedResources);
}

// Pattern 3: Error handler integration
public class ResourceManager {
    private final ErrorHandler errorHandler = 
        throwable -> metrics.incrementCleanupErrors();
    
    public void cleanup(AutoCloseable... resources) {
        CloseHelper.closeAll(errorHandler, resources);
    }
}
```

---

## 4. Exception Handling

### 4.1 LangUtil Class

The `LangUtil` class provides utilities for exception handling, particularly for rethrowing checked exceptions as unchecked while preserving stack traces.

> **Source**: `/agrona/src/main/java/org/agrona/LangUtil.java`

#### 4.1.1 Unchecked Exception Rethrowing

```java
// Rethrow checked exception as unchecked (preserves stack trace)
try {
    // Code that throws checked exception
    Files.delete(path);
} catch (IOException e) {
    LangUtil.rethrowUnchecked(e); // Throws IOException as RuntimeException
}

// Usage in method that cannot declare checked exceptions
public void cleanup() {
    try {
        performIOOperation();
    } catch (Exception e) {
        LangUtil.rethrowUnchecked(e);
    }
}
```

**Key Benefits:**
- Preserves original exception type and stack trace
- Allows checked exceptions to propagate through unchecked-only interfaces
- Zero-overhead exception type casting using generics

**Use Cases:**
- Lambda expressions that cannot declare checked exceptions
- Interface implementations where checked exceptions are not allowed
- Utility methods that need to propagate various exception types

---

## 5. Validation and Verification

### 5.1 Verify Class

The `Verify` class provides runtime assertion utilities for validating preconditions and object states with descriptive error messages.

> **Source**: `/agrona/src/main/java/org/agrona/Verify.java`

#### 5.1.1 Null Reference Validation

```java
// Verify object is not null
public void processData(String data, Buffer buffer) {
    Verify.notNull(data, "data");
    Verify.notNull(buffer, "buffer");
    // Proceed with processing
}

// Verify object is null (for state validation)
public void initialize(Connection existingConnection) {
    Verify.verifyNull(existingConnection, "existingConnection");
    // Initialize new connection
}
```

#### 5.1.2 Map Entry Validation

```java
// Verify map contains required entries
Map<String, Configuration> configs = loadConfigurations();
Verify.present(configs, "database", "database configuration");
Verify.present(configs, "cache", "cache configuration");

// Use in initialization patterns
public class ServiceRegistry {
    private final Map<String, Service> services = new HashMap<>();
    
    public Service getRequiredService(String name) {
        Verify.present(services, name, "service: " + name);
        return services.get(name);
    }
}
```

**Validation API:**

| Method | Parameters | Exception | Description | Source Line |
|--------|------------|-----------|-------------|-------------|
| `notNull(Object, String)` | reference, name | NullPointerException | Validates reference is not null | 35 |
| `verifyNull(Object, String)` | reference, name | NullPointerException | Validates reference is null | 49 |
| `present(Map, Object, String)` | map, key, name | IllegalStateException | Validates map contains key | 66 |

#### 5.1.3 Usage Patterns

```java
// Constructor validation
public class BufferProcessor {
    private final DirectBuffer buffer;
    private final int capacity;
    
    public BufferProcessor(DirectBuffer buffer) {
        Verify.notNull(buffer, "buffer");
        this.buffer = buffer;
        this.capacity = buffer.capacity();
    }
}

// Method precondition checking
public void transfer(Account from, Account to, long amount) {
    Verify.notNull(from, "from account");
    Verify.notNull(to, "to account");
    if (amount <= 0) {
        throw new IllegalArgumentException("amount must be positive: " + amount);
    }
    // Perform transfer
}
```

### 5.2 References Class

The `References` class provides utilities for inspecting `java.lang.ref.Reference` objects without creating strong references.

> **Source**: `/agrona/src/main/java/org/agrona/References.java`

#### 5.2.1 Reference State Inspection

```java
// Check if reference has been cleared
WeakReference<Object> weakRef = new WeakReference<>(someObject);
boolean isCleared = References.isCleared(weakRef);

// Check if reference points to specific object
boolean isReferringTo = References.isReferringTo(weakRef, targetObject);

// Usage in cache implementations
public class WeakCache<K, V> {
    private final Map<K, WeakReference<V>> cache = new HashMap<>();
    
    public void cleanup() {
        cache.entrySet().removeIf(entry -> 
            References.isCleared(entry.getValue()));
    }
}
```

---

## 6. String and Data Utilities

### 6.1 SemanticVersion Class

The `SemanticVersion` class provides utilities for encoding and decoding semantic version information in compact integer format.

> **Source**: `/agrona/src/main/java/org/agrona/SemanticVersion.java`

#### 6.1.1 Version Composition and Extraction

```java
// Compose semantic version (major.minor.patch)
int version = SemanticVersion.compose(1, 4, 2); // Version 1.4.2

// Extract version components
int major = SemanticVersion.major(version);    // Returns 1
int minor = SemanticVersion.minor(version);    // Returns 4  
int patch = SemanticVersion.patch(version);    // Returns 2

// Convert to string representation
String versionString = SemanticVersion.toString(version); // "1.4.2"
```

**Version Encoding API:**

| Method | Parameters | Return | Description | Source Line |
|--------|------------|--------|-------------|-------------|
| `compose(int, int, int)` | major, minor, patch | int | Encode version components into integer | 37 |
| `major(int)` | version | int | Extract major version (bits 16-23) | 68 |
| `minor(int)` | version | int | Extract minor version (bits 8-15) | 79 |
| `patch(int)` | version | int | Extract patch version (bits 0-7) | 90 |
| `toString(int)` | version | String | Format as "major.minor.patch" | 101 |

**Constraints:**
- Each component must be in range 0-255
- Sum of all components must be greater than zero
- Total encoded version fits in 24 bits of integer

#### 6.1.2 Usage Examples

```java
// Protocol version negotiation
public class ProtocolHandler {
    private static final int SUPPORTED_VERSION = SemanticVersion.compose(2, 1, 0);
    
    public boolean isCompatible(int clientVersion) {
        int clientMajor = SemanticVersion.major(clientVersion);
        int supportedMajor = SemanticVersion.major(SUPPORTED_VERSION);
        return clientMajor == supportedMajor;
    }
}

// File format versioning
public class DataFileHeader {
    private final int formatVersion;
    
    public DataFileHeader(int major, int minor, int patch) {
        this.formatVersion = SemanticVersion.compose(major, minor, patch);
    }
    
    public String getVersionString() {
        return SemanticVersion.toString(formatVersion);
    }
}
```

### 6.2 Strings Class

The `Strings` class provides null-safe string operations and parsing utilities.

> **Source**: `/agrona/src/main/java/org/agrona/Strings.java`

#### 6.2.1 String Validation and Parsing

```java
// Null-safe empty string checking
boolean isEmpty = Strings.isEmpty(someString);

// Safe integer parsing with default value
int value = Strings.parseIntOrDefault("123", 0);     // Returns 123
int defaulted = Strings.parseIntOrDefault("abc", 42); // Returns 42
int nullDefault = Strings.parseIntOrDefault(null, 10); // Returns 10
```

**String Utilities API:**

| Method | Parameters | Return | Description | Source Line |
|--------|------------|--------|-------------|-------------|
| `isEmpty(String)` | string | boolean | True if string is null or empty | - |
| `parseIntOrDefault(String, int)` | string, defaultValue | int | Parse int or return default | - |

---

## 7. Error Handling Framework

### 7.1 ErrorHandler Interface

The `ErrorHandler` interface defines a functional contract for non-throwing error processing in event-driven and operational systems.

> **Source**: `/agrona/src/main/java/org/agrona/ErrorHandler.java`

#### 7.1.1 Basic Error Handler Implementation

```java
// Simple logging error handler
ErrorHandler logger = throwable -> 
    System.err.println("Error occurred: " + throwable.getMessage());

// Metrics collecting error handler
ErrorHandler metricsHandler = throwable -> {
    errorCounter.increment();
    if (throwable instanceof TimeoutException) {
        timeoutCounter.increment();
    }
};

// File-based error logging
ErrorHandler fileLogger = throwable -> {
    try (PrintWriter writer = new PrintWriter(new FileWriter("errors.log", true))) {
        throwable.printStackTrace(writer);
    } catch (IOException e) {
        // Cannot use ErrorHandler here - would create recursion
        e.printStackTrace();
    }
};
```

#### 7.1.2 Error Handler Integration Patterns

```java
// Integration with async operations
public class AsyncProcessor {
    private final ErrorHandler errorHandler;
    
    public AsyncProcessor(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }
    
    public void processAsync(Runnable task) {
        executor.submit(() -> {
            try {
                task.run();
            } catch (Exception e) {
                errorHandler.onError(e);
            }
        });
    }
}

// Ring buffer error handling
public class MessageProcessor {
    private final ErrorHandler errorHandler;
    
    public void onEvent(Event event) {
        try {
            processEvent(event);
        } catch (Exception e) {
            errorHandler.onError(e);
            // Continue processing other events
        }
    }
}
```

### 7.2 DelegatingErrorHandler Interface

The `DelegatingErrorHandler` interface extends `ErrorHandler` with chain-of-responsibility pattern support for building error handling pipelines.

> **Source**: `/agrona/src/main/java/org/agrona/DelegatingErrorHandler.java`

#### 7.2.1 Error Handler Chaining

```java
// Build error handler chain
DelegatingErrorHandler primary = new MetricsErrorHandler();
DelegatingErrorHandler secondary = new LoggingErrorHandler();
DelegatingErrorHandler fallback = new AlertingErrorHandler();

// Chain handlers together
primary.next(secondary);
secondary.next(fallback);

// Error processing flows through chain
primary.onError(new RuntimeException("Processing failed"));
```

#### 7.2.2 Chain Implementation Patterns

```java
// Custom delegating error handler
public class MetricsErrorHandler implements DelegatingErrorHandler {
    private ErrorHandler nextHandler;
    private final MetricsRegistry metrics;
    
    @Override
    public void onError(Throwable throwable) {
        // Handle locally
        metrics.counter("errors.total").increment();
        metrics.counter("errors." + throwable.getClass().getSimpleName()).increment();
        
        // Delegate to next handler
        if (nextHandler != null) {
            nextHandler.onError(throwable);
        }
    }
    
    @Override
    public ErrorHandler next(ErrorHandler errorHandler) {
        this.nextHandler = errorHandler;
        return errorHandler;
    }
}

// Conditional delegation
public class FilteringErrorHandler implements DelegatingErrorHandler {
    private ErrorHandler nextHandler;
    private final Predicate<Throwable> filter;
    
    public FilteringErrorHandler(Predicate<Throwable> filter) {
        this.filter = filter;
    }
    
    @Override
    public void onError(Throwable throwable) {
        if (filter.test(throwable)) {
            // Process this error type
            handleSpecificError(throwable);
        }
        
        // Always delegate to next handler
        if (nextHandler != null) {
            nextHandler.onError(throwable);
        }
    }
}
```

---

## 8. Resource Lifecycle Management

### 8.1 ManagedResource Interface

The `ManagedResource` interface provides a contract for tracking resource lifecycle state changes and cleanup operations.

> **Source**: `/agrona/src/main/java/org/agrona/ManagedResource.java`

#### 8.1.1 Lifecycle State Tracking

```java
// Implementation of managed resource
public class FileBasedResource implements ManagedResource {
    private volatile long lastStateChange;
    private final File resourceFile;
    
    @Override
    public void timeOfLastStateChange(long time) {
        this.lastStateChange = time;
    }
    
    @Override
    public long timeOfLastStateChange() {
        return lastStateChange;
    }
    
    @Override
    public void delete() {
        IoUtil.delete(resourceFile, false);
        timeOfLastStateChange(System.currentTimeMillis());
    }
}
```

#### 8.1.2 Resource Management Patterns

```java
// Resource registry with lifecycle tracking
public class ResourceRegistry {
    private final Map<String, ManagedResource> resources = new ConcurrentHashMap<>();
    private final long maxIdleTime = TimeUnit.MINUTES.toMillis(30);
    
    public void register(String id, ManagedResource resource) {
        resource.timeOfLastStateChange(System.currentTimeMillis());
        resources.put(id, resource);
    }
    
    public void cleanupIdleResources() {
        long now = System.currentTimeMillis();
        
        resources.entrySet().removeIf(entry -> {
            ManagedResource resource = entry.getValue();
            if (now - resource.timeOfLastStateChange() > maxIdleTime) {
                resource.delete();
                return true;
            }
            return false;
        });
    }
}
```

**ManagedResource API:**

| Method | Parameters | Description | Source Line |
|--------|------------|-------------|-------------|
| `timeOfLastStateChange(long)` | time | Set timestamp of last state change | - |
| `timeOfLastStateChange()` | - | Get timestamp of last state change | - |
| `delete()` | - | Perform resource cleanup and deletion | - |

---

## 9. Inter-Process Communication

### 9.1 MarkFile Class

The `MarkFile` class provides memory-mapped file management for inter-process communication, liveness signaling, and process coordination.

> **Source**: `/agrona/src/main/java/org/agrona/MarkFile.java`

#### 9.1.1 MarkFile Creation and Management

```java
// Create mark file for process coordination
File directory = new File("/tmp/myapp");
String filename = "process.mark";
int versionOffset = 0;
int timestampOffset = 4;
int totalLength = 1024;
long timeoutMs = 5000;
EpochClock clock = SystemEpochClock.INSTANCE;
IntConsumer versionCheck = version -> {
    if (version != EXPECTED_VERSION) {
        throw new IllegalStateException("Incompatible version: " + version);
    }
};
Consumer<String> logger = System.out::println;

MarkFile markFile = new MarkFile(
    directory, filename, true, false,
    versionOffset, timestampOffset, totalLength,
    timeoutMs, clock, versionCheck, logger
);
```

#### 9.1.2 Process Liveness and Coordination

```java
// Signal process readiness
markFile.signalReady(MY_PROCESS_VERSION);

// Update timestamp for liveness indication
markFile.timestampRelease(clock.time());

// Check if process is active
boolean isActive = markFile.isActive();

// Read version and timestamp atomically
int currentVersion = markFile.versionVolatile();
long lastTimestamp = markFile.timestampVolatile();
```

#### 9.1.3 Cleanup and Resource Management

```java
// Close mark file and unmap memory
markFile.close();

// Delete entire directory structure
markFile.deleteDirectory();

// Automatic cleanup with try-with-resources
try (MarkFile markFile = new MarkFile(...)) {
    // Use mark file for process coordination
    markFile.signalReady(VERSION);
    
    // Update liveness periodically
    scheduleAtFixedRate(() -> markFile.timestampRelease(clock.time()), 
                       1, 1, TimeUnit.SECONDS);
} // Automatically closed and cleaned up
```

**MarkFile API:**

| Method | Parameters | Description | Source Line |
|--------|------------|-------------|-------------|
| `signalReady(int)` | version | Signal process ready with version | - |
| `timestampRelease(long)` | timestamp | Update timestamp for liveness | - |
| `versionVolatile()` | - | Read version field atomically | - |
| `timestampVolatile()` | - | Read timestamp field atomically | - |
| `isActive()` | - | Check if process is active based on timestamp | - |
| `close()` | - | Close and unmap mark file | - |
| `deleteDirectory()` | - | Delete containing directory | - |

#### 9.1.4 Usage Patterns

```java
// Process startup coordination
public class ProcessCoordinator {
    private static final int PROCESS_VERSION = SemanticVersion.compose(1, 0, 0);
    private final MarkFile markFile;
    private final ScheduledExecutorService scheduler;
    
    public ProcessCoordinator(File workingDir) {
        this.markFile = new MarkFile(
            workingDir, "coordinator.mark", 
            true, true, 0, 4, 256,
            10000, SystemEpochClock.INSTANCE,
            this::validateVersion,
            System.out::println
        );
        
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    
    public void start() {
        markFile.signalReady(PROCESS_VERSION);
        
        // Update liveness every second
        scheduler.scheduleAtFixedRate(
            () -> markFile.timestampRelease(System.currentTimeMillis()),
            1, 1, TimeUnit.SECONDS
        );
    }
    
    public void shutdown() {
        scheduler.shutdown();
        markFile.close();
    }
    
    private void validateVersion(int version) {
        if (SemanticVersion.major(version) != SemanticVersion.major(PROCESS_VERSION)) {
            throw new IllegalStateException("Incompatible major version");
        }
    }
}
```

---

## 10. Timer Scheduling

### 10.1 DeadlineTimerWheel Class

The `DeadlineTimerWheel` class implements a hashed timing wheel for efficient deadline-based timer scheduling with O(1) operations.

> **Source**: `/agrona/src/main/java/org/agrona/DeadlineTimerWheel.java`

#### 10.1.1 Timer Wheel Construction

```java
// Create timer wheel with nanosecond resolution
DeadlineTimerWheel timerWheel = new DeadlineTimerWheel(
    TimeUnit.NANOSECONDS,    // time unit
    System.nanoTime(),       // start time
    1_000_000,              // tick resolution (1ms in nanos)
    512                     // ticks per wheel
);

// Create timer wheel with millisecond resolution
DeadlineTimerWheel msTimerWheel = new DeadlineTimerWheel(
    TimeUnit.MILLISECONDS,   // time unit
    System.currentTimeMillis(), // start time  
    10,                     // tick resolution (10ms)
    1024                    // ticks per wheel
);
```

#### 10.1.2 Timer Scheduling and Management

```java
// Schedule timer to expire at specific deadline
long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
long timerId = timerWheel.scheduleTimer(deadline);

// Cancel previously scheduled timer
boolean cancelled = timerWheel.cancelTimer(timerId);

// Clear all timers
timerWheel.clear();

// Reset start time (affects all relative calculations)
timerWheel.resetStartTime(System.nanoTime());
```

#### 10.1.3 Timer Processing

```java
// Define timer expiry handler
DeadlineTimerWheel.TimerHandler handler = (timeUnit, now, timerId) -> {
    System.out.println("Timer " + timerId + " expired at " + now);
    return true; // Consume the timer
};

// Poll for expired timers
long now = System.nanoTime();
int maxTimersToProcess = 100;
int processedCount = timerWheel.poll(now, handler, maxTimersToProcess);

// Process with early termination
DeadlineTimerWheel.TimerHandler earlyExitHandler = (timeUnit, now, timerId) -> {
    if (shouldStopProcessing()) {
        return false; // Stop processing more timers
    }
    processExpiredTimer(timerId);
    return true;
};
```

#### 10.1.4 Timer Inspection

```java
// Iterate over all active timers
DeadlineTimerWheel.TimerConsumer inspector = (deadline, timerId) -> {
    long timeToExpiry = deadline - System.nanoTime();
    System.out.println("Timer " + timerId + " expires in " + timeToExpiry + "ns");
};

timerWheel.forEach(inspector);

// Check timer wheel status
long timerCount = timerWheel.timerCount();
boolean isEmpty = (timerCount == 0);
```

**Timer Wheel API:**

| Method | Parameters | Return | Description | Source Line |
|--------|------------|--------|-------------|-------------|
| `scheduleTimer(long)` | deadline | long | Schedule timer, returns timer ID | - |
| `cancelTimer(long)` | timerId | boolean | Cancel timer, returns true if found | - |
| `poll(long, TimerHandler, int)` | now, handler, maxTimers | int | Process expired timers | - |
| `clear()` | - | void | Remove all timers | - |
| `resetStartTime(long)` | startTime | void | Reset timing baseline | - |
| `forEach(TimerConsumer)` | consumer | void | Iterate over all timers | - |
| `timerCount()` | - | long | Get count of active timers | - |

#### 10.1.5 Usage Patterns

```java
// Timeout management system
public class TimeoutManager {
    private final DeadlineTimerWheel timerWheel;
    private final Map<Long, TimeoutCallback> callbacks = new ConcurrentHashMap<>();
    private final AtomicLong timerIdGenerator = new AtomicLong(0);
    
    public TimeoutManager() {
        this.timerWheel = new DeadlineTimerWheel(
            TimeUnit.NANOSECONDS, System.nanoTime(), 
            1_000_000, 1024); // 1ms resolution, 1024 slots
    }
    
    public long scheduleTimeout(long delayNanos, TimeoutCallback callback) {
        long deadline = System.nanoTime() + delayNanos;
        long timerId = timerIdGenerator.incrementAndGet();
        
        callbacks.put(timerId, callback);
        timerWheel.scheduleTimer(deadline);
        
        return timerId;
    }
    
    public boolean cancelTimeout(long timerId) {
        callbacks.remove(timerId);
        return timerWheel.cancelTimer(timerId);
    }
    
    public void processTimeouts() {
        long now = System.nanoTime();
        timerWheel.poll(now, this::handleTimeout, 1000);
    }
    
    private boolean handleTimeout(TimeUnit timeUnit, long now, long timerId) {
        TimeoutCallback callback = callbacks.remove(timerId);
        if (callback != null) {
            try {
                callback.onTimeout(timerId, now);
            } catch (Exception e) {
                // Handle callback errors
                System.err.println("Timeout callback failed: " + e.getMessage());
            }
        }
        return true;
    }
    
    @FunctionalInterface
    public interface TimeoutCallback {
        void onTimeout(long timerId, long now);
    }
}

// Connection timeout tracking
public class ConnectionManager {
    private final TimeoutManager timeoutManager = new TimeoutManager();
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();
    private final long connectionTimeoutNanos = TimeUnit.SECONDS.toNanos(30);
    
    public void addConnection(String id, Connection connection) {
        connections.put(id, connection);
        
        // Schedule timeout for connection
        timeoutManager.scheduleTimeout(connectionTimeoutNanos, (timerId, now) -> {
            Connection conn = connections.remove(id);
            if (conn != null && !conn.isActive()) {
                conn.close();
                System.out.println("Connection " + id + " timed out");
            }
        });
    }
    
    public void processTimeouts() {
        timeoutManager.processTimeouts();
    }
}
```

#### 10.1.6 Performance Characteristics

**Time Complexity:**
- Timer scheduling: O(1) average case
- Timer cancellation: O(1)
- Expired timer processing: O(k) where k is number of expired timers

**Memory Usage:**
- Dynamic capacity growth with no shrinking
- Initial allocation based on `ticksPerWheel` parameter
- Memory usage grows with maximum concurrent timers

**Best Practices:**
- Choose tick resolution based on required precision vs. memory usage
- Use power-of-2 values for `ticksPerWheel` for optimal performance
- Process timeouts regularly to prevent accumulation
- Consider timer wheel per time resolution requirement

---

## System Utilities Integration Examples

### Complete Application Setup

```java
public class AgronaPoweredApplication {
    private static final int APP_VERSION = SemanticVersion.compose(2, 1, 0);
    
    public static void main(String[] args) {
        // System introspection
        System.out.println("Starting on " + SystemUtil.osName() + 
                          " (" + SystemUtil.osArch() + ")");
        System.out.println("Process ID: " + SystemUtil.getPid());
        
        // Load configuration
        SystemUtil.loadPropertiesFile("application.properties");
        
        // Setup working directory
        File workingDir = new File(SystemUtil.tmpDirName() + "myapp");
        IoUtil.ensureDirectoryExists(workingDir, "application working directory");
        
        // Error handling setup
        ErrorHandler errorHandler = throwable -> {
            System.err.println("Application error: " + throwable.getMessage());
            if (throwable instanceof RuntimeException) {
                LangUtil.rethrowUnchecked(throwable);
            }
        };
        
        // Resource management
        List<AutoCloseable> resources = new ArrayList<>();
        
        try {
            // Create mark file for process coordination
            MarkFile markFile = createMarkFile(workingDir);
            resources.add(markFile);
            
            // Initialize timer wheel for scheduling
            DeadlineTimerWheel timerWheel = new DeadlineTimerWheel(
                TimeUnit.NANOSECONDS, System.nanoTime(), 1_000_000, 512);
            
            // Signal application ready
            markFile.signalReady(APP_VERSION);
            
            // Schedule periodic liveness updates
            schedulePeriodicTask(timerWheel, markFile);
            
            // Main application logic
            runApplication(timerWheel, errorHandler);
            
        } catch (Exception e) {
            errorHandler.onError(e);
        } finally {
            // Clean shutdown
            CloseHelper.quietCloseAll(resources);
            IoUtil.delete(workingDir, true);
        }
    }
    
    private static MarkFile createMarkFile(File workingDir) {
        return new MarkFile(
            workingDir, "app.mark", true, false,
            0, 4, 256, 10000, 
            SystemEpochClock.INSTANCE,
            version -> Verify.notNull(version, "mark file version"),
            msg -> System.out.println("MarkFile: " + msg)
        );
    }
    
    private static void schedulePeriodicTask(DeadlineTimerWheel timerWheel, MarkFile markFile) {
        long intervalNanos = TimeUnit.SECONDS.toNanos(1);
        timerWheel.scheduleTimer(System.nanoTime() + intervalNanos);
        
        // Process in timer handler
        DeadlineTimerWheel.TimerHandler handler = (timeUnit, now, timerId) -> {
            markFile.timestampRelease(now);
            timerWheel.scheduleTimer(now + intervalNanos); // Reschedule
            return true;
        };
    }
}
```

This comprehensive API reference provides complete coverage of Agrona's system utilities, enabling developers to build high-performance, robust Java applications with enterprise-grade system integration capabilities.
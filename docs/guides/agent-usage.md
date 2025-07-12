# Agent Usage Guide

Complete guide for using the Agrona buffer alignment agent, covering ByteBuddy-based instrumentation, JVM agent activation, runtime configuration, performance impact, troubleshooting, and integration with build tools.

## Table of Contents

1. [Overview](#overview)
2. [Agent Installation](#agent-installation)
3. [JVM Agent Activation](#jvm-agent-activation)
4. [Runtime Configuration](#runtime-configuration)
5. [Integration with Build Tools](#integration-with-build-tools)
6. [Performance Impact and Considerations](#performance-impact-and-considerations)
7. [Troubleshooting](#troubleshooting)
8. [Advanced Usage](#advanced-usage)
9. [Best Practices](#best-practices)

## Overview

The Agrona Buffer Alignment Agent is a ByteBuddy-based Java agent that provides runtime verification of memory access alignment for DirectBuffer implementations. This agent helps detect unaligned memory accesses that can cause performance degradation or JVM crashes on certain architectures.

> Source: `agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentAgent.java:46-53`

### Key Features

- **ByteBuddy-based Instrumentation**: Uses ByteBuddy library for efficient bytecode manipulation
- **Runtime Alignment Verification**: Detects unaligned memory accesses for all primitive types
- **Flexible Attachment**: Supports both static (premain) and dynamic (agentmain) attachment modes
- **Minimal Performance Overhead**: Optimized instrumentation with configurable verification levels
- **Development Safety**: Prevents architecture-specific crashes during development and testing

### Supported Primitive Types

The agent verifies alignment for the following primitive types:

| Type | Size (bytes) | Alignment Requirement |
|------|-------------|-----------------------|
| `long` | 8 | 8-byte aligned |
| `double` | 8 | 8-byte aligned |
| `int` | 4 | 4-byte aligned |
| `float` | 4 | 4-byte aligned |
| `short` | 2 | 2-byte aligned |
| `char` | 2 | 2-byte aligned |
| `byte` | 1 | No alignment required |

> Source: `agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentInterceptor.java:34-181`

## Agent Installation

### Maven Dependency

Add the agent dependency to your project:

```xml
<dependency>
    <groupId>org.agrona</groupId>
    <artifactId>agrona-agent</artifactId>
    <version>${agrona.version}</version>
    <scope>test</scope>
</dependency>
```

### Gradle Dependency

```gradle
dependencies {
    testImplementation "org.agrona:agrona-agent:${agronaVersion}"
}
```

### Direct JAR Download

Download the agent JAR from Maven Central:
- Latest release: [Maven Central - agrona-agent](https://central.sonatype.com/search?q=g:org.agrona%20a:agrona-agent)
- Artifact naming: `agrona-agent-{version}.jar`

> Source: `build.gradle:377-409` (agent module configuration)

## JVM Agent Activation

### Static Agent Attachment (premain)

Attach the agent when starting the JVM using the `-javaagent` flag:

```bash
java -javaagent:path/to/agrona-agent-{version}.jar \
     -cp your-application.jar \
     com.example.YourMainClass
```

#### Command Line Options

The agent accepts no command-line arguments; all configuration is handled through system properties:

```bash
java -javaagent:agrona-agent.jar \
     -Dagrona.disable.bounds.checks=false \
     -Dagrona.strict.alignment.checks=true \
     -cp your-application.jar \
     com.example.YourMainClass
```

### Dynamic Agent Attachment (agentmain)

Attach the agent to a running JVM programmatically:

```java
import com.sun.tools.attach.VirtualMachine;

public class AgentAttacher {
    public static void attachAgent(String pid, String agentPath) throws Exception {
        VirtualMachine vm = VirtualMachine.attach(pid);
        try {
            vm.loadAgent(agentPath);
            System.out.println("Agent attached successfully");
        } finally {
            vm.detach();
        }
    }
}
```

> Source: `agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentAgent.java:64-83`

### Java 21+ Considerations

For Java 21 and later, enable dynamic agent loading:

```bash
java -XX:+EnableDynamicAgentLoading \
     -javaagent:agrona-agent.jar \
     -cp your-application.jar \
     com.example.YourMainClass
```

> Source: `build.gradle:209-211`

## Runtime Configuration

### System Properties

Configure agent behavior using JVM system properties:

#### Core Configuration Properties

```bash
# Disable bounds checking in DirectBuffer implementations
-Dagrona.disable.bounds.checks=false

# Enable strict alignment verification (recommended for testing)
-Dagrona.strict.alignment.checks=true

# Enable ByteBuddy experimental features
-Dnet.bytebuddy.experimental=true
```

> Source: `build.gradle:224-227`

#### Runtime Enable/Disable

The agent can be dynamically removed at runtime:

```java
import org.agrona.agent.BufferAlignmentAgent;

// Remove alignment verification at runtime
BufferAlignmentAgent.removeTransformer();
```

> Source: `agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentAgent.java:122-130`

### Configuration Examples

#### Development Environment

```bash
# Maximum safety for development
java -javaagent:agrona-agent.jar \
     -Dagrona.disable.bounds.checks=false \
     -Dagrona.strict.alignment.checks=true \
     -Dnet.bytebuddy.experimental=true \
     -cp your-application.jar \
     com.example.YourApplication
```

#### Testing Environment

```bash
# Balanced configuration for testing
java -javaagent:agrona-agent.jar \
     -Dagrona.disable.bounds.checks=false \
     -cp test-application.jar \
     org.junit.platform.console.ConsoleLauncher
```

## Integration with Build Tools

### Gradle Integration

#### Test Configuration with Agent

```gradle
test {
    // Enable agent for all tests
    jvmArgs('-javaagent:' + configurations.testRuntimeClasspath.find { it.name.contains('agrona-agent') })
    
    // Required for Java 21+
    if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_21)) {
        jvmArgs('-XX:+EnableDynamicAgentLoading')
    }
    
    // Agent configuration
    systemProperties(
        'agrona.disable.bounds.checks': 'false',
        'agrona.strict.alignment.checks': 'true',
        'net.bytebuddy.experimental': 'true'
    )
    
    // Required JVM module access
    jvmArgs('--add-opens', 'java.base/jdk.internal.misc=ALL-UNNAMED')
    jvmArgs('--add-opens', 'java.base/java.util.zip=ALL-UNNAMED')
}
```

#### Custom Test Task

```gradle
task testWithAgent(type: Test) {
    description = 'Run tests with buffer alignment agent'
    group = 'verification'
    
    def agentJar = configurations.testRuntimeClasspath
        .find { it.name.contains('agrona-agent') }
    
    jvmArgs("-javaagent:${agentJar}")
    systemProperty 'agrona.strict.alignment.checks', 'true'
    
    testClassesDirs = sourceSets.test.output.classesDirs
    classpath = sourceSets.test.runtimeClasspath
}
```

> Source: `build.gradle:205-230` (test configuration)

### Maven Integration

#### Surefire Plugin Configuration

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <version>3.0.0-M9</version>
    <configuration>
        <argLine>
            -javaagent:${org.agrona:agrona-agent:jar}
            -Dagrona.disable.bounds.checks=false
            -Dagrona.strict.alignment.checks=true
            --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
        </argLine>
        <systemPropertyVariables>
            <net.bytebuddy.experimental>true</net.bytebuddy.experimental>
        </systemPropertyVariables>
    </configuration>
</plugin>
```

#### Failsafe Plugin for Integration Tests

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <version>3.0.0-M9</version>
    <configuration>
        <argLine>
            -javaagent:${org.agrona:agrona-agent:jar}
            -XX:+EnableDynamicAgentLoading
        </argLine>
    </configuration>
</plugin>
```

### IDE Integration

#### IntelliJ IDEA

1. Open **Run/Debug Configurations**
2. Add VM options:
   ```
   -javaagent:path/to/agrona-agent.jar
   -Dagrona.strict.alignment.checks=true
   --add-opens java.base/jdk.internal.misc=ALL-UNNAMED
   ```

#### Eclipse

1. Open **Run Configurations**
2. Go to **Arguments** tab
3. Add to **VM arguments**:
   ```
   -javaagent:path/to/agrona-agent.jar
   -Dagrona.disable.bounds.checks=false
   ```

## Performance Impact and Considerations

### Overhead Analysis

The agent introduces minimal overhead through ByteBuddy instrumentation:

| Operation Type | Overhead | Impact |
|----------------|----------|---------|
| **Aligned Access** | ~1-2 ns | Negligible for most applications |
| **Unaligned Detection** | Exception overhead | Only when violations occur |
| **Class Loading** | One-time cost | During application startup |

### Benchmarking Agent Impact

Use JMH to measure agent performance impact:

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(value = 2, jvmArgsAppend = {"-javaagent:agrona-agent.jar"})
public class AgentPerformanceBenchmark {
    
    private DirectBuffer buffer;
    
    @Setup
    public void setup() {
        byte[] bytes = new byte[1024];
        buffer = new UnsafeBuffer(bytes);
    }
    
    @Benchmark
    public long testAlignedLongAccess() {
        return buffer.getLong(0);  // Aligned access
    }
    
    @Benchmark
    public int testAlignedIntAccess() {
        return buffer.getInt(4);   // Aligned access
    }
}
```

### Production Considerations

**Disable Agent in Production**:
```bash
# Production configuration - NO agent
java -Dagrona.disable.bounds.checks=true \
     -cp production-application.jar \
     com.example.ProductionApp
```

**Conditional Agent Loading**:
```java
public class AgentManager {
    private static final boolean DEVELOPMENT_MODE = 
        "development".equals(System.getProperty("app.environment"));
    
    public static void conditionallyLoadAgent() {
        if (DEVELOPMENT_MODE) {
            // Load agent only in development
            System.setProperty("agrona.strict.alignment.checks", "true");
        }
    }
}
```

## Troubleshooting

### Common Issues and Solutions

#### 1. Agent Not Loading

**Symptom**: No alignment verification occurring
**Causes**:
- Incorrect agent path
- Missing agent JAR file
- Insufficient JVM permissions

**Solutions**:
```bash
# Verify agent file exists
ls -la path/to/agrona-agent.jar

# Check JVM startup logs
java -verbose:class -javaagent:agrona-agent.jar ...

# Verify agent manifest
jar tf agrona-agent.jar | grep MANIFEST
unzip -p agrona-agent.jar META-INF/MANIFEST.MF
```

#### 2. BufferAlignmentException

**Symptom**: Runtime exceptions with alignment violations
```
Exception in thread "main" org.agrona.agent.BufferAlignmentException: 
Unaligned int access (index=1, addressOffset=140712234567680)
```

**Root Causes**:
- Incorrect buffer index calculations
- Misaligned data structure layouts
- Byte order conversion issues

**Debugging Steps**:
```java
// Check buffer offset alignment
long addressOffset = buffer.addressOffset();
int index = 1; // Problematic index
int alignmentMask = Integer.BYTES - 1; // 3 for int

if (((addressOffset + index) & alignmentMask) != 0) {
    System.err.printf("Unaligned access: address=0x%x, index=%d, " +
                     "effective_address=0x%x, alignment_mask=0x%x%n",
                     addressOffset, index, addressOffset + index, alignmentMask);
}
```

> Source: `agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentException.java:42-47`

#### 3. ByteBuddy Version Conflicts

**Symptom**: ClassNotFoundException or MethodNotFoundError
**Cause**: Conflicting ByteBuddy versions in classpath

**Solution**:
```gradle
configurations.all {
    resolutionStrategy {
        force 'net.bytebuddy:byte-buddy:1.17.6'
        force 'net.bytebuddy:byte-buddy-agent:1.17.6'
    }
}
```

#### 4. Java Module System Issues

**Symptom**: IllegalAccessError with modular applications
**Solution**:
```bash
# Add required module opens
--add-opens java.base/jdk.internal.misc=ALL-UNNAMED
--add-opens java.base/java.util.zip=ALL-UNNAMED
--add-opens java.instrument/sun.instrument=ALL-UNNAMED
```

#### 5. Memory Access Violations

**Symptom**: JVM crashes or unexpected behavior
**Debugging**:
```bash
# Enable JVM debugging
-XX:+PrintGCDetails \
-XX:+PrintCompilation \
-XX:+UnlockDiagnosticVMOptions \
-XX:+LogVMOutput \
-XX:+TraceClassLoading
```

### Agent Diagnostic Information

#### Enable Agent Debugging

```java
// Custom agent listener for debugging
public class DebugAgentListener implements AgentBuilder.Listener {
    @Override
    public void onTransformation(TypeDescription typeDescription, 
                               ClassLoader classLoader, 
                               JavaModule javaModule, 
                               boolean loaded, 
                               DynamicType dynamicType) {
        System.out.printf("Transformed: %s (loaded=%b)%n", 
                         typeDescription.getName(), loaded);
    }
    
    @Override
    public void onError(String typeName, 
                       ClassLoader classLoader, 
                       JavaModule javaModule, 
                       boolean loaded, 
                       Throwable throwable) {
        System.err.printf("Transformation error: %s - %s%n", 
                         typeName, throwable.getMessage());
        throwable.printStackTrace();
    }
}
```

> Source: `agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentAgent.java:132-177`

#### Verify Agent Installation

```java
import java.lang.instrument.Instrumentation;
import java.lang.management.ManagementFactory;

public class AgentVerification {
    public static void verifyAgentInstalled() {
        String jvmArgs = ManagementFactory.getRuntimeMXBean()
            .getInputArguments().toString();
        
        if (jvmArgs.contains("agrona-agent")) {
            System.out.println("Agrona agent detected in JVM arguments");
        } else {
            System.out.println("Agrona agent NOT detected");
        }
    }
}
```

## Advanced Usage

### Custom Alignment Strategies

#### Programmatic Agent Control

```java
import org.agrona.agent.BufferAlignmentAgent;

public class AlignmentManager {
    private static boolean agentActive = true;
    
    public static void disableAlignment() {
        if (agentActive) {
            BufferAlignmentAgent.removeTransformer();
            agentActive = false;
            System.out.println("Buffer alignment verification disabled");
        }
    }
    
    public static boolean isAgentActive() {
        return agentActive;
    }
}
```

#### Conditional Verification

```java
public class ConditionalAlignment {
    private static final boolean DEBUG_MODE = 
        Boolean.getBoolean("app.debug.alignment");
    
    public static void performBufferOperation(DirectBuffer buffer, int index) {
        if (DEBUG_MODE) {
            // Additional alignment checks
            validateAlignment(buffer, index, Long.BYTES);
        }
        
        // Perform operation
        long value = buffer.getLong(index);
    }
    
    private static void validateAlignment(DirectBuffer buffer, 
                                        int index, 
                                        int typeSize) {
        long address = buffer.addressOffset() + index;
        if ((address & (typeSize - 1)) != 0) {
            throw new IllegalArgumentException(
                String.format("Unaligned access: address=0x%x, type_size=%d", 
                             address, typeSize));
        }
    }
}
```

### Integration with Testing Frameworks

#### JUnit 5 Extension

```java
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class BufferAlignmentExtension implements BeforeAllCallback {
    @Override
    public void beforeAll(ExtensionContext context) throws Exception {
        // Verify agent is active
        String jvmArgs = ManagementFactory.getRuntimeMXBean()
            .getInputArguments().toString();
        
        if (!jvmArgs.contains("agrona-agent")) {
            throw new IllegalStateException(
                "Buffer alignment agent not detected. " +
                "Add -javaagent:agrona-agent.jar to test JVM arguments");
        }
        
        // Enable strict checking for tests
        System.setProperty("agrona.strict.alignment.checks", "true");
    }
}
```

#### Test Usage

```java
@ExtendWith(BufferAlignmentExtension.class)
class BufferAlignmentTest {
    
    @Test
    void testAlignedAccess() {
        byte[] bytes = new byte[16];
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        
        // These should pass with agent active
        assertDoesNotThrow(() -> buffer.getLong(0));
        assertDoesNotThrow(() -> buffer.getInt(8));
        assertDoesNotThrow(() -> buffer.getShort(12));
    }
    
    @Test
    void testUnalignedAccess() {
        byte[] bytes = new byte[16];
        DirectBuffer buffer = new UnsafeBuffer(bytes);
        
        // These should throw BufferAlignmentException
        assertThrows(BufferAlignmentException.class, 
                    () -> buffer.getLong(1));  // Unaligned by 1 byte
        assertThrows(BufferAlignmentException.class, 
                    () -> buffer.getInt(1));   // Unaligned by 1 byte
    }
}
```

## Best Practices

### Development Workflow

#### 1. **Always Use Agent in Development**

```bash
# Development startup script
#!/bin/bash
AGENT_JAR="$HOME/.gradle/caches/modules-2/files-2.1/org.agrona/agrona-agent/*/agrona-agent-*.jar"
java -javaagent:$AGENT_JAR \
     -Dagrona.strict.alignment.checks=true \
     -cp build/libs/app.jar \
     com.example.Application
```

#### 2. **Disable Agent in Production**

```java
public class ProductionConfig {
    static {
        // Ensure alignment checks are disabled in production
        System.setProperty("agrona.disable.bounds.checks", "true");
        System.setProperty("agrona.strict.alignment.checks", "false");
    }
}
```

#### 3. **Use Environment-Based Configuration**

```bash
# Environment-specific configuration
if [ "$ENV" = "development" ]; then
    AGENT_ARGS="-javaagent:agrona-agent.jar -Dagrona.strict.alignment.checks=true"
elif [ "$ENV" = "testing" ]; then
    AGENT_ARGS="-javaagent:agrona-agent.jar"
else
    AGENT_ARGS=""  # No agent in production
fi

java $AGENT_ARGS -cp app.jar com.example.App
```

### Code Design Guidelines

#### 1. **Design for Alignment**

```java
public class AlignmentAwareStructure {
    // Good: Start with largest types for natural alignment
    private static final int LONG_OFFSET = 0;      // 8-byte aligned
    private static final int INT_OFFSET = 8;       // 4-byte aligned  
    private static final int SHORT_OFFSET = 12;    // 2-byte aligned
    private static final int BYTE_OFFSET = 14;     // Any alignment
    
    public void writeStructure(MutableDirectBuffer buffer, int baseOffset) {
        buffer.putLong(baseOffset + LONG_OFFSET, longValue);
        buffer.putInt(baseOffset + INT_OFFSET, intValue);
        buffer.putShort(baseOffset + SHORT_OFFSET, shortValue);
        buffer.putByte(baseOffset + BYTE_OFFSET, byteValue);
    }
}
```

#### 2. **Validate Buffer Indices**

```java
public class SafeBufferAccess {
    public static void safeLongRead(DirectBuffer buffer, int index) {
        // Validate alignment before access
        if ((index & (Long.BYTES - 1)) != 0) {
            throw new IllegalArgumentException(
                "Index " + index + " is not aligned for long access");
        }
        
        long value = buffer.getLong(index);
    }
}
```

#### 3. **Use Alignment Utilities**

```java
import static org.agrona.BitUtil.*;

public class AlignmentUtils {
    public static int alignToLong(int value) {
        return align(value, SIZE_OF_LONG);
    }
    
    public static int alignToInt(int value) {
        return align(value, SIZE_OF_INT);
    }
    
    public static boolean isAligned(long address, int alignment) {
        return (address & (alignment - 1)) == 0;
    }
}
```

### Performance Optimization

#### 1. **Minimize Agent Overhead**

- Use agent only during development and testing
- Remove agent for production deployments
- Configure conditional verification based on environment

#### 2. **Optimize Buffer Usage Patterns**

```java
public class OptimalBufferUsage {
    // Pre-calculate aligned offsets
    private static final int[] ALIGNED_OFFSETS = {
        0, 8, 16, 24, 32  // 8-byte aligned offsets
    };
    
    public void processAlignedData(DirectBuffer buffer) {
        for (int offset : ALIGNED_OFFSETS) {
            long value = buffer.getLong(offset);  // Always aligned
            processValue(value);
        }
    }
}
```

#### 3. **Monitor Agent Performance**

```java
public class AgentPerformanceMonitor {
    private static final long AGENT_OVERHEAD_THRESHOLD_NS = 10;
    
    public static void measureAgentOverhead() {
        DirectBuffer buffer = new UnsafeBuffer(new byte[64]);
        
        // Warm up
        for (int i = 0; i < 10000; i++) {
            buffer.getLong(0);
        }
        
        // Measure
        long startTime = System.nanoTime();
        for (int i = 0; i < 1000000; i++) {
            buffer.getLong(0);
        }
        long endTime = System.nanoTime();
        
        long avgOverhead = (endTime - startTime) / 1000000;
        if (avgOverhead > AGENT_OVERHEAD_THRESHOLD_NS) {
            System.err.printf("Agent overhead: %d ns per operation%n", avgOverhead);
        }
    }
}
```

---

## References

- **Main Agent Class**: `/agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentAgent.java`
- **Interceptor Implementation**: `/agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentInterceptor.java`
- **Exception Handling**: `/agrona-agent/src/main/java/org/agrona/agent/BufferAlignmentException.java`
- **Build Configuration**: `/build.gradle` (lines 377-461)
- **DirectBuffer Interface**: `/agrona/src/main/java/org/agrona/DirectBuffer.java`
- **Project Overview**: `/README.md`

---

*This documentation covers ByteBuddy-based buffer alignment enforcement agent configuration and deployment as specified in Section 0.4.1 of the Agrona technical specification.*
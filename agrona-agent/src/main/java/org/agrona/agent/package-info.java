/*
 * Copyright 2014-2025 Real Logic Limited.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * <h1>Agrona Agent - Runtime Buffer Alignment Verification</h1>
 * 
 * <p>This package provides a ByteBuddy-based Java agent for runtime instrumentation of 
 * {@link org.agrona.DirectBuffer} implementations to verify aligned memory access patterns.
 * The agent enforces alignment requirements critical for optimal performance on non-x86 
 * architectures and ensures compliance with memory ordering semantics.</p>
 * 
 * <h2>Agent Purpose and Functionality</h2>
 * 
 * <p>The Agrona Agent addresses the critical need for runtime validation of memory alignment
 * constraints in high-performance buffer operations. While x86 architectures are generally
 * tolerant of misaligned access, other architectures (ARM, SPARC, etc.) require strict
 * alignment for atomic operations and optimal cache utilization.</p>
 * 
 * <ul>
 * <li><strong>Alignment Verification</strong>: Validates that all buffer access operations
 *     respect the alignment requirements for the target data type (2-byte for short, 
 *     4-byte for int, 8-byte for long)</li>
 * <li><strong>Performance Impact Detection</strong>: Identifies misaligned access patterns
 *     that could cause performance degradation or runtime exceptions on strict architectures</li>
 * <li><strong>Development-Time Validation</strong>: Enables early detection of alignment
 *     issues during development and testing phases</li>
 * <li><strong>Zero-Copy Operation Safety</strong>: Ensures that zero-copy buffer operations
 *     maintain their performance characteristics across all supported platforms</li>
 * </ul>
 * 
 * <h2>Integration with Agrona Buffer System</h2>
 * 
 * <p>The agent integrates seamlessly with Agrona's buffer management component through
 * bytecode instrumentation of buffer access methods. This approach provides:</p>
 * 
 * <ul>
 * <li><strong>Non-Intrusive Monitoring</strong>: No source code modifications required
 *     in application or library code</li>
 * <li><strong>Runtime Toggle</strong>: Can be enabled or disabled through JVM agent
 *     configuration without recompilation</li>
 * <li><strong>Minimal Overhead</strong>: Instrumentation overhead is negligible in
 *     production environments when agent is not active</li>
 * <li><strong>Comprehensive Coverage</strong>: Monitors all buffer implementations
 *     including UnsafeBuffer, ExpandableArrayBuffer, and memory-mapped variants</li>
 * </ul>
 * 
 * <h2>Usage and Configuration</h2>
 * 
 * <p>The agent is activated through standard JVM agent mechanisms:</p>
 * 
 * <pre>{@code
 * // Enable agent with default configuration
 * -javaagent:agrona-agent.jar
 * 
 * // Enable with custom configuration
 * -javaagent:agrona-agent.jar=alignment.strict=true
 * }</pre>
 * 
 * <h2>Architecture Integration</h2>
 * 
 * <p>This agent module is part of Agrona's modular architecture designed for
 * microsecond-latency operations. It complements the core library by providing
 * runtime validation capabilities without impacting production performance when
 * disabled.</p>
 * 
 * <h3>Related Components</h3>
 * <ul>
 * <li><strong>Core Buffer System</strong>: {@code org.agrona} - Provides the buffer
 *     interfaces and implementations being instrumented</li>
 * <li><strong>Concurrent Utilities</strong>: {@code org.agrona.concurrent} - Ring buffers
 *     and queues that rely on proper alignment for atomic operations</li>
 * <li><strong>Benchmark Suite</strong>: {@code agrona-benchmarks} - Performance validation
 *     with and without agent instrumentation</li>
 * </ul>
 * 
 * <h2>Documentation References</h2>
 * 
 * <p>For comprehensive usage examples and integration patterns, refer to:</p>
 * <ul>
 * <li><strong>User Guide</strong>: {@code /docs/guides/agent-usage.md}</li>
 * <li><strong>API Reference</strong>: {@code /docs/api/buffer-management.md}</li>
 * <li><strong>Architecture Overview</strong>: {@code /docs/architecture/system-design.md}</li>
 * </ul>
 * 
 * @see org.agrona.DirectBuffer
 * @see org.agrona.MutableDirectBuffer
 * @see org.agrona.concurrent.AtomicBuffer
 * @since 1.0.0
 */
package org.agrona.agent;

/**
 * Package documentation metadata for the Agrona Agent module.
 * This class provides structured documentation information that can be
 * referenced by documentation generation tools and IDE assistance.
 */
final class PackageDocumentation
{
    /**
     * Comprehensive package documentation string for external tool reference.
     * Contains structured information about the agent module's purpose,
     * functionality, and integration points within the Agrona ecosystem.
     */
    public static final String PACKAGE_DOCUMENTATION = 
        "Agrona Agent - Runtime Buffer Alignment Verification\n" +
        "\n" +
        "Purpose: ByteBuddy-based Java agent for runtime instrumentation of DirectBuffer " +
        "implementations to verify aligned memory access patterns. Enforces alignment " +
        "requirements critical for optimal performance on non-x86 architectures.\n" +
        "\n" +
        "Key Features:\n" +
        "- Alignment verification for all buffer access operations\n" +
        "- Performance impact detection for misaligned access patterns\n" +
        "- Development-time validation without source code modifications\n" +
        "- Zero-copy operation safety across all supported platforms\n" +
        "\n" +
        "Integration: Non-intrusive bytecode instrumentation with runtime toggle capability. " +
        "Minimal overhead when inactive, comprehensive coverage of all buffer implementations.\n" +
        "\n" +
        "Usage: Activated through JVM agent mechanisms (-javaagent:agrona-agent.jar). " +
        "Supports custom configuration for strict alignment enforcement.\n" +
        "\n" +
        "Architecture: Part of Agrona's modular architecture for microsecond-latency operations. " +
        "Complements core buffer system, concurrent utilities, and benchmark suite.\n" +
        "\n" +
        "Documentation: Comprehensive guides available in /docs/guides/agent-usage.md, " +
        "API reference in /docs/api/buffer-management.md, and architecture overview in " +
        "/docs/architecture/system-design.md";

    private PackageDocumentation()
    {
        // Utility class - prevent instantiation
    }
}
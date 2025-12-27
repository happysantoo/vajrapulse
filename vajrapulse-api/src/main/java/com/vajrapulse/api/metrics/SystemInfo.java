package com.vajrapulse.api.metrics;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * System information about the test environment.
 * 
 * <p>This record captures JVM and OS information for reproducibility
 * and debugging purposes.
 * 
 * @param javaVersion the Java version (e.g., "21.0.1")
 * @param javaVendor the Java vendor (e.g., "Eclipse Adoptium")
 * @param osName the operating system name (e.g., "Mac OS X")
 * @param osVersion the operating system version
 * @param osArch the operating system architecture (e.g., "aarch64")
 * @param hostname the hostname of the machine
 * @param availableProcessors the number of available processors
 * @since 0.9.11
 */
public record SystemInfo(
    String javaVersion,
    String javaVendor,
    String osName,
    String osVersion,
    String osArch,
    String hostname,
    int availableProcessors
) {
    
    /**
     * Creates a SystemInfo instance with the current system's information.
     * 
     * @return a new SystemInfo with current system values
     */
    public static SystemInfo current() {
        return new SystemInfo(
            System.getProperty("java.version", "unknown"),
            System.getProperty("java.vendor", "unknown"),
            System.getProperty("os.name", "unknown"),
            System.getProperty("os.version", "unknown"),
            System.getProperty("os.arch", "unknown"),
            getHostname(),
            Runtime.getRuntime().availableProcessors()
        );
    }
    
    /**
     * Creates an unknown SystemInfo instance.
     * 
     * @return a SystemInfo with unknown values
     */
    public static SystemInfo unknown() {
        return new SystemInfo("unknown", "unknown", "unknown", "unknown", "unknown", "unknown", 0);
    }
    
    private static String getHostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown";
        }
    }
}

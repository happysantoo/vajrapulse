package com.vajrapulse.api.metrics

import spock.lang.Specification
import spock.lang.Timeout

@Timeout(10)
class SystemInfoSpec extends Specification {

    def "should create SystemInfo with current system values"() {
        when: "getting current system info"
        def info = SystemInfo.current()
        
        then: "all values are populated"
        info.javaVersion() != null
        info.javaVersion() != "unknown"
        info.javaVendor() != null
        info.osName() != null
        info.osVersion() != null
        info.osArch() != null
        info.hostname() != null
        info.availableProcessors() > 0
    }
    
    def "should create unknown SystemInfo"() {
        when: "creating unknown system info"
        def info = SystemInfo.unknown()
        
        then: "all values are unknown"
        info.javaVersion() == "unknown"
        info.javaVendor() == "unknown"
        info.osName() == "unknown"
        info.osVersion() == "unknown"
        info.osArch() == "unknown"
        info.hostname() == "unknown"
        info.availableProcessors() == 0
    }
    
    def "should create SystemInfo with custom values"() {
        given: "custom system info values"
        def javaVersion = "21.0.1"
        def javaVendor = "Eclipse Adoptium"
        def osName = "Mac OS X"
        def osVersion = "14.0"
        def osArch = "aarch64"
        def hostname = "test-host"
        def processors = 8
        
        when: "creating custom SystemInfo"
        def info = new SystemInfo(javaVersion, javaVendor, osName, osVersion, osArch, hostname, processors)
        
        then: "all values match"
        info.javaVersion() == javaVersion
        info.javaVendor() == javaVendor
        info.osName() == osName
        info.osVersion() == osVersion
        info.osArch() == osArch
        info.hostname() == hostname
        info.availableProcessors() == processors
    }
}

package com.rpeters.jellyfin.utils

import android.util.Log
import android.util.Patterns
import java.net.MalformedURLException
import java.net.URI
import java.net.URISyntaxException
import java.net.URL

/**
 * Utility class for validating and normalizing Jellyfin server URLs.
 * Handles common URL formatting issues that cause connection problems.
 */
object ServerUrlValidator {
    private const val TAG = "ServerUrlValidator"
    
    // Common Jellyfin ports
    private const val DEFAULT_HTTP_PORT = 8096
    private const val DEFAULT_HTTPS_PORT = 8920
    
    /**
     * Validates and normalizes a server URL for Jellyfin connection.
     * 
     * @param inputUrl The user-provided server URL
     * @return A normalized URL string or null if invalid
     */
    fun validateAndNormalizeUrl(inputUrl: String): String? {
        if (inputUrl.isBlank()) {
            return null
        }
        
        var url = inputUrl.trim()
        
        // Remove trailing slashes
        url = url.trimEnd('/')
        
        // Add protocol if missing
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            // Default to HTTP for Jellyfin
            url = "http://$url"
        }
        
        // Add default port if missing
        url = addDefaultPortIfMissing(url)
        
        // Validate the final URL
        return if (isValidUrl(url)) {
            url
        } else {
            Log.w(TAG, "Invalid URL after normalization: $url")
            null
        }
    }
    
    /**
     * Gets fallback URL for connection failures.
     * If HTTPS fails, try HTTP. If HTTP fails, try different ports.
     * 
     * @param originalUrl The original URL that failed
     * @return Alternative URL to try, or null if no fallback available
     */
    fun getFallbackUrl(originalUrl: String): String? {
        return try {
            val uri = URI(originalUrl)
            val host = uri.host ?: return null
            
            when {
                // HTTPS on 8920 failed -> try HTTP on 8096
                uri.scheme == "https" && uri.port == DEFAULT_HTTPS_PORT -> {
                    "http://$host:$DEFAULT_HTTP_PORT"
                }
                // HTTPS on default port failed -> try HTTP on 8096
                uri.scheme == "https" && uri.port == 443 -> {
                    "http://$host:$DEFAULT_HTTP_PORT"
                }
                // HTTP on 8096 failed -> try HTTP on 80 (if not already tried)
                uri.scheme == "http" && uri.port == DEFAULT_HTTP_PORT -> {
                    "http://$host:80"
                }
                // HTTP on 80 failed -> try HTTPS on 443 (if not already tried)
                uri.scheme == "http" && uri.port == 80 -> {
                    "https://$host:443"
                }
                else -> null // No more fallbacks available
            }
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Failed to parse URI for fallback: $originalUrl", e)
            null
        }
    }
    
    /**
     * Gets all possible URL variations to try for a server.
     * Returns list in order of preference (most to least likely to work).
     * 
     * @param inputUrl The user-provided server URL
     * @return List of URLs to try in order
     */
    fun getUrlVariations(inputUrl: String): List<String> {
        if (inputUrl.isBlank()) return emptyList()
        
        var baseUrl = inputUrl.trim().trimEnd('/')
        
        // Extract components from input
        val (host, originalPort, path) = try {
            when {
                baseUrl.startsWith("http://") || baseUrl.startsWith("https://") -> {
                    val uri = URI(baseUrl)
                    Triple(uri.host, uri.port, uri.path ?: "")
                }
                else -> {
                    // No protocol specified, parse manually
                    val parts = baseUrl.split("/", limit = 2)
                    val hostPort = parts[0]
                    val path = if (parts.size > 1) "/${parts[1]}" else ""
                    
                    val host = if (hostPort.contains(":")) {
                        hostPort.substringBefore(":")
                    } else {
                        hostPort
                    }
                    val port = if (hostPort.contains(":")) {
                        hostPort.substringAfter(":").toIntOrNull() ?: -1
                    } else {
                        -1
                    }
                    Triple(host, port, path)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to extract components from URL: $inputUrl", e)
            return emptyList()
        }
        
        if (host.isNullOrBlank() || !isValidHost(host)) {
            return emptyList()
        }
        
        // Generate variations in order of preference for reverse proxy scenarios
        val variations = mutableListOf<String>()
        
        // Determine if this looks like a reverse proxy setup
        val isReverseProxyLikely = isReverseProxySetup(baseUrl, originalPort)
        
        if (isReverseProxyLikely) {
            // Reverse proxy: prioritize the exact URL provided, then try common proxy patterns
            when {
                baseUrl.startsWith("https://") -> {
                    // User specified HTTPS - likely reverse proxy handles SSL termination
                    variations.add("$baseUrl$path")
                    if (originalPort != -1 && originalPort != 443) {
                        variations.add("https://$host:$originalPort$path")
                    }
                    // Try with /jellyfin path if not already present
                    if (!path.contains("jellyfin")) {
                        variations.add("https://$host:${if (originalPort != -1) originalPort else 443}/jellyfin")
                        variations.add("https://$host/jellyfin")
                    }
                    // Try standard HTTPS ports
                    variations.add("https://$host:443$path")
                    variations.add("https://$host$path")
                    // Try HTTP on same ports (proxy might redirect)
                    if (originalPort != -1) {
                        variations.add("http://$host:$originalPort$path")
                    }
                    variations.add("http://$host$path")
                }
                baseUrl.startsWith("http://") -> {
                    variations.add("$baseUrl$path")
                    if (originalPort != -1 && originalPort != 80) {
                        variations.add("http://$host:$originalPort$path")
                    }
                    // Try with /jellyfin path if not already present
                    if (!path.contains("jellyfin")) {
                        variations.add("http://$host:${if (originalPort != -1) originalPort else 80}/jellyfin")
                        variations.add("http://$host/jellyfin")
                    }
                    variations.add("http://$host:80$path")
                    variations.add("http://$host$path")
                }
                else -> {
                    // No protocol, but looks like reverse proxy
                    if (originalPort != -1) {
                        variations.add("https://$host:$originalPort$path")
                        variations.add("http://$host:$originalPort$path")
                        // Try with /jellyfin path
                        if (!path.contains("jellyfin")) {
                            variations.add("https://$host:$originalPort/jellyfin")
                            variations.add("http://$host:$originalPort/jellyfin")
                        }
                    }
                    // Try with /jellyfin path
                    if (!path.contains("jellyfin")) {
                        variations.add("https://$host/jellyfin")
                        variations.add("http://$host/jellyfin")
                    }
                    variations.add("https://$host$path")
                    variations.add("http://$host$path")
                }
            }
        } else {
            // Direct connection: use standard Jellyfin port priorities
            when {
                baseUrl.startsWith("https://") -> {
                    variations.add("https://$host:$DEFAULT_HTTPS_PORT")
                    variations.add("https://$host:443")
                    variations.add("http://$host:$DEFAULT_HTTP_PORT")
                    variations.add("http://$host:80")
                }
                baseUrl.startsWith("http://") -> {
                    variations.add("http://$host:$DEFAULT_HTTP_PORT")
                    variations.add("http://$host:80")
                    variations.add("https://$host:$DEFAULT_HTTPS_PORT")
                    variations.add("https://$host:443")
                }
                else -> {
                    variations.add("http://$host:$DEFAULT_HTTP_PORT")
                    variations.add("https://$host:$DEFAULT_HTTPS_PORT")
                    variations.add("http://$host:80")
                    variations.add("https://$host:443")
                }
            }
        }
        
        // Remove duplicates while preserving order
        return variations.distinct().filter { isValidUrl(it) }
    }
    
    /**
     * Determines if the URL looks like a reverse proxy setup.
     * Indicators: non-standard ports, domain names with subdomains, paths
     */
    private fun isReverseProxySetup(url: String, port: Int): Boolean {
        return when {
            // Has a path component beyond root
            url.contains("/") && url.substringAfter("/").isNotEmpty() -> true
            // Uses non-standard ports (not 80, 443, 8096, 8920)
            port != -1 && port !in listOf(80, 443, DEFAULT_HTTP_PORT, DEFAULT_HTTPS_PORT) -> true
            // Domain has subdomain (more than 2 parts)
            url.substringAfter("://").substringBefore("/").substringBefore(":").count { it == '.' } >= 2 -> true
            // Contains common reverse proxy indicators in hostname
            url.contains("proxy") || url.contains("cdn") || url.contains("gateway") -> true
            else -> false
        }
    }
    
    /**
     * Adds default Jellyfin port if no port is specified.
     */
    private fun addDefaultPortIfMissing(url: String): String {
        return try {
            val uri = URI(url)
            
            // If port is already specified, keep it
            if (uri.port != -1) {
                return url
            }
            
            // Add default port based on protocol
            val defaultPort = when (uri.scheme?.lowercase()) {
                "https" -> DEFAULT_HTTPS_PORT
                "http" -> DEFAULT_HTTP_PORT
                else -> DEFAULT_HTTP_PORT
            }
            
            "${uri.scheme}://${uri.host}:$defaultPort${uri.path ?: ""}"
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Failed to parse URI for port addition: $url", e)
            url
        }
    }
    
    /**
     * Validates if a URL is properly formatted and potentially reachable.
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val urlObj = URL(url)
            
            // Check basic URL structure
            if (urlObj.host.isNullOrBlank()) {
                return false
            }
            
            // Check if host looks valid (basic pattern check)
            val host = urlObj.host
            if (!isValidHost(host)) {
                return false
            }
            
            // Check protocol
            if (urlObj.protocol !in listOf("http", "https")) {
                return false
            }
            
            // Check port range
            val port = if (urlObj.port == -1) {
                when (urlObj.protocol) {
                    "https" -> 443
                    "http" -> 80
                    else -> 80
                }
            } else {
                urlObj.port
            }
            
            if (port < 1 || port > 65535) {
                return false
            }
            
            true
        } catch (e: MalformedURLException) {
            Log.w(TAG, "Malformed URL: $url", e)
            false
        } catch (e: Exception) {
            Log.w(TAG, "Unexpected error validating URL: $url", e)
            false
        }
    }
    
    /**
     * Validates if a host string is valid (IP address, domain, or localhost).
     */
    private fun isValidHost(host: String): Boolean {
        // Check for localhost variants
        if (host.equals("localhost", ignoreCase = true) || 
            host == "127.0.0.1" || 
            host == "::1") {
            return true
        }
        
        // Check for valid IP address pattern
        if (isValidIPAddress(host)) {
            return true
        }
        
        // Check for valid domain pattern
        if (Patterns.DOMAIN_NAME.matcher(host).matches()) {
            return true
        }
        
        // Allow local network IPs (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
        if (isLocalNetworkIP(host)) {
            return true
        }
        
        return false
    }
    
    /**
     * Checks if an IP address is in private/local network ranges.
     */
    private fun isLocalNetworkIP(host: String): Boolean {
        val ipPattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".toRegex()
        val match = ipPattern.find(host) ?: return false
        
        val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: 256 }
        
        // Validate octet ranges
        if (parts.any { it < 0 || it > 255 }) {
            return false
        }
        
        val (a, b, c, d) = parts
        
        // Check private IP ranges
        return when {
            // 192.168.0.0/16
            a == 192 && b == 168 -> true
            // 10.0.0.0/8  
            a == 10 -> true
            // 172.16.0.0/12
            a == 172 && b in 16..31 -> true
            else -> false
        }
    }
    
    /**
     * Extracts the base URL (protocol + host + port) from a full URL.
     */
    fun extractBaseUrl(fullUrl: String): String? {
        return try {
            val uri = URI(fullUrl)
            val port = if (uri.port == -1) "" else ":${uri.port}"
            "${uri.scheme}://${uri.host}$port"
        } catch (e: URISyntaxException) {
            Log.w(TAG, "Failed to extract base URL from: $fullUrl", e)
            null
        }
    }
    
    /**
     * Checks if two URLs point to the same server (ignoring paths).
     */
    fun isSameServer(url1: String, url2: String): Boolean {
        val base1 = extractBaseUrl(url1)
        val base2 = extractBaseUrl(url2)
        return base1 != null && base2 != null && base1.equals(base2, ignoreCase = true)
    }
    
    /**
     * Validates if a string is a valid IPv4 address.
     */
    private fun isValidIPAddress(host: String): Boolean {
        val ipPattern = """^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$""".toRegex()
        val match = ipPattern.find(host) ?: return false
        
        val parts = match.groupValues.drop(1).map { it.toIntOrNull() ?: 256 }
        
        // Validate octet ranges (0-255)
        return parts.all { it in 0..255 }
    }
}
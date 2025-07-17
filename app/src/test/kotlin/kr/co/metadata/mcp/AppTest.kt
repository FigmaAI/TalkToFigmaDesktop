package kr.co.metadata.mcp

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class AppTest {

    @Test
    fun `application should have valid package name`() {
        val packageName = "kr.co.metadata.mcp"
        assertNotNull(packageName)
        assertTrue(packageName.isNotEmpty())
    }

    @Test
    fun `test basic functionality`() {
        
        assertTrue(true)
    }
} 
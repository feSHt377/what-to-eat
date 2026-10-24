package com.whattoeat.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {
    @Test
    fun `Release标签中的v前缀不影响比较`() {
        assertEquals(0, VersionComparator.compare("v0.2.0", "0.2.0"))
    }

    @Test
    fun `按数字段而不是字符串比较版本`() {
        assertTrue(VersionComparator.compare("v0.10.0", "0.9.0") > 0)
        assertTrue(VersionComparator.compare("v0.2.0", "0.2.1") < 0)
    }

    @Test
    fun `缺少的尾段按零处理`() {
        assertEquals(0, VersionComparator.compare("0.2", "0.2.0"))
    }

    @Test
    fun `同版本正式版高于预发布版`() {
        assertTrue(VersionComparator.compare("v0.3.0", "v0.3.0-rc1") > 0)
    }
}

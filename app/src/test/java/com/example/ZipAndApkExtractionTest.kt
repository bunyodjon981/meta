package com.example

import com.example.domain.ApkStorageHelper
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipAndApkExtractionTest {

    @Test
    fun `extracts apk wrapped inside zip artifact`() {
        val fakeApkPayload = "PK_FAKE_APK_CONTENT_12345".toByteArray()

        val zipBytes = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("app-debug.apk"))
                zos.write(fakeApkPayload)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val extracted = ApkStorageHelper.extractFirstApkFromZipBytes(
            zipBytes = zipBytes,
            fallbackBase = "TestApp"
        )

        assertNotNull(extracted)
        assertEquals("TestApp-debug.apk", extracted!!.first)
        assertArrayEquals(fakeApkPayload, extracted.second)
    }

    @Test
    fun `extracts apk from nested zip archive`() {
        val fakeApkPayload = "NESTED_APK_BYTES".toByteArray()

        val innerZip = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("release/my-app.apk"))
                zos.write(fakeApkPayload)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val outerZip = ByteArrayOutputStream().use { baos ->
            ZipOutputStream(baos).use { zos ->
                zos.putNextEntry(ZipEntry("artifact.zip"))
                zos.write(innerZip)
                zos.closeEntry()
            }
            baos.toByteArray()
        }

        val extracted = ApkStorageHelper.extractFirstApkFromZipBytes(
            zipBytes = outerZip,
            fallbackBase = "NestedApp"
        )

        assertNotNull(extracted)
        assertEquals("my-app.apk", extracted!!.first)
        assertArrayEquals(fakeApkPayload, extracted.second)
    }
}

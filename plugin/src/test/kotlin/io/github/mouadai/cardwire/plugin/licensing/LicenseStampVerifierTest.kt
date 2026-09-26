package io.github.mouadai.cardwire.plugin.licensing

import junit.framework.TestCase
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** Rejection paths only: a genuine stamp needs a JetBrains-signed license, which tests cannot mint. */
class LicenseStampVerifierTest : TestCase() {

    fun testRootCertificatesParse() {
        val factory = CertificateFactory.getInstance("X.509")
        val subjects = LicenseStampVerifier.ROOT_CERTIFICATES.map {
            (factory.generateCertificate(ByteArrayInputStream(it.toByteArray())) as X509Certificate).subjectX500Principal.name
        }
        assertEquals(listOf("CN=JetProfile CA", "CN=License Servers CA"), subjects)
    }

    fun testMissingOrUnknownStampsAreRejected() {
        assertFalse(LicenseStampVerifier.isValid(null))
        assertFalse(LicenseStampVerifier.isValid(""))
        assertFalse(LicenseStampVerifier.isValid("eval:whatever"))
    }

    fun testMalformedKeysAreRejected() {
        assertFalse(LicenseStampVerifier.isValid("key:"))
        assertFalse(LicenseStampVerifier.isValid("key:a-b-c"))
        assertFalse(LicenseStampVerifier.isValid("key:ID-bm90IGpzb24=-c2ln-bm90IGEgY2VydA=="))
    }

    fun testMalformedServerStampsAreRejected() {
        assertFalse(LicenseStampVerifier.isValid("stamp:"))
        assertFalse(LicenseStampVerifier.isValid("stamp:m:notanumber:m:SHA1withRSA:c2ln:Y2VydA=="))
        assertFalse(LicenseStampVerifier.isValid("stamp:m:1:m:SHA1withRSA:c2ln:Y2VydA=="))
    }
}

package io.github.mouadai.cardwire.plugin.licensing

import java.io.ByteArrayInputStream
import java.security.Signature
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathValidator
import java.security.cert.CertStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.util.Base64

/**
 * Checks the confirmation stamps JetBrains Marketplace licensing hands to paid plugins, following
 * JetBrains' published sample (marketplace-makemecoffee-plugin, CheckLicense): a `key:` stamp is a
 * license signed by a certificate chaining to a JetBrains root, a `stamp:` stamp is a recent,
 * signed reply from a Floating License Server. No IDE dependencies, so it is unit-testable.
 */
object LicenseStampVerifier {

    private const val KEY_PREFIX = "key:"
    private const val STAMP_PREFIX = "stamp:"

    /** How old a license server reply may be. */
    private const val SERVER_STAMP_VALIDITY_MS = 60 * 60 * 1000L

    /** Public JetBrains root certificates (JetProfile CA, License Servers CA), from the sample. */
    internal val ROOT_CERTIFICATES: List<String> = listOf(
        """
        -----BEGIN CERTIFICATE-----
        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
        -----END CERTIFICATE-----
        """.trimIndent(),
        """
        -----BEGIN CERTIFICATE-----
        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
        7tlDFnvaRnQukqSG+VqNWg==
        -----END CERTIFICATE-----
        """.trimIndent()
    )

    fun isValid(stamp: String?, now: Long = System.currentTimeMillis()): Boolean = when {
        stamp == null -> false
        stamp.startsWith(KEY_PREFIX) -> isKeyValid(stamp.removePrefix(KEY_PREFIX))
        stamp.startsWith(STAMP_PREFIX) -> isServerStampValid(stamp.removePrefix(STAMP_PREFIX), now)
        else -> false
    }

    private fun isKeyValid(key: String): Boolean {
        val parts = key.split("-")
        if (parts.size != 4) return false
        val (licenseId, licensePart, signaturePart, certPart) = parts
        return try {
            val base64 = Base64.getMimeDecoder()
            val signature = Signature.getInstance("SHA1withRSA")
            // No expiry check on the certificate: only its JetBrains origin matters for a key.
            signature.initVerify(certificate(base64.decode(certPart), emptyList(), checkValidityNow = false))
            val licenseBytes = base64.decode(licensePart)
            signature.update(licenseBytes)
            signature.verify(base64.decode(signaturePart)) &&
                String(licenseBytes, Charsets.UTF_8).contains("\"licenseId\":\"$licenseId\"")
        } catch (e: Exception) {
            false
        }
    }

    private fun isServerStampValid(serverStamp: String, now: Long): Boolean = try {
        val parts = serverStamp.split(":")
        val base64 = Base64.getMimeDecoder()
        val expectedMachineId = parts[0]
        val timestamp = parts[1].toLong()
        val machineId = parts[2]
        val signature = Signature.getInstance(parts[3])
        val intermediates = parts.drop(6).map { base64.decode(it) }
        // Expired license server certificates cannot be trusted.
        signature.initVerify(certificate(base64.decode(parts[5]), intermediates, checkValidityNow = true))
        signature.update("$timestamp:$machineId".toByteArray(Charsets.UTF_8))
        signature.verify(base64.decode(parts[4])) &&
            expectedMachineId == machineId &&
            Math.abs(now - timestamp) < SERVER_STAMP_VALIDITY_MS
    } catch (e: Exception) {
        false
    }

    private fun certificate(certBytes: ByteArray, intermediates: List<ByteArray>, checkValidityNow: Boolean): X509Certificate {
        val factory = CertificateFactory.getInstance("X.509")
        val cert = factory.generateCertificate(ByteArrayInputStream(certBytes)) as X509Certificate
        val all = HashSet<Certificate>()
        all += cert
        intermediates.forEach { all += factory.generateCertificate(ByteArrayInputStream(it)) }

        val anchors = ROOT_CERTIFICATES.map {
            TrustAnchor(factory.generateCertificate(ByteArrayInputStream(it.toByteArray(Charsets.UTF_8))) as X509Certificate, null)
        }.toSet()
        val params = PKIXBuilderParameters(anchors, X509CertSelector().apply { certificate = cert }).apply {
            isRevocationEnabled = false
            // Checking at the certificate's own start date ignores expiry, as the sample does for keys.
            if (!checkValidityNow) date = cert.notBefore
            addCertStore(CertStore.getInstance("Collection", CollectionCertStoreParameters(all)))
        }
        val path = CertPathBuilder.getInstance("PKIX").build(params).certPath
        CertPathValidator.getInstance("PKIX").validate(path, params)
        return cert
    }
}

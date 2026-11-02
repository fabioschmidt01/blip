package dev.vereda.core

import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.params.X25519PublicKeyParameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Caixa de uma mensagem. A chave simétrica nasce de um X25519 efêmero e não se repete,
 * então o nonce de 12 bytes zerados não é reutilizado. O AAD amarra a chave de assinatura
 * do remetente: copiar o ciphertext para outro pacote falha na abertura.
 */
internal object SealedBox {
    private val info = "vereda-seal-v1".encodeToByteArray()
    private val nonce = ByteArray(12)

    fun seal(
        senderSignPublic: ByteArray,
        recipientEncPublic: ByteArray,
        plaintext: ByteArray,
        random: SecureRandom,
    ): ByteArray {
        require(senderSignPublic.size == 32 && recipientEncPublic.size == 32)
        val seed = ByteArray(32).also { random.nextBytes(it) }
        val ephemeral = X25519PrivateKeyParameters(seed, 0)
        val ephemeralPublic = ephemeral.generatePublicKey().encoded
        val shared = agree(ephemeral, X25519PublicKeyParameters(recipientEncPublic, 0))
        val key = derive(shared, ephemeralPublic, recipientEncPublic)
        val cipher = encrypt(key, aad(ephemeralPublic, senderSignPublic), plaintext)
        return ephemeralPublic + cipher
    }

    fun open(senderSignPublic: ByteArray, recipientEncSeed: ByteArray, sealed: ByteArray): ByteArray? {
        if (senderSignPublic.size != 32 || recipientEncSeed.size != 32) return null
        if (sealed.size < 32 + 16) return null
        return try {
            val ephemeralPublic = sealed.copyOfRange(0, 32)
            val cipher = sealed.copyOfRange(32, sealed.size)
            val secret = X25519PrivateKeyParameters(recipientEncSeed, 0)
            val shared = agree(secret, X25519PublicKeyParameters(ephemeralPublic, 0))
            val recipientPublic = secret.generatePublicKey().encoded
            val key = derive(shared, ephemeralPublic, recipientPublic)
            decrypt(key, aad(ephemeralPublic, senderSignPublic), cipher)
        } catch (_: Exception) {
            null
        }
    }

    private fun agree(secret: X25519PrivateKeyParameters, peer: X25519PublicKeyParameters): ByteArray {
        val agreement = org.bouncycastle.crypto.agreement.X25519Agreement()
        agreement.init(secret)
        val shared = ByteArray(agreement.agreementSize)
        agreement.calculateAgreement(peer, shared, 0)
        return shared
    }

    private fun derive(shared: ByteArray, ephemeralPublic: ByteArray, recipientPublic: ByteArray): ByteArray {
        val hkdf = HKDFBytesGenerator(SHA256Digest())
        hkdf.init(HKDFParameters(shared, ephemeralPublic + recipientPublic, info))
        val key = ByteArray(32)
        hkdf.generateBytes(key, 0, key.size)
        return key
    }

    private fun aad(ephemeralPublic: ByteArray, senderSignPublic: ByteArray): ByteArray {
        return ephemeralPublic + senderSignPublic
    }

    private fun encrypt(key: ByteArray, aad: ByteArray, plaintext: ByteArray): ByteArray {
        return cipher(Cipher.ENCRYPT_MODE, key, aad).doFinal(plaintext)
    }

    private fun decrypt(key: ByteArray, aad: ByteArray, ciphertext: ByteArray): ByteArray {
        return cipher(Cipher.DECRYPT_MODE, key, aad).doFinal(ciphertext)
    }

    private fun cipher(mode: Int, key: ByteArray, aad: ByteArray): Cipher {
        val keySpec = SecretKeySpec(key, "ChaCha20")
        val iv = nonce.copyOf()
        try {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(mode, keySpec, IvParameterSpec(iv))
            cipher.updateAAD(aad)
            return cipher
        } catch (_: GeneralSecurityException) {
            val cipher = Cipher.getInstance("ChaCha20-Poly1305")
            cipher.init(mode, keySpec, GCMParameterSpec(128, iv))
            cipher.updateAAD(aad)
            return cipher
        }
    }
}

package dev.vereda.core

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.params.X25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import java.security.MessageDigest
import java.security.SecureRandom

class Identity private constructor(
    private val signSeed: ByteArray,
    private val encSeed: ByteArray,
) {
    private val signPub: ByteArray
    private val encPub: ByteArray
    val peerId: String

    init {
        require(signSeed.size == 32 && encSeed.size == 32)
        signPub = Ed25519PrivateKeyParameters(signSeed, 0).generatePublicKey().encoded
        encPub = X25519PrivateKeyParameters(encSeed, 0).generatePublicKey().encoded
        peerId = Hex.encode(signPub)
    }

    fun signPublic(): ByteArray = signPub.copyOf()

    fun encPublic(): ByteArray = encPub.copyOf()

    fun sign(message: ByteArray): ByteArray {
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(signSeed, 0))
        signer.update(message, 0, message.size)
        return signer.generateSignature()
    }

    fun open(senderSignPublic: ByteArray, sealed: ByteArray): ByteArray? {
        return SealedBox.open(senderSignPublic, encSeed, sealed)
    }

    fun fingerprint(): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(signPub)
        return hash.take(12).joinToString("") { "%02x".format(it) }.chunked(4).joinToString(" ")
    }

    fun encode(): ByteArray {
        val out = ByteArray(1 + 64)
        out[0] = FORMAT
        signSeed.copyInto(out, 1)
        encSeed.copyInto(out, 33)
        return out
    }

    companion object {
        private const val FORMAT: Byte = 1

        fun generate(random: SecureRandom = SecureRandom()): Identity {
            return Identity(randomBytes(random), randomBytes(random))
        }

        fun decode(bytes: ByteArray): Identity {
            require(bytes.size == 65 && bytes[0] == FORMAT) { "identidade ilegível" }
            return Identity(bytes.copyOfRange(1, 33), bytes.copyOfRange(33, 65))
        }

        fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean {
            if (publicKey.size != 32 || signature.size != 64) return false
            return try {
                val signer = Ed25519Signer()
                signer.init(false, Ed25519PublicKeyParameters(publicKey, 0))
                signer.update(message, 0, message.size)
                signer.verifySignature(signature)
            } catch (_: RuntimeException) {
                false
            }
        }

        private fun randomBytes(random: SecureRandom): ByteArray {
            return ByteArray(32).also { random.nextBytes(it) }
        }
    }
}

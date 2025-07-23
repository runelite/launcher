package net.rsprox.patch.runelite

import jdk.security.jarsigner.JarSigner
import net.rsprox.patch.PatchResult
import net.rsprox.patch.findBoyerMoore
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.KeyStore
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.moveTo
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeBytes
import kotlin.io.path.writeText

@Suppress("DuplicatedCode", "SameParameterValue", "unused")
public class RuneLitePatcher {
    public fun patch(
        path: Path,
        rsa: String,
        port: Int,
        varpCount: Int,
    ): PatchResult {
        if (!path.isRegularFile(LinkOption.NOFOLLOW_LINKS)) {
            throw IllegalArgumentException("Path $path does not point to a file.")
        }
        logger.debug("Attempting to patch {}", path)
        val time = System.currentTimeMillis()
        val oldModulus: String
        val patchedJar: Path
        try {
            logger.debug("Attempting to patch a jar.")
            logger.debug("Reading zip file into memory: {}", path)
            val inMemoryZip = readZipFileIntoMemory(path)
            logger.debug("Patching class files.")
            oldModulus = overwriteModulus(inMemoryZip, rsa)
            overwriteLocalHost(inMemoryZip)
            patchPort(inMemoryZip, port)
            patchVarpCount(inMemoryZip, varpCount)
            patchedJar = path.parent.resolve(path.nameWithoutExtension + "-$time-patched." + path.extension)
            writeInMemoryZipToPath(inMemoryZip, patchedJar)
        } finally {
            logger.debug("Deleting temporary extracted class files.")
        }
        logger.debug("Jar patching complete.")
        return PatchResult.Success(
            oldModulus,
            patchedJar,
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    public fun sha256Hash(bytes: ByteArray): String {
        val messageDigest = MessageDigest.getInstance("SHA-256")
        messageDigest.update(bytes)
        return messageDigest.digest().toHexString(HexFormat.UpperCase)
    }

    public fun patchLocalHostSupport(
        path: Path,
        worldClientPort: Int,
        name: String?,
    ): Path {
        val time = System.currentTimeMillis()
        val inputPath = path.parent.resolve(path.nameWithoutExtension + "-$time-patched." + path.extension)
        val configurationPath = Path(System.getProperty("user.home"), ".rsprox")
        val runelitePath = configurationPath.resolve("runelite")
        val shaPath = runelitePath.resolve("latest-runelite-$worldClientPort.sha256")
        val existingClient = runelitePath.resolve("latest-runelite-$worldClientPort.jar")
        val currentSha256 = sha256Hash(path.readBytes())
        if (shaPath.exists(LinkOption.NOFOLLOW_LINKS) &&
            existingClient.exists(LinkOption.NOFOLLOW_LINKS)
        ) {
            val existingSha256 = shaPath.readText(Charsets.UTF_8)
            if (existingSha256 == currentSha256) {
                logger.debug("Using cached runelite-client as sha-256 matches")
                inputPath.writeBytes(existingClient.readBytes())
                return inputPath
            }
        }
        existingClient.deleteIfExists()
        val copy = path.copyTo(inputPath)
        val inMemoryZip = readZipFileIntoMemory(copy)
        inMemoryZip.remove("META-INF/MANIFEST.MF")
        inMemoryZip.remove("META-INF/RL.RSA")
        inMemoryZip.remove("META-INF/RL.SF")

        replaceWorldClient(
            inMemoryZip,
            "net/runelite/client/game/WorldClient.class",
            "Original WorldClient.class",
            "WorldClient.class",
            worldClientPort,
        )
        replaceRunelite(
            inMemoryZip,
            "net/runelite/client/RuneLite.class",
            "RuneLite.class",
            name,
        )
        replaceClientLoader(
            inMemoryZip,
            "net/runelite/client/rs/ClientLoader.class",
            "ClientLoader.class",
            worldClientPort,
        )
        if (name != null) {
            writeRuneLiteProperties(
                inMemoryZip,
                name,
            )
        }

        val patchedJar =
            path.parent
                .resolve(path.nameWithoutExtension + "-$time-patched." + path.extension)
        writeInMemoryZipToPath(inMemoryZip, patchedJar)

        val jarFile = patchedJar.toFile()
        try {
            sign(patchedJar)
        } catch (e: NoSuchAlgorithmException) {
            logger.error(
                "Current JDK does not support the required jar signing algorithm. " +
                    "Switch to an older JDK (e.g. Corretto 11) that still supports it.",
                e,
            )
            throw e
        }
        jarFile.copyTo(existingClient.toFile())
        shaPath.writeText(currentSha256, Charsets.UTF_8)
        return copy
    }

    private fun writeRuneLiteProperties(
        zip: InMemoryZip,
        name: String,
    ) {
        try {
            val fileName = "net/runelite/client/runelite.properties"
            val propertiesFile =
                zip[fileName]
                    ?: error("Runelite properties not found: $fileName")
            val lines = propertiesFile.toString(Charsets.ISO_8859_1).lines()
            val replacement =
                buildString {
                    for (line in lines) {
                        when {
                            line.startsWith("runelite.title=") -> {
                                appendLine("runelite.title=$name")
                            }

                            else -> {
                                appendLine(line)
                            }
                        }
                    }
                }
            zip[fileName] = replacement.toByteArray(Charsets.ISO_8859_1)
        } catch (e: Exception) {
            logger.error("Unable to overwrite runelite properties.", e)
        }
    }

    public fun patchRuneLiteApi(path: Path): Path {
        val time = System.currentTimeMillis()
        val inputPath = path.parent.resolve(path.nameWithoutExtension + "-$time-patched." + path.extension)
        val copy = path.copyTo(inputPath)
        val inMemoryZip = readZipFileIntoMemory(copy)
        writeFile(inMemoryZip, "Varbits.class")
        writeFile(inMemoryZip, "VarPlayer.class")
        writeFile(inMemoryZip, "VarClientInt.class")
        writeFile(inMemoryZip, "VarClientStr.class")
        writeFile(inMemoryZip, "ComponentID.class", "widgets")
        writeFile(inMemoryZip, "InterfaceID.class", "widgets")
        writeGameVals(inMemoryZip)
        writeInMemoryZipToPath(inMemoryZip, copy)
        return copy
    }

    private fun sign(path: Path) {
        val fakeCertificate =
            Path(System.getProperty("user.home"))
                .resolve(".rsprox")
                .resolve("signkey")
                .resolve("fake-cert.jks")
        val password = "123456".toCharArray()
        val store = KeyStore.getInstance(fakeCertificate.toFile(), password)
        val entry = store.getEntry("test", KeyStore.PasswordProtection(password)) as KeyStore.PrivateKeyEntry
        val signer = JarSigner.Builder(entry).build()
        val output = path.parent.resolve(path.nameWithoutExtension + "-signed.${path.extension}")
        signer.sign(java.util.zip.ZipFile(path.toFile()), output.toFile().outputStream())
        output.moveTo(path, overwrite = true)
    }

    private fun writeGameVals(inMemoryZip: InMemoryZip) {
        try {
            val name = "gameval.zip"
            val zip =
                RuneLitePatcher::class.java
                    .getResourceAsStream(name)
                    ?.readAllBytes()
                    ?: throw IllegalStateException("$name resource not available.")
            val zipDestination = Path("gameval.zip")
            // Copy the zip file over from resources as the zip library we use doesn't seem to support resources
            zipDestination.writeBytes(zip)
            val gameVals = readZipFileIntoMemory(zipDestination)
            for ((gameValName, bytes) in gameVals) {
                inMemoryZip["net/runelite/api/gameval/$gameValName"] = bytes
            }
            zipDestination.deleteIfExists()
        } catch (e: Exception) {
            logger.error("Unable to extract gamevals", e)
        }
    }

    private fun writeFile(
        zip: InMemoryZip,
        name: String,
        subDir: String? = null,
    ) {
        // In order for developer mode to work, we must re-add the Var*.class that
        // RuneLite excludes from the API unless building from source
        val classByteArray =
            RuneLitePatcher::class.java
                .getResourceAsStream(name)
                ?.readAllBytes()
                ?: throw IllegalStateException("$name resource not available.")

        if (subDir != null) {
            zip["net/runelite/api/$subDir/$name"] = classByteArray
        } else {
            zip["net/runelite/api/$name"] = classByteArray
        }
    }

    private fun replaceClass(
        classFile: File,
        originalResource: String,
        replacementResource: String,
    ) {
        val replacementResourceFile =
            RuneLitePatcher::class.java
                .getResourceAsStream(replacementResource)
                ?.readAllBytes()
                ?: throw IllegalStateException("$replacementResource resource not available")

        val originalResourceFile =
            RuneLitePatcher::class.java
                .getResourceAsStream(originalResource)
                ?.readAllBytes()
                ?: throw IllegalStateException("$originalResource resource not available.")

        val originalBytes = classFile.readBytes()
        if (!originalBytes.contentEquals(originalResourceFile)) {
            throw IllegalStateException("Unable to patch RuneLite $replacementResource - out of date.")
        }

        // Overwrite the WorldClient.class file to read worlds from our proxied-list
        // This ensures that the world switcher still goes through the proxy tool,
        // instead of just connecting to RuneLite's own world list API.
        classFile.writeBytes(replacementResourceFile)
    }

    private fun replaceClientLoader(
        zip: InMemoryZip,
        name: String,
        replacementResource: String,
        port: Int,
    ) {
        val replacementResourceFile = loadResource(zip[name], replacementResource)
        if (port != 43600) {
            val inputPort = toByteArray(listOf(3, 0, 0, 43600 ushr 8 and 0xFF, 43600 and 0xFF))
            val outputPort = toByteArray(listOf(3, 0, 0, port ushr 8 and 0xFF, port and 0xFF))

            val index = replacementResourceFile.indexOf(inputPort)
            if (index != -1) {
                replacementResourceFile.replaceBytes(inputPort, outputPort)
                logger.debug("Patching port from 43600 to $port for clientloader")
            } else {
                logger.warn("Unable to patch clientloader port.")
            }
        }
        zip[name] = replacementResourceFile
    }

    private fun replaceWorldClient(
        zip: InMemoryZip,
        name: String,
        originalResource: String,
        replacementResource: String,
        port: Int,
    ) {
        val replacementResourceFile =
            RuneLitePatcher::class.java
                .getResourceAsStream(replacementResource)
                ?.readAllBytes()
                ?: throw IllegalStateException("$replacementResource resource not available")
        if (port != 43600) {
            val inputPort = toByteArray(listOf(3, 0, 0, 43600 ushr 8 and 0xFF, 43600 and 0xFF))
            val outputPort = toByteArray(listOf(3, 0, 0, port ushr 8 and 0xFF, port and 0xFF))

            val index = replacementResourceFile.indexOf(inputPort)
            if (index != -1) {
                replacementResourceFile.replaceBytes(inputPort, outputPort)
                logger.debug("Patching port from 43600 to $port")
            } else {
                logger.warn("Unable to patch worldclient port.")
            }
        }

        val originalResourceFile =
            RuneLitePatcher::class.java
                .getResourceAsStream(originalResource)
                ?.readAllBytes()
                ?: throw IllegalStateException("$originalResource resource not available.")

        val originalBytes = zip[name]
        if (!originalBytes.contentEquals(originalResourceFile)) {
            throw IllegalStateException("Unable to patch RuneLite $replacementResource - out of date.")
        }

        // Overwrite the WorldClient.class file to read worlds from our proxied-list
        // This ensures that the world switcher still goes through the proxy tool,
        // instead of just connecting to RuneLite's own world list API.
        zip[name] = replacementResourceFile
    }

    private fun replaceRunelite(
        zip: InMemoryZip,
        name: String,
        replacementResource: String,
        clientName: String?,
    ) {
        var replacementResourceFile = loadResource(zip[name], replacementResource)

        if (clientName != null) {
            val sourceDirectory = ".runelite"
            val source = sourceDirectory.toByteArray(Charsets.UTF_8)
            val index = replacementResourceFile.indexOf(source)
            val prefix = replacementResourceFile.sliceArray(0..<index)
            val suffix = replacementResourceFile.sliceArray((index + source.size)..<replacementResourceFile.size)
            val replacementDirectory = ".rlcustom"
            val replacement = replacementDirectory.toByteArray(Charsets.UTF_8)
            val combined = prefix + replacement + suffix
            replacementResourceFile = combined
            logger.info("Replacing $sourceDirectory directory with $replacementDirectory")
        }

        zip[name] = replacementResourceFile
    }

    private fun loadResource(
        originalBytes: ByteArray?,
        className: String,
    ): ByteArray {
        val name = className.replace(".class", "")
        var count = 1
        while (true) {
            val originalKnownResource =
                RuneLitePatcher::class.java
                    .getResourceAsStream("Original $name-$count.class")
                    ?.readAllBytes()
                    ?: throw IllegalStateException("Resource $className is out of date.")
            if (originalKnownResource.contentEquals(originalBytes)) {
                return RuneLitePatcher::class.java
                    .getResourceAsStream("$name-$count.class")
                    ?.readAllBytes()
                    ?: throw IllegalStateException("Unable to locate replacement resource for $className.")
            }
            count++
        }
    }

    private fun patchPort(
        zip: InMemoryZip,
        port: Int,
    ) {
        val inputPort = toByteArray(listOf(3, 0, 0, 43594 ushr 8 and 0xFF, 43594 and 0xFF))
        val outputPort = toByteArray(listOf(3, 0, 0, port ushr 8 and 0xFF, port and 0xFF))
        for ((name, bytes) in zip) {
            val index = bytes.indexOf(inputPort)
            if (index == -1) {
                continue
            }
            logger.debug("Patching port from 43594 to $port in $name")
            bytes.replaceBytes(inputPort, outputPort)
        }
    }

    private fun patchVarpCount(
        zip: InMemoryZip,
        varpCount: Int,
    ) {
        val sourceVarpCount = 5000
        if (varpCount == -1 || varpCount == sourceVarpCount) return
        // Signature is SIPUSH(17) 5000(19, -120) NEWARRAY(-68) T_INT(10) PUTSTATIC(-77)
        val inputPort = toByteArray(listOf(17, sourceVarpCount ushr 8 and 0xFF, sourceVarpCount and 0xFF, -68, 10, -77))
        val outputPort = toByteArray(listOf(17, varpCount ushr 8 and 0xFF, varpCount and 0xFF, -68, 10, -77))
        for ((name, bytes) in zip) {
            val firstIndex = bytes.indexOf(inputPort)
            if (firstIndex == -1) {
                continue
            }
            val secondIndex = bytes.indexOf(inputPort, firstIndex + inputPort.size)
            if (secondIndex == -1) {
                continue
            }
            logger.debug("Patching varp count from $sourceVarpCount to $varpCount in $name")
            // Replace the signature twice, as it is declared twice back-to-back.
            bytes.replaceBytes(inputPort, outputPort)
            bytes.replaceBytes(inputPort, outputPort)
        }
    }

    private fun toByteArray(list: List<Int>): ByteArray = list.map(Int::toByte).toByteArray()

    private fun ByteArray.replaceBytes(
        input: ByteArray,
        output: ByteArray,
    ) {
        val index = indexOf(input)
        check(index != -1) {
            "Unable to find byte sequence: ${input.contentToString()}"
        }
        overwrite(index, output)
    }

    private fun ByteArray.overwrite(
        index: Int,
        replacement: ByteArray,
    ) {
        for (i in replacement.indices) {
            this[i + index] = replacement[i]
        }
    }

    private fun overwriteModulus(
        zip: InMemoryZip,
        rsa: String,
    ): String {
        for ((name, bytes) in zip) {
            val index = bytes.indexOf("10001".toByteArray(Charsets.UTF_8))
            if (index == -1) {
                continue
            }
            logger.debug("Attempting to patch modulus in class $name")
            val (replacementBytes, oldModulus) =
                patchModulus(
                    bytes,
                    rsa,
                )
            zip[name] = replacementBytes
            return oldModulus
        }
        throw IllegalStateException("Unable to find modulus.")
    }

    private fun overwriteLocalHost(zip: InMemoryZip) {
        for ((name, bytes) in zip) {
            val index = bytes.indexOf("127.0.0.1".toByteArray(Charsets.UTF_8))
            if (index == -1) continue
            logger.debug("Patching localhost in file $name.")
            val new = patchLocalhost(bytes)
            zip[name] = new
            return
        }
        throw IllegalStateException("Unable to find localhost.")
    }

    private fun patchModulus(
        bytes: ByteArray,
        replacement: String,
    ): Pair<ByteArray, String> {
        val sliceIndices =
            bytes.firstSliceIndices(0, 256) {
                isHex(it.toInt().toChar())
            }
        check(!isHex(bytes[sliceIndices.first - 1].toInt().toChar()))
        check(!isHex(bytes[sliceIndices.last + 1].toInt().toChar()))
        val slice = bytes.sliceArray(sliceIndices)
        val oldModulus = slice.toString(Charsets.UTF_8)
        val newModulus = replacement.toByteArray(Charsets.UTF_8)
        if (newModulus.size > slice.size) {
            throw IllegalStateException("New modulus cannot be larger than the old.")
        }
        val output = bytes.setString(sliceIndices.first, replacement)

        logger.debug("Patched RSA modulus")
        logger.debug("Old modulus: $oldModulus")
        logger.debug("New modulus: $replacement")
        return output to oldModulus
    }

    private fun patchLocalhost(bytes: ByteArray): ByteArray {
        // Rather than only accept the localhost below
        val searchInput = "127.0.0.1"
        // Due to the Java client using "endsWith" function, we can't set any string here
        val replacement = ""

        val newSet = replaceText(bytes, searchInput, replacement)
        logger.debug("Replaced localhost from $searchInput to $replacement")
        return newSet
    }

    private fun replaceText(
        bytes: ByteArray,
        input: String,
        replacement: String,
    ): ByteArray {
        require(replacement.length <= input.length) {
            "Replacement string cannot be longer than the input"
        }
        val searchBytes = input.toByteArray(Charsets.UTF_8)
        val index = bytes.indexOf(searchBytes)
        if (index == -1) {
            throw IllegalArgumentException("Unable to locate input $input")
        }
        return bytes.setString(index, replacement)
    }

    private fun ByteArray.setString(
        stringStartIndex: Int,
        replacementString: String,
    ): ByteArray {
        val oldLenByte1 = this[stringStartIndex - 2].toInt() and 0xFF
        val oldLenByte2 = this[stringStartIndex - 1].toInt() and 0xFF
        val oldLength = (oldLenByte1 shl 8) or oldLenByte2
        val lengthDelta = replacementString.length - oldLength
        val replacement = ByteArray(size + lengthDelta)

        // Fill in the bytes right up until the length of the string (unmodified)
        copyInto(replacement, 0, 0, stringStartIndex - 2)

        // Fill in the length of the replacement string
        check(replacementString.length in 0..<0xFFFF)
        val newSizeByte1 = replacementString.length ushr 8 and 0xFF
        val newSizeByte2 = replacementString.length and 0xFF
        replacement[stringStartIndex - 2] = newSizeByte1.toByte()
        replacement[stringStartIndex - 1] = newSizeByte2.toByte()

        // Fill in the actual replacement string itself
        val replacementBytes = replacementString.toByteArray(Charsets.UTF_8)
        for (i in replacementBytes.indices) {
            replacement[stringStartIndex + i] = replacementBytes[i]
        }

        // Fill in the trailing bytes that come after the string (unmodified)
        copyInto(
            replacement,
            stringStartIndex + replacementString.length,
            stringStartIndex + oldLength,
        )
        return replacement
    }

    private fun ByteArray.firstSliceIndices(
        startIndex: Int,
        length: Int = -1,
        condition: (Byte) -> Boolean,
    ): IntRange {
        var start = startIndex
        val size = this.size
        while (true) {
            // First locate the starting index where a byte is being accepted
            while (start < size) {
                val byte = this[start]
                if (condition(byte)) {
                    break
                }
                start++
            }
            var end = start + 1
            // Now find the end index where a byte is not being accepted
            while (end < size) {
                val byte = this[end]
                if (!condition(byte)) {
                    break
                }
                end++
            }
            if (length != -1 && end - start < length) {
                start = end
                continue
            }
            return start..<end
        }
    }

    private fun ByteArray.indexOf(
        search: ByteArray,
        startIndex: Int = 0,
    ): Int {
        require(search.isNotEmpty()) {
            "Bytes to search are empty"
        }
        require(startIndex >= 0) {
            "Start index is negative"
        }
        return findBoyerMoore(this, search, startIndex)
    }

    private fun isHex(char: Char): Boolean =
        char in lowercaseHexStringCharRange ||
            char in uppercaseHexStringCharRange ||
            char in hexDigitsCharRange

    private companion object {
        private val lowercaseHexStringCharRange = 'a'..'f'
        private val uppercaseHexStringCharRange = 'A'..'F'
        private val hexDigitsCharRange = '0'..'9'
        private val logger = LoggerFactory.getLogger(RuneLitePatcher::class.java)
    }
}

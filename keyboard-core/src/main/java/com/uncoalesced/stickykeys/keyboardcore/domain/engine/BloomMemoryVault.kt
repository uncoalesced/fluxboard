// Engineered by uncoalesced
package com.uncoalesced.stickykeys.keyboardcore.domain.engine

import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalBigramDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.dao.PersonalDictionaryDao
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalBigramEntity
import com.uncoalesced.stickykeys.keyboardcore.data.local.entity.PersonalWordEntity
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/**
 * Writes [bytes] to [file] through a sibling `.tmp` file and an atomic replace, so a reader
 * never sees a half-written file and a crash mid-write leaves the previous version intact.
 *
 * `Files.move` rather than `File.renameTo`: `renameTo` returns false on Windows whenever the
 * target exists, so the JVM tests on a Windows host only ever exercised a non-atomic fallback
 * that never runs on a phone. `Files.move` behaves the same on both. Callers must serialise
 * writes to the same [file]; the temp name is fixed so a leftover from a crash is overwritten
 * rather than accumulating.
 */
internal fun writeFileAtomically(
    file: File,
    bytes: ByteArray,
) {
    file.parentFile?.mkdirs()
    val tmpFile = File(file.parentFile, "${file.name}.tmp")
    try {
        FileOutputStream(tmpFile).use {
            it.write(bytes)
            it.fd.sync()
        }
        try {
            Files.move(
                tmpFile.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(tmpFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    } finally {
        if (tmpFile.exists()) tmpFile.delete()
    }
}

/**
 * Cross-version durable memory snapshot for FluxBoard's Bloom Engine and BloomType.
 *
 * Ensures that learned unigrams, bigrams, continuous glide spatial offsets ([BloomSpatialProfile]),
 * and 2D tap hitbox centroids ([SmartKeyHitbox]) carry over intact across app updates, database
 * migrations, and process restarts without ever forcing FluxBoard to relearn the user.
 *
 * Every file operation holds [ioLock]. Saves are launched on a background scope once per learned
 * word, so two can be in flight at once, and both write the same temp file.
 */
object BloomMemoryVault {
    private const val MAGIC = 0x424D454D // "BMEM"
    private const val VERSION = 2
    private const val NUM_FLOATS = 52
    private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000

    private val ioLock = Any()

    /**
     * The last state written to or read from disk, as key -> (count, lastUsedTimestamp).
     *
     * The L1 lexicon and bigram cache carry counts but no timestamps, so a save built from them
     * has to get timestamps from somewhere. Stamping everything with the current time made every
     * entry look freshly used on every save: nothing ever aged past the 30-day cutoff, and
     * [reconcileAndSync] restored rows that SQLite had already pruned. An entry whose count is
     * unchanged since the last save keeps the timestamp it had. Guarded by [ioLock]; null until
     * seeded from disk.
     */
    private var knownUnigrams: MutableMap<String, Pair<Int, Long>>? = null
    private var knownBigrams: MutableMap<Pair<String, String>, Pair<Int, Long>>? = null

    data class Snapshot(
        val glideOffsets: FloatArray,
        val tapOffsets: FloatArray,
        val unigrams: List<PersonalWordEntity>,
        val bigrams: List<PersonalBigramEntity>,
        val tapVariances: FloatArray = FloatArray(NUM_FLOATS),
        val actAcceptCount: Int = 0,
        val actRevertCount: Int = 0,
        val adaptiveThresholdOffset: Float = 0f,
    )

    /**
     * Atomically writes the complete user memory snapshot to [file] with a SHA-256 integrity footer.
     */
    fun save(
        file: File,
        glideOffsets: FloatArray,
        tapOffsets: FloatArray,
        unigrams: List<PersonalWordEntity>,
        bigrams: List<PersonalBigramEntity>,
        tapVariances: FloatArray = FloatArray(NUM_FLOATS),
        actAcceptCount: Int = 0,
        actRevertCount: Int = 0,
        adaptiveThresholdOffset: Float = 0f,
    ) {
        if (glideOffsets.size != NUM_FLOATS ||
            tapOffsets.size != NUM_FLOATS ||
            tapVariances.size != NUM_FLOATS
        ) {
            return
        }
        val bytes =
            encode(
                glideOffsets = glideOffsets,
                tapOffsets = tapOffsets,
                unigrams = unigrams,
                bigrams = bigrams,
                tapVariances = tapVariances,
                actAcceptCount = actAcceptCount,
                actRevertCount = actRevertCount,
                adaptiveThresholdOffset = adaptiveThresholdOffset,
            )
        synchronized(ioLock) {
            writeFileAtomically(file, bytes)
            remember(unigrams, bigrams)
        }
    }

    /**
     * Loads and verifies the user memory snapshot from [file]. Returns null if absent or corrupted.
     */
    fun load(file: File): Snapshot? =
        synchronized(ioLock) {
            val snapshot = loadLocked(file)
            if (snapshot != null) remember(snapshot.unigrams, snapshot.bigrams)
            snapshot
        }

    /** Deletes the vault under the I/O lock, so an in-flight save cannot resurrect it. */
    fun delete(file: File) {
        synchronized(ioLock) {
            if (file.exists()) file.delete()
            File(file.parentFile, "${file.name}.tmp").delete()
            knownUnigrams = null
            knownBigrams = null
        }
    }

    /** Drops the cached timestamp state. For tests that simulate a fresh process. */
    internal fun forgetCachedState() {
        synchronized(ioLock) {
            knownUnigrams = null
            knownBigrams = null
        }
    }

    private fun encode(
        glideOffsets: FloatArray,
        tapOffsets: FloatArray,
        unigrams: List<PersonalWordEntity>,
        bigrams: List<PersonalBigramEntity>,
        tapVariances: FloatArray = FloatArray(NUM_FLOATS),
        actAcceptCount: Int = 0,
        actRevertCount: Int = 0,
        adaptiveThresholdOffset: Float = 0f,
    ): ByteArray {
        val payloadStream = ByteArrayOutputStream(4096)
        val payloadOut = DataOutputStream(payloadStream)

        for (f in glideOffsets) {
            payloadOut.writeInt(java.lang.Float.floatToIntBits(f))
        }
        for (f in tapOffsets) {
            payloadOut.writeInt(java.lang.Float.floatToIntBits(f))
        }

        val cappedUnigrams = unigrams.take(BloomPersonalLexicon.MAX_UNIGRAMS)
        payloadOut.writeInt(cappedUnigrams.size)
        for (u in cappedUnigrams) {
            val bytes = u.word.toByteArray(Charsets.UTF_8)
            payloadOut.writeShort(bytes.size)
            payloadOut.write(bytes)
            payloadOut.writeInt(u.frequency)
            payloadOut.writeLong(u.lastUsedTimestamp)
        }

        val cappedBigrams = bigrams.take(BloomPersonalBigramCache.MAX_BIGRAMS)
        payloadOut.writeInt(cappedBigrams.size)
        for (b in cappedBigrams) {
            val prevBytes = b.previousWord.toByteArray(Charsets.UTF_8)
            val nextBytes = b.nextWord.toByteArray(Charsets.UTF_8)
            payloadOut.writeShort(prevBytes.size)
            payloadOut.write(prevBytes)
            payloadOut.writeShort(nextBytes.size)
            payloadOut.write(nextBytes)
            payloadOut.writeInt(b.count)
            payloadOut.writeLong(b.lastUsedTimestamp)
        }

        for (f in tapVariances) {
            payloadOut.writeInt(java.lang.Float.floatToIntBits(f))
        }
        payloadOut.writeInt(actAcceptCount)
        payloadOut.writeInt(actRevertCount)
        payloadOut.writeInt(java.lang.Float.floatToIntBits(adaptiveThresholdOffset))
        payloadOut.flush()

        val payloadBytes = payloadStream.toByteArray()
        val digest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)

        val totalSize = 4 + 4 + payloadBytes.size + 32
        val buffer = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN)
        buffer.putInt(MAGIC)
        buffer.putInt(VERSION)
        buffer.put(payloadBytes)
        buffer.put(digest)
        return buffer.array()
    }

    /** Caller holds [ioLock]. */
    private fun remember(
        unigrams: List<PersonalWordEntity>,
        bigrams: List<PersonalBigramEntity>,
    ) {
        knownUnigrams =
            unigrams.associateTo(HashMap()) { it.word to (it.frequency to it.lastUsedTimestamp) }
        knownBigrams =
            bigrams.associateTo(HashMap()) {
                (it.previousWord to it.nextWord) to (it.count to it.lastUsedTimestamp)
            }
    }

    /** Caller holds [ioLock]. */
    private fun loadLocked(file: File): Snapshot? {
        if (!file.exists() || file.length() < (8 + NUM_FLOATS * 8 + 8 + 32).toLong()) return null
        return try {
            val bytes = FileInputStream(file).use { it.readBytes() }
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)

            if (buffer.int != MAGIC) return null
            val version = buffer.int
            if (version < 1) return null

            val payloadLen = bytes.size - 8 - 32
            if (payloadLen <= 0) return null
            val payloadBytes = ByteArray(payloadLen)
            buffer.get(payloadBytes)

            val expectedDigest = ByteArray(32)
            buffer.get(expectedDigest)
            val actualDigest = MessageDigest.getInstance("SHA-256").digest(payloadBytes)
            if (!actualDigest.contentEquals(expectedDigest)) return null

            val pBuf = ByteBuffer.wrap(payloadBytes).order(ByteOrder.BIG_ENDIAN)
            val glideOffsets = finiteOrZero(FloatArray(NUM_FLOATS) { Float.fromBits(pBuf.int) })
            val tapOffsets = finiteOrZero(FloatArray(NUM_FLOATS) { Float.fromBits(pBuf.int) })

            val unigramCount =
                pBuf.int.coerceIn(0, BloomPersonalLexicon.MAX_UNIGRAMS)
            val unigrams = ArrayList<PersonalWordEntity>(unigramCount)
            for (i in 0 until unigramCount) {
                val len = pBuf.short.toInt() and 0xFFFF
                if (len <= 0 || pBuf.remaining() < len + 12) break
                val wordBytes = ByteArray(len)
                pBuf.get(wordBytes)
                val freq = pBuf.int
                val ts = pBuf.long
                unigrams.add(
                    PersonalWordEntity(
                        word = String(wordBytes, Charsets.UTF_8),
                        frequency = freq,
                        lastUsedTimestamp = ts,
                    ),
                )
            }

            val bigramCount =
                if (pBuf.remaining() >= 4) {
                    pBuf.int.coerceIn(0, BloomPersonalBigramCache.MAX_BIGRAMS)
                } else {
                    0
                }
            val bigrams = ArrayList<PersonalBigramEntity>(bigramCount)
            for (i in 0 until bigramCount) {
                if (pBuf.remaining() < 4) break
                val prevLen = pBuf.short.toInt() and 0xFFFF
                if (prevLen <= 0 || pBuf.remaining() < prevLen + 2) break
                val prevBytes = ByteArray(prevLen)
                pBuf.get(prevBytes)

                val nextLen = pBuf.short.toInt() and 0xFFFF
                if (nextLen <= 0 || pBuf.remaining() < nextLen + 12) break
                val nextBytes = ByteArray(nextLen)
                pBuf.get(nextBytes)

                val count = pBuf.int
                val ts = pBuf.long
                bigrams.add(
                    PersonalBigramEntity(
                        previousWord = String(prevBytes, Charsets.UTF_8),
                        nextWord = String(nextBytes, Charsets.UTF_8),
                        count = count,
                        lastUsedTimestamp = ts,
                    ),
                )
            }

            var tapVariances = FloatArray(NUM_FLOATS)
            var actAcceptCount = 0
            var actRevertCount = 0
            var adaptiveThresholdOffset = 0f
            if (version >= 2 && pBuf.remaining() >= NUM_FLOATS * 4 + 12) {
                tapVariances =
                    finiteOrZero(FloatArray(NUM_FLOATS) { Float.fromBits(pBuf.int) })
                actAcceptCount = pBuf.int.coerceAtLeast(0)
                actRevertCount = pBuf.int.coerceAtLeast(0)
                val rawOffset = Float.fromBits(pBuf.int)
                adaptiveThresholdOffset =
                    if (rawOffset.isFinite()) {
                        rawOffset.coerceIn(
                            BloomAdaptiveThreshold.MIN_OFFSET,
                            BloomAdaptiveThreshold.MAX_OFFSET,
                        )
                    } else {
                        0f
                    }
            }

            Snapshot(
                glideOffsets = glideOffsets,
                tapOffsets = tapOffsets,
                unigrams = unigrams,
                bigrams = bigrams,
                tapVariances = tapVariances,
                actAcceptCount = actAcceptCount,
                actRevertCount = actRevertCount,
                adaptiveThresholdOffset = adaptiveThresholdOffset,
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * A digest only proves the bytes are the ones written, not that they were sane. A NaN
     * offset survives `coerceIn` (every comparison with NaN is false) and then poisons every
     * distance it touches, so a spatial array with any non-finite value is discarded whole.
     * The learned words alongside it are unaffected.
     */
    private fun finiteOrZero(values: FloatArray): FloatArray =
        if (values.all { it.isFinite() }) values else FloatArray(NUM_FLOATS)

    /**
     * Reconciles Room database tables, L1 caches, [BloomSpatialProfile], and [SmartKeyHitbox]
     * with the durable cross-version vault snapshot at [vaultFile], restoring any state that was
     * missing or eroded and writing back the unified snapshot.
     */
    internal fun reconcileAndSync(
        vaultFile: File,
        glideFile: File?,
        tapFile: File?,
        personalDao: PersonalDictionaryDao,
        personalBigramDao: PersonalBigramDao?,
        learningEngine: PersonalLearningEngine,
        glideProfile: BloomSpatialProfile,
        adaptiveThreshold: BloomAdaptiveThreshold? = null,
    ) {
        val now = System.currentTimeMillis()
        val cutoff = now - THIRTY_DAYS_MS
        val existingVault = load(vaultFile)

        // 1. Reconcile Glide Spatial Profile
        val currentGlide = glideProfile.snapshot()
        if (existingVault != null &&
            currentGlide.all { it == 0f } &&
            existingVault.glideOffsets.any { it != 0f }
        ) {
            glideProfile.restore(existingVault.glideOffsets)
            if (glideFile != null) {
                try {
                    glideProfile.save(glideFile)
                } catch (e: Exception) {
                }
            }
        }

        // 2. Reconcile Tap Spatial Hitbox Profile
        val currentTap = SmartKeyHitbox.snapshot()
        if (existingVault != null &&
            currentTap.all { it == 0f } &&
            existingVault.tapOffsets.any { it != 0f }
        ) {
            SmartKeyHitbox.restore(existingVault.tapOffsets)
            if (tapFile != null) {
                try {
                    SmartKeyHitbox.save(tapFile)
                } catch (e: Exception) {
                }
            }
        }

        val currentVariances = SmartKeyHitbox.varianceSnapshot()
        if (existingVault != null &&
            currentVariances.all { it == 0f } &&
            existingVault.tapVariances.any { it > 0f }
        ) {
            SmartKeyHitbox.restoreVariances(existingVault.tapVariances)
        }

        if (existingVault != null && adaptiveThreshold != null) {
            val (curAccepts, curReverts, curOffset) = adaptiveThreshold.snapshot()
            val hasVaultAct =
                existingVault.actAcceptCount > 0 ||
                    existingVault.actRevertCount > 0 ||
                    existingVault.adaptiveThresholdOffset != 0f
            if (curAccepts == 0 && curReverts == 0 && curOffset == 0f && hasVaultAct) {
                adaptiveThreshold.restore(
                    existingVault.actAcceptCount,
                    existingVault.actRevertCount,
                    existingVault.adaptiveThresholdOffset,
                )
            }
        }

        // 3. Reconcile Unigrams (DB <-> Vault <-> L1 Lexicon)
        val dbUnigrams =
            try {
                personalDao
                    .getAll(BloomPersonalLexicon.MAX_UNIGRAMS)
                    .associateBy { it.word }
                    .toMutableMap()
            } catch (e: Exception) {
                mutableMapOf()
            }

        if (existingVault != null) {
            for (vWord in existingVault.unigrams) {
                if (vWord.lastUsedTimestamp < cutoff || vWord.frequency <= 0) continue
                val existing = dbUnigrams[vWord.word]
                if (existing == null || vWord.frequency > existing.frequency) {
                    val lastTs =
                        maxOf(vWord.lastUsedTimestamp, existing?.lastUsedTimestamp ?: now)
                    val merged =
                        PersonalWordEntity(
                            word = vWord.word,
                            frequency = maxOf(vWord.frequency, existing?.frequency ?: 0),
                            lastUsedTimestamp = lastTs,
                        )
                    dbUnigrams[vWord.word] = merged
                    try {
                        personalDao.insertOrUpdate(merged)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        for (u in dbUnigrams.values) {
            val l1Count = learningEngine.lexicon.countOf(u.word)
            if (u.frequency > l1Count) {
                learningEngine.lexicon.recordOccurrence(u.word, u.frequency - l1Count)
            }
        }

        // 4. Reconcile Bigrams (DB <-> Vault <-> L1 BigramCache)
        val dbBigrams =
            try {
                personalBigramDao
                    ?.getAllRecent(BloomPersonalBigramCache.MAX_BIGRAMS)
                    ?.associateBy { it.previousWord to it.nextWord }
                    ?.toMutableMap() ?: mutableMapOf()
            } catch (e: Exception) {
                mutableMapOf()
            }

        if (existingVault != null) {
            for (vBigram in existingVault.bigrams) {
                if (vBigram.lastUsedTimestamp < cutoff || vBigram.count <= 0) continue
                val key = vBigram.previousWord to vBigram.nextWord
                val existing = dbBigrams[key]
                if (existing == null || vBigram.count > existing.count) {
                    val lastTs =
                        maxOf(vBigram.lastUsedTimestamp, existing?.lastUsedTimestamp ?: now)
                    val merged =
                        PersonalBigramEntity(
                            previousWord = vBigram.previousWord,
                            nextWord = vBigram.nextWord,
                            count = maxOf(vBigram.count, existing?.count ?: 0),
                            lastUsedTimestamp = lastTs,
                        )
                    dbBigrams[key] = merged
                    try {
                        personalBigramDao?.upsertBigram(merged)
                    } catch (e: Exception) {
                    }
                }
            }
        }

        for (b in dbBigrams.values) {
            val l1Count = learningEngine.bigramCache.countFor(b.previousWord, b.nextWord)
            if (b.count > l1Count) {
                learningEngine.bigramCache.recordTransition(
                    b.previousWord,
                    b.nextWord,
                    b.count - l1Count,
                )
            }
        }

        // 5. Persist unified snapshot from current L1/DB state
        saveCurrentState(
            vaultFile = vaultFile,
            learningEngine = learningEngine,
            glideProfile = glideProfile,
            dbUnigrams = dbUnigrams.values.toList(),
            dbBigrams = dbBigrams.values.toList(),
            adaptiveThreshold = adaptiveThreshold,
        )
    }

    /**
     * Writes the current L1 state (or the DB rows, when given) to [vaultFile].
     *
     * Building the entity lists and writing them happen in one critical section, so two
     * concurrent calls cannot both read the same "last saved" state and then each overwrite
     * the other's timestamps.
     */
    internal fun saveCurrentState(
        vaultFile: File,
        learningEngine: PersonalLearningEngine,
        glideProfile: BloomSpatialProfile,
        dbUnigrams: List<PersonalWordEntity>? = null,
        dbBigrams: List<PersonalBigramEntity>? = null,
        adaptiveThreshold: BloomAdaptiveThreshold? = null,
    ) {
        try {
            val glide = glideProfile.snapshot()
            val tap = SmartKeyHitbox.snapshot()
            val tapVariances = SmartKeyHitbox.varianceSnapshot()
            val (actAccepts, actReverts, actOffset) =
                adaptiveThreshold?.snapshot() ?: Triple(0, 0, 0f)
            synchronized(ioLock) {
                if (knownUnigrams == null || knownBigrams == null) {
                    // First save in this process: seed timestamps from what is on disk.
                    loadLocked(vaultFile)?.let { remember(it.unigrams, it.bigrams) }
                }
                val now = System.currentTimeMillis()
                val priorUnigrams = knownUnigrams.orEmpty()
                val priorBigrams = knownBigrams.orEmpty()
                val unigrams =
                    dbUnigrams
                        ?: learningEngine.lexicon.snapshot().map { (w, count) ->
                            val ts = timestampFor(priorUnigrams[w], count, now)
                            PersonalWordEntity(w, count, ts)
                        }
                val bigrams =
                    dbBigrams
                        ?: learningEngine.bigramCache.snapshot().flatMap { (prev, map) ->
                            map.map { (next, count) ->
                                PersonalBigramEntity(
                                    prev,
                                    next,
                                    count,
                                    timestampFor(priorBigrams[prev to next], count, now),
                                )
                            }
                        }
                if (glide.size != NUM_FLOATS ||
                    tap.size != NUM_FLOATS ||
                    tapVariances.size != NUM_FLOATS
                ) {
                    return
                }
                writeFileAtomically(
                    vaultFile,
                    encode(
                        glideOffsets = glide,
                        tapOffsets = tap,
                        unigrams = unigrams,
                        bigrams = bigrams,
                        tapVariances = tapVariances,
                        actAcceptCount = actAccepts,
                        actRevertCount = actReverts,
                        adaptiveThresholdOffset = actOffset,
                    ),
                )
                remember(unigrams, bigrams)
            }
        } catch (e: Exception) {
        }
    }

    private fun timestampFor(
        prior: Pair<Int, Long>?,
        count: Int,
        now: Long,
    ): Long = if (prior != null && prior.first == count) prior.second else now
}

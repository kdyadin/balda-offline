package com.kdyadin.balda.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.kdyadin.balda.BuildConfig
import com.kdyadin.balda.core.Alphabet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Состояние базы толкований. */
sealed class DefinitionsStatus {
    data object NotDownloaded : DefinitionsStatus()

    data class Downloading(val downloadedBytes: Long, val totalBytes: Long) : DefinitionsStatus() {
        val fraction: Float? get() = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else null
    }

    data class Ready(val sizeBytes: Long, val entries: Int) : DefinitionsStatus()

    data class Error(val message: String, val partialBytes: Long) : DefinitionsStatus()
}

/**
 * Загрузка (один раз, по желанию пользователя) и чтение базы толкований.
 * База — обычный файл SQLite, собранный скриптом tools/build-dictionary/build_definitions.py.
 */
class DefinitionsRepository(context: Context, private val scope: CoroutineScope) {
    private val dbFile = File(context.filesDir, "definitions.db")
    private val partFile = File(context.filesDir, "definitions.db.part")

    private val _status = MutableStateFlow<DefinitionsStatus>(initialStatus())
    val status: StateFlow<DefinitionsStatus> = _status.asStateFlow()

    val isAvailable: Boolean get() = _status.value is DefinitionsStatus.Ready
    val isConfigured: Boolean get() = BuildConfig.DEFINITIONS_URL.isNotBlank()

    private var downloadJob: Job? = null
    @Volatile
    private var db: SQLiteDatabase? = null

    private fun initialStatus(): DefinitionsStatus = when {
        dbFile.exists() -> DefinitionsStatus.Ready(dbFile.length(), countEntries())
        partFile.exists() -> DefinitionsStatus.Error("Загрузка была прервана", partFile.length())
        else -> DefinitionsStatus.NotDownloaded
    }

    private fun countEntries(): Int = runCatching {
        open()?.rawQuery("SELECT value FROM meta WHERE key = 'count'", null)?.use { c ->
            if (c.moveToFirst()) c.getString(0).toIntOrNull() ?: 0 else 0
        } ?: 0
    }.getOrDefault(0)

    @Synchronized
    private fun open(): SQLiteDatabase? {
        db?.let { return it }
        if (!dbFile.exists()) return null
        return runCatching {
            SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS)
        }.getOrNull()?.also { db = it }
    }

    @Synchronized
    private fun close() {
        db?.close()
        db = null
    }

    /** Толкования слова (до 3 значений) или null, если базы нет либо слова в ней нет. */
    suspend fun lookup(word: String): List<String>? = withContext(Dispatchers.IO) {
        val database = open() ?: return@withContext null
        val key = Alphabet.normalize(word)
        runCatching {
            database.rawQuery("SELECT defs FROM definitions WHERE word = ?", arrayOf(key)).use { c ->
                if (c.moveToFirst()) c.getString(0).split('\n').filter { it.isNotBlank() } else emptyList()
            }
        }.getOrNull()
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        if (!isConfigured) {
            _status.value = DefinitionsStatus.Error("Адрес базы толкований не настроен в сборке", 0)
            return
        }
        downloadJob = scope.launch(Dispatchers.IO) {
            try {
                download()
            } catch (e: kotlinx.coroutines.CancellationException) {
                _status.value = DefinitionsStatus.Error("Загрузка отменена", partFile.length())
                throw e
            } catch (e: Exception) {
                _status.value = DefinitionsStatus.Error(e.message ?: "Ошибка загрузки", partFile.length())
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    suspend fun delete() = withContext(Dispatchers.IO) {
        downloadJob?.cancel()
        close()
        dbFile.delete()
        partFile.delete()
        _status.value = DefinitionsStatus.NotDownloaded
    }

    private suspend fun download() {
        val expectedSha = fetchExpectedSha256()
        val url = URL(BuildConfig.DEFINITIONS_URL)
        var downloaded = if (partFile.exists()) partFile.length() else 0L
        _status.value = DefinitionsStatus.Downloading(downloaded, 0)

        val conn = openConnection(url)
        if (downloaded > 0) conn.setRequestProperty("Range", "bytes=$downloaded-")
        conn.connect()
        try {
            val code = conn.responseCode
            when {
                code == HttpURLConnection.HTTP_PARTIAL -> Unit // докачка
                code == HttpURLConnection.HTTP_OK -> {
                    // сервер не поддерживает Range или файл изменился — начинаем сначала
                    partFile.delete()
                    downloaded = 0
                }
                else -> throw IOException("Сервер ответил кодом $code")
            }
            val remaining = conn.contentLengthLong
            val total = if (remaining >= 0) downloaded + remaining else -1L
            _status.value = DefinitionsStatus.Downloading(downloaded, total)

            RandomAccessFile(partFile, "rw").use { out ->
                out.seek(downloaded)
                conn.inputStream.use { input ->
                    val buf = ByteArray(64 * 1024)
                    var lastReport = System.currentTimeMillis()
                    while (true) {
                        currentCoroutineContextEnsureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        val now = System.currentTimeMillis()
                        if (now - lastReport > 150) {
                            _status.value = DefinitionsStatus.Downloading(downloaded, total)
                            lastReport = now
                        }
                    }
                }
            }
            if (total > 0 && downloaded < total) throw IOException("Соединение оборвано, загружено $downloaded из $total байт")
        } finally {
            conn.disconnect()
        }

        _status.value = DefinitionsStatus.Downloading(downloaded, downloaded)
        val actual = sha256(partFile)
        if (!actual.equals(expectedSha, ignoreCase = true)) {
            partFile.delete()
            throw IOException("Контрольная сумма не совпала, файл удалён. Попробуйте ещё раз")
        }
        close()
        if (dbFile.exists()) dbFile.delete()
        if (!partFile.renameTo(dbFile)) throw IOException("Не удалось сохранить базу")
        // Проверим, что это действительно наша база.
        val entries = countEntries()
        if (open() == null || entries <= 0) {
            close()
            dbFile.delete()
            throw IOException("Файл не является базой толкований")
        }
        _status.value = DefinitionsStatus.Ready(dbFile.length(), entries)
    }

    private fun fetchExpectedSha256(): String {
        val conn = openConnection(URL(BuildConfig.DEFINITIONS_SHA256_URL))
        try {
            conn.connect()
            if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("Не удалось получить контрольную сумму (код ${conn.responseCode})")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val sha = Regex("[0-9a-fA-F]{64}").find(text)?.value ?: throw IOException("Файл контрольной суммы повреждён")
            return sha.lowercase()
        } finally {
            conn.disconnect()
        }
    }

    private fun openConnection(url: URL): HttpURLConnection = (url.openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "Balda-Offline/${BuildConfig.VERSION_NAME}")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private suspend fun currentCoroutineContextEnsureActive() {
        kotlin.coroutines.coroutineContext.ensureActive()
    }
}

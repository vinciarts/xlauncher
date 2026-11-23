package com.example.data

import android.Manifest
import android.app.SearchManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.DocumentsContract
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.model.ContactSearchResult
import com.example.model.FileSearchResult
import com.example.model.IndexedFolderInfo
import com.example.model.SearchProviderApp
import com.example.search.PinyinSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class UniversalSearchRepository(private val context: Context) {

    private val pm: PackageManager get() = context.packageManager
    private val providerIconCache = ConcurrentHashMap<String, ImageBitmap>()

    fun hasContactsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun loadContactsIfPermitted(): List<ContactSearchResult> = withContext(Dispatchers.IO) {
        if (!hasContactsPermission()) return@withContext emptyList()

        // Step 1: map primary phone number per contact ID
        val phonesByContactId = HashMap<Long, String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                val numCol = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (idCol >= 0 && numCol >= 0) {
                    while (cursor.moveToNext()) {
                        val cid = cursor.getLong(idCol)
                        if (cid !in phonesByContactId) {
                            val num = cursor.getString(numCol)?.trim()
                            if (!num.isNullOrEmpty()) {
                                phonesByContactId[cid] = num
                            }
                        }
                    }
                }
            }
        }

        // Step 2: load contacts
        val results = ArrayList<ContactSearchResult>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.Contacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.Contacts._ID,
                    ContactsContract.Contacts.LOOKUP_KEY,
                    ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ),
                null,
                null,
                "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val keyCol = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameCol = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                if (idCol >= 0 && keyCol >= 0 && nameCol >= 0) {
                    while (cursor.moveToNext() && results.size < MAX_CACHED_CONTACTS) {
                        val id = cursor.getLong(idCol)
                        val lookupKey = cursor.getString(keyCol) ?: continue
                        val name = cursor.getString(nameCol)?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        results.add(
                            ContactSearchResult(
                                contactId = id,
                                lookupKey = lookupKey,
                                displayName = name,
                                phoneNumber = phonesByContactId[id],
                            )
                        )
                    }
                }
            }
        }

        PinyinSearchEngine.prewarm(results.map { it.displayName })
        results
    }

    fun searchContacts(
        query: String,
        contacts: List<ContactSearchResult>,
        limit: Int = 8,
    ): List<ContactSearchResult> {
        if (query.isBlank() || contacts.isEmpty()) return emptyList()
        val qDigits = query.filter { it.isDigit() }
        val scored = ArrayList<Pair<Int, ContactSearchResult>>()
        for (c in contacts) {
            val phoneClean = c.phoneNumber?.filter { it.isDigit() }
            val secondary = if (qDigits.length >= 2) phoneClean else null
            val score = PinyinSearchEngine.matchScore(query, c.displayName, secondary)
            if (score != PinyinSearchEngine.NO_MATCH) {
                scored.add(score to c)
            }
        }
        return scored
            .sortedWith(compareBy<Pair<Int, ContactSearchResult>> { it.first }.thenBy { it.second.displayName })
            .take(limit)
            .map { it.second }
    }

    fun openContact(contact: ContactSearchResult): Boolean = runCatching {
        val lookupUri = ContactsContract.Contacts.getLookupUri(contact.contactId, contact.lookupKey)
        val intent = Intent(Intent.ACTION_VIEW, lookupUri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    fun dialContact(phoneNumber: String): Boolean = runCatching {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phoneNumber)}")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    suspend fun resolveIndexedFolders(treeUriStrings: List<String>): List<IndexedFolderInfo> =
        withContext(Dispatchers.IO) {
            treeUriStrings.map { raw ->
                IndexedFolderInfo(
                    uri = raw,
                    displayName = getTreeFolderLabel(raw),
                )
            }
        }

    fun getTreeFolderLabel(treeUriString: String): String {
        val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return treeUriString
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (!treeDocId.isNullOrBlank()) {
            val docUri = runCatching {
                DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
            }.getOrNull()
            if (docUri != null) {
                val queried = runCatching {
                    context.contentResolver.query(
                        docUri,
                        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                        null,
                        null,
                        null,
                    )?.use { c ->
                        if (c.moveToFirst()) c.getString(0)?.trim() else null
                    }
                }.getOrNull()
                if (!queried.isNullOrBlank()) return queried
            }
            val parsed = treeDocId.substringAfterLast(':').substringAfterLast('/').trim()
            if (parsed.isNotEmpty()) return parsed
        }
        return treeUri.lastPathSegment?.substringAfterLast(':')?.ifBlank { treeUriString } ?: treeUriString
    }

    suspend fun indexFileTrees(treeUriStrings: List<String>): List<FileSearchResult> =
        withContext(Dispatchers.IO) {
            if (treeUriStrings.isEmpty()) return@withContext emptyList()
            val out = ArrayList<FileSearchResult>()
            val seenUris = HashSet<String>()

            for (rawTree in treeUriStrings) {
                if (out.size >= MAX_INDEXED_FILES) break
                val treeUri = runCatching { Uri.parse(rawTree) }.getOrNull() ?: continue
                val rootDocId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
                    ?: continue
                val rootLabel = getTreeFolderLabel(rawTree)
                traverseTree(
                    treeUri = treeUri,
                    parentDocId = rootDocId,
                    folderLabel = rootLabel,
                    depth = 0,
                    seenUris = seenUris,
                    out = out,
                )
            }
            PinyinSearchEngine.prewarm(out.map { it.name })
            out
        }

    private fun traverseTree(
        treeUri: Uri,
        parentDocId: String,
        folderLabel: String,
        depth: Int,
        seenUris: HashSet<String>,
        out: MutableList<FileSearchResult>,
    ) {
        if (depth > MAX_FILE_TREE_DEPTH || out.size >= MAX_INDEXED_FILES) return
        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        }.getOrNull() ?: return

        val subDirs = ArrayList<Pair<String, String>>()
        runCatching {
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeCol = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                if (idCol < 0 || nameCol < 0 || mimeCol < 0) return@use

                while (cursor.moveToNext() && out.size < MAX_INDEXED_FILES) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol)?.trim().orEmpty()
                    if (name.isEmpty() || name.startsWith(".")) continue
                    val mime = cursor.getString(mimeCol) ?: "*/*"
                    val size = if (sizeCol >= 0 && !cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L

                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (depth < MAX_FILE_TREE_DEPTH) {
                            subDirs.add(docId to name)
                        }
                    } else {
                        val docUri = runCatching {
                            DocumentsContract.buildDocumentUriUsingTree(treeUri, docId).toString()
                        }.getOrNull() ?: continue
                        if (seenUris.add(docUri)) {
                            out.add(
                                FileSearchResult(
                                    documentUri = docUri,
                                    name = name,
                                    mimeType = mime,
                                    sizeBytes = size,
                                    parentFolderName = folderLabel,
                                )
                            )
                        }
                    }
                }
            }
        }

        for ((subDocId, subName) in subDirs) {
            if (out.size >= MAX_INDEXED_FILES) break
            traverseTree(
                treeUri = treeUri,
                parentDocId = subDocId,
                folderLabel = subName,
                depth = depth + 1,
                seenUris = seenUris,
                out = out,
            )
        }
    }

    fun searchFiles(
        query: String,
        files: List<FileSearchResult>,
        limit: Int = 10,
    ): List<FileSearchResult> {
        if (query.isBlank() || files.isEmpty()) return emptyList()
        val scored = ArrayList<Pair<Int, FileSearchResult>>()
        for (file in files) {
            val score = PinyinSearchEngine.matchScore(query, file.name, file.parentFolderName)
            if (score != PinyinSearchEngine.NO_MATCH) {
                scored.add(score to file)
            }
        }
        return scored
            .sortedWith(compareBy<Pair<Int, FileSearchResult>> { it.first }.thenBy { it.second.name })
            .take(limit)
            .map { it.second }
    }

    fun openFile(file: FileSearchResult): Boolean = runCatching {
        val uri = Uri.parse(file.documentUri)
        val mime = file.mimeType.ifBlank { "*/*" }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    suspend fun loadThirdPartySearchProviders(): List<SearchProviderApp> = withContext(Dispatchers.IO) {
        val myPkg = context.packageName
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        val byPackage = LinkedHashMap<String, SearchProviderApp>()

        val actions = listOf(Intent.ACTION_SEARCH, Intent.ACTION_WEB_SEARCH)
        for (action in actions) {
            val queryIntent = Intent(action)
            val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(queryIntent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(queryIntent, 0)
            }

            for (ri in resolved) {
                val ai = ri.activityInfo ?: continue
                if (!ai.exported) continue
                val pkg = ai.packageName ?: continue
                if (pkg == myPkg || pkg in byPackage) continue
                val cls = ai.name ?: continue

                val appLabel = runCatching {
                    pm.getApplicationLabel(ai.applicationInfo)?.toString()?.trim()
                }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: runCatching { ri.loadLabel(pm)?.toString()?.trim() }.getOrNull()?.takeIf { it.isNotEmpty() }
                    ?: pkg.substringAfterLast('.')

                val icon = providerIconCache[pkg] ?: runCatching {
                    pm.getApplicationIcon(pkg)
                        .toBitmap(width = 112, height = 112, config = Bitmap.Config.ARGB_8888)
                        .asImageBitmap()
                }.getOrNull()?.also { bmp ->
                    providerIconCache[pkg] = bmp
                }

                byPackage[pkg] = SearchProviderApp(
                    packageName = pkg,
                    activityName = cls,
                    label = appLabel,
                    action = action,
                    icon = icon,
                )
            }
        }

        val sorted = byPackage.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
        PinyinSearchEngine.prewarm(sorted.map { it.label })
        sorted
    }

    fun launchThirdPartySearch(provider: SearchProviderApp, query: String): Boolean {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return false
        val targeted = runCatching {
            val intent = Intent(provider.action).apply {
                component = ComponentName(provider.packageName, provider.activityName)
                putExtra(SearchManager.QUERY, trimmed)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        }.getOrDefault(false)
        if (targeted) return true

        return runCatching {
            val fallback = Intent(provider.action).apply {
                setPackage(provider.packageName)
                putExtra(SearchManager.QUERY, trimmed)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallback)
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val MAX_CACHED_CONTACTS = 2_500
        private const val MAX_INDEXED_FILES = 1_500
        private const val MAX_FILE_TREE_DEPTH = 3
    }
}

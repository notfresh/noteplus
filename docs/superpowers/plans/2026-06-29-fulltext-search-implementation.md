# 全文检索功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 NotePlus 添加基于 Lucene + IK Analyzer 的中文全文检索功能，支持笔记正文分词搜索。

**Architecture:** 采用单例 SearchManager 统一管理索引构建和搜索服务。索引数据存储在应用私有目录，通过 search_index_status 表跟踪索引状态。搜索时利用 IK 分词器对输入进行分词，构建 BooleanQuery 实现 OR 匹配。

**Tech Stack:** Lucene Core 9.x, IK Analyzer (Android 版), WorkManager, SQLite

---

## 文件结构

```
新增文件:
- app/src/main/java/person/notfresh/noteplus/search/SearchResult.java
- app/src/main/java/person/notfresh/noteplus/search/NoteIndexer.java
- app/src/main/java/person/notfresh/noteplus/search/SearchService.java
- app/src/main/java/person/notfresh/noteplus/search/SearchManager.java
- app/src/main/java/person/notfresh/noteplus/search/SearchIndexDbHelper.java
- app/src/main/java/person/notfresh/noteplus/util/SearchIndexInitWorker.java

修改文件:
- app/src/main/java/person/notfresh/noteplus/db/NoteDbHelper.java
- app/build.gradle.kts
- app/src/main/java/person/notfresh/noteplus/MainActivity.java
```

---

## Task 1: 添加 IK Analyzer 和 Lucene 依赖

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: 添加 Maven 仓库（JitPack 用于 IK Analyzer）**

在 `settings.gradle.kts` 或项目级 `build.gradle.kts` 的 `dependencyResolutionManagement` 中添加:

```kotlin
maven { url = uri("https://jitpack.io") }
```

- [ ] **Step 2: 添加 Lucene 和 IK 依赖**

在 `app/build.gradle.kts` 的 `dependencies` 中添加:

```kotlin
// Lucene 核心
implementation("org.apache.lucene:lucene-core:9.10.0")
implementation("org.apache.lucene:lucene-analysis-api:9.10.0")

// IK Analyzer Android 版 (来自 jitpack)
implementation("com.github.magese:ik-analyzer-android:1.0.5")
```

- [ ] **Step 3: Sync Gradle 并验证依赖引入成功**

Run: `cd app && ../gradlew dependencies --configuration releaseRuntimeClasspath 2>&1 | grep -E "(lucene|ik-analyzer)"`

Expected: 看到 lucene-core 和 ik-analyzer 相关依赖

- [ ] **Step 4: 提交**

```bash
git add app/build.gradle.kts && git commit -m "feat: 添加 Lucene 和 IK Analyzer 依赖"
```

---

## Task 2: 创建 search_index_status 表

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/db/NoteDbHelper.java`

- [ ] **Step 1: 在 NoteDbHelper 中添加表常量和建表 SQL**

在 `NoteDbHelper.java` 中添加:

```java
// search_index_status 表
public static final String TABLE_SEARCH_INDEX_STATUS = "search_index_status";
public static final String COLUMN_INDEX_NOTE_ID = "note_id";
public static final String COLUMN_INDEXED_AT = "indexed_at";
```

在 `DATABASE_CREATE` 之后、`onCreate` 中添加:

```java
String CREATE_SEARCH_INDEX_STATUS_TABLE = "CREATE TABLE " + TABLE_SEARCH_INDEX_STATUS + "("
        + COLUMN_INDEX_NOTE_ID + " INTEGER PRIMARY KEY,"
        + COLUMN_INDEXED_AT + " INTEGER NOT NULL,"
        + "FOREIGN KEY (" + COLUMN_INDEX_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE"
        + ")";
database.execSQL(CREATE_SEARCH_INDEX_STATUS_TABLE);
```

- [ ] **Step 2: 在 onUpgrade 中添加建表逻辑**

在 `onUpgrade` 方法的末尾（`oldVersion < 10` 判断之后）添加:

```java
if (oldVersion < 11) {
    String CREATE_SEARCH_INDEX_STATUS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_SEARCH_INDEX_STATUS + "("
            + COLUMN_INDEX_NOTE_ID + " INTEGER PRIMARY KEY,"
            + COLUMN_INDEXED_AT + " INTEGER NOT NULL,"
            + "FOREIGN KEY (" + COLUMN_INDEX_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE"
            + ")";
    db.execSQL(CREATE_SEARCH_INDEX_STATUS_TABLE);
}
```

同时将 `DATABASE_VERSION` 从 `10` 改为 `11`。

- [ ] **Step 3: 添加索引记录操作方法**

在 `NoteDbHelper.java` 中添加以下方法:

```java
/**
 * 标记笔记已索引
 * @param noteId 笔记ID
 * @return 是否成功
 */
public boolean markNoteIndexed(long noteId) {
    SQLiteDatabase db = this.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(COLUMN_INDEX_NOTE_ID, noteId);
    values.put(COLUMN_INDEXED_AT, System.currentTimeMillis());
    return db.insertWithOnConflict(TABLE_SEARCH_INDEX_STATUS, null, values, SQLiteDatabase.CONFLICT_REPLACE) != -1;
}

/**
 * 移除笔记索引标记（删除时调用）
 * @param noteId 笔记ID
 * @return 删除的行数
 */
public int unmarkNoteIndexed(long noteId) {
    SQLiteDatabase db = this.getWritableDatabase();
    return db.delete(TABLE_SEARCH_INDEX_STATUS, COLUMN_INDEX_NOTE_ID + "=?", new String[]{String.valueOf(noteId)});
}

/**
 * 获取所有未索引的笔记ID列表
 * @return Cursor，包含未索引笔记的 _id
 */
public Cursor getUnindexedNotes() {
    SQLiteDatabase db = this.getReadableDatabase();
    String query = "SELECT n." + COLUMN_ID + " FROM " + TABLE_NOTES + " n "
            + "LEFT JOIN " + TABLE_SEARCH_INDEX_STATUS + " s ON n." + COLUMN_ID + " = s." + COLUMN_INDEX_NOTE_ID
            + " WHERE s." + COLUMN_INDEX_NOTE_ID + " IS NULL AND n." + COLUMN_IS_ARCHIVED + " = 0";
    return db.rawQuery(query, null);
}

/**
 * 检查笔记是否已索引
 * @param noteId 笔记ID
 * @return true 表示已索引
 */
public boolean isNoteIndexed(long noteId) {
    SQLiteDatabase db = this.getReadableDatabase();
    Cursor cursor = db.query(TABLE_SEARCH_INDEX_STATUS,
            new String[]{COLUMN_INDEX_NOTE_ID},
            COLUMN_INDEX_NOTE_ID + "=?",
            new String[]{String.valueOf(noteId)},
            null, null, null);
    boolean indexed = cursor != null && cursor.getCount() > 0;
    if (cursor != null) cursor.close();
    return indexed;
}
```

- [ ] **Step 4: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | tail -20`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/db/NoteDbHelper.java && git commit -m "feat(search): 添加 search_index_status 表及索引管理方法"
```

---

## Task 3: 创建 SearchResult 模型

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/search/SearchResult.java`

- [ ] **Step 1: 创建 SearchResult 类**

```java
package person.notfresh.noteplus.search;

import person.notfresh.noteplus.core.model.Note;

/**
 * 搜索结果模型
 */
public class SearchResult {
    private final Note note;
    private final String highlightedContent;  // 高亮处理后的内容摘要
    private final float score;  // 相关性得分

    public SearchResult(Note note, String highlightedContent, float score) {
        this.note = note;
        this.highlightedContent = highlightedContent;
        this.score = score;
    }

    public Note getNote() {
        return note;
    }

    public String getHighlightedContent() {
        return highlightedContent;
    }

    public float getScore() {
        return score;
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/search/SearchResult.java && git commit -m "feat(search): 创建 SearchResult 模型"
```

---

## Task 4: 创建 NoteIndexer 索引构建器

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/search/NoteIndexer.java`

- [ ] **Step 1: 创建 NoteIndexer 类**

```java
package person.notfresh.noteplus.search;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.Version;

import java.io.File;
import java.io.IOException;

import person.notfresh.noteplus.db.NoteDbHelper;

/**
 * 笔记索引构建器
 * 负责构建和更新 Lucene 索引
 */
public class NoteIndexer {
    private static final String TAG = "NoteIndexer";
    private static final String INDEX_DIR = "search_index";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TIMESTAMP = "timestamp";

    private final Context context;
    private final NoteDbHelper dbHelper;
    private Analyzer analyzer;
    private Directory indexDirectory;
    private IndexWriter indexWriter;

    public NoteIndexer(Context context, NoteDbHelper dbHelper) {
        this.context = context.getApplicationContext();
        this.dbHelper = dbHelper;
        initIndex();
    }

    private void initIndex() {
        try {
            File indexDir = new File(context.getFilesDir(), INDEX_DIR);
            if (!indexDir.exists()) {
                indexDir.mkdirs();
            }
            LockFactory lockFactory = SimpleFSLockFactory.getDefault();
            indexDirectory = new org.apache.lucene.store.FSDirectory.open(indexDir.toPath(), lockFactory);
            analyzer = new org.wltea.analyzer.lucene.IKAnalyzer(true);  // true = 细粒度分词
            IndexWriterConfig config = new IndexWriterConfig(analyzer);
            indexWriter = new IndexWriter(indexDirectory, config);
            Log.i(TAG, "索引初始化成功，索引目录: " + indexDir.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "索引初始化失败", e);
        }
    }

    /**
     * 为指定笔记构建索引
     * @param noteId 笔记ID
     * @param content 笔记内容
     * @param timestamp 时间戳
     * @return 是否成功
     */
    public boolean indexNote(long noteId, String content, long timestamp) {
        if (indexWriter == null) {
            Log.e(TAG, "索引写入器未初始化");
            return false;
        }
        try {
            // 先删除旧文档（如果存在）
            indexWriter.deleteDocuments(new LongPoint(FIELD_ID, noteId));

            // 创建新文档
            Document doc = new Document();
            doc.add(new LongPoint(FIELD_ID, noteId));
            doc.add(new StoredField(FIELD_ID, noteId));
            doc.add(new TextField(FIELD_CONTENT, content, Field.Store.YES));
            doc.add(new LongPoint(FIELD_TIMESTAMP, timestamp));
            doc.add(new StoredField(FIELD_TIMESTAMP, timestamp));

            indexWriter.addDocument(doc);
            indexWriter.commit();

            // 标记为已索引
            dbHelper.markNoteIndexed(noteId);
            Log.d(TAG, "笔记 " + noteId + " 索引构建成功");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "笔记 " + noteId + " 索引构建失败", e);
            return false;
        }
    }

    /**
     * 从索引中删除指定笔记
     * @param noteId 笔记ID
     * @return 是否成功
     */
    public boolean deleteNoteIndex(long noteId) {
        if (indexWriter == null) {
            Log.e(TAG, "索引写入器未初始化");
            return false;
        }
        try {
            indexWriter.deleteDocuments(new LongPoint(FIELD_ID, noteId));
            indexWriter.commit();
            dbHelper.unmarkNoteIndexed(noteId);
            Log.d(TAG, "笔记 " + noteId + " 从索引中删除");
            return true;
        } catch (IOException e) {
            Log.e(TAG, "删除笔记 " + noteId + " 索引失败", e);
            return false;
        }
    }

    /**
     * 批量构建未索引笔记的索引
     * @param progressCallback 进度回调 (current, total)
     * @return 成功构建的数量
     */
    public int indexUnindexedNotes(java.util.function.BiConsumer<Integer, Integer> progressCallback) {
        Cursor cursor = dbHelper.getUnindexedNotes();
        int count = 0;
        int total = cursor.getCount();
        Log.i(TAG, "发现 " + total + " 条未索引笔记");

        try {
            while (cursor.moveToNext()) {
                long noteId = cursor.getLong(cursor.getColumnIndexOrThrow("_id"));
                // 获取笔记完整信息
                Cursor noteCursor = dbHelper.getWritableDatabase().query(
                        NoteDbHelper.TABLE_NOTES,
                        new String[]{NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP},
                        NoteDbHelper.COLUMN_ID + "=?",
                        new String[]{String.valueOf(noteId)},
                        null, null, null
                );
                if (noteCursor != null && noteCursor.moveToFirst()) {
                    String content = noteCursor.getString(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT));
                    long timestamp = noteCursor.getLong(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP));
                    if (indexNote(noteId, content, timestamp)) {
                        count++;
                    }
                    noteCursor.close();
                }
                if (progressCallback != null) {
                    progressCallback.accept(count, total);
                }
            }
        } finally {
            cursor.close();
        }
        Log.i(TAG, "批量索引构建完成，成功 " + count + " 条");
        return count;
    }

    /**
     * 关闭索引写入器
     */
    public void close() {
        try {
            if (indexWriter != null) {
                indexWriter.close();
            }
            if (analyzer != null) {
                analyzer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭索引写入器失败", e);
        }
    }
}
```

**注意**: IK Analyzer 的类名是 `org.wltea.analyzer.lucene.IKAnalyzer`，如果 Android 版类名不同需要调整。

- [ ] **Step 2: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | grep -E "(error|Error|BUILD)" | head -20`

Expected: 无编译错误

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/search/NoteIndexer.java && git commit -m "feat(search): 创建 NoteIndexer 索引构建器"
```

---

## Task 5: 创建 SearchService 搜索服务

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/search/SearchService.java`

- [ ] **Step 1: 创建 SearchService 类**

```java
package person.notfresh.noteplus.search;

import android.content.Context;
import android.database.Cursor;
import android.util.Log;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockFactory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.wltea.analyzer.lucene.IKAnalyzer;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.noteplus.db.NoteDbHelper;

/**
 * 搜索服务
 * 负责分词和搜索查询
 */
public class SearchService {
    private static final String TAG = "SearchService";
    private static final String INDEX_DIR = "search_index";
    private static final String FIELD_ID = "id";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TIMESTAMP = "timestamp";
    private static final int MAX_SEARCH_RESULTS = 100;

    private final Context context;
    private final NoteDbHelper dbHelper;
    private Analyzer searchAnalyzer;  // 搜索时用智能分词
    private Directory indexDirectory;

    public SearchService(Context context, NoteDbHelper dbHelper) {
        this.context = context.getApplicationContext();
        this.dbHelper = dbHelper;
        initIndex();
    }

    private void initIndex() {
        try {
            File indexDir = new File(context.getFilesDir(), INDEX_DIR);
            if (!indexDir.exists()) {
                indexDir.mkdirs();
            }
            LockFactory lockFactory = SimpleFSLockFactory.getDefault();
            indexDirectory = new org.apache.lucene.store.FSDirectory.open(indexDir.toPath(), lockFactory);
            searchAnalyzer = new IKAnalyzer(false);  // false = 智能分词模式
        } catch (IOException e) {
            Log.e(TAG, "搜索服务初始化失败", e);
        }
    }

    /**
     * 对搜索词进行分词
     * @param query 搜索词
     * @return 分词后的词列表
     */
    public List<String> tokenize(String query) {
        List<String> tokens = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return tokens;
        }
        try {
            // IKAnalyzer 的 tokenStream 方法
            org.apache.lucene.analysis.TokenStream tokenStream = searchAnalyzer.tokenStream(FIELD_CONTENT, query);
            org.apache.lucene.analysis.tokenattributes.CharTermAttribute termAttr = tokenStream.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
            tokenStream.reset();
            while (tokenStream.incrementToken()) {
                String term = termAttr.toString();
                if (!term.isEmpty()) {
                    tokens.add(term);
                }
            }
            tokenStream.end();
            tokenStream.close();
        } catch (IOException e) {
            Log.e(TAG, "分词失败", e);
        }
        return tokens;
    }

    /**
     * 搜索笔记
     * @param query 搜索词
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query) {
        List<SearchResult> results = new ArrayList<>();
        if (query == null || query.trim().isEmpty()) {
            return results;
        }

        try {
            // 1. 分词
            List<String> tokens = tokenize(query);
            if (tokens.isEmpty()) {
                Log.d(TAG, "分词结果为空");
                return results;
            }
            Log.d(TAG, "分词结果: " + tokens);

            // 2. 构建 BooleanQuery（OR 匹配）
            BooleanQuery.Builder boolQueryBuilder = new BooleanQuery.Builder();
            for (String token : tokens) {
                // 在 content 字段中搜索每个分词
                boolQueryBuilder.add(new BooleanClause(
                        new org.apache.lucene.search.TermQuery(new Term(FIELD_CONTENT, token)),
                        BooleanClause.Occur.SHOULD
                ));
            }
            BooleanQuery booleanQuery = boolQueryBuilder.build();

            // 3. 执行搜索
            IndexReader reader = DirectoryReader.open(indexDirectory);
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs topDocs = searcher.search(booleanQuery, MAX_SEARCH_RESULTS);

            Log.d(TAG, "找到 " + topDocs.totalHits + " 条匹配结果");

            // 4. 处理结果
            for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
                Document doc = searcher.doc(scoreDoc.doc);
                long noteId = doc.getField(FIELD_ID).numericValue().longValue();
                String content = doc.getField(FIELD_CONTENT).stringValue();
                long timestamp = doc.getField(FIELD_TIMESTAMP).numericValue().longValue();

                // 获取对应的 Note 对象
                Cursor noteCursor = dbHelper.getWritableDatabase().query(
                        NoteDbHelper.TABLE_NOTES,
                        new String[]{NoteDbHelper.COLUMN_ID, NoteDbHelper.COLUMN_CONTENT,
                                NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST,
                                NoteDbHelper.COLUMN_IS_PINNED},
                        NoteDbHelper.COLUMN_ID + "=?",
                        new String[]{String.valueOf(noteId)},
                        null, null, null
                );

                person.notfresh.noteplus.core.model.Note note = null;
                if (noteCursor != null && noteCursor.moveToFirst()) {
                    note = new person.notfresh.noteplus.core.model.Note(
                            noteCursor.getLong(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_ID)),
                            noteCursor.getString(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT)),
                            noteCursor.getLong(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP)),
                            noteCursor.getDouble(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST)),
                            noteCursor.getInt(noteCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_IS_PINNED)) == 1
                    );
                    noteCursor.close();
                }

                if (note != null) {
                    // 高亮处理：简单替换匹配词
                    String highlighted = highlightContent(content, tokens);
                    results.add(new SearchResult(note, highlighted, scoreDoc.score));
                }
            }
            reader.close();

        } catch (IOException | ParseException e) {
            Log.e(TAG, "搜索失败", e);
        }

        return results;
    }

    /**
     * 简单高亮处理：将匹配的词用 ** 包裹
     * @param content 原始内容
     * @param tokens 分词列表
     * @return 高亮后的内容
     */
    private String highlightContent(String content, List<String> tokens) {
        if (content == null || tokens.isEmpty()) {
            return content;
        }
        String result = content;
        for (String token : tokens) {
            // 简单替换，实际可使用 Lucene 的 Highlighter 类
            result = result.replace(token, "【" + token + "】");
        }
        // 限制显示长度
        if (result.length() > 200) {
            int highlightIndex = result.indexOf("【");
            if (highlightIndex > 100) {
                result = "..." + result.substring(highlightIndex - 50);
            } else {
                result = result.substring(0, 200) + "...";
            }
        }
        return result;
    }

    /**
     * 关闭资源
     */
    public void close() {
        try {
            if (searchAnalyzer != null) {
                searchAnalyzer.close();
            }
        } catch (IOException e) {
            Log.e(TAG, "关闭搜索服务失败", e);
        }
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | grep -E "(error|Error|BUILD)" | head -20`

Expected: 无编译错误

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/search/SearchService.java && git commit -m "feat(search): 创建 SearchService 搜索服务"
```

---

## Task 6: 创建 SearchManager 单例管理器

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/search/SearchManager.java`

- [ ] **Step 1: 创建 SearchManager 类**

```java
package person.notfresh.noteplus.search;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import person.notfresh.noteplus.db.NoteDbHelper;

/**
 * 搜索管理器（单例）
 * 统一管理索引构建和搜索服务
 */
public class SearchManager {
    private static final String TAG = "SearchManager";
    private static volatile SearchManager instance;

    private final Context context;
    private final NoteDbHelper dbHelper;
    private NoteIndexer noteIndexer;
    private SearchService searchService;
    private final Handler mainHandler;
    private boolean isIndexing = false;

    private SearchManager(Context context) {
        this.context = context.getApplicationContext();
        this.dbHelper = NoteDbHelper.getInstance(this.context);
        this.mainHandler = new Handler(Looper.getMainLooper());
        initServices();
    }

    public static SearchManager getInstance(Context context) {
        if (instance == null) {
            synchronized (SearchManager.class) {
                if (instance == null) {
                    instance = new SearchManager(context);
                }
            }
        }
        return instance;
    }

    private void initServices() {
        noteIndexer = new NoteIndexer(context, dbHelper);
        searchService = new SearchService(context, dbHelper);
        Log.i(TAG, "SearchManager 初始化完成");
    }

    /**
     * 索引单条笔记
     * @param noteId 笔记ID
     * @param content 笔记内容
     * @param timestamp 时间戳
     */
    public void indexNote(long noteId, String content, long timestamp) {
        new Thread(() -> {
            boolean success = noteIndexer.indexNote(noteId, content, timestamp);
            if (!success) {
                Log.w(TAG, "笔记 " + noteId + " 索引失败");
            }
        }).start();
    }

    /**
     * 删除笔记索引
     * @param noteId 笔记ID
     */
    public void deleteNoteIndex(long noteId) {
        new Thread(() -> {
            noteIndexer.deleteNoteIndex(noteId);
        }).start();
    }

    /**
     * 搜索笔记
     * @param query 搜索词
     * @param callback 搜索结果回调（在主线程）
     */
    public void search(String query, SearchCallback callback) {
        if (callback == null) {
            return;
        }
        new Thread(() -> {
            java.util.List<SearchResult> results = searchService.search(query);
            mainHandler.post(() -> callback.onSearchResult(results));
        }).start();
    }

    /**
     * 批量索引未索引的笔记（后台执行）
     * @param callback 进度回调（current, total）
     */
    public void indexUnindexedNotes(java.util.function.BiConsumer<Integer, Integer> callback) {
        if (isIndexing) {
            Log.w(TAG, "索引任务已在执行中");
            return;
        }
        isIndexing = true;
        new Thread(() -> {
            int count = noteIndexer.indexUnindexedNotes((current, total) -> {
                if (callback != null) {
                    mainHandler.post(() -> callback.accept(current, total));
                }
            });
            isIndexing = false;
            Log.i(TAG, "批量索引完成，共 " + count + " 条");
        }).start();
    }

    /**
     * 释放资源
     */
    public void release() {
        if (noteIndexer != null) {
            noteIndexer.close();
        }
        if (searchService != null) {
            searchService.close();
        }
    }

    /**
     * 搜索回调接口
     */
    public interface SearchCallback {
        void onSearchResult(java.util.List<SearchResult> results);
    }
}
```

- [ ] **Step 2: 在 NoteDbHelper 中添加单例方法**

在 `NoteDbHelper.java` 中将构造函数改为单例模式:

```java
private static volatile NoteDbHelper instance;

public static NoteDbHelper getInstance(Context context) {
    if (instance == null) {
        synchronized (NoteDbHelper.class) {
            if (instance == null) {
                instance = new NoteDbHelper(context.getApplicationContext());
            }
        }
    }
    return instance;
}
```

- [ ] **Step 3: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | grep -E "(error|Error|BUILD)" | head -20`

Expected: 无编译错误

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/search/SearchManager.java && git commit -m "feat(search): 创建 SearchManager 单例管理器"
```

---

## Task 7: 创建 SearchIndexDbHelper 数据库操作类

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/search/SearchIndexDbHelper.java`

- [ ] **Step 1: 创建 SearchIndexDbHelper 类**

```java
package person.notfresh.noteplus.search;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import person.notfresh.noteplus.db.NoteDbHelper;

/**
 * 搜索索引数据库操作辅助类
 * 封装 search_index_status 表的常用操作
 */
public class SearchIndexDbHelper {
    private static volatile SearchIndexDbHelper instance;
    private final NoteDbHelper dbHelper;

    private SearchIndexDbHelper(Context context) {
        this.dbHelper = NoteDbHelper.getInstance(context);
    }

    public static SearchIndexDbHelper getInstance(Context context) {
        if (instance == null) {
            synchronized (SearchIndexDbHelper.class) {
                if (instance == null) {
                    instance = new SearchIndexDbHelper(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 标记笔记已索引
     */
    public boolean markIndexed(long noteId) {
        return dbHelper.markNoteIndexed(noteId);
    }

    /**
     * 移除索引标记
     */
    public int unmarkIndexed(long noteId) {
        return dbHelper.unmarkNoteIndexed(noteId);
    }

    /**
     * 获取未索引笔记的游标
     */
    public Cursor getUnindexedNotesCursor() {
        return dbHelper.getUnindexedNotes();
    }

    /**
     * 检查笔记是否已索引
     */
    public boolean isIndexed(long noteId) {
        return dbHelper.isNoteIndexed(noteId);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/search/SearchIndexDbHelper.java && git commit -m "feat(search): 创建 SearchIndexDbHelper 辅助类"
```

---

## Task 8: 创建 SearchIndexInitWorker 后台索引任务

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/util/SearchIndexInitWorker.java`

- [ ] **Step 1: 创建 SearchIndexInitWorker 类**

```java
package person.notfresh.noteplus.util;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.search.NoteIndexer;
import person.notfresh.noteplus.search.SearchManager;

/**
 * 后台索引初始化任务
 * 使用 WorkManager 在后台构建未索引笔记的索引
 */
public class SearchIndexInitWorker extends Worker {
    private static final String TAG = "SearchIndexInitWorker";
    public static final String WORK_NAME = "search_index_init";

    public SearchIndexInitWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.i(TAG, "开始后台索引构建任务");
        try {
            SearchManager searchManager = SearchManager.getInstance(getApplicationContext());
            NoteIndexer indexer = new NoteIndexer(getApplicationContext(), NoteDbHelper.getInstance(getApplicationContext()));

            // 获取未索引笔记数量
            android.database.Cursor cursor = NoteDbHelper.getInstance(getApplicationContext()).getUnindexedNotes();
            int total = cursor.getCount();
            cursor.close();

            if (total == 0) {
                Log.i(TAG, "没有需要索引的笔记");
                return Result.success();
            }

            Log.i(TAG, "发现 " + total + " 条未索引笔记，开始构建...");

            // 执行批量索引
            int[] completed = {0};
            indexer.indexUnindexedNotes((current, t) -> {
                Log.d(TAG, "索引进度: " + current + "/" + t);
            });

            indexer.close();
            Log.i(TAG, "后台索引构建任务完成");
            return Result.success();

        } catch (Exception e) {
            Log.e(TAG, "后台索引构建任务失败", e);
            return Result.retry();
        }
    }

    /**
     * 调度后台索引任务
     */
    public static void schedule(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build();

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(SearchIndexInitWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, workRequest);

        Log.i(TAG, "后台索引任务已调度");
    }

    /**
     * 取消后台索引任务
     */
    public static void cancel(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME);
        Log.i(TAG, "后台索引任务已取消");
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/util/SearchIndexInitWorker.java && git commit -m "feat(search): 创建 SearchIndexInitWorker 后台索引任务"
```

---

## Task 9: 在 MainActivity 中集成搜索 UI

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 在 MainActivity 中添加搜索相关成员变量**

在 MainActivity 类的成员变量区域添加:

```java
// 搜索相关
private boolean isSearchMode = false;
private EditText searchEditText;
private ListView searchResultListView;
private SearchResultAdapter searchResultAdapter;
private ArrayList<SearchResult> searchResults = new ArrayList<>();
private SearchManager searchManager;
private Handler searchHandler = new Handler();
private Runnable searchRunnable;
```

- [ ] **Step 2: 在 onCreate 中初始化 SearchManager**

```java
// 初始化搜索管理器
searchManager = SearchManager.getInstance(this);
// 检查并构建未索引笔记
SearchIndexInitWorker.schedule(this);
```

- [ ] **Step 3: 在 onCreateOptionsMenu 中添加搜索菜单项**

在 Toolbar 菜单的 `onCreateOptionsMenu` 方法中添加:

```java
@Override
public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu_main, menu);

    // 获取搜索菜单项
    MenuItem searchItem = menu.findItem(R.id.action_search);
    if (searchItem != null) {
        searchItem.setOnMenuItemClickListener(item -> {
            enterSearchMode();
            return true;
        });
    }

    return true;
}
```

- [ ] **Step 4: 添加搜索模式 UI 切换方法**

```java
private void enterSearchMode() {
    isSearchMode = true;
    // 隐藏原有内容，显示搜索框
    // 需要在布局中添加搜索相关的 View
    // 具体实现根据你的布局结构而定
}

private void exitSearchMode() {
    isSearchMode = false;
    if (searchEditText != null) {
        searchEditText.setText("");
    }
    searchResults.clear();
    if (searchResultAdapter != null) {
        searchResultAdapter.notifyDataSetChanged();
    }
    // 隐藏搜索框，恢复原有内容
}

private void performSearch(String query) {
    if (searchManager == null || query.trim().isEmpty()) {
        return;
    }

    searchManager.search(query, results -> {
        searchResults.clear();
        searchResults.addAll(results);
        if (searchResultAdapter != null) {
            searchResultAdapter.notifyDataSetChanged();
        }
    });
}
```

- [ ] **Step 5: 在 menu_main.xml 中添加搜索菜单项**

创建或修改 `app/src/main/res/menu/menu_main.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<menu xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto">

    <item
        android:id="@+id/action_search"
        android:icon="@android:drawable/ic_menu_search"
        android:title="搜索"
        app:showAsAction="ifRoom" />

</menu>
```

- [ ] **Step 6: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/MainActivity.java app/src/main/res/menu/menu_main.xml && git commit -m "feat(search): 在 MainActivity 集成搜索 UI"
```

---

## Task 10: 集成索引构建时机（笔记 CRUD 时）

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 在保存笔记的方法中添加索引调用**

找到保存笔记的方法（如 `saveNote` 或类似方法），在保存成功后添加:

```java
// 笔记保存成功后，更新索引
long noteId = /* 获取保存的笔记ID */;
String content = /* 获取笔记内容 */;
long timestamp = /* 获取时间戳 */;
SearchManager.getInstance(this).indexNote(noteId, content, timestamp);
```

- [ ] **Step 2: 在删除笔记的方法中添加索引删除调用**

找到删除笔记的方法，在删除成功后添加:

```java
// 笔记删除后，从索引中移除
SearchManager.getInstance(this).deleteNoteIndex(noteId);
```

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/MainActivity.java && git commit -m "feat(search): 集成笔记 CRUD 与索引同步"
```

---

## Task 11: 创建 SearchResultAdapter 搜索结果适配器

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/search/SearchResultAdapter.java`

- [ ] **Step 1: 创建 SearchResultAdapter 类**

```java
package person.notfresh.noteplus.search;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import person.notfresh.noteplus.R;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 搜索结果适配器
 */
public class SearchResultAdapter extends ArrayAdapter<SearchResult> {
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public SearchResultAdapter(@NonNull Context context, @NonNull List<SearchResult> objects) {
        super(context, R.layout.item_search_result, objects);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_search_result, parent, false);
        }

        SearchResult result = getItem(position);
        if (result != null) {
            TextView contentView = convertView.findViewById(R.id.search_result_content);
            TextView timeView = convertView.findViewById(R.id.search_result_time);

            contentView.setText(result.getHighlightedContent());
            timeView.setText(DATE_FORMAT.format(new Date(result.getNote().getTimestamp())));
        }

        return convertView;
    }
}
```

- [ ] **Step 2: 创建 item_search_result.xml 布局文件**

创建 `app/src/main/res/layout/item_search_result.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/search_result_content"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="14sp"
        android:textColor="@android:color/black"
        android:maxLines="3"
        android:ellipsize="end" />

    <TextView
        android:id="@+id/search_result_time"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textSize="12sp"
        android:textColor="@android:color/darker_gray" />

</LinearLayout>
```

- [ ] **Step 3: 编译验证**

Run: `cd app && ../gradlew assembleDebug 2>&1 | tail -5`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/search/SearchResultAdapter.java app/src/main/res/layout/item_search_result.xml && git commit -m "feat(search): 创建搜索结果列表适配器和布局"
```

---

## Task 12: 完整功能验证

- [ ] **Step 1: 编译整个项目**

Run: `cd app && ../gradlew assembleDebug 2>&1 | tail -10`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: 安装到设备并测试**

手动测试:
1. 打开应用，确保搜索图标出现在 Toolbar
2. 点击搜索图标，进入搜索模式
3. 输入搜索词，验证分词和搜索结果
4. 创建新笔记，验证是否自动索引
5. 删除笔记，验证索引是否同步删除
6. 查看日志确认索引构建和搜索流程正常

- [ ] **Step 3: 提交最终代码**

```bash
git add -A && git commit -m "feat: 完成全文检索功能

- Lucene + IK Analyzer 中文分词搜索
- search_index_status 表跟踪索引状态
- Toolbar 搜索入口和搜索结果列表
- 笔记 CRUD 时自动同步索引
- 后台 WorkManager 批量构建未索引笔记

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 实施检查清单

| 任务 | 状态 | 说明 |
|------|------|------|
| Task 1: 添加依赖 | - | Lucene + IK Analyzer |
| Task 2: 创建数据库表 | - | search_index_status |
| Task 3: SearchResult 模型 | - | |
| Task 4: NoteIndexer | - | 索引构建 |
| Task 5: SearchService | - | 搜索服务 |
| Task 6: SearchManager | - | 单例管理 |
| Task 7: SearchIndexDbHelper | - | DB 辅助类 |
| Task 8: SearchIndexInitWorker | - | 后台任务 |
| Task 9: MainActivity 搜索 UI | - | |
| Task 10: 集成索引同步 | - | |
| Task 11: SearchResultAdapter | - | 结果列表 |
| Task 12: 完整验证 | - | |

---

**状态**: 待实现

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
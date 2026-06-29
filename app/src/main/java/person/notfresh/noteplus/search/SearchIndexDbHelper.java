package person.notfresh.noteplus.search;

import android.content.Context;
import android.database.Cursor;

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
package person.notfresh.noteplus.util;

import android.database.Cursor;
import person.notfresh.noteplus.core.model.Note;
import person.notfresh.noteplus.db.NoteDbHelper;

import java.util.HashMap;
import java.util.Map;

/**
 * Cursor 包装器，实现懒加载和缓存
 * 在 getView() 时才从 Cursor 创建 Note 对象，避免一次性加载所有数据
 */
public class NoteCursorWrapper {
    private final Cursor cursor;
    private final String projectName;
    private final int idIndex, contentIndex, timestampIndex, costIndex, pinnedIndex;
    
    // 缓存已创建的 Note 对象（按 position 缓存）
    private final Map<Integer, Note> noteCache = new HashMap<>();
    
    public NoteCursorWrapper(Cursor cursor, String projectName) {
        this.cursor = cursor;
        this.projectName = projectName;
        // 缓存列索引，避免重复查找
        idIndex = cursor.getColumnIndexOrThrow("_id");
        contentIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT);
        timestampIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP);
        costIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST);
        pinnedIndex = cursor.getColumnIndex(NoteDbHelper.COLUMN_IS_PINNED);
    }
    
    /**
     * 获取指定位置的 Note 对象（懒加载 + 缓存）
     * @param position 位置索引
     * @return Note 对象
     */
    public Note getNote(int position) {
        // 先检查缓存
        if (noteCache.containsKey(position)) {
            return noteCache.get(position);
        }
        
        // 从 Cursor 读取数据（按需加载）
        cursor.moveToPosition(position);
        Note note = new Note(
            cursor.getLong(idIndex),
            cursor.getString(contentIndex),
            cursor.getLong(timestampIndex),
            cursor.getDouble(costIndex),
            pinnedIndex >= 0 && cursor.getInt(pinnedIndex) == 1,
            projectName
        );
        
        // 缓存 Note 对象（避免重复创建）
        noteCache.put(position, note);
        return note;
    }
    
    /**
     * 获取总数
     */
    public int getCount() {
        return cursor.getCount();
    }
    
    /**
     * 清空缓存
     */
    public void clearCache() {
        noteCache.clear();
    }
    
    /**
     * 关闭 Cursor（需要时调用）
     */
    public void close() {
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
        noteCache.clear();
    }
}


package person.notfresh.noteplus.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

public class NoteDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notes.db";
    private static final int DATABASE_VERSION = 6;

    public static final String TABLE_NOTES = "notes";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_COST = "cost";

    public static final String TABLE_TAGS = "tags";
    public static final String TABLE_TIME_RANGES = "time_ranges";
    public static final String TABLE_NOTE_TAGS = "note_tags";
    public static final String TABLE_SETTINGS = "settings";
    public static final String TABLE_NOTE_COMMENTS = "note_comments";

    public static final String COLUMN_TAG_ID = "tag_id";
    public static final String COLUMN_TAG_NAME = "tag_name";
    public static final String COLUMN_TAG_COLOR = "tag_color";

    public static final String COLUMN_RANGE_ID = "range_id";
    public static final String COLUMN_NOTE_ID = "note_id";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";

    public static final String COLUMN_RECORD_ID = "record_id";
    public static final String COLUMN_SETTING_KEY = "key";
    public static final String COLUMN_SETTING_VALUE = "value";
    
    // 评论表字段
    public static final String COLUMN_COMMENT_ID = "comment_id";
    public static final String COLUMN_COMMENT_NOTE_ID = "note_id";
    public static final String COLUMN_PARENT_COMMENT_ID = "parent_comment_id";
    public static final String COLUMN_COMMENT_CONTENT = "content";
    public static final String COLUMN_COMMENT_TIMESTAMP = "timestamp";
    public static final String COLUMN_COMMENT_COST = "cost";

    public static final String KEY_TIME_RANGE_REQUIRED = "time_range_required";
    public static final String KEY_COST_DISPLAY = "cost_display";
    public static final String KEY_COST_REQUIRED = "cost_required";
    public static final String KEY_TIME_DESC_ORDER = "time_desc_order";
    public static final String KEY_FOLD_DISPLAY_LENGTH = "fold_display_length";

    private static final String DATABASE_CREATE = "create table "
            + TABLE_NOTES + "(" 
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_CONTENT + " text not null, "
            + COLUMN_TIMESTAMP + " integer not null, "
            + COLUMN_COST + " real default 0);";

    public NoteDbHelper(Context context, String databaseName) {
        super(context, databaseName, null, DATABASE_VERSION);
    }

    public NoteDbHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);

        String CREATE_TAGS_TABLE = "CREATE TABLE " + TABLE_TAGS + "("
                + COLUMN_TAG_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_TAG_NAME + " TEXT NOT NULL UNIQUE,"
                + COLUMN_TAG_COLOR + " TEXT DEFAULT '#CCCCCC'"
                + ")";
                
        String CREATE_TIME_RANGES_TABLE = "CREATE TABLE " + TABLE_TIME_RANGES + "("
                + COLUMN_RANGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_NOTE_ID + " INTEGER NOT NULL,"
                + COLUMN_START_TIME + " INTEGER NOT NULL,"
                + COLUMN_END_TIME + " INTEGER NOT NULL,"
                + "FOREIGN KEY (" + COLUMN_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE"
                + ")";
                
        String CREATE_NOTE_TAGS_TABLE = "CREATE TABLE " + TABLE_NOTE_TAGS + "("
                + COLUMN_RECORD_ID + " INTEGER NOT NULL,"
                + COLUMN_TAG_ID + " INTEGER NOT NULL,"
                + "PRIMARY KEY (" + COLUMN_RECORD_ID + ", " + COLUMN_TAG_ID + "),"
                + "FOREIGN KEY (" + COLUMN_RECORD_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY (" + COLUMN_TAG_ID + ") REFERENCES " + TABLE_TAGS + "(" + COLUMN_TAG_ID + ") ON DELETE CASCADE"
                + ")";
        
        String CREATE_SETTINGS_TABLE = "CREATE TABLE " + TABLE_SETTINGS + "("
                + COLUMN_SETTING_KEY + " TEXT PRIMARY KEY,"
                + COLUMN_SETTING_VALUE + " TEXT NOT NULL"
                + ")";
        
        database.execSQL(CREATE_TAGS_TABLE);
        database.execSQL(CREATE_TIME_RANGES_TABLE);
        database.execSQL(CREATE_NOTE_TAGS_TABLE);
        database.execSQL(CREATE_SETTINGS_TABLE);
        
        ContentValues defaultSettings = new ContentValues();
        defaultSettings.put(COLUMN_SETTING_KEY, KEY_TIME_RANGE_REQUIRED);
        defaultSettings.put(COLUMN_SETTING_VALUE, "false");
        database.insert(TABLE_SETTINGS, null, defaultSettings);
        
        ContentValues costDisplaySettings = new ContentValues();
        costDisplaySettings.put(COLUMN_SETTING_KEY, KEY_COST_DISPLAY);
        costDisplaySettings.put(COLUMN_SETTING_VALUE, "true");
        database.insert(TABLE_SETTINGS, null, costDisplaySettings);
        
        ContentValues costRequiredSettings = new ContentValues();
        costRequiredSettings.put(COLUMN_SETTING_KEY, KEY_COST_REQUIRED);
        costRequiredSettings.put(COLUMN_SETTING_VALUE, "false");
        database.insert(TABLE_SETTINGS, null, costRequiredSettings);
        
        ContentValues timeDescOrderSettings = new ContentValues();
        timeDescOrderSettings.put(COLUMN_SETTING_KEY, KEY_TIME_DESC_ORDER);
        timeDescOrderSettings.put(COLUMN_SETTING_VALUE, "true");
        database.insert(TABLE_SETTINGS, null, timeDescOrderSettings);
        
        ContentValues foldDisplayLengthSettings = new ContentValues();
        foldDisplayLengthSettings.put(COLUMN_SETTING_KEY, KEY_FOLD_DISPLAY_LENGTH);
        foldDisplayLengthSettings.put(COLUMN_SETTING_VALUE, "300");
        database.insert(TABLE_SETTINGS, null, foldDisplayLengthSettings);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            String CREATE_SETTINGS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_SETTINGS + "("
                    + COLUMN_SETTING_KEY + " TEXT PRIMARY KEY,"
                    + COLUMN_SETTING_VALUE + " TEXT NOT NULL"
                    + ")";
            db.execSQL(CREATE_SETTINGS_TABLE);
            
            ContentValues defaultSettings = new ContentValues();
            defaultSettings.put(COLUMN_SETTING_KEY, KEY_TIME_RANGE_REQUIRED);
            defaultSettings.put(COLUMN_SETTING_VALUE, "false");
            
            try {
                db.insert(TABLE_SETTINGS, null, defaultSettings);
            } catch (Exception e) {
                // 如果插入失败(比如记录已存在)，不处理异常
            }
        }
        
        if (oldVersion < 4) {
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COLUMN_COST + " REAL DEFAULT 0");
            } catch (Exception e) {
                // 如果字段已存在，忽略错误
            }
            
            try {
                ContentValues costDisplaySettings = new ContentValues();
                costDisplaySettings.put(COLUMN_SETTING_KEY, KEY_COST_DISPLAY);
                costDisplaySettings.put(COLUMN_SETTING_VALUE, "true");
                db.insert(TABLE_SETTINGS, null, costDisplaySettings);
                
                ContentValues costRequiredSettings = new ContentValues();
                costRequiredSettings.put(COLUMN_SETTING_KEY, KEY_COST_REQUIRED);
                costRequiredSettings.put(COLUMN_SETTING_VALUE, "false");
                db.insert(TABLE_SETTINGS, null, costRequiredSettings);
            } catch (Exception e) {
                // 忽略可能的重复插入错误
            }
        }
        
        if (oldVersion < 5) {
            try {
                ContentValues foldDisplayLengthSettings = new ContentValues();
                foldDisplayLengthSettings.put(COLUMN_SETTING_KEY, KEY_FOLD_DISPLAY_LENGTH);
                foldDisplayLengthSettings.put(COLUMN_SETTING_VALUE, "300");
                db.insert(TABLE_SETTINGS, null, foldDisplayLengthSettings);
            } catch (Exception e) {
                // 忽略可能的重复插入错误
            }
        }
        
        if (oldVersion < 6) {
            // 创建评论表
            String CREATE_NOTE_COMMENTS_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NOTE_COMMENTS + "("
                    + COLUMN_COMMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_COMMENT_NOTE_ID + " INTEGER NOT NULL,"
                    + COLUMN_PARENT_COMMENT_ID + " INTEGER DEFAULT NULL,"
                    + COLUMN_COMMENT_CONTENT + " TEXT NOT NULL,"
                    + COLUMN_COMMENT_TIMESTAMP + " INTEGER NOT NULL,"
                    + COLUMN_COMMENT_COST + " REAL DEFAULT 0,"
                    + "FOREIGN KEY (" + COLUMN_COMMENT_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE,"
                    + "FOREIGN KEY (" + COLUMN_PARENT_COMMENT_ID + ") REFERENCES " + TABLE_NOTE_COMMENTS + "(" + COLUMN_COMMENT_ID + ") ON DELETE CASCADE"
                    + ")";
            
            db.execSQL(CREATE_NOTE_COMMENTS_TABLE);
            
            // 创建索引
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_note_comments_note_id ON " 
                        + TABLE_NOTE_COMMENTS + "(" + COLUMN_COMMENT_NOTE_ID + ")");
                db.execSQL("CREATE INDEX IF NOT EXISTS idx_note_comments_timestamp ON " 
                        + TABLE_NOTE_COMMENTS + "(" + COLUMN_COMMENT_TIMESTAMP + ")");
            } catch (Exception e) {
                // 忽略索引已存在的错误
            }
        }
    }

    public Cursor getAllTags() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.rawQuery("SELECT " + 
                          COLUMN_TAG_ID + " AS _id, " +
                          COLUMN_TAG_NAME + ", " + 
                          COLUMN_TAG_COLOR + 
                          " FROM " + TABLE_TAGS, null);
    }

    public long addTag(String tagName, String tagColor) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_TAG_NAME, tagName);
        values.put(COLUMN_TAG_COLOR, tagColor);
        
        return db.insert(TABLE_TAGS, null, values);
    }

    public long linkNoteToTag(long noteId, long tagId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_RECORD_ID, noteId);
        values.put(COLUMN_TAG_ID, tagId);
        
        return db.insert(TABLE_NOTE_TAGS, null, values);
    }

    public long saveTimeRange(long noteId, long startTime, long endTime) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_NOTE_ID, noteId);
        values.put(COLUMN_START_TIME, startTime);
        values.put(COLUMN_END_TIME, endTime);
        
        return db.insert(TABLE_TIME_RANGES, null, values);
    }

    public Cursor getTagsForNote(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT t." + COLUMN_TAG_ID + " AS _id, t." + COLUMN_TAG_NAME + ", t." + COLUMN_TAG_COLOR 
                + " FROM " + TABLE_TAGS + " t"
                + " INNER JOIN " + TABLE_NOTE_TAGS + " nt ON t." + COLUMN_TAG_ID + " = nt." + COLUMN_TAG_ID
                + " WHERE nt." + COLUMN_RECORD_ID + " = ?";
        
        return db.rawQuery(query, new String[]{String.valueOf(noteId)});
    }

    public Cursor getTimeRangesForNote(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(TABLE_TIME_RANGES,
                new String[]{COLUMN_RANGE_ID, COLUMN_START_TIME, COLUMN_END_TIME},
                COLUMN_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)},
                null, null, null);
    }

    /**
     * 获取所有记录
     */
    public Cursor getAllMoments() {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_NOTES,
                new String[]{"_id", COLUMN_CONTENT, COLUMN_TIMESTAMP},
                null, null, null, null,
                COLUMN_TIMESTAMP + " DESC"
        );
    }

    /**
     * 获取设置值
     */
    public String getSetting(String key, String defaultValue) {
        SQLiteDatabase db = this.getReadableDatabase();
        String value = defaultValue;
        
        Cursor cursor = db.query(
                TABLE_SETTINGS,
                new String[]{COLUMN_SETTING_VALUE},
                COLUMN_SETTING_KEY + "=?",
                new String[]{key},
                null, null, null);
        
        if (cursor.moveToFirst()) {
            value = cursor.getString(0);
        }
        cursor.close();
        
        return value;
    }

    /**
     * 保存设置值
     */
    public void saveSetting(String key, String value) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        ContentValues values = new ContentValues();
        values.put(COLUMN_SETTING_VALUE, value);
        
        int rows = db.update(
                TABLE_SETTINGS,
                values,
                COLUMN_SETTING_KEY + "=?",
                new String[]{key});
        
        if (rows == 0) {
            values.put(COLUMN_SETTING_KEY, key);
            db.insert(TABLE_SETTINGS, null, values);
        }
    }

    /**
     * 根据标签名称获取标签ID
     */
    public long getTagIdByName(String tagName) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_TAGS,
                new String[]{COLUMN_TAG_ID},
                COLUMN_TAG_NAME + "=?",
                new String[]{tagName},
                null, null, null);
        
        long tagId = -1;
        if (cursor.moveToFirst()) {
            tagId = cursor.getLong(0);
        }
        cursor.close();
        
        return tagId;
    }
    
    /**
     * 添加评论（追加内容）
     * @param noteId 笔记ID
     * @param parentCommentId 父评论ID（null表示直接回复笔记，非null表示回复某个评论）
     * @param content 评论内容
     * @param cost 花费金额（可选）
     * @return 评论ID，失败返回-1
     */
    public long addComment(long noteId, Long parentCommentId, String content, double cost) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_COMMENT_NOTE_ID, noteId);
        if (parentCommentId != null) {
            values.put(COLUMN_PARENT_COMMENT_ID, parentCommentId);
        }
        values.put(COLUMN_COMMENT_CONTENT, content);
        values.put(COLUMN_COMMENT_TIMESTAMP, System.currentTimeMillis());
        values.put(COLUMN_COMMENT_COST, cost);
        
        return db.insert(TABLE_NOTE_COMMENTS, null, values);
    }
    
    /**
     * 获取笔记的所有评论（包括回复，按时间正序）
     * @param noteId 笔记ID
     * @return Cursor
     */
    public Cursor getCommentsForNote(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        return db.query(
                TABLE_NOTE_COMMENTS,
                new String[]{
                    COLUMN_COMMENT_ID,
                    COLUMN_PARENT_COMMENT_ID,
                    COLUMN_COMMENT_CONTENT,
                    COLUMN_COMMENT_TIMESTAMP,
                    COLUMN_COMMENT_COST
                },
                COLUMN_COMMENT_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)},
                null, null,
                COLUMN_COMMENT_TIMESTAMP + " ASC"  // 按时间正序
        );
    }
    
    /**
     * 获取笔记的评论数量
     * @param noteId 笔记ID
     * @return 数量
     */
    public int getCommentCount(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_NOTE_COMMENTS,
                new String[]{"COUNT(*) as count"},
                COLUMN_COMMENT_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)},
                null, null, null
        );
        
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        return count;
    }
    
    /**
     * 删除评论
     * @param commentId 评论ID
     * @return 删除的行数
     */
    public int deleteComment(long commentId) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(
                TABLE_NOTE_COMMENTS,
                COLUMN_COMMENT_ID + " = ?",
                new String[]{String.valueOf(commentId)}
        );
    }
    
    /**
     * 更新评论内容
     * @param commentId 评论ID
     * @param content 新内容
     * @return 更新的行数
     */
    public int updateComment(long commentId, String content) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_COMMENT_CONTENT, content);
        
        return db.update(
                TABLE_NOTE_COMMENTS,
                values,
                COLUMN_COMMENT_ID + " = ?",
                new String[]{String.valueOf(commentId)}
        );
    }
    
    /**
     * 获取下一个评论编号（用于显示，只计算直接回复笔记的评论）
     * @param noteId 笔记ID
     * @return 下一个编号（从1开始）
     */
    public int getNextCommentNumber(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        // 只统计parent_comment_id为NULL的评论（直接回复笔记的）
        Cursor cursor = db.query(
                TABLE_NOTE_COMMENTS,
                new String[]{"COUNT(*) as count"},
                COLUMN_COMMENT_NOTE_ID + " = ? AND " + COLUMN_PARENT_COMMENT_ID + " IS NULL",
                new String[]{String.valueOf(noteId)},
                null, null, null
        );
        
        int count = 0;
        if (cursor != null && cursor.moveToFirst()) {
            count = cursor.getInt(0);
            cursor.close();
        }
        return count + 1; // 下一个编号
    }
    
    /**
     * 根据评论ID获取评论编号（用于显示）
     * 所有评论（包括回复）都按时间顺序编号，从1开始
     * @param commentId 评论ID
     * @return 评论编号，如果找不到返回-1
     */
    public int getCommentNumber(long commentId) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 先获取评论信息
        Cursor commentCursor = db.query(
                TABLE_NOTE_COMMENTS,
                new String[]{COLUMN_COMMENT_NOTE_ID, COLUMN_COMMENT_TIMESTAMP},
                COLUMN_COMMENT_ID + " = ?",
                new String[]{String.valueOf(commentId)},
                null, null, null
        );
        
        if (commentCursor == null || !commentCursor.moveToFirst()) {
            if (commentCursor != null) {
                commentCursor.close();
            }
            return -1;
        }
        
        long noteId = commentCursor.getLong(commentCursor.getColumnIndexOrThrow(COLUMN_COMMENT_NOTE_ID));
        long timestamp = commentCursor.getLong(commentCursor.getColumnIndexOrThrow(COLUMN_COMMENT_TIMESTAMP));
        commentCursor.close();
        
        // 计算编号：统计该笔记中，时间戳小于等于当前评论的所有评论数量（包括回复）
        Cursor countCursor = db.query(
                TABLE_NOTE_COMMENTS,
                new String[]{"COUNT(*) as count"},
                COLUMN_COMMENT_NOTE_ID + " = ? AND " + COLUMN_COMMENT_TIMESTAMP + " <= ?",
                new String[]{String.valueOf(noteId), String.valueOf(timestamp)},
                null, null, null
        );
        
        int number = 0;
        if (countCursor != null && countCursor.moveToFirst()) {
            number = countCursor.getInt(0);
            countCursor.close();
        }
        
        return number;
    }
} 
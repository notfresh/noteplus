package person.notfresh.noteplus.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

public class NoteDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notes.db";
    private static final int DATABASE_VERSION = 9;

    public static final String TABLE_NOTES = "notes";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_COST = "cost";
    public static final String COLUMN_IS_PINNED = "is_pinned";
    public static final String COLUMN_IS_ARCHIVED = "is_archived";
    public static final String COLUMN_ARCHIVED_AT = "archived_at";

    public static final String TABLE_TAGS = "tags";
    public static final String TABLE_TIME_RANGES = "time_ranges";
    public static final String TABLE_NOTE_TAGS = "note_tags";
    public static final String TABLE_SETTINGS = "settings";
    public static final String TABLE_NOTE_COMMENTS = "note_comments";
    public static final String TABLE_NOTE_IMAGES = "note_images";

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

    public static final String COLUMN_IMAGE_ID = "image_id";
    public static final String COLUMN_IMAGE_NOTE_ID = "note_id";
    public static final String COLUMN_IMAGE_PATH = "path";

    public static final String KEY_TIME_RANGE_REQUIRED = "time_range_required";
    public static final String KEY_TIME_RANGE_DISPLAY = "time_range_display";
    public static final String KEY_COST_DISPLAY = "cost_display";
    public static final String KEY_COST_REQUIRED = "cost_required";
    public static final String KEY_TIME_DESC_ORDER = "time_desc_order";
    public static final String KEY_FOLD_DISPLAY_LENGTH = "fold_display_length";

    private static final String DATABASE_CREATE = "create table "
            + TABLE_NOTES + "(" 
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_CONTENT + " text not null, "
            + COLUMN_TIMESTAMP + " integer not null, "
            + COLUMN_COST + " real default 0, "
            + COLUMN_IS_PINNED + " integer default 0, "
            + COLUMN_IS_ARCHIVED + " integer default 0, "
            + COLUMN_ARCHIVED_AT + " integer default 0);";

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
        
        String CREATE_NOTE_COMMENTS_TABLE = "CREATE TABLE " + TABLE_NOTE_COMMENTS + "("
                + COLUMN_COMMENT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                + COLUMN_COMMENT_NOTE_ID + " INTEGER NOT NULL,"
                + COLUMN_PARENT_COMMENT_ID + " INTEGER DEFAULT NULL,"
                + COLUMN_COMMENT_CONTENT + " TEXT NOT NULL,"
                + COLUMN_COMMENT_TIMESTAMP + " INTEGER NOT NULL,"
                + COLUMN_COMMENT_COST + " REAL DEFAULT 0,"
                + "FOREIGN KEY (" + COLUMN_COMMENT_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE,"
                + "FOREIGN KEY (" + COLUMN_PARENT_COMMENT_ID + ") REFERENCES " + TABLE_NOTE_COMMENTS + "(" + COLUMN_COMMENT_ID + ") ON DELETE CASCADE"
                + ")";

        String CREATE_NOTE_IMAGES_TABLE = "CREATE TABLE " + TABLE_NOTE_IMAGES + "("
            + COLUMN_IMAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
            + COLUMN_IMAGE_NOTE_ID + " INTEGER NOT NULL,"
            + COLUMN_IMAGE_PATH + " TEXT NOT NULL,"
            + "FOREIGN KEY (" + COLUMN_IMAGE_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE"
            + ")";
        
        database.execSQL(CREATE_TAGS_TABLE);
        database.execSQL(CREATE_TIME_RANGES_TABLE);
        database.execSQL(CREATE_NOTE_TAGS_TABLE);
        database.execSQL(CREATE_SETTINGS_TABLE);
        database.execSQL(CREATE_NOTE_COMMENTS_TABLE);
        database.execSQL(CREATE_NOTE_IMAGES_TABLE);
        
        // 创建索引
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_note_comments_note_id ON " 
                + TABLE_NOTE_COMMENTS + "(" + COLUMN_COMMENT_NOTE_ID + ")");
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_note_comments_timestamp ON " 
                + TABLE_NOTE_COMMENTS + "(" + COLUMN_COMMENT_TIMESTAMP + ")");
        
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
        
        if (oldVersion < 7) {
            // 添加置顶字段
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COLUMN_IS_PINNED + " INTEGER DEFAULT 0");
            } catch (Exception e) {
                // 如果字段已存在，忽略错误
            }
        }

        if (oldVersion < 8) {
            // 添加归档字段
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COLUMN_IS_ARCHIVED + " INTEGER DEFAULT 0");
            } catch (Exception e) {
                // 如果字段已存在，忽略错误
            }
            try {
                db.execSQL("ALTER TABLE " + TABLE_NOTES + " ADD COLUMN " + COLUMN_ARCHIVED_AT + " INTEGER DEFAULT 0");
            } catch (Exception e) {
                // 如果字段已存在，忽略错误
            }
        }

        if (oldVersion < 9) {
            // 创建笔记图片表
            String CREATE_NOTE_IMAGES_TABLE = "CREATE TABLE IF NOT EXISTS " + TABLE_NOTE_IMAGES + "("
                    + COLUMN_IMAGE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + COLUMN_IMAGE_NOTE_ID + " INTEGER NOT NULL,"
                    + COLUMN_IMAGE_PATH + " TEXT NOT NULL,"
                    + "FOREIGN KEY (" + COLUMN_IMAGE_NOTE_ID + ") REFERENCES " + TABLE_NOTES + "(" + COLUMN_ID + ") ON DELETE CASCADE"
                    + ")";
            db.execSQL(CREATE_NOTE_IMAGES_TABLE);
        }
    }

    /**
     * 插入笔记图片路径
     */
    public long insertNoteImage(long noteId, String imagePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IMAGE_NOTE_ID, noteId);
        values.put(COLUMN_IMAGE_PATH, imagePath);
        return db.insert(TABLE_NOTE_IMAGES, null, values);
    }

    /**
     * 获取笔记图片路径列表
     */
    public java.util.List<String> getNoteImagePaths(long noteId) {
        java.util.List<String> paths = new java.util.ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_NOTE_IMAGES,
                new String[]{COLUMN_IMAGE_PATH},
                COLUMN_IMAGE_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)},
                null, null, null
        );

        if (cursor != null) {
            while (cursor.moveToNext()) {
                paths.add(cursor.getString(0));
            }
            cursor.close();
        }
        return paths;
    }

    /**
     * 删除笔记的图片路径记录
     */
    public void deleteNoteImages(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(
                TABLE_NOTE_IMAGES,
                COLUMN_IMAGE_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)}
        );
    }

    /**
     * 删除单张笔记图片路径记录
     */
    public int deleteNoteImage(long noteId, String imagePath) {
        SQLiteDatabase db = this.getWritableDatabase();
        return db.delete(
                TABLE_NOTE_IMAGES,
                COLUMN_IMAGE_NOTE_ID + " = ? AND " + COLUMN_IMAGE_PATH + " = ?",
                new String[]{String.valueOf(noteId), imagePath}
        );
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
     * 根据评论ID获取评论内容
     * @param commentId 评论ID
     * @return 评论内容字符串，如果获取失败返回null
     */
    public String getCommentContentById(long commentId) {
        SQLiteDatabase db = getReadableDatabase();
        String content = null;
        
        Cursor cursor = db.query(
            NoteDbHelper.TABLE_NOTE_COMMENTS,
            new String[]{NoteDbHelper.COLUMN_COMMENT_CONTENT},
            NoteDbHelper.COLUMN_COMMENT_ID + "=?",
            new String[]{String.valueOf(commentId)},
            null, null, null
        );
        
        if (cursor != null && cursor.moveToFirst()) {
            int contentIndex = cursor.getColumnIndex(NoteDbHelper.COLUMN_COMMENT_CONTENT);
            if (contentIndex != -1) {
                content = cursor.getString(contentIndex);
            }
            cursor.close();
        }
        
        return content;
    }
    
    /**
     * 切换笔记的置顶状态
     * @param noteId 笔记ID
     * @return 是否成功
     */
    public boolean togglePinNote(long noteId) {
        SQLiteDatabase db = getWritableDatabase();
        
        // 先获取当前置顶状态
        Cursor cursor = db.query(
            TABLE_NOTES,
            new String[]{COLUMN_IS_PINNED},
            COLUMN_ID + "=?",
            new String[]{String.valueOf(noteId)},
            null, null, null
        );
        
        int currentPinned = 0;
        if (cursor != null && cursor.moveToFirst()) {
            int pinnedIndex = cursor.getColumnIndex(COLUMN_IS_PINNED);
            if (pinnedIndex != -1) {
                currentPinned = cursor.getInt(pinnedIndex);
            }
            cursor.close();
        }
        
        // 切换置顶状态（0 -> 1, 1 -> 0）
        int newPinned = (currentPinned == 1) ? 0 : 1;
        
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_PINNED, newPinned);
        
        int rowsAffected = db.update(
            TABLE_NOTES,
            values,
            COLUMN_ID + "=?",
            new String[]{String.valueOf(noteId)}
        );
        
        return rowsAffected > 0;
    }
    
    /**
     * 获取笔记的置顶状态
     * @param noteId 笔记ID
     * @return true表示置顶，false表示未置顶
     */
    public boolean isNotePinned(long noteId) {
        SQLiteDatabase db = getReadableDatabase();
        
        Cursor cursor = db.query(
            TABLE_NOTES,
            new String[]{COLUMN_IS_PINNED},
            COLUMN_ID + "=?",
            new String[]{String.valueOf(noteId)},
            null, null, null
        );
        
        boolean pinned = false;
        if (cursor != null && cursor.moveToFirst()) {
            int pinnedIndex = cursor.getColumnIndex(COLUMN_IS_PINNED);
            if (pinnedIndex != -1) {
                pinned = cursor.getInt(pinnedIndex) == 1;
            }
            cursor.close();
        }
        
        return pinned;
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

    /**
     * 加载笔记数据
     * 根据排序方式查询笔记，置顶的记录排在最前面
     * 
     * @param timeDescOrder true表示时间降序，false表示时间升序
     * @return 包含笔记数据的Cursor，字段包括：_id, content, timestamp, cost, is_pinned
     */
    public Cursor loadNotes(boolean timeDescOrder) {
        SQLiteDatabase db = this.getReadableDatabase();
        
        // 根据设置决定排序方式，置顶的记录排在最前面
        String orderBy = NoteDbHelper.COLUMN_IS_PINNED + " DESC, " + 
                (timeDescOrder ? 
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC" : 
                NoteDbHelper.COLUMN_TIMESTAMP + " ASC");
        
        Cursor cursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{
                    "_id", 
                    NoteDbHelper.COLUMN_CONTENT, 
                    NoteDbHelper.COLUMN_TIMESTAMP, 
                    NoteDbHelper.COLUMN_COST, 
                    NoteDbHelper.COLUMN_IS_PINNED
                },
                NoteDbHelper.COLUMN_IS_ARCHIVED + " = 0", null, null, null,
                orderBy
        );
        
        return cursor;
    }

    /**
     * 加载归档笔记数据（按归档时间倒序）
     * 
     * @return 包含归档笔记数据的Cursor
     */
    public Cursor loadArchivedNotes() {
        return loadArchivedNotes(true);
    }

    /**
     * 加载归档笔记数据
     * 
     * @param archivedTimeDesc true表示按归档时间倒序，false表示正序
     * @return 包含归档笔记数据的Cursor
     */
    public Cursor loadArchivedNotes(boolean archivedTimeDesc) {
        SQLiteDatabase db = this.getReadableDatabase();
        String orderBy = NoteDbHelper.COLUMN_ARCHIVED_AT + (archivedTimeDesc ? " DESC" : " ASC");
        return db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{
                    "_id",
                    NoteDbHelper.COLUMN_CONTENT,
                    NoteDbHelper.COLUMN_TIMESTAMP,
                    NoteDbHelper.COLUMN_COST,
                    NoteDbHelper.COLUMN_IS_PINNED,
                    NoteDbHelper.COLUMN_ARCHIVED_AT
                },
                NoteDbHelper.COLUMN_IS_ARCHIVED + " = 1",
                null, null, null,
                orderBy
        );
    }

    /**
     * 归档笔记
     * @param noteId 笔记ID
     * @return 是否成功
     */
    public boolean archiveNote(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_ARCHIVED, 1);
        values.put(COLUMN_ARCHIVED_AT, System.currentTimeMillis());
        int rowsAffected = db.update(
                TABLE_NOTES,
                values,
                COLUMN_ID + "=?",
                new String[]{String.valueOf(noteId)}
        );
        return rowsAffected > 0;
    }

    /**
     * 还原归档笔记
     * @param noteId 笔记ID
     * @return 是否成功
     */
    public boolean restoreArchivedNote(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_IS_ARCHIVED, 0);
        values.put(COLUMN_ARCHIVED_AT, 0);
        int rowsAffected = db.update(
                TABLE_NOTES,
                values,
                COLUMN_ID + "=?",
                new String[]{String.valueOf(noteId)}
        );
        return rowsAffected > 0;
    }

    /**
     * 判断笔记是否已归档
     * @param noteId 笔记ID
     * @return true表示已归档，false表示未归档
     */
    public boolean isNoteArchived(long noteId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_NOTES,
                new String[]{COLUMN_IS_ARCHIVED},
                COLUMN_ID + "=?",
                new String[]{String.valueOf(noteId)},
                null, null, null
        );
        boolean archived = false;
        if (cursor != null && cursor.moveToFirst()) {
            int archivedIndex = cursor.getColumnIndex(COLUMN_IS_ARCHIVED);
            if (archivedIndex != -1) {
                archived = cursor.getInt(archivedIndex) == 1;
            }
            cursor.close();
        }
        return archived;
    }
    
    /**
     * 加载笔记数据（使用默认排序方式：时间降序）
     * 
     * @return 包含笔记数据的Cursor
     */
    public Cursor loadNotes() {
        return loadNotes(true);
    }
    
    /**
     * 获取笔记的所有评论ID列表
     * @param noteId 笔记ID
     * @return 评论ID集合
     */
    public java.util.Set<Long> getCommentIdsForNote(long noteId) {
        java.util.Set<Long> commentIds = new java.util.HashSet<>();
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(
                TABLE_NOTE_COMMENTS,
                new String[]{COLUMN_COMMENT_ID},
                COLUMN_COMMENT_NOTE_ID + " = ?",
                new String[]{String.valueOf(noteId)},
                null, null, null
        );
        
        if (cursor != null) {
            while (cursor.moveToNext()) {
                long commentId = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_COMMENT_ID));
                commentIds.add(commentId);
            }
            cursor.close();
        }
        
        return commentIds;
    }
    
    /**
     * 插入评论（支持指定时间戳，用于合并操作）
     * @param noteId 笔记ID
     * @param parentCommentId 父评论ID（可为null）
     * @param content 评论内容
     * @param timestamp 时间戳
     * @param cost 花费金额
     * @return 新插入的评论ID，失败返回-1
     */
    public long insertCommentWithTimestamp(long noteId, Long parentCommentId, String content, long timestamp, double cost) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(COLUMN_COMMENT_NOTE_ID, noteId);
        if (parentCommentId != null) {
            values.put(COLUMN_PARENT_COMMENT_ID, parentCommentId);
        }
        values.put(COLUMN_COMMENT_CONTENT, content);
        values.put(COLUMN_COMMENT_TIMESTAMP, timestamp);
        values.put(COLUMN_COMMENT_COST, cost);
        
        return db.insert(TABLE_NOTE_COMMENTS, null, values);
    }
    
    /**
     * 在事务中删除笔记及其所有关联数据
     * 注意：此方法不管理事务，调用者需要确保在事务中调用
     * @param db 数据库实例（必须在事务中）
     * @param noteId 笔记ID
     */
    public void deleteNoteInTransaction(SQLiteDatabase db, long noteId) {
        // 1. 删除相关的标签关联
        db.delete(
            TABLE_NOTE_TAGS,
            COLUMN_RECORD_ID + " = ?",
            new String[]{String.valueOf(noteId)}
        );
        
        // 2. 删除相关的时间范围
        db.delete(
            TABLE_TIME_RANGES,
            COLUMN_NOTE_ID + " = ?",
            new String[]{String.valueOf(noteId)}
        );
        
        // 3. 删除相关的评论（外键级联删除会自动处理，但显式删除更清晰）
        db.delete(
            TABLE_NOTE_COMMENTS,
            COLUMN_COMMENT_NOTE_ID + " = ?",
            new String[]{String.valueOf(noteId)}
        );

        // 4. 删除相关的图片路径记录
        db.delete(
            TABLE_NOTE_IMAGES,
            COLUMN_IMAGE_NOTE_ID + " = ?",
            new String[]{String.valueOf(noteId)}
        );
        
        // 5. 删除记录本身
        db.delete(
            TABLE_NOTES,
            COLUMN_ID + " = ?",
            new String[]{String.valueOf(noteId)}
        );
    }
    
    /**
     * 删除笔记及其所有关联数据（在事务中执行）
     * 此方法会处理事务管理
     * 
     * @param noteId 笔记ID
     * @return 删除的行数（主要是notes表的删除行数）
     */
    public int deleteNote(long noteId) {
        SQLiteDatabase db = this.getWritableDatabase();

        java.util.List<String> imagePaths = getNoteImagePaths(noteId);
        
        // 先检查笔记是否存在
        Cursor cursor = db.query(
            TABLE_NOTES,
            new String[]{COLUMN_ID},
            COLUMN_ID + " = ?",
            new String[]{String.valueOf(noteId)},
            null, null, null
        );
        
        boolean exists = cursor != null && cursor.getCount() > 0;
        if (cursor != null) {
            cursor.close();
        }
        
        if (!exists) {
            return 0;
        }
        
        int result = 0;

        // 开始事务
        db.beginTransaction();
        try {
            // 使用事务中的删除方法
            deleteNoteInTransaction(db, noteId);

            // 提交事务
            db.setTransactionSuccessful();

            result = 1;
        } catch (Exception e) {
            result = 0;
        } finally {
            // 结束事务
            db.endTransaction();
        }

        if (result > 0 && imagePaths != null && !imagePaths.isEmpty()) {
            for (String imagePath : imagePaths) {
                java.io.File imageFile = new java.io.File(imagePath);
                if (imageFile.exists()) {
                    // 忽略删除失败
                    imageFile.delete();
                }
            }
        }

        return result;
    }
    
    /**
     * 合并笔记：将源笔记及其所有评论合并到目标笔记中
     * 此方法会处理事务、ID映射等所有细节
     * 
     * @param sourceNote 源笔记对象（需要包含所有评论）
     * @param targetNote 目标笔记对象（需要包含所有评论）
     * @return true表示合并成功，false表示失败
     */
    public boolean mergeNotes(person.notfresh.noteplus.core.model.Note sourceNote, 
                              person.notfresh.noteplus.core.model.Note targetNote) {
        if (sourceNote == null || targetNote == null) {
            return false;
        }
        
        SQLiteDatabase db = this.getWritableDatabase();
        
        // 开始事务
        db.beginTransaction();
        try {
            // 1. 记录目标Note合并前的所有commentId（用于区分哪些是新合并进来的）
            java.util.Set<Long> originalCommentIds = getCommentIdsForNote(targetNote.getId());
            
            // 2. 执行合并（使用Note对象的mergeNoteAsComment方法）
            targetNote.mergeNoteAsComment(sourceNote);
            
            // 3. 保存合并后的新评论到数据库
            // 只保存那些不在originalCommentIds中的评论（即合并进来的新评论）
            java.util.Map<Long, Long> commentIdMap = new java.util.HashMap<>();
            
            // 先建立原有评论的ID映射（原有评论的ID保持不变）
            for (person.notfresh.noteplus.core.model.Comment comment : targetNote.getComments()) {
                if (originalCommentIds.contains(comment.getId())) {
                    commentIdMap.put(comment.getId(), comment.getId());
                }
            }
            
            // 然后插入合并进来的新评论，建立ID映射
            for (person.notfresh.noteplus.core.model.Comment comment : targetNote.getComments()) {
                // 跳过原有的评论
                if (originalCommentIds.contains(comment.getId())) {
                    continue;
                }
                
                // 处理parentCommentId的映射
                Long mappedParentId = null;
                if (comment.getParentCommentId() != null) {
                    mappedParentId = commentIdMap.get(comment.getParentCommentId());
                }
                
                // 插入新评论（使用支持指定时间戳的方法）
                long newCommentId = insertCommentWithTimestamp(
                    targetNote.getId(),
                    mappedParentId,
                    comment.getContent(),
                    comment.getTimestamp(),
                    comment.getCost()
                );
                
                if (newCommentId != -1) {
                    commentIdMap.put(comment.getId(), newCommentId);
                }
            }
            
            // 4. 删除源笔记（在事务中执行）
            deleteNoteInTransaction(db, sourceNote.getId());
            
            // 5. 提交事务
            db.setTransactionSuccessful();
            return true;
        } catch (Exception e) {
            // 事务失败，会自动回滚
            return false;
        } finally {
            // 结束事务
            db.endTransaction();
        }
    }
} 
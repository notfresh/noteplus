package person.notfresh.noteplus.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

public class NoteDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notes.db";
    private static final int DATABASE_VERSION = 5;

    public static final String TABLE_NOTES = "notes";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_TIMESTAMP = "timestamp";
    public static final String COLUMN_COST = "cost";

    public static final String TABLE_TAGS = "tags";
    public static final String TABLE_TIME_RANGES = "time_ranges";
    public static final String TABLE_NOTE_TAGS = "note_tags";
    public static final String TABLE_SETTINGS = "settings";

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
} 
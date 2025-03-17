package person.notfresh.noteplus.db;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.content.ContentValues;

public class NoteDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "notes.db";
    private static final int DATABASE_VERSION = 2;

    public static final String TABLE_NOTES = "notes";
    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_CONTENT = "content";
    public static final String COLUMN_TIMESTAMP = "timestamp";

    public static final String TABLE_TAGS = "tags";
    public static final String TABLE_TIME_RANGES = "time_ranges";
    public static final String TABLE_NOTE_TAGS = "note_tags";

    public static final String COLUMN_TAG_ID = "tag_id";
    public static final String COLUMN_TAG_NAME = "tag_name";
    public static final String COLUMN_TAG_COLOR = "tag_color";

    public static final String COLUMN_RANGE_ID = "range_id";
    public static final String COLUMN_NOTE_ID = "note_id";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";

    public static final String COLUMN_RECORD_ID = "record_id";

    private static final String DATABASE_CREATE = "create table "
            + TABLE_NOTES + "(" 
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_CONTENT + " text not null, "
            + COLUMN_TIMESTAMP + " integer not null);";

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
        
        database.execSQL(CREATE_TAGS_TABLE);
        database.execSQL(CREATE_TIME_RANGES_TABLE);
        database.execSQL(CREATE_NOTE_TAGS_TABLE);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTE_TAGS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TIME_RANGES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_TAGS);
        onCreate(db);
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
} 
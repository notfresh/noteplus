package person.notfresh.noteplus.core;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.core.model.Note;
import person.notfresh.noteplus.core.model.Comment;
import person.notfresh.noteplus.core.model.TimelineItemType;
import person.notfresh.noteplus.core.model.modelUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 笔记数据加载器 - 纯数据加载逻辑
 * 提供从数据库查询笔记数据的方法，不包含UI相关逻辑
 */
public class NoteDataLoader {
    
    private final SQLiteOpenHelper dbHelper;
    private String projectName;  // 项目名称
    
    /**
     * 构造函数
     * @param dbHelper 数据库帮助类实例
     */
    public NoteDataLoader(SQLiteOpenHelper dbHelper) {
        this.dbHelper = dbHelper;
    }
    
    /**
     * 构造函数（带项目名称）
     * @param dbHelper 数据库帮助类实例
     * @param projectName 项目名称
     */
    public NoteDataLoader(SQLiteOpenHelper dbHelper, String projectName) {
        this.dbHelper = dbHelper;
        this.projectName = projectName;
    }
    
    /**
     * 设置项目名称
     * @param projectName 项目名称
     */
    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }
    
    /**
     * 获取项目名称
     * @return 项目名称
     */
    public String getProjectName() {
        return projectName;
    }
    
    /**
     * 将笔记列表展平为评论列表（时间线视图）
     * Note 和 Comment 是平等的，Note 会被转换为特殊的 Comment（作为主题的第一个条目）
     * 所有条目按时间排序
     * 
     * @param timeRange 时间范围枚举：LAST_DAY（最近一天）、LAST_WEEK（最近一周）、LAST_MONTH（最近一个月）
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 展平后的评论列表，按时间排序
     */
    public List<Comment> loadTimelineByTimeRange(TimeRangeFilter timeRange, boolean descending) {
        // 先加载笔记列表（包含评论）
        List<Note> notes = loadNotesListByTimeRange(timeRange);
        
        // 展平为评论列表
        return flattenNotesToComments(notes, descending);
    }
    
    /**
     * 加载完整时间线（直接查询时间范围内的所有 Note 和 Comment，平等处理）
     * 与 loadTimelineByTimeRange 的区别：
     * - loadTimelineByTimeRange: 先找时间范围内的 Note，然后加载这些 Note 的 Comment（可能遗漏不在范围内的 Note 的 Comment）
     * - loadFullTimelineByTimeRange: 直接查询时间范围内的所有 Note 和 Comment，平等处理，统一排序
     * 
     * @param timeRange 时间范围枚举：LAST_DAY（最近一天）、LAST_WEEK（最近一周）、LAST_MONTH（最近一个月）
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 时间线评论列表，按时间排序
     */
    public List<Comment> loadFullTimelineByTimeRange(TimeRangeFilter timeRange, boolean descending) {
        if (!(dbHelper instanceof NoteDbHelper)) {
            throw new IllegalArgumentException("dbHelper must be an instance of NoteDbHelper");
        }
        
        NoteDbHelper noteDbHelper = (NoteDbHelper) dbHelper;
        SQLiteDatabase db = noteDbHelper.getReadableDatabase();
        
        // 计算时间范围的起始时间（当前时间减去对应天数）
        Calendar calendar = Calendar.getInstance();
        long currentTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, -timeRange.getDays());
        long startTime = calendar.getTimeInMillis();
        
        List<Comment> timeline = new ArrayList<>();
        
        // 1. 查询时间范围内的所有 Note，转换为 Comment
        String noteSelection = NoteDbHelper.COLUMN_TIMESTAMP + " >= ?";
        String[] noteSelectionArgs = new String[]{String.valueOf(startTime)};
        
        Cursor notesCursor = null;
        try {
            // 先尝试查询包含 is_pinned 列的完整查询
            notesCursor = db.query(
                    NoteDbHelper.TABLE_NOTES,
                    new String[]{
                        "_id",
                        NoteDbHelper.COLUMN_CONTENT,
                        NoteDbHelper.COLUMN_TIMESTAMP,
                        NoteDbHelper.COLUMN_COST,
                        NoteDbHelper.COLUMN_IS_PINNED
                    },
                    noteSelection,
                    noteSelectionArgs,
                    null, null,
                    null  // 不在这里排序，后面统一排序
            );
        } catch (android.database.sqlite.SQLiteException e) {
            // 如果查询失败（可能是 is_pinned 列不存在），使用不包含 is_pinned 的查询
            android.util.Log.w("Timeline", "Timeline: 查询包含 is_pinned 失败，尝试不包含 is_pinned 的查询: " + e.getMessage());
            if (notesCursor != null) {
                notesCursor.close();
            }
            notesCursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{
                    "_id",
                    NoteDbHelper.COLUMN_CONTENT,
                    NoteDbHelper.COLUMN_TIMESTAMP,
                    NoteDbHelper.COLUMN_COST
                },
                noteSelection,
                noteSelectionArgs,
                null, null,
                null  // 不在这里排序，后面统一排序
        );
        }
        
        if (notesCursor != null) {
            try {
                int idIndex = notesCursor.getColumnIndexOrThrow("_id");
                int contentIndex = notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT);
                int timestampIndex = notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP);
                int costIndex = notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST);
                int pinnedIndex = notesCursor.getColumnIndex(NoteDbHelper.COLUMN_IS_PINNED);
                
                while (notesCursor.moveToNext()) {
                    long noteId = notesCursor.getLong(idIndex);
                    String content = notesCursor.getString(contentIndex);
                    long timestamp = notesCursor.getLong(timestampIndex);
                    double cost = notesCursor.getDouble(costIndex);
                    // 如果 pinnedIndex < 0，说明列不存在，使用默认值 false
                    boolean isPinned = pinnedIndex >= 0 && notesCursor.getInt(pinnedIndex) == 1;
                    
                    // 将 Note 转换为 Comment，设置类型为 NOTE，并传递置顶状态
                    Comment noteAsComment = new Comment(
                            noteId,           // commentId = noteId
                            noteId,           // noteId = noteId（指向自己）
                            null,             // parentCommentId = null
                            content,          // content
                            timestamp,        // timestamp
                            cost,             // cost
                            projectName,      // projectName
                            TimelineItemType.NOTE,  // itemType = NOTE（表示这是 Note 转换来的）
                            isPinned         // isPinned（置顶状态，如果列不存在则为 false）
                    );
                    timeline.add(noteAsComment);
                }
            } finally {
                notesCursor.close();
            }
        }
        
        // 2. 查询时间范围内的所有 Comment
        String commentSelection = NoteDbHelper.COLUMN_COMMENT_TIMESTAMP + " >= ?";
        String[] commentSelectionArgs = new String[]{String.valueOf(startTime)};
        
        Cursor commentsCursor = null;
        try {
            // 尝试查询 Comment 表
            commentsCursor = db.query(
                NoteDbHelper.TABLE_NOTE_COMMENTS,
                new String[]{
                    NoteDbHelper.COLUMN_COMMENT_ID,
                    NoteDbHelper.COLUMN_COMMENT_NOTE_ID,
                    NoteDbHelper.COLUMN_PARENT_COMMENT_ID,
                    NoteDbHelper.COLUMN_COMMENT_CONTENT,
                    NoteDbHelper.COLUMN_COMMENT_TIMESTAMP,
                    NoteDbHelper.COLUMN_COMMENT_COST
                },
                commentSelection,
                commentSelectionArgs,
                null, null,
                null  // 不在这里排序，后面统一排序
        );
        } catch (android.database.sqlite.SQLiteException e) {
            // 如果查询失败（可能是 note_comments 表不存在），记录警告并跳过
            android.util.Log.w("Timeline", "Timeline: 查询 Comment 表失败，可能表不存在: " + e.getMessage());
            // commentsCursor 保持为 null，后续不会处理
        }
        
        if (commentsCursor != null) {
            try {
                int commentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID);
                int noteIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_NOTE_ID);
                int parentCommentIdIndex = commentsCursor.getColumnIndex(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                int contentIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT);
                int timestampIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP);
                int costIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST);
                
                while (commentsCursor.moveToNext()) {
                    long commentId = commentsCursor.getLong(commentIdIndex);
                    long noteId = commentsCursor.getLong(noteIdIndex);
                    Long parentCommentId = null;
                    if (parentCommentIdIndex >= 0 && !commentsCursor.isNull(parentCommentIdIndex)) {
                        parentCommentId = commentsCursor.getLong(parentCommentIdIndex);
                    }
                    String content = commentsCursor.getString(contentIndex);
                    long timestamp = commentsCursor.getLong(timestampIndex);
                    double cost = commentsCursor.getDouble(costIndex);
                    
                    Comment comment = new Comment(
                            commentId,
                            noteId,
                            parentCommentId,
                            content,
                            timestamp,
                            cost,
                            projectName,
                            TimelineItemType.COMMENT  // itemType = COMMENT（表示这是真正的 Comment）
                    );
                    timeline.add(comment);
                }
            } finally {
                commentsCursor.close();
            }
        }
        
        // 3. 统一按时间排序
        Collections.sort(timeline, new Comparator<Comment>() {
            @Override
            public int compare(Comment a, Comment b) {
                if (descending) {
                    // 逆序：b.timestamp - a.timestamp（最新的在前）
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                } else {
                    // 顺序：a.timestamp - b.timestamp（最旧的在前）
                    return Long.compare(a.getTimestamp(), b.getTimestamp());
                }
            }
        });
        
        return timeline;
    }
    
     /**
     * 根据时间范围加载笔记数据列表（平台无关）
     * 按时间逆序排列（最新的最前面），置顶的记录仍然排在最前面
     * 
     * @param timeRange 时间范围枚举：LAST_DAY（最近一天）、LAST_WEEK（最近一周）、LAST_MONTH（最近一个月）
     * @return 笔记列表，已完全加载到内存中
     */
     public List<Note> loadNotesListByTimeRange(TimeRangeFilter timeRange) {
        Cursor cursor = loadNotesByTimeRange(timeRange);
        return cursorToNoteList(cursor);
    }

    
    /**
     * 加载所有的笔记数据列表（平台无关）
     * 根据排序方式查询笔记，置顶的记录排在最前面
     * 
     * @param timeDescOrder true表示时间降序，false表示时间升序
     * @return 笔记列表，已完全加载到内存中（包含评论数据）
     */
    public List<Note> loadAllNotesList(boolean timeDescOrder) {
        if (!(dbHelper instanceof NoteDbHelper)) {
            throw new IllegalArgumentException("dbHelper must be an instance of NoteDbHelper");
        }
        
        NoteDbHelper noteDbHelper = (NoteDbHelper) dbHelper;
        Cursor cursor = noteDbHelper.loadNotes(timeDescOrder);
        return cursorToNoteList(cursor);
    }
    
    /**
     * 加载笔记数据列表（平台无关，使用默认排序方式：时间降序）
     * 
     * @return 笔记列表，已完全加载到内存中
     */
    public List<Note> loadAllNotesList() {
        return loadAllNotesList(true);
    }


    /**
     * 根据时间范围加载笔记数据
     * 按时间逆序排列（最新的最前面），置顶的记录仍然排在最前面
     * 
     * @param timeRange 时间范围枚举：LAST_DAY（最近一天）、LAST_WEEK（最近一周）、LAST_MONTH（最近一个月）
     * @return 包含笔记数据的Cursor，字段包括：_id, content, timestamp, cost, is_pinned
     */
    public Cursor loadNotesByTimeRange(TimeRangeFilter timeRange) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        
        // 计算时间范围的起始时间（当前时间减去对应天数）
        Calendar calendar = Calendar.getInstance();
        long currentTime = calendar.getTimeInMillis();
        calendar.add(Calendar.DAY_OF_YEAR, -timeRange.getDays());
        long startTime = calendar.getTimeInMillis();
        
        // 构建查询条件：timestamp >= startTime
        String selection = NoteDbHelper.COLUMN_TIMESTAMP + " >= ?";
        String[] selectionArgs = new String[]{String.valueOf(startTime)};
        
        // 排序：置顶的记录排在最前面，然后按时间逆序（最新的最前面）
        String orderBy = NoteDbHelper.COLUMN_IS_PINNED + " DESC, " + 
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC";
        
        Cursor cursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{
                    "_id", 
                    NoteDbHelper.COLUMN_CONTENT, 
                    NoteDbHelper.COLUMN_TIMESTAMP, 
                    NoteDbHelper.COLUMN_COST, 
                    NoteDbHelper.COLUMN_IS_PINNED
                },
                selection,
                selectionArgs,
                null, null,
                orderBy
        );
        
        return cursor;
    }
    
    
    /**
     * 将笔记列表展平为评论列表
     * 每个 Note 会被转换为一个特殊的 Comment（parentCommentId 为 null，表示主题的第一个条目）
     * 然后包含该 Note 的所有 Comment，并填充项目名称
     * 
     * @param notes 笔记列表
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 展平后的评论列表，按时间排序
     */
    private List<Comment> flattenNotesToComments(List<Note> notes, boolean descending) {
        List<Comment> timeline = new ArrayList<>();
        
        for (Note note : notes) {
            String noteProjectName = note.getProjectName() != null ? note.getProjectName() : projectName;
            
            // 1. 将 Note 转换为 Comment（作为主题的第一个条目）
            Comment noteAsComment = modelUtil.convertNoteToComment(note, projectName);
            timeline.add(noteAsComment);
            
            // 2. 添加该 Note 的所有 Comment，并填充项目名称和类型
            for (Comment comment : note.getComments()) {
                if (comment.getProjectName() == null) {
                    comment.setProjectName(noteProjectName);
                }
                // 确保类型设置为 COMMENT（如果还没有设置）
                if (comment.getItemType() == null) {
                    comment.setItemType(TimelineItemType.COMMENT);
                }
                timeline.add(comment);
            }
        }
        
        // 3. 按时间戳排序
        Collections.sort(timeline, new Comparator<Comment>() {
            @Override
            public int compare(Comment a, Comment b) {
                if (descending) {
                    // 逆序：b.timestamp - a.timestamp（最新的在前）
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                } else {
                    // 顺序：a.timestamp - b.timestamp（最旧的在前）
                    return Long.compare(a.getTimestamp(), b.getTimestamp());
                }
            }
        });
        
        return timeline;
    }
    
    /**
     * 将Cursor转换为List<Note>（平台无关的数据结构）
     * 注意：此方法会关闭Cursor，并批量加载所有笔记的评论数据（优化：只查询一次数据库）
     * 
     * @param cursor 数据库查询结果
     * @return 笔记列表（包含评论数据）
     */
    private List<Note> cursorToNoteList(Cursor cursor) {
        List<Note> notes = new ArrayList<>();
        
        if (cursor != null) {
            try {
                int idIndex = cursor.getColumnIndexOrThrow("_id");
                int contentIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT);
                int timestampIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP);
                int costIndex = cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST);
                int pinnedIndex = cursor.getColumnIndex(NoteDbHelper.COLUMN_IS_PINNED);
                
                // 第一步：先加载所有笔记
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIndex);
                    String content = cursor.getString(contentIndex);
                    long timestamp = cursor.getLong(timestampIndex);
                    double cost = cursor.getDouble(costIndex);
                    boolean isPinned = pinnedIndex >= 0 && cursor.getInt(pinnedIndex) == 1;
                    
                    Note note = new Note(id, content, timestamp, cost, isPinned, projectName);
                    notes.add(note);
                }
            } finally {
                cursor.close();
            }
        }
        
        // 第二步：批量加载所有评论（只查询一次数据库）
        if (!notes.isEmpty() && dbHelper instanceof NoteDbHelper) {
            loadAllCommentsForNotes(notes);
        }
        
        return notes;
    }
    
    /**
     * 批量加载所有笔记的评论数据（优化：只查询一次数据库）
     * 
     * @param notes 笔记列表
     */
    private void loadAllCommentsForNotes(List<Note> notes) {
        if (!(dbHelper instanceof NoteDbHelper)) {
            return;
        }
        
        NoteDbHelper noteDbHelper = (NoteDbHelper) dbHelper;
        
        // 构建笔记ID列表，用于查询这些笔记的所有评论
        if (notes.isEmpty()) {
            return;
        }
        
        // 方法1：查询所有评论（如果笔记数量很多，这种方式更高效）
        SQLiteDatabase db = noteDbHelper.getReadableDatabase();
        Cursor commentsCursor = db.query(
                NoteDbHelper.TABLE_NOTE_COMMENTS,
                new String[]{
                    NoteDbHelper.COLUMN_COMMENT_ID,
                    NoteDbHelper.COLUMN_COMMENT_NOTE_ID,
                    NoteDbHelper.COLUMN_PARENT_COMMENT_ID,
                    NoteDbHelper.COLUMN_COMMENT_CONTENT,
                    NoteDbHelper.COLUMN_COMMENT_TIMESTAMP,
                    NoteDbHelper.COLUMN_COMMENT_COST
                },
                null, null, null, null,
                NoteDbHelper.COLUMN_COMMENT_TIMESTAMP + " ASC"
        );
        
        // 创建笔记ID到Note对象的映射，便于快速查找
        Map<Long, Note> noteMap = new HashMap<>();
        for (Note note : notes) {
            noteMap.put(note.getId(), note);
        }
        
        // 遍历所有评论，匹配到对应的笔记
        if (commentsCursor != null) {
            try {
                int commentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID);
                int noteIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_NOTE_ID);
                int parentCommentIdIndex = commentsCursor.getColumnIndex(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                int contentIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT);
                int timestampIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP);
                int costIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST);
                
                while (commentsCursor.moveToNext()) {
                    long noteId = commentsCursor.getLong(noteIdIndex);
                    Note note = noteMap.get(noteId);
                    
                    // 只处理属于当前笔记列表的评论
                    if (note != null) {
                        long commentId = commentsCursor.getLong(commentIdIndex);
                        Long parentCommentId = null;
                        if (parentCommentIdIndex >= 0 && !commentsCursor.isNull(parentCommentIdIndex)) {
                            parentCommentId = commentsCursor.getLong(parentCommentIdIndex);
                        }
                        String commentContent = commentsCursor.getString(contentIndex);
                        long commentTimestamp = commentsCursor.getLong(timestampIndex);
                        double commentCost = commentsCursor.getDouble(costIndex);
                        
                        // 获取项目名称（优先使用 note 的项目名称，否则使用 loader 的项目名称）
                        String commentProjectName = note.getProjectName() != null ? 
                                note.getProjectName() : projectName;
                        
                        Comment comment = new Comment(commentId, noteId, parentCommentId, 
                                                      commentContent, commentTimestamp, commentCost, 
                                                      commentProjectName,
                                                      TimelineItemType.COMMENT);  // 设置类型为 COMMENT
                        note.addComment(comment);
                    }
                }
            } finally {
                commentsCursor.close();
            }
        }
    }
}

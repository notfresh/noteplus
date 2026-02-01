package person.notfresh.noteplus.manager;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;

/**
 * 导入导出管理器
 * 支持JSON和CSV格式，包含评论（追加内容）的导入导出
 */
public class ImportExportManager {
    private final Context context;
    private final NoteDbHelper dbHelper;
    private final ProjectContextManager projectManager;
    private final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    public ImportExportManager(Context context, NoteDbHelper dbHelper, ProjectContextManager projectManager) {
        this.context = context;
        this.dbHelper = dbHelper;
        this.projectManager = projectManager;
    }

    // ==================== 导出方法 ====================

    /**
     * 写入JSON数据（包含评论）
     */
    public void writeJsonData(OutputStream outputStream) throws IOException, JSONException {
        android.util.Log.i("NotePlusExport", "[writeJsonData] Starting JSON data export");
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        android.util.Log.d("NotePlusExport", "[writeJsonData] Database opened");
        
        // 获取所有记录
        android.util.Log.d("NotePlusExport", "[writeJsonData] Querying notes from database");
        Cursor notesCursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST},
                null, null, null, null,
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC"
        );
        
        int noteCount = notesCursor.getCount();
        android.util.Log.i("NotePlusExport", "[writeJsonData] Found " + noteCount + " notes to export");
        
        JSONArray notesArray = new JSONArray();
        int processedNotes = 0;
        
        // 构建记录
        while (notesCursor.moveToNext()) {
            processedNotes++;
            android.util.Log.d("NotePlusExport", "[writeJsonData] Processing note " + processedNotes + "/" + noteCount);
            JSONObject noteObject = new JSONObject();
            
            long noteId = notesCursor.getLong(notesCursor.getColumnIndexOrThrow("_id"));
            android.util.Log.d("NotePlusExport", "[writeJsonData] Note ID: " + noteId);
            String content = notesCursor.getString(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT));
            long timestamp = notesCursor.getLong(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP));
            double cost = notesCursor.getDouble(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST));
            
            noteObject.put("id", noteId);
            noteObject.put("content", content);
            noteObject.put("timestamp", sdf.format(new Date(timestamp)));
            noteObject.put("cost", cost);
            
            // 获取时间范围
            android.util.Log.d("NotePlusExport", "[writeJsonData] Fetching time ranges for note " + noteId);
            Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
            if (timeRangeCursor.moveToFirst()) {
                JSONObject timeRange = new JSONObject();
                long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
                long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
                
                timeRange.put("start", sdf.format(new Date(startTime)));
                timeRange.put("end", sdf.format(new Date(endTime)));
                noteObject.put("timeRange", timeRange);
                android.util.Log.d("NotePlusExport", "[writeJsonData] Time range added for note " + noteId);
            }
            timeRangeCursor.close();
            
            // 获取标签
            android.util.Log.d("NotePlusExport", "[writeJsonData] Fetching tags for note " + noteId);
            Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
            JSONArray tagsArray = new JSONArray();
            int tagCount = 0;
            while (tagsCursor.moveToNext()) {
                JSONObject tagObject = new JSONObject();
                tagObject.put("name", tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME)));
                tagObject.put("color", tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_COLOR)));
                tagsArray.put(tagObject);
                tagCount++;
            }
            tagsCursor.close();
            noteObject.put("tags", tagsArray);
            if (tagCount > 0) {
                android.util.Log.d("NotePlusExport", "[writeJsonData] Added " + tagCount + " tags for note " + noteId);
            }
            
            // 获取评论（追加内容）
            android.util.Log.d("NotePlusExport", "[writeJsonData] Fetching comments for note " + noteId);
            Cursor commentsCursor = dbHelper.getCommentsForNote(noteId);
            JSONArray commentsArray = new JSONArray();
            int commentCount = 0;
            while (commentsCursor.moveToNext()) {
                JSONObject commentObject = new JSONObject();
                long commentId = commentsCursor.getLong(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID));
                int parentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                String commentContent = commentsCursor.getString(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT));
                long commentTimestamp = commentsCursor.getLong(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP));
                double commentCost = commentsCursor.getDouble(
                    commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST));
                
                commentObject.put("commentId", commentId);
                if (!commentsCursor.isNull(parentIdIndex)) {
                    commentObject.put("parentCommentId", commentsCursor.getLong(parentIdIndex));
                } else {
                    commentObject.put("parentCommentId", JSONObject.NULL);
                }
                commentObject.put("content", commentContent);
                commentObject.put("timestamp", sdf.format(new Date(commentTimestamp)));
                commentObject.put("cost", commentCost);
                
                commentsArray.put(commentObject);
                commentCount++;
            }
            commentsCursor.close();
            
            // 如果有评论，才添加到JSON中
            if (commentsArray.length() > 0) {
                noteObject.put("comments", commentsArray);
                android.util.Log.d("NotePlusExport", "[writeJsonData] Added " + commentCount + " comments for note " + noteId);
            }
            
            notesArray.put(noteObject);
        }
        
        notesCursor.close();
        android.util.Log.i("NotePlusExport", "[writeJsonData] Processed all " + processedNotes + " notes");
        
        // 创建根JSON对象
        android.util.Log.d("NotePlusExport", "[writeJsonData] Creating root JSON object");
        JSONObject rootObject = new JSONObject();
        rootObject.put("projectName", projectManager.getCurrentProject());
        rootObject.put("exportDate", sdf.format(new Date()));
        rootObject.put("notes", notesArray);
        android.util.Log.d("NotePlusExport", "[writeJsonData] Root object created, project: " + projectManager.getCurrentProject());
        
        // 写入数据
        android.util.Log.d("NotePlusExport", "[writeJsonData] Creating OutputStreamWriter with UTF-8 encoding");
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        android.util.Log.d("NotePlusExport", "[writeJsonData] Writing JSON string to output stream");
        String jsonString = rootObject.toString(2); // 格式化JSON输出
        android.util.Log.d("NotePlusExport", "[writeJsonData] JSON string length: " + jsonString.length() + " characters");
        writer.write(jsonString);
        android.util.Log.d("NotePlusExport", "[writeJsonData] Flushing output stream");
        writer.flush();
        android.util.Log.i("NotePlusExport", "[writeJsonData] JSON data export completed successfully");
    }

    /**
     * 写入CSV数据（兼容旧格式，支持评论）
     */
    public void writeCsvData(OutputStream outputStream) throws IOException {
        android.util.Log.i("NotePlusExport", "[writeCsvData] Starting CSV data export");
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        android.util.Log.d("NotePlusExport", "[writeCsvData] Database opened");
        
        // 获取所有记录
        android.util.Log.d("NotePlusExport", "[writeCsvData] Querying notes from database");
        Cursor notesCursor = db.query(
                NoteDbHelper.TABLE_NOTES,
                new String[]{"_id", NoteDbHelper.COLUMN_CONTENT, NoteDbHelper.COLUMN_TIMESTAMP, NoteDbHelper.COLUMN_COST},
                null, null, null, null,
                NoteDbHelper.COLUMN_TIMESTAMP + " DESC"
        );
        
        int noteCount = notesCursor.getCount();
        android.util.Log.i("NotePlusExport", "[writeCsvData] Found " + noteCount + " notes to export");
        
        OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
        android.util.Log.d("NotePlusExport", "[writeCsvData] OutputStreamWriter created with UTF-8 encoding");
        
        // 检查是否有评论，决定使用哪种格式
        android.util.Log.d("NotePlusExport", "[writeCsvData] Checking if comments exist");
        boolean hasComments = false;
        // 先检查是否有评论（不移动主游标）
        Cursor checkCommentsCursor = db.query(
            NoteDbHelper.TABLE_NOTE_COMMENTS,
            new String[]{"COUNT(*) as count"},
            null, null, null, null, null
        );
        if (checkCommentsCursor.moveToFirst() && checkCommentsCursor.getInt(0) > 0) {
            hasComments = true;
            android.util.Log.d("NotePlusExport", "[writeCsvData] Comments found, using new format with type column");
        } else {
            android.util.Log.d("NotePlusExport", "[writeCsvData] No comments found, using old format");
        }
        checkCommentsCursor.close();
        
        if (hasComments) {
            // 新格式：带类型列
            writer.write("类型,笔记ID,评论ID,父评论ID,内容,时间戳,花费,开始时间,结束时间,标签\n");
        } else {
            // 旧格式：兼容原有格式
            writer.write("ID,内容,时间戳,花费,开始时间,结束时间,标签\n");
        }
        
        // 写入记录
        int processedNotes = 0;
        while (notesCursor.moveToNext()) {
            processedNotes++;
            android.util.Log.d("NotePlusExport", "[writeCsvData] Processing note " + processedNotes + "/" + noteCount);
            long noteId = notesCursor.getLong(notesCursor.getColumnIndexOrThrow("_id"));
            String content = notesCursor.getString(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_CONTENT));
            long timestamp = notesCursor.getLong(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TIMESTAMP));
            double cost = notesCursor.getDouble(notesCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COST));
            
            // 处理CSV中的特殊字符
            String escapedContent = "\"" + content.replace("\"", "\"\"") + "\"";
            
            // 获取时间范围
            Cursor timeRangeCursor = dbHelper.getTimeRangesForNote(noteId);
            String startTimeStr = "";
            String endTimeStr = "";
            if (timeRangeCursor.moveToFirst()) {
                long startTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
                long endTime = timeRangeCursor.getLong(timeRangeCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
                startTimeStr = sdf.format(new Date(startTime));
                endTimeStr = sdf.format(new Date(endTime));
            }
            timeRangeCursor.close();
            
            // 获取标签
            Cursor tagsCursor = dbHelper.getTagsForNote(noteId);
            StringBuilder tags = new StringBuilder();
            while (tagsCursor.moveToNext()) {
                if (tags.length() > 0) {
                    tags.append(";");
                }
                tags.append(tagsCursor.getString(tagsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME)));
            }
            tagsCursor.close();
            String tagsStr = "\"" + tags.toString() + "\"";
            
            if (hasComments) {
                // 新格式：先写笔记行
                StringBuilder noteLine = new StringBuilder();
                noteLine.append("NOTE,");
                noteLine.append(noteId).append(",");
                noteLine.append(","); // 评论ID为空
                noteLine.append(","); // 父评论ID为空
                noteLine.append(escapedContent).append(",");
                noteLine.append(sdf.format(new Date(timestamp))).append(",");
                noteLine.append(cost).append(",");
                noteLine.append(startTimeStr).append(",");
                noteLine.append(endTimeStr).append(",");
                noteLine.append(tagsStr);
                writer.write(noteLine.toString() + "\n");
                
                // 再写评论行
                Cursor commentsCursor = dbHelper.getCommentsForNote(noteId);
                while (commentsCursor.moveToNext()) {
                    long commentId = commentsCursor.getLong(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_ID));
                    int parentIdIndex = commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_PARENT_COMMENT_ID);
                    String commentContent = commentsCursor.getString(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT));
                    long commentTimestamp = commentsCursor.getLong(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP));
                    double commentCost = commentsCursor.getDouble(
                        commentsCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_COST));
                    
                    String escapedCommentContent = "\"" + commentContent.replace("\"", "\"\"") + "\"";
                    
                    StringBuilder commentLine = new StringBuilder();
                    commentLine.append("COMMENT,");
                    commentLine.append(noteId).append(",");
                    commentLine.append(commentId).append(",");
                    if (!commentsCursor.isNull(parentIdIndex)) {
                        commentLine.append(commentsCursor.getLong(parentIdIndex));
                    }
                    commentLine.append(",");
                    commentLine.append(escapedCommentContent).append(",");
                    commentLine.append(sdf.format(new Date(commentTimestamp))).append(",");
                    commentLine.append(commentCost).append(",");
                    commentLine.append(","); // 开始时间为空
                    commentLine.append(","); // 结束时间为空
                    commentLine.append("\"\""); // 标签为空
                    writer.write(commentLine.toString() + "\n");
                }
                commentsCursor.close();
            } else {
                // 旧格式
                StringBuilder line = new StringBuilder();
                line.append(noteId).append(",");
                line.append(escapedContent).append(",");
                line.append(sdf.format(new Date(timestamp))).append(",");
                line.append(cost).append(",");
                line.append(startTimeStr).append(",");
                line.append(endTimeStr).append(",");
                line.append(tagsStr);
                writer.write(line.toString() + "\n");
            }
        }
        
        notesCursor.close();
        android.util.Log.i("NotePlusExport", "[writeCsvData] Processed all " + processedNotes + " notes");
        android.util.Log.d("NotePlusExport", "[writeCsvData] Flushing output stream");
        writer.flush();
        android.util.Log.i("NotePlusExport", "[writeCsvData] CSV data export completed successfully");
    }

    // ==================== 导入方法 ====================

    /**
     * 从JSON导入数据（包含评论）
     */
    public ImportResult readJsonData(Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("无法读取文件");
        }
        
        // 读取JSON内容
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder jsonString = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            jsonString.append(line);
        }
        reader.close();
        inputStream.close();
        
        JSONObject rootObject = new JSONObject(jsonString.toString());
        JSONArray notesArray = rootObject.getJSONArray("notes");
        
        int importedCount = 0;
        int skippedCount = 0;
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        
        try {
            for (int i = 0; i < notesArray.length(); i++) {
                try {
                    JSONObject noteObject = notesArray.getJSONObject(i);
                    
                    // 解析笔记基本信息
                    String content = noteObject.getString("content");
                    String timestampStr = noteObject.getString("timestamp");
                    long timestamp = sdf.parse(timestampStr).getTime();
                    
                    // 插入笔记
                    ContentValues noteValues = new ContentValues();
                    noteValues.put(NoteDbHelper.COLUMN_CONTENT, content);
                    noteValues.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);
                    
                    // 处理花费信息
                    if (noteObject.has("cost")) {
                        double cost = noteObject.getDouble("cost");
                        noteValues.put(NoteDbHelper.COLUMN_COST, cost);
                    }
                    
                    long newNoteId = db.insert(NoteDbHelper.TABLE_NOTES, null, noteValues);
                    
                    // 处理时间范围
                    if (noteObject.has("timeRange")) {
                        JSONObject timeRange = noteObject.getJSONObject("timeRange");
                        String startTimeStr = timeRange.getString("start");
                        String endTimeStr = timeRange.getString("end");
                        
                        long startTime = sdf.parse(startTimeStr).getTime();
                        long endTime = sdf.parse(endTimeStr).getTime();
                        dbHelper.saveTimeRange(newNoteId, startTime, endTime);
                    }
                    
                    // 处理标签
                    if (noteObject.has("tags")) {
                        JSONArray tagsArray = noteObject.getJSONArray("tags");
                        for (int j = 0; j < tagsArray.length(); j++) {
                            JSONObject tagObject = tagsArray.getJSONObject(j);
                            String tagName = tagObject.getString("name");
                            String tagColor = tagObject.getString("color");
                            
                            // 检查标签是否存在
                            long tagId = dbHelper.getTagIdByName(tagName);
                            if (tagId == -1) {
                                // 创建新标签
                                tagId = dbHelper.addTag(tagName, tagColor);
                            }
                            if (tagId != -1) {
                                dbHelper.linkNoteToTag(newNoteId, tagId);
                            }
                        }
                    }
                    
                    // 处理评论（追加内容）
                    if (noteObject.has("comments")) {
                        JSONArray commentsArray = noteObject.getJSONArray("comments");
                        Map<Long, Long> commentIdMap = new HashMap<>(); // 旧ID -> 新ID
                        
                        // 按时间顺序处理评论（确保父评论先处理）
                        // JSON数组已经按时间排序，直接遍历即可
                        for (int j = 0; j < commentsArray.length(); j++) {
                            JSONObject commentObject = commentsArray.getJSONObject(j);
                            
                            long oldCommentId = commentObject.getLong("commentId");
                            Long oldParentCommentId = null;
                            if (!commentObject.isNull("parentCommentId")) {
                                oldParentCommentId = commentObject.getLong("parentCommentId");
                            }
                            
                            String commentContent = commentObject.getString("content");
                            String commentTimestampStr = commentObject.getString("timestamp");
                            long commentTimestamp = sdf.parse(commentTimestampStr).getTime();
                            double commentCost = 0.0;
                            if (commentObject.has("cost")) {
                                commentCost = commentObject.getDouble("cost");
                            }
                            
                            // 映射父评论ID
                            Long newParentCommentId = null;
                            if (oldParentCommentId != null) {
                                newParentCommentId = commentIdMap.get(oldParentCommentId);
                                if (newParentCommentId == null) {
                                    // 找不到父评论映射，跳过该评论
                                    continue;
                                }
                            }
                            
                            // 插入评论
                            ContentValues commentValues = new ContentValues();
                            commentValues.put(NoteDbHelper.COLUMN_COMMENT_NOTE_ID, newNoteId);
                            if (newParentCommentId != null) {
                                commentValues.put(NoteDbHelper.COLUMN_PARENT_COMMENT_ID, newParentCommentId);
                            }
                            commentValues.put(NoteDbHelper.COLUMN_COMMENT_CONTENT, commentContent);
                            commentValues.put(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP, commentTimestamp);
                            commentValues.put(NoteDbHelper.COLUMN_COMMENT_COST, commentCost);
                            
                            long newCommentId = db.insert(NoteDbHelper.TABLE_NOTE_COMMENTS, null, commentValues);
                            if (newCommentId != -1) {
                                commentIdMap.put(oldCommentId, newCommentId);
                            }
                        }
                    }
                    
                    importedCount++;
                    
                } catch (Exception e) {
                    skippedCount++;
                    e.printStackTrace();
                }
            }
            
            db.setTransactionSuccessful();
            
        } finally {
            db.endTransaction();
        }
        
        return new ImportResult(importedCount, skippedCount);
    }

    /**
     * 从CSV导入数据（兼容旧格式，支持评论）
     */
    public ImportResult readCsvData(Uri uri) throws Exception {
        ContentResolver resolver = context.getContentResolver();
        InputStream inputStream = resolver.openInputStream(uri);
        if (inputStream == null) {
            throw new IOException("无法读取文件");
        }
        
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String headerLine = reader.readLine();
        if (headerLine == null) {
            reader.close();
            inputStream.close();
            throw new IOException("CSV文件为空");
        }
        
        // 判断格式：检查是否有"类型"列
        String[] headerFields = parseCsvLine(headerLine);
        boolean isNewFormat = headerFields.length > 0 && "类型".equals(headerFields[0]);
        
        int importedCount = 0;
        int skippedCount = 0;
        
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.beginTransaction();
        
        try {
            if (isNewFormat) {
                // 新格式：两遍扫描，先导入笔记，再导入评论
                Map<Long, Long> noteIdMap = new HashMap<>(); // 旧笔记ID -> 新笔记ID
                Map<Long, Long> commentIdMap = new HashMap<>(); // 旧评论ID -> 新评论ID
                List<String> commentLines = new ArrayList<>(); // 暂存评论行
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    try {
                        String[] fields = parseCsvLine(line);
                        if (fields.length < 2) continue;
                        
                        String type = fields[0];
                        if ("NOTE".equals(type)) {
                            // 导入笔记
                            long oldNoteId = Long.parseLong(fields[1]);
                            String content = fields[4].replace("\"\"", "\"").replace("\"", "");
                            String timestampStr = fields[5];
                            long timestamp = sdf.parse(timestampStr).getTime();
                            
                            ContentValues noteValues = new ContentValues();
                            noteValues.put(NoteDbHelper.COLUMN_CONTENT, content);
                            noteValues.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);
                            
                            if (fields.length > 6 && !fields[6].isEmpty()) {
                                try {
                                    double cost = Double.parseDouble(fields[6]);
                                    noteValues.put(NoteDbHelper.COLUMN_COST, cost);
                                } catch (NumberFormatException e) {
                                    noteValues.put(NoteDbHelper.COLUMN_COST, 0.0);
                                }
                            }
                            
                            long newNoteId = db.insert(NoteDbHelper.TABLE_NOTES, null, noteValues);
                            noteIdMap.put(oldNoteId, newNoteId);
                            
                            // 处理时间范围
                            if (fields.length > 8 && !fields[7].isEmpty() && !fields[8].isEmpty()) {
                                try {
                                    long startTime = sdf.parse(fields[7]).getTime();
                                    long endTime = sdf.parse(fields[8]).getTime();
                                    dbHelper.saveTimeRange(newNoteId, startTime, endTime);
                                } catch (Exception e) {
                                    // 时间范围解析失败，跳过
                                }
                            }
                            
                            // 处理标签
                            if (fields.length > 9 && !fields[9].isEmpty()) {
                                String tagsStr = fields[9].replace("\"", "");
                                String[] tagNames = tagsStr.split(";");
                                for (String tagName : tagNames) {
                                    if (!tagName.trim().isEmpty()) {
                                        long tagId = dbHelper.getTagIdByName(tagName.trim());
                                        if (tagId == -1) {
                                            String[] colors = {"#FF5722", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107", "#607D8B"};
                                            String randomColor = colors[new Random().nextInt(colors.length)];
                                            tagId = dbHelper.addTag(tagName.trim(), randomColor);
                                        }
                                        if (tagId != -1) {
                                            dbHelper.linkNoteToTag(newNoteId, tagId);
                                        }
                                    }
                                }
                            }
                            
                            importedCount++;
                        } else if ("COMMENT".equals(type)) {
                            // 暂存评论行，稍后处理
                            commentLines.add(line);
                        }
                    } catch (Exception e) {
                        skippedCount++;
                        e.printStackTrace();
                    }
                }
                
                // 第二遍：处理评论
                for (String commentLine : commentLines) {
                    try {
                        String[] fields = parseCsvLine(commentLine);
                        if (fields.length < 5) continue;
                        
                        long oldNoteId = Long.parseLong(fields[1]);
                        Long newNoteId = noteIdMap.get(oldNoteId);
                        if (newNoteId == null) {
                            // 找不到对应的笔记，跳过
                            skippedCount++;
                            continue;
                        }
                        
                        long oldCommentId = Long.parseLong(fields[2]);
                        Long oldParentCommentId = null;
                        if (fields.length > 3 && !fields[3].isEmpty()) {
                            oldParentCommentId = Long.parseLong(fields[3]);
                        }
                        
                        String commentContent = fields[4].replace("\"\"", "\"").replace("\"", "");
                        String commentTimestampStr = fields[5];
                        long commentTimestamp = sdf.parse(commentTimestampStr).getTime();
                        double commentCost = 0.0;
                        if (fields.length > 6 && !fields[6].isEmpty()) {
                            try {
                                commentCost = Double.parseDouble(fields[6]);
                            } catch (NumberFormatException e) {
                                commentCost = 0.0;
                            }
                        }
                        
                        // 映射父评论ID
                        Long newParentCommentId = null;
                        if (oldParentCommentId != null) {
                            newParentCommentId = commentIdMap.get(oldParentCommentId);
                            if (newParentCommentId == null) {
                                // 找不到父评论映射，跳过
                                skippedCount++;
                                continue;
                            }
                        }
                        
                        // 插入评论
                        ContentValues commentValues = new ContentValues();
                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_NOTE_ID, newNoteId);
                        if (newParentCommentId != null) {
                            commentValues.put(NoteDbHelper.COLUMN_PARENT_COMMENT_ID, newParentCommentId);
                        }
                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_CONTENT, commentContent);
                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_TIMESTAMP, commentTimestamp);
                        commentValues.put(NoteDbHelper.COLUMN_COMMENT_COST, commentCost);
                        
                        long newCommentId = db.insert(NoteDbHelper.TABLE_NOTE_COMMENTS, null, commentValues);
                        if (newCommentId != -1) {
                            commentIdMap.put(oldCommentId, newCommentId);
                        }
                    } catch (Exception e) {
                        skippedCount++;
                        e.printStackTrace();
                    }
                }
            } else {
                // 旧格式：只导入笔记，不导入评论
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.trim().isEmpty()) continue;
                    
                    try {
                        String[] fields = parseCsvLine(line);
                        if (fields.length >= 3) {
                            // 解析字段 - 格式：ID,内容,时间戳,花费,开始时间,结束时间,标签
                            String content = fields[1].replace("\"\"", "\"").replace("\"", "");
                            String timestampStr = fields[2];
                            long timestamp = sdf.parse(timestampStr).getTime();
                            
                            ContentValues noteValues = new ContentValues();
                            noteValues.put(NoteDbHelper.COLUMN_CONTENT, content);
                            noteValues.put(NoteDbHelper.COLUMN_TIMESTAMP, timestamp);
                            
                            if (fields.length > 3 && !fields[3].isEmpty()) {
                                try {
                                    double cost = Double.parseDouble(fields[3]);
                                    noteValues.put(NoteDbHelper.COLUMN_COST, cost);
                                } catch (NumberFormatException e) {
                                    noteValues.put(NoteDbHelper.COLUMN_COST, 0.0);
                                }
                            }
                            
                            long newNoteId = db.insert(NoteDbHelper.TABLE_NOTES, null, noteValues);
                            
                            // 处理时间范围
                            if (fields.length > 6 && !fields[4].isEmpty() && !fields[5].isEmpty()) {
                                try {
                                    long startTime = sdf.parse(fields[4]).getTime();
                                    long endTime = sdf.parse(fields[5]).getTime();
                                    dbHelper.saveTimeRange(newNoteId, startTime, endTime);
                                } catch (Exception e) {
                                    // 时间范围解析失败，跳过
                                }
                            }
                            
                            // 处理标签
                            if (fields.length > 6 && !fields[6].isEmpty()) {
                                String tagsStr = fields[6].replace("\"", "");
                                String[] tagNames = tagsStr.split(";");
                                for (String tagName : tagNames) {
                                    if (!tagName.trim().isEmpty()) {
                                        long tagId = dbHelper.getTagIdByName(tagName.trim());
                                        if (tagId == -1) {
                                            String[] colors = {"#FF5722", "#9C27B0", "#2196F3", "#4CAF50", "#FFC107", "#607D8B"};
                                            String randomColor = colors[new Random().nextInt(colors.length)];
                                            tagId = dbHelper.addTag(tagName.trim(), randomColor);
                                        }
                                        if (tagId != -1) {
                                            dbHelper.linkNoteToTag(newNoteId, tagId);
                                        }
                                    }
                                }
                            }
                            
                            importedCount++;
                        }
                    } catch (Exception e) {
                        skippedCount++;
                        e.printStackTrace();
                    }
                }
            }
            
            db.setTransactionSuccessful();
            
        } finally {
            db.endTransaction();
            reader.close();
            inputStream.close();
        }
        
        return new ImportResult(importedCount, skippedCount);
    }

    /**
     * 解析CSV行
     */
    private String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder currentField = new StringBuilder();
        boolean inQuotes = false;
        
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    // 转义的双引号
                    currentField.append('"');
                    i++; // 跳过下一个引号
                } else {
                    // 切换引号状态
                    inQuotes = !inQuotes;
                }
            } else if (c == ',' && !inQuotes) {
                // 字段分隔符
                fields.add(currentField.toString());
                currentField = new StringBuilder();
            } else {
                currentField.append(c);
            }
        }
        
        // 添加最后一个字段
        fields.add(currentField.toString());
        
        return fields.toArray(new String[0]);
    }

    /**
     * 导入结果
     */
    public static class ImportResult {
        public final int importedCount;
        public final int skippedCount;

        public ImportResult(int importedCount, int skippedCount) {
            this.importedCount = importedCount;
            this.skippedCount = skippedCount;
        }
    }
}


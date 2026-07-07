# 多选笔记合并功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在多选模式下增加"合并"功能，将多个选中的笔记合并为一个新笔记，原笔记放入回收站。

**Architecture:** 在 MainActivity 的多选菜单系统中添加"合并"菜单项，点击后显示确认对话框，预览合并内容，用户确认后执行合并操作。

**Tech Stack:** Android, Java, SQLite (NoteDbHelper)

---

## 文件修改清单

- **Modify:** `app/src/main/res/menu/main_menu.xml` - 添加合并菜单项
- **Modify:** `app/src/main/java/person/notfresh/noteplus/MainActivity.java` - 添加菜单处理和合并逻辑

---

## Task 1: 添加合并菜单项

**Files:**
- Modify: `app/src/main/res/menu/main_menu.xml`

- [ ] **Step 1: 在 main_menu.xml 中添加合并菜单项**

在 `action_move_to_project` 之后添加：

```xml
<item
    android:id="@+id/action_merge_notes"
    android:title="合并"
    android:icon="@drawable/ic_merge"
    app:showAsAction="always"
    android:visible="false" />
```

> 注意：需要创建一个新的图标 `ic_merge`，或者复用现有图标（如 `ic_combine` 或其他合适的图标）。如果使用现有图标，修改 `android:icon` 为现有图标名称。

---

## Task 2: 在多选模式下显示合并菜单

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java:1051-1110`

- [ ] **Step 1: 在 onCreateOptionsMenu 中添加合并菜单项的可见性控制**

在 `isMultiSelectMode` 为 true 的分支中，添加：

```java
MenuItem mergeNotesMenuItem = menu.findItem(R.id.action_merge_notes);
if (mergeNotesMenuItem != null) {
    mergeNotesMenuItem.setVisible(true);
}
```

在 `isMultiSelectMode` 为 false 的分支中，添加：

```java
MenuItem mergeNotesMenuItem = menu.findItem(R.id.action_merge_notes);
if (mergeNotesMenuItem != null) {
    mergeNotesMenuItem.setVisible(false);
}
```

---

## Task 3: 处理合并菜单点击事件

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java:1274-1287`

- [ ] **Step 1: 在 onOptionsItemSelected 中添加合并处理分支**

在 `action_move_to_project` 分支之后添加：

```java
} else if (id == R.id.action_merge_notes) {
    showMergeConfirmDialog();
    return true;
}
```

---

## Task 4: 实现 showMergeConfirmDialog 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java` - 在 `showMoveToProjectDialog` 方法附近添加

- [ ] **Step 1: 实现 showMergeConfirmDialog 方法**

```java
/**
 * 显示合并确认对话框
 */
private void showMergeConfirmDialog() {
    if (selectedNoteIds == null || selectedNoteIds.size() < 2) {
        Toast.makeText(this, "请至少选择2条笔记进行合并", Toast.LENGTH_SHORT).show();
        return;
    }

    // 构建合并预览内容
    StringBuilder preview = new StringBuilder();
    preview.append("将合并 ").append(selectedNoteIds.size()).append(" 条笔记：\n\n");

    // 按时间排序后遍历
    List<Long> sortedIds = new ArrayList<>(selectedNoteIds);
    Collections.sort(sortedIds);

    for (int i = 0; i < sortedIds.size(); i++) {
        long noteId = sortedIds.get(i);
        Note note = dbHelper.getNoteById(noteId);
        if (note != null) {
            // 获取时间范围
            String timeStr = getNoteTimeString(noteId, note.getTimestamp());
            preview.append("• ").append(timeStr).append("\n");
            preview.append("  ").append(note.getContent().substring(0, Math.min(30, note.getContent().length()))).append("...\n");
        }
        if (i < sortedIds.size() - 1) {
            preview.append("\n");
        }
    }

    preview.append("\n合并后原笔记将放入回收站，是否继续？");

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("确认合并");
    builder.setMessage(preview.toString());
    builder.setPositiveButton("合并", (dialog, which) -> mergeSelectedNotes());
    builder.setNegativeButton("取消", null);
    builder.show();
}
```

- [ ] **Step 2: 实现 getNoteTimeString 辅助方法**

```java
/**
 * 获取笔记的时间字符串（用于合并预览）
 * @param noteId 笔记ID
 * @param timestamp 默认时间戳
 * @return 格式化的时间字符串
 */
private String getNoteTimeString(long noteId, long timestamp) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());

    // 尝试获取时间范围
    Cursor cursor = dbHelper.getTimeRangesForNote(noteId);
    if (cursor != null && cursor.moveToFirst()) {
        long startTime = cursor.getLong(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
        long endTime = cursor.getLong(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
        cursor.close();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return "[" + dateFormat.format(startTime) + "-" + dateFormat.format(endTime) + "]";
    }
    if (cursor != null) {
        cursor.close();
    }

    return "[" + sdf.format(timestamp) + "]";
}
```

---

## Task 5: 实现 mergeSelectedNotes 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java` - 在 `showMergeConfirmDialog` 方法之后添加

- [ ] **Step 1: 实现 mergeSelectedNotes 方法**

```java
/**
 * 执行笔记合并操作
 * 将多个选中的笔记合并为一个新笔记，原笔记放入回收站
 */
private void mergeSelectedNotes() {
    if (selectedNoteIds == null || selectedNoteIds.size() < 2) {
        return;
    }

    // 显示进度对话框
    ProgressDialog progressDialog = new ProgressDialog(this);
    progressDialog.setMessage("正在合并笔记...");
    progressDialog.setCancelable(false);
    progressDialog.show();

    new Thread(() -> {
        try {
            // 按时间排序
            List<Long> sortedIds = new ArrayList<>(selectedNoteIds);
            Collections.sort(sortedIds);

            // 构建合并后的内容
            StringBuilder mergedContent = new StringBuilder();

            for (int i = 0; i < sortedIds.size(); i++) {
                long noteId = sortedIds.get(i);
                Note note = dbHelper.getNoteById(noteId);
                if (note == null) continue;

                // 获取时间范围
                String timeStr = getNoteTimeStringForMerge(noteId, note.getTimestamp());
                mergedContent.append(timeStr).append("\n");

                // 如果有花费
                if (note.getCost() > 0) {
                    mergedContent.append("Cost: ").append(String.format(Locale.getDefault(), "%.2f", note.getCost())).append("\n");
                }

                // 如果有标签
                List<String> tags = getTagsForNote(noteId);
                if (!tags.isEmpty()) {
                    mergedContent.append("Tags: ").append(String.join(", ", tags)).append("\n");
                }

                // 笔记内容
                mergedContent.append(note.getContent()).append("\n");

                // 获取评论
                List<String> comments = getCommentsForNote(noteId);
                if (!comments.isEmpty()) {
                    mergedContent.append("comments:\n");
                    for (int j = 0; j < comments.size(); j++) {
                        mergedContent.append("comment").append(j + 1).append(": ").append(comments.get(j)).append("\n");
                    }
                }

                if (i < sortedIds.size() - 1) {
                    mergedContent.append("---\n");
                }
            }

            // 创建新笔记
            SQLiteDatabase db = dbHelper.getWritableDatabase();
            db.beginTransaction();
            long newNoteId = -1;
            try {
                ContentValues values = new ContentValues();
                values.put(NoteDbHelper.COLUMN_CONTENT, mergedContent.toString());
                values.put(NoteDbHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());
                values.put(NoteDbHelper.COLUMN_COST, 0); // 合并后花费归零
                newNoteId = db.insert(NoteDbHelper.TABLE_NOTES, null, values);

                if (newNoteId > 0) {
                    // 复制第一个笔记的时间范围（如果有）
                    long firstNoteId = sortedIds.get(0);
                    Cursor cursor = dbHelper.getTimeRangesForNote(firstNoteId);
                    if (cursor != null && cursor.moveToFirst()) {
                        long startTime = cursor.getLong(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
                        long endTime = cursor.getLong(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
                        dbHelper.saveTimeRange(newNoteId, startTime, endTime);
                        cursor.close();
                    }

                    // 复制所有原笔记的图片
                    for (long oldNoteId : sortedIds) {
                        List<String> images = dbHelper.getImagePathsForNote(oldNoteId);
                        for (String imagePath : images) {
                            dbHelper.insertNoteImage(newNoteId, imagePath);
                        }
                    }

                    // 复制所有原笔记的音频
                    for (long oldNoteId : sortedIds) {
                        List<AudioAttachment> audios = dbHelper.getAudioAttachmentsForNote(oldNoteId);
                        for (AudioAttachment audio : audios) {
                            dbHelper.insertNoteAudio(newNoteId, audio.getPath(), audio.getDurationMs());
                        }
                    }

                    // 复制所有原笔记的标签
                    for (long oldNoteId : sortedIds) {
                        Cursor tagCursor = dbHelper.getTagsForNote(oldNoteId);
                        if (tagCursor != null) {
                            while (tagCursor.moveToNext()) {
                                long tagId = tagCursor.getLong(tagCursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_ID));
                                dbHelper.linkNoteToTag(newNoteId, tagId);
                            }
                            tagCursor.close();
                        }
                    }

                    // 将原笔记放入回收站
                    for (long oldNoteId : sortedIds) {
                        dbHelper.archiveNote(oldNoteId);
                    }
                }

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }

            // 更新搜索索引
            if (newNoteId > 0) {
                final long finalNewNoteId = newNoteId;
                runOnUiThread(() -> {
                    searchManager.indexNote(finalNewNoteId, mergedContent.toString(), System.currentTimeMillis(), projectManager.getCurrentProject());
                });

                // 移除原笔记的索引
                for (long oldNoteId : sortedIds) {
                    final long finalOldNoteId = oldNoteId;
                    runOnUiThread(() -> {
                        searchManager.removeNoteIndex(finalOldNoteId);
                    });
                }
            }

            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "笔记合并成功", Toast.LENGTH_SHORT).show();

                // 退出多选模式并刷新
                exitMultiSelectMode();
                if (noteListManager != null) {
                    noteListManager.refreshNotes();
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            runOnUiThread(() -> {
                progressDialog.dismiss();
                Toast.makeText(MainActivity.this, "合并失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            });
        }
    }).start();
}
```

- [ ] **Step 2: 实现 getNoteTimeStringForMerge 辅助方法**

```java
/**
 * 获取笔记的时间字符串（用于合并内容）
 */
private String getNoteTimeStringForMerge(long noteId, long timestamp) {
    Cursor cursor = dbHelper.getTimeRangesForNote(noteId);
    if (cursor != null && cursor.moveToFirst()) {
        long startTime = cursor.getLong(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_START_TIME));
        long endTime = cursor.getLong(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_END_TIME));
        cursor.close();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
        return "[" + dateFormat.format(startTime) + "-" + dateFormat.format(endTime) + "]";
    }
    if (cursor != null) {
        cursor.close();
    }

    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    return "[" + sdf.format(timestamp) + "]";
}
```

- [ ] **Step 3: 实现 getTagsForNote 辅助方法**

```java
/**
 * 获取笔记的标签列表
 */
private List<String> getTagsForNote(long noteId) {
    List<String> tags = new ArrayList<>();
    Cursor cursor = dbHelper.getTagsForNote(noteId);
    if (cursor != null) {
        while (cursor.moveToNext()) {
            String tagName = cursor.getString(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_TAG_NAME));
            if (tagName != null) {
                tags.add(tagName);
            }
        }
        cursor.close();
    }
    return tags;
}
```

- [ ] **Step 4: 实现 getCommentsForNote 辅助方法**

```java
/**
 * 获取笔记的评论内容列表
 */
private List<String> getCommentsForNote(long noteId) {
    List<String> comments = new ArrayList<>();
    Cursor cursor = dbHelper.getCommentsForNote(noteId);
    if (cursor != null) {
        while (cursor.moveToNext()) {
            String content = cursor.getString(cursor.getColumnIndexOrThrow(NoteDbHelper.COLUMN_COMMENT_CONTENT));
            if (content != null) {
                comments.add(content);
            }
        }
        cursor.close();
    }
    return comments;
}
```

---

## Task 6: 添加必要的 import

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 检查并添加缺失的 import**

确保以下 import 存在：

```java
import android.app.ProgressDialog;
import person.notfresh.noteplus.core.model.AudioAttachment;
import java.util.Collections;
```

如果 `AudioAttachment` 没有被 import，添加它。如果 `Collections` 没有被 import，添加它。

---

## Task 7: 创建合并图标

**Files:**
- Create: `app/src/main/res/drawable/ic_merge.xml`

- [ ] **Step 1: 创建 ic_merge.xml 图标**

如果需要新图标，创建合并图标（两个箭头合并的样式）：

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
    <path
        android:fillColor="#FF000000"
        android:pathData="M17,20.41L18.41,19L15,15.59L13.59,17M7.5,8H11v5.59L5.59,19L7,20.41L13,14.41V8h3.5L12,3.5"/>
</vector>
```

或者临时复用现有图标 `@drawable/ic_combine` 或 `@drawable/ic_move`，后续再替换。

---

## 自检清单

- [ ] Spec coverage: 检查设计文档中每个需求是否都有对应实现
- [ ] Placeholder scan: 检查没有 TBD、TODO 等占位符
- [ ] Type consistency: 检查方法签名和类型一致性

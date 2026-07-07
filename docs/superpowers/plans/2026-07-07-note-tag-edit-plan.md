# 笔记标签编辑功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在笔记列表中为已有笔记添加标签编辑功能，支持添加/删除标签

**Architecture:** 在 `NoteListManager.addCommentsInfo()` 方法中添加"标签"按钮，点击弹出标签编辑对话框。复用数据库已有的 `linkNoteToTag` 和 `unlinkNoteFromTag` 方法。

**Tech Stack:** Android, SQLite, AlertDialog

---

## 文件变更

- `NoteListManager.java`: 添加标签按钮和标签编辑弹窗逻辑
- 新建 `layout/dialog_note_tag_edit.xml`: 标签编辑对话框布局

---

## Task 1: 添加标签按钮到列表项

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java:1521-1534`

- [ ] **Step 1: 找到 addCommentsInfo 方法中"追加内容"按钮的位置**

在 `addCommentsInfo` 方法中，"追加内容"按钮创建代码约为第1521-1534行。

- [ ] **Step 2: 在"追加内容"按钮旁边添加"标签"按钮**

在 `addCommentButton.setOnClickListener` 之后，添加：

```java
// 添加"标签"按钮
TextView tagButton = new TextView(context);
tagButton.setText("标签");
tagButton.setTextColor(0xFF4CAF50); // 绿色，与追加内容按钮区分
tagButton.setTextSize(14);
LinearLayout.LayoutParams tagButtonParams = new LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT);
tagButtonParams.setMargins(DisplayUtil.dpToPx(context, 16), 0, 0, 0);
tagButton.setLayoutParams(tagButtonParams);
tagButton.setOnClickListener(v -> {
    showNoteTagDialog(noteId);
});
actionLayout.addView(tagButton);
```

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat(tag-edit): add tag button next to add comment button"
```

---

## Task 2: 创建标签编辑对话框布局

**Files:**
- Create: `app/src/main/res/layout/dialog_note_tag_edit.xml`

- [ ] **Step 1: 创建标签编辑对话框布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <!-- 当前笔记标签区域 -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="当前标签"
        android:textStyle="bold"
        android:textSize="14sp"
        android:layout_marginBottom="8dp" />

    <LinearLayout
        android:id="@+id/currentTagsContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:layout_marginBottom="16dp" />

    <!-- 分隔线 -->
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#E0E0E0"
        android:layout_marginBottom="16dp" />

    <!-- 添加标签区域 -->
    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="添加标签"
        android:textStyle="bold"
        android:textSize="14sp"
        android:layout_marginBottom="8dp" />

    <LinearLayout
        android:id="@+id/allTagsContainer"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:layout_marginBottom="16dp" />

    <!-- 创建新标签区域 -->
    <View
        android:layout_width="match_parent"
        android:layout_height="1dp"
        android:background="#E0E0E0"
        android:layout_marginBottom="16dp" />

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="创建新标签"
        android:textStyle="bold"
        android:textSize="14sp"
        android:layout_marginBottom="8dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal">

        <EditText
            android:id="@+id/editNewTagName"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:hint="标签名称"
            android:inputType="text"
            android:layout_marginEnd="8dp" />

        <Button
            android:id="@+id/buttonCreateTag"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="创建" />

    </LinearLayout>

</LinearLayout>
```

- [ ] **Step 2: 提交代码**

```bash
git add app/src/main/res/layout/dialog_note_tag_edit.xml
git commit -m "feat(tag-edit): add tag edit dialog layout"
```

---

## Task 3: 实现 showNoteTagDialog 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java`

需要先查看 NoteListManager 中的 import 和 callback 接口，确认 dbHelper 获取方式。

- [ ] **Step 1: 在 NoteListManager 末尾添加 showNoteTagDialog 方法**

在 `NoteListManager.java` 文件末尾，`}` 之前添加：

```java
/**
 * 显示笔记标签编辑对话框
 * @param noteId 笔记ID
 */
public void showNoteTagDialog(long noteId) {
    if (callback == null) {
        return;
    }

    NoteDbHelper dbHelper = callback.getDbHelper();
    Context context = callback.getContext();
    if (dbHelper == null || context == null) {
        return;
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_note_tag_edit, null);
    builder.setView(dialogView);
    builder.setTitle("编辑标签");

    // 获取容器
    LinearLayout currentTagsContainer = dialogView.findViewById(R.id.currentTagsContainer);
    LinearLayout allTagsContainer = dialogView.findViewById(R.id.allTagsContainer);
    EditText editNewTagName = dialogView.findViewById(R.id.editNewTagName);
    Button buttonCreateTag = dialogView.findViewById(R.id.buttonCreateTag);

    // 加载当前笔记的标签
    Cursor currentTagsCursor = dbHelper.getTagsForNote(noteId);
    List<Long> currentTagIds = new ArrayList<>();
    if (currentTagsCursor != null && currentTagsCursor.getCount() > 0) {
        while (currentTagsCursor.moveToNext()) {
            @SuppressLint("Range") long tagId = currentTagsCursor.getLong(currentTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_ID));
            @SuppressLint("Range") String tagName = currentTagsCursor.getString(currentTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
            @SuppressLint("Range") String tagColor = currentTagsCursor.getString(currentTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));
            currentTagIds.add(tagId);

            // 创建标签视图（带删除按钮）
            LinearLayout tagLayout = new LinearLayout(context);
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            tagLayout.setGravity(android.view.Gravity.CENTER_VERTICAL);

            TextView tagView = new TextView(context);
            tagView.setText(tagName);
            tagView.setPadding(DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4), DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4));
            tagView.setTextColor(Color.WHITE);
            tagView.setTextSize(12);
            try {
                tagView.setBackgroundColor(Color.parseColor(tagColor));
            } catch (Exception e) {
                tagView.setBackgroundColor(Color.GRAY);
            }

            TextView removeBtn = new TextView(context);
            removeBtn.setText(" ×");
            removeBtn.setTextColor(Color.RED);
            removeBtn.setTextSize(16);
            removeBtn.setOnClickListener(v -> {
                dbHelper.unlinkNoteFromTag(noteId, tagId);
                refreshNoteView(noteId);
                // 刷新对话框
                builder.create().dismiss();
                showNoteTagDialog(noteId);
            });

            tagLayout.addView(tagView);
            tagLayout.addView(removeBtn);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 8));
            tagLayout.setLayoutParams(params);

            currentTagsContainer.addView(tagLayout);
        }
        currentTagsCursor.close();
    }

    // 加载所有可用标签（排除已添加的）
    Cursor allTagsCursor = dbHelper.getAllTags();
    if (allTagsCursor != null && allTagsCursor.getCount() > 0) {
        while (allTagsCursor.moveToNext()) {
            @SuppressLint("Range") long tagId = allTagsCursor.getLong(allTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_ID));
            // 跳过已添加的标签
            if (currentTagIds.contains(tagId)) {
                continue;
            }
            @SuppressLint("Range") String tagName = allTagsCursor.getString(allTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_NAME));
            @SuppressLint("Range") String tagColor = allTagsCursor.getString(allTagsCursor.getColumnIndex(NoteDbHelper.COLUMN_TAG_COLOR));

            TextView tagView = new TextView(context);
            tagView.setText("+ " + tagName);
            tagView.setPadding(DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4), DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 4));
            tagView.setTextColor(tagColor);
            tagView.setTextSize(12);
            tagView.setBackgroundResource(android.R.drawable.edit_text);
            tagView.setOnClickListener(v -> {
                dbHelper.linkNoteToTag(noteId, tagId);
                refreshNoteView(noteId);
                // 刷新对话框
                builder.create().dismiss();
                showNoteTagDialog(noteId);
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, DisplayUtil.dpToPx(context, 8), DisplayUtil.dpToPx(context, 8));
            tagView.setLayoutParams(params);

            allTagsContainer.addView(tagView);
        }
        allTagsCursor.close();
    }

    // 创建按钮点击事件
    buttonCreateTag.setOnClickListener(v -> {
        String newTagName = editNewTagName.getText().toString().trim();
        if (!newTagName.isEmpty()) {
            long newTagId = dbHelper.addTag(newTagName, "#2196F3");
            if (newTagId > 0) {
                dbHelper.linkNoteToTag(noteId, newTagId);
                refreshNoteView(noteId);
                builder.create().dismiss();
                showNoteTagDialog(noteId);
            }
        }
    });

    builder.setNegativeButton("关闭", null);
    builder.show();
}
```

- [ ] **Step 2: 添加必要的 import 语句**

在文件顶部添加：
```java
import android.suppressLint;
import android.suppressWarnings;
import java.util.ArrayList;
import java.util.List;
```

注意：`@SuppressLint("Range")` 是用于抑制 SQLite cursor getColumnIndex 的 lint 警告。

- [ ] **Step 3: 提交代码**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat(tag-edit): implement showNoteTagDialog method"
```

---

## Task 4: 测试和验证

- [ ] **Step 1: 运行构建验证**

```bash
cd app && ./gradlew assembleDebug
```

- [ ] **Step 2: 在设备上测试**
1. 打开应用，进入任意项目
2. 创建一条带标签的笔记
3. 在列表中找到该笔记
4. 点击"标签"按钮
5. 验证：
   - 对话框正确显示当前笔记的标签
   - 可以点击已有标签进行添加
   - 可以点击×删除已有标签
   - 可以创建新标签并自动添加

- [ ] **Step 3: 最终提交**

```bash
git add -A
git commit -m "feat: complete note tag editing feature"
```

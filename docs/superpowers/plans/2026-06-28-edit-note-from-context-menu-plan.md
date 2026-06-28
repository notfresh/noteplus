# 编辑笔记功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在笔记列表长按菜单中增加"编辑"选项，点击后弹出全屏编辑框修改笔记内容。

**Architecture:** 复用现有的全屏编辑对话框 `dialog_fullscreen_edit.xml`，通过 ContentValues 执行 UPDATE 操作更新数据库，编辑完成后刷新单条笔记视图。

**Tech Stack:** Android, Java, SQLite (ContentValues)

---

## 文件变更清单

| 文件 | 变更类型 | 职责 |
|------|----------|------|
| `NoteListManager.java` | 修改 | 在 `showNoteOptionsMenu` 增加"编辑"选项；新增 `showEditNoteDialog()` 和 `updateNoteContent()` 方法 |

---

## 实现步骤

### Task 1: 在长按菜单增加"编辑"选项

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java:1978`

- [ ] **Step 1: 修改选项数组，在"复制到剪切板"后添加"编辑"**

找到 `NoteListManager.java` 第 1978 行：
```java
String[] options = {"复制到剪切板", "追加内容", isPinned ? "取消置顶" : "置顶", "合并到...", "移动到...", "归档", "删除"};
```

改为：
```java
String[] options = {"复制到剪切板", "编辑", "追加内容", isPinned ? "取消置顶" : "置顶", "合并到...", "移动到...", "归档", "删除"};
```

- [ ] **Step 2: 在 switch 语句中添加 case 1 处理"编辑"选项**

在 `showNoteOptionsMenu` 方法中，找到 switch 语句，case 1 目前是"追加内容"。需要在 case 0（复制到剪切板）后添加 case 1（编辑），原 case 1-6 序号依次递增。

原始 switch 结构：
```java
switch (which) {
    case 0: // 复制到剪切板
        copyToClipboard(noteId);
        break;
    case 1: // 追加内容
        showAddCommentDialog(noteId, null);
        break;
    case 2: // 置顶/取消置顶
        togglePinNote(noteId);
        break;
    // ... etc
}
```

改为：
```java
switch (which) {
    case 0: // 复制到剪切板
        copyToClipboard(noteId);
        break;
    case 1: // 编辑
        showEditNoteDialog(noteId);
        break;
    case 2: // 追加内容
        showAddCommentDialog(noteId, null);
        break;
    case 3: // 置顶/取消置顶
        togglePinNote(noteId);
        break;
    // ... etc
}
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat: 在笔记长按菜单增加编辑选项"
```

---

### Task 2: 新增 `showEditNoteDialog(long noteId)` 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java`

- [ ] **Step 1: 在 NoteListManager 类中添加 `showEditNoteDialog` 方法**

在 `NoteListManager.java` 中找一个合适的位置添加此方法（建议放在 `copyToClipboard` 方法附近）：

```java
/**
 * 显示编辑笔记对话框
 * 复用全屏编辑布局，支持修改笔记内容
 * @param noteId 笔记ID
 */
public void showEditNoteDialog(long noteId) {
    if (callback == null) {
        return;
    }

    Context context = callback.getContext();
    NoteDbHelper dbHelper = callback.getDbHelper();
    if (context == null || dbHelper == null) {
        return;
    }

    // 从 adapter 中获取笔记内容
    String currentContent = null;
    if (adapter != null) {
        for (int i = 0; i < adapter.getCount(); i++) {
            Note note = adapter.getItem(i);
            if (note != null && note.getId() == noteId) {
                currentContent = note.getContent();
                break;
            }
        }
    }

    // 如果 adapter 中找不到，从数据库加载
    if (currentContent == null) {
        currentContent = dbHelper.getNoteContentById(noteId);
    }

    // 创建全屏编辑对话框
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    View fullscreenView = LayoutInflater.from(context).inflate(R.layout.dialog_fullscreen_edit, null);
    builder.setView(fullscreenView);

    AlertDialog dialog = builder.create();

    EditText fullscreenEditText = fullscreenView.findViewById(R.id.fullscreenEditText);
    ImageButton btnExitFullscreen = fullscreenView.findViewById(R.id.btnExitFullscreen);
    Button btnSaveFullscreen = fullscreenView.findViewById(R.id.btnSaveFullscreen);

    // 填充当前内容
    fullscreenEditText.setText(currentContent != null ? currentContent : "");
    if (currentContent != null && currentContent.length() > 0) {
        fullscreenEditText.setSelection(currentContent.length());
    }

    // 退出按钮：关闭对话框，不保存
    btnExitFullscreen.setOnClickListener(v -> dialog.dismiss());

    // 保存按钮：更新笔记内容并关闭
    btnSaveFullscreen.setOnClickListener(v -> {
        String newContent = fullscreenEditText.getText().toString().trim();
        if (!newContent.isEmpty()) {
            updateNoteContent(noteId, newContent);
            refreshNoteView(noteId);
        } else {
            Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show();
            return;
        }
        dialog.dismiss();
    });

    // 设置窗口属性
    Window window = dialog.getWindow();
    if (window != null) {
        window.setBackgroundDrawableResource(android.R.color.white);
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }

    dialog.show();

    // 显示键盘
    fullscreenEditText.post(() -> {
        fullscreenEditText.requestFocus();
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.showSoftInput(fullscreenEditText, InputMethodManager.SHOW_IMPLICIT);
        }
    });
}
```

**注意**：需要确保 `NoteListManager.java` 中已导入以下类：
```java
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.EditorInfo;
import android.view.WindowManager;
import android.content.DialogInterface;
import android.widget.Button;
import android.app.AlertDialog;
```

- [ ] **Step 2: 提交变更**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat: 新增 showEditNoteDialog 方法支持编辑笔记"
```

---

### Task 3: 新增 `updateNoteContent(long noteId, String content)` 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java`

- [ ] **Step 1: 在 `showEditNoteDialog` 方法附近添加 `updateNoteContent` 方法**

```java
/**
 * 更新笔记内容
 * @param noteId 笔记ID
 * @param content 新的笔记内容
 */
private void updateNoteContent(long noteId, String content) {
    if (callback == null) {
        return;
    }

    NoteDbHelper dbHelper = callback.getDbHelper();
    if (dbHelper == null) {
        return;
    }

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    ContentValues values = new ContentValues();
    values.put(NoteDbHelper.COLUMN_CONTENT, content);
    values.put(NoteDbHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());

    int rowsUpdated = db.update(
        NoteDbHelper.TABLE_NOTES,
        values,
        NoteDbHelper.COLUMN_ID + "=?",
        new String[]{String.valueOf(noteId)}
    );

    if (rowsUpdated > 0) {
        Context context = callback.getContext();
        if (context != null) {
            Toast.makeText(context, "笔记已更新", Toast.LENGTH_SHORT).show();
        }
    } else {
        Context context = callback.getContext();
        if (context != null) {
            Toast.makeText(context, "更新失败", Toast.LENGTH_SHORT).show();
        }
    }
}
```

- [ ] **Step 2: 确保已导入 SQLiteDatabase 和 ContentValues**

检查 `NoteListManager.java` 顶部 import 区域，如有以下导入则跳过，否则添加：
```java
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
```

- [ ] **Step 3: 提交变更**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat: 新增 updateNoteContent 方法执行笔记更新"
```

---

### Task 4: 验证与测试

**Files:**
- Test: 手动测试

- [ ] **Step 1: 编译项目验证无语法错误**

```bash
cd app && ./gradlew assembleDebug 2>&1 | tail -20
```
预期：无编译错误

- [ ] **Step 2: 手动测试流程**

1. 运行应用，进入笔记列表
2. 长按任意一条笔记，确认弹出菜单中有"编辑"选项
3. 点击"编辑"，确认弹出全屏编辑框，内容为该笔记原有内容
4. 修改内容并点击保存，确认：
   - Toast 提示"笔记已更新"
   - 笔记列表中该笔记显示已更新内容
5. 点击编辑框的退出按钮，确认不保存直接关闭

- [ ] **Step 3: 提交所有变更**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat: 完成编辑笔记功能实现"
```

---

## 验证清单

- [ ] 长按笔记弹出菜单包含"编辑"选项
- [ ] 点击"编辑"弹出全屏编辑框
- [ ] 编辑框预填充笔记原有内容
- [ ] 保存后笔记内容更新
- [ ] 保存后列表正确刷新
- [ ] 点击退出按钮不保存直接关闭
- [ ] 内容为空时提示"内容不能为空"

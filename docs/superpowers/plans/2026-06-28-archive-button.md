# 存档按钮 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在笔记全屏编辑对话框中添加"存档"按钮，点击后保存笔记内容但不关闭对话框，用户可继续编辑。

**架构：** 在全屏编辑对话框布局中添加存档按钮；在 `NoteListManager.showEditNoteDialog()` 中绑定点击事件，执行保存逻辑但不调用 `dialog.dismiss()`。

**技术栈：** Android / Java / XML 布局

---

## 文件结构

- **修改:** `app/src/main/res/layout/dialog_fullscreen_edit.xml` — 添加存档按钮
- **修改:** `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java` — 绑定点击事件

---

## 任务 1: 在布局文件中添加存档按钮

**文件:**
- 修改: `app/src/main/res/layout/dialog_fullscreen_edit.xml`

在"保存"按钮（`btnSaveFullscreen`）右侧添加存档按钮：

```xml
<Button
    android:id="@+id/btnArchive"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:text="存档"
    android:textColor="#FFFFFF"
    android:backgroundTint="@android:color/transparent"
    android:minWidth="0dp"
    android:paddingStart="8dp"
    android:paddingEnd="8dp" />
```

工具栏从左到右顺序：`btnInsertTimestamp` → spacer → `btnExitFullscreen` → `btnSaveFullscreen` → `btnArchive`

---

## 任务 2: 在 NoteListManager 中绑定存档按钮点击事件

**文件:**
- 修改: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java` — `showEditNoteDialog()` 方法

在 `btnSaveFullscreen.setOnClickListener` 之后添加存档按钮的点击事件：

```java
Button btnArchive = fullscreenView.findViewById(R.id.btnArchive);
btnArchive.setOnClickListener(v -> {
    String newContent = fullscreenEditText.getText().toString().trim();
    if (!newContent.isEmpty()) {
        updateNoteContent(noteId, newContent);
        Toast.makeText(context, "已存档", Toast.LENGTH_SHORT).show();
    } else {
        Toast.makeText(context, "内容不能为空", Toast.LENGTH_SHORT).show();
    }
});
```

注意：`btnArchive` 需要加到现有的 null 检查中。

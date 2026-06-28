# 存档按钮 — 设计文档

## 概述

在笔记全屏编辑对话框中添加"存档"按钮，点击后保存笔记内容但不关闭对话框，用户可以继续编辑。

## 行为定义

- 点击"存档" → 保存内容到数据库 → Toast 提示"已存档" → 对话框保持打开，光标保持在原位，键盘保持显示

## 修改文件

### 1. `res/layout/dialog_fullscreen_edit.xml`

在"保存"按钮右侧添加"存档" Button：

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

工具栏按钮从左到右顺序：`btnInsertTimestamp` → spacer → `btnExitFullscreen` → `btnSaveFullscreen` → `btnArchive`

### 2. `manager/NoteListManager.java` — `showEditNoteDialog()` 方法

添加 `btnArchive` 的点击事件：

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

注意：不调用 `dialog.dismiss()`，对话框保持打开。

## 验证要点

- 点击存档后笔记内容被保存到数据库
- 对话框保持打开，用户可以继续编辑
- 光标位置保持不变
- Toast 提示"已存档"显示
- 键盘保持显示

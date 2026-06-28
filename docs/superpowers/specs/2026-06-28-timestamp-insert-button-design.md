# 时间戳插入按钮 — 设计文档

## 概述

在笔记全屏编辑对话框的顶部工具栏最左侧添加一个时间戳按钮，点击后向 EditText 光标位置插入当前时间戳，格式为 `[yyyy-MM-dd HH:mm:ss]\n`。

## 修改文件

### 1. `res/layout/dialog_fullscreen_edit.xml`

在顶部工具栏的最左侧（退出按钮左边）添加时间戳按钮：

```xml
<ImageButton
    android:id="@+id/btnInsertTimestamp"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:src="@drawable/ic_timestamp"  <!-- 或用现有图标 -->
    android:tint="#FFFFFF"
    android:backgroundTint="@android:color/transparent"
    android:contentDescription="插入时间戳"
    android:padding="8dp"
    android:layout_marginEnd="8dp" />
```

### 2. `manager/NoteListManager.java` — `showEditNoteDialog()` 方法

在 `btnExitFullscreen` 按钮查找之后，添加时间戳按钮的点击事件：

```java
ImageButton btnInsertTimestamp = fullscreenView.findViewById(R.id.btnInsertTimestamp);
btnInsertTimestamp.setOnClickListener(v -> {
    SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]\n", Locale.CHINA);
    String timestamp = sdf.format(new Date());
    int cursorPosition = fullscreenEditText.getSelectionStart();
    fullscreenEditText.getText().insert(cursorPosition, timestamp);
});
```

## 时间戳格式

`[2026-06-28 15:30:00]\n`

格式说明：
- 方括号包裹
- 年-月-日 时:分:秒
- 末尾自动换行

## 验证要点

- 按钮显示在工具栏最左侧
- 点击按钮后在光标位置插入时间戳
- 光标在文本中间时，时间戳插入到光标位置而非末尾
- 插入后光标位于时间戳之后换行符之后

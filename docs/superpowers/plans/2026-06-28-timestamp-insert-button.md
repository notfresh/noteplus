# 时间戳插入按钮 — 实现计划

> **For agentistic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 在笔记全屏编辑对话框顶部工具栏最左侧添加时间戳按钮，点击后向 EditText 光标位置插入当前时间戳，格式为 `[yyyy-MM-dd HH:mm:ss]\n`。

**架构：** 修改全屏编辑对话框的 XML 布局，在工具栏添加时间戳 ImageButton；在 `NoteListManager.showEditNoteDialog()` 方法中为按钮绑定点击事件，插入时间戳文本。

**技术栈：** Android / Java / XML 布局

---

## 文件结构

- **修改:** `app/src/main/res/layout/dialog_fullscreen_edit.xml` — 添加时间戳按钮
- **修改:** `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java` — 绑定点击事件

---

## 任务 1: 在布局文件中添加时间戳按钮

**文件:**
- 修改: `app/src/main/res/layout/dialog_fullscreen_edit.xml:19-45`

- [ ] **Step 1: 在工具栏最左侧添加 ImageButton**

在退出按钮（`btnExitFullscreen`）左侧插入时间戳按钮，使用文字按钮形式（直接显示 "🕐" 或 "时间"）以避免需要新的图标资源：

```xml
<LinearLayout
    android:layout_width="match_parent"
    android:layout_height="?attr/actionBarSize"
    android:orientation="horizontal"
    android:gravity="center_vertical|end"
    android:paddingStart="16dp"
    android:paddingEnd="16dp"
    android:background="?attr/colorPrimary"
    android:elevation="4dp">

    <!-- 时间戳按钮 - 放在最左侧 -->
    <Button
        android:id="@+id/btnInsertTimestamp"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="🕐"
        android:textColor="#FFFFFF"
        android:backgroundTint="@android:color/transparent"
        android:minWidth="0dp"
        android:paddingStart="8dp"
        android:paddingEnd="8dp"
        android:contentDescription="插入时间戳" />

    <View
        android:layout_width="0dp"
        android:layout_height="1dp"
        android:layout_weight="1" />

    <ImageButton
        android:id="@+id/btnExitFullscreen"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:src="@drawable/ic_collapse"
        android:tint="#FFFFFF"
        android:backgroundTint="@android:color/transparent"
        android:contentDescription="退出全屏"
        android:padding="8dp"
        android:layout_marginEnd="8dp" />

    <Button
        android:id="@+id/btnSaveFullscreen"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="保存"
        android:textColor="#FFFFFF"
        android:backgroundTint="@android:color/transparent"
        android:minWidth="0dp"
        android:paddingStart="8dp"
        android:paddingEnd="8dp" />
</LinearLayout>
```

---

## 任务 2: 在 NoteListManager 中绑定时间戳按钮点击事件

**文件:**
- 修改: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java:689-735`（`showEditNoteDialog()` 方法区域）

- [ ] **Step 1: 找到 btnInsertTimestamp 并设置点击事件**

在 `btnExitFullscreen` 设置点击事件的代码之后，添加：

```java
// 插入时间戳按钮：在光标位置插入当前时间戳
ImageButton btnInsertTimestamp = fullscreenView.findViewById(R.id.btnInsertTimestamp);
btnInsertTimestamp.setOnClickListener(v -> {
    SimpleDateFormat sdf = new SimpleDateFormat("[yyyy-MM-dd HH:mm:ss]\n", Locale.CHINA);
    String timestamp = sdf.format(new Date());
    int cursorPosition = fullscreenEditText.getSelectionStart();
    fullscreenEditText.getText().insert(cursorPosition, timestamp);
});
```

需要确认 `showEditNoteDialog()` 方法中已有 `SimpleDateFormat` 和 `Date` 的 import（查看现有代码，import 已有 `java.text.SimpleDateFormat` 和 `java.util.Date`，但需要确认 `Locale.CHINA`）。

---

## 自检清单

1. **Spec 覆盖检查:** 设计文档中的需求是否都有对应实现？
   - ✅ 按钮放在工具栏最左侧
   - ✅ 点击插入时间戳 `[yyyy-MM-dd HH:mm:ss]\n`
   - ✅ 插入到光标位置

2. **占位符检查:** 无 TBD/TODO/待实现等占位符

3. **类型一致性检查:** `fullscreenEditText.getText().insert()` — `EditText` 的 `getText()` 返回 `Editable`，`insert` 方法存在

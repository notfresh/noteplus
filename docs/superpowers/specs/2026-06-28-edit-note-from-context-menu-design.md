# 设计方案：为笔记长按菜单增加"编辑"功能

## 需求
在笔记列表长按弹出的选项菜单中，增加"编辑"选项，允许用户重新编辑已有笔记的内容。

## 现状分析
- `showNoteOptionsMenu()` 目前提供的选项：复制到剪切板、追加内容、置顶、合并到、移动到、归档、删除
- `showFullscreenEditor()` 用于从底部输入框展开全屏编辑，但只支持新建笔记
- `saveMoment()` 执行 INSERT 操作，无 UPDATE 逻辑

## 方案设计

### 1. 在 `NoteListManager.showNoteOptionsMenu()` 中增加"编辑"选项

**文件**: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java`

在现有选项数组中，"编辑"放在"复制到剪切板"之后：

```java
String[] options = {"复制到剪切板", "编辑", "追加内容", isPinned ? "取消置顶" : "置顶", ...};
```

### 2. 新增 `showEditNoteDialog(long noteId)` 方法

**职责**:
- 创建全屏编辑对话框（复用 `dialog_fullscreen_edit.xml` 布局）
- 从数据库加载指定 `noteId` 的内容并预填充
- 保存时执行 UPDATE 而不是 INSERT
- 编辑完成后刷新该笔记的显示

**逻辑流程**:
1. 获取笔记当前内容（从 adapter 中已加载的 Note 对象，或从数据库查询）
2. 创建全屏对话框，设置编辑框内容
3. 用户点击保存 → 调用 `updateNoteContent()` 更新数据库 → 刷新视图

### 3. 新增 `updateNoteContent(long noteId, String content)` 方法

**文件**: `NoteListManager.java`

使用 ContentValues 执行 UPDATE：

```java
private void updateNoteContent(long noteId, String content) {
    NoteDbHelper dbHelper = callback.getDbHelper();
    if (dbHelper == null) return;

    ContentValues values = new ContentValues();
    values.put(NoteDbHelper.COLUMN_CONTENT, content);
    values.put(NoteDbHelper.COLUMN_TIMESTAMP, System.currentTimeMillis());

    SQLiteDatabase db = dbHelper.getWritableDatabase();
    db.update(NoteDbHelper.TABLE_NOTES, values,
               NoteDbHelper.COLUMN_ID + "=?", new String[]{String.valueOf(noteId)});
}
```

### 4. 编辑完成后刷新视图

调用 `refreshNoteView(noteId)` 刷新单条笔记的显示（折叠状态、置顶标识等保持不变）。

## 改动文件清单

| 文件 | 改动内容 |
|------|----------|
| `NoteListManager.java` | 1. 在 `showNoteOptionsMenu` 增加"编辑"选项<br>2. 新增 `showEditNoteDialog()` 方法<br>3. 新增 `updateNoteContent()` 方法 |

## 数据流

```
长按笔记 → showNoteOptionsMenu → 选择"编辑"
→ showEditNoteDialog(noteId) → 加载内容到全屏编辑框
→ 用户修改内容 → 点击保存
→ updateNoteContent(noteId, content) → 数据库 UPDATE
→ refreshNoteView(noteId) → 刷新单条笔记显示
```

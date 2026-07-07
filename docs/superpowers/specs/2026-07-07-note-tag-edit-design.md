# 笔记标签编辑功能设计

## 概述

在笔记列表中，为已有笔记添加标签编辑功能。用户可以在列表项中直接点击"标签"按钮，弹窗编辑该笔记的标签（添加/删除）。

## UI 交互

### 位置
在笔记列表项的"追加内容"按钮旁边添加"标签"按钮。

### 标签编辑弹窗
- 显示当前笔记已有标签（右侧带×删除按钮）
- 显示所有可用标签列表（点击添加）
- 提供"创建新标签"输入框和创建按钮

### 数据操作
- 添加标签：调用 `dbHelper.linkNoteToTag(noteId, tagId)`
- 删除标签：调用 `dbHelper.unlinkNoteFromTag(noteId, tagId)`
- 刷新：编辑后调用 `refreshNoteView(noteId)` 刷新列表项

## 实现要点

### 1. 添加标签按钮
在 `NoteListManager.addCommentsInfo()` 方法中，在"追加内容"按钮旁边添加"标签"按钮。

### 2. 新建标签编辑对话框方法
在 `NoteListManager` 中新建 `showNoteTagDialog(long noteId)` 方法，显示标签编辑弹窗。

### 3. 复用现有组件
- 数据库已有 `linkNoteToTag` 和 `unlinkNoteFromTag` 方法
- 复用 `dialog_tag_selection.xml` 布局风格
- 复用 `showTagSelectionDialog()` 中的标签创建逻辑

### 4. 刷新列表
编辑完成后调用 `refreshNoteView(noteId)` 刷新该笔记项的标签显示。

## 文件变更

- `NoteListManager.java`：添加标签按钮和标签编辑弹窗逻辑
- 复用 `R.layout.dialog_note_detail` 现有布局或新建简单布局

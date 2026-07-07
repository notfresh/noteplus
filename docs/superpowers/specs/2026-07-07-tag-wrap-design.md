# 标签自动换行修复设计

## 概述

修复标签显示和编辑对话框中的标签换行问题，使用 FlexboxLayout 实现水平排列自动换行。

## 问题

1. **列表显示**：标签用 `LinearLayout(HORIZONTAL)`，所有标签挤在一行
2. **编辑对话框**：当前标签容器需确认是否正确换行

## 解决方案

使用 Google FlexboxLayout 替代 `LinearLayout`：
- 支持 `flexWrap="wrap"` 自动换行
- 保持标签水平排列
- 标签多时自动换行到下一行

## 实现要点

### 1. 添加依赖
在 `app/build.gradle` 添加：
```groovy
implementation 'com.google.android.flexbox:flexbox:3.0.0'
```

### 2. 修改 addTagsInfo（列表显示）
将 `LinearLayout` 改为 `FlexboxLayout`：
- `flexWrap="wrap"`
- `flexDirection="row"`
- `alignItems="center"`

### 3. 修改 showNoteTagDialog（编辑对话框）
同样将相关容器改为 FlexboxLayout

## 文件变更

- `app/build.gradle`: 添加 FlexboxLayout 依赖
- `NoteListManager.java`: 修改 `addTagsInfo` 和 `showNoteTagDialog` 使用 FlexboxLayout

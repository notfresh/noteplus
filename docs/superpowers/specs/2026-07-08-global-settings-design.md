# 全局设置功能设计

## 概述

在应用右上角菜单新增"全局设置"入口，提供跨项目通用设置和应用级设置，与现有的"项目设置"形成互补。

## 优先级规则

**项目设置 > 全局设置**（类似编程语言中局部变量优先于全局变量）

当某项设置在项目级别未配置时，自动使用全局设置的值。

## 入口变更

- 右上角菜单新增"全局设置"菜单项
- 现有"项目设置"保持不变，仅包含当前项目的专属设置

## 全局设置内容

### 通用设置（跨项目）

| 设置项 | 说明 | 默认值 |
|--------|------|--------|
| 时间排序顺序 | 所有项目的默认排序方式 | 时间降序 |
| 折叠显示字数 | 所有项目的默认折叠字数 | 300 |

### 应用设置

| 设置项 | 说明 |
|--------|------|
| 关于 | 应用版本信息 |
| 数据管理 | 备份/恢复/全量导出/全量导入 |
| 提醒设置 | 定时提醒开关和间隔 |

## 全量导出/导入

### JSON 格式

```json
{
  "exportType": "all_projects",
  "exportDate": "2026-07-08",
  "projects": {
    "project_A": {
      "projectName": "A",
      "exportDate": "...",
      "notes": [...]
    }
  }
}
```

### 导入逻辑

1. 检测 `exportType` 字段
2. 如果是 `all_projects`，遍历 `projects` 对象
3. 每个子对象的结构与现有单项目导出格式一致
4. 复用现有的 `ImportExportManager.readJsonData()` 逻辑
5. 根据 `projectName` 路由到对应项目

### 文件命名

- 单项目导出：`项目名_日期.json`
- 全量导出：`NotePlus_AllProjects_日期.json`

## 文件变更

### 新增文件

- `app/src/main/res/layout/dialog_global_settings.xml` - 全局设置对话框布局

### 修改文件

- `app/src/main/res/menu/main_menu.xml` - 添加全局设置菜单项
- `app/src/main/java/person/notfresh/noteplus/MainActivity.java`
  - 添加 `showGlobalSettingsDialog()` 方法
  - 修改 `showSettingsDialog()` 读取全局设置作为默认值
  - 添加全量导出/导入逻辑
- `app/src/main/java/person/notfresh/noteplus/manager/ImportExportManager.java`
  - 添加 `exportAllProjects()` 方法
  - 修改 `readJsonData()` 支持检测 `exportType`

## 数据库变更

无需数据库变更。设置继续存储在 `settings` 表。

## 设置存储

全局设置 key 前缀：`global_`

| key | 说明 |
|-----|------|
| `global_time_desc_order` | 全局时间排序 |
| `global_fold_display_length` | 全局折叠字数 |

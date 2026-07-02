# 项目切换历史功能设计

## 概述

为长按项目切换功能添加历史记录，支持最多保存3个项目，支持循环切换和持久化存储。

## 需求

1. 手动点击切换按钮选择项目时，把该项目加入切换历史
2. 长按切换按钮时，从历史中按顺序循环切换（不更新历史）
3. 当前项目不在历史中时，自动加入历史后切换
4. 历史最多保存3个项目

## 设计

### 数据结构

在 `ProjectContextManager` 中新增：

- `List<String> projectSwitchHistory` - 内存中的历史列表
- 常量 `MAX_HISTORY_SIZE = 3`
- SharedPreferences Key: `KEY_PROJECT_SWITCH_HISTORY = "project_switch_history"`

### 持久化格式

使用逗号分隔存储：
```
project_switch_history = "项目C,项目B,项目A"
```

### 核心方法

| 方法 | 描述 |
|------|------|
| `addToSwitchHistory(project)` | 手动切换时调用，加入历史最前面，超出3个移除最老的 |
| `getSwitchHistory()` | 获取历史列表副本 |
| `getPreviousProjectFromHistory()` | 获取历史中的上一个项目（用于长按切换） |
| `saveHistory()` | 持久化到 SharedPreferences |
| `loadHistory()` | 启动时从 SharedPreferences 加载 |

### 交互逻辑

#### 手动点击切换按钮
```
用户点击项目 → addToSwitchHistory(目标项目) → switchProject(目标项目)
```

#### 长按切换按钮
```
长按 → 检查当前项目是否在历史中
  ├─ 不在 → addToSwitchHistory(当前项目)
  └─ 在 → 直接进行
→ getPreviousProjectFromHistory() 获取目标
→ switchProject(目标)
→ Toast 提示 "已切换到项目: XXX"
```

#### 循环切换顺序
假设历史为 [C, B, A]，当前在 C：
- 第1次长按 → 切换到 B
- 第2次长按 → 切换到 A
- 第3次长按 → 循环回 C

### 代码位置

主要修改 `ProjectContextManager.java`，新增方法：
- `addToSwitchHistory(String project)`
- `getSwitchHistory()`
- `getPreviousProjectFromHistory()`
- `saveHistory()`
- `loadHistory()`

同时修改 `MainActivity.java` 中的长按逻辑：
- `switchToPreviousProject()` 方法改为调用 `getPreviousProjectFromHistory()`

## 实现步骤

1. 在 `ProjectContextManager` 中添加历史相关的常量和变量
2. 实现 `loadHistory()` - 启动时加载历史
3. 实现 `saveHistory()` - 持久化历史
4. 实现 `addToSwitchHistory()` - 添加到历史
5. 实现 `getPreviousProjectFromHistory()` - 获取历史中的上一个
6. 修改 `switchToProject()` - 手动切换时调用 `addToSwitchHistory()`
7. 修改 `MainActivity.switchToPreviousProject()` - 使用新的历史方法
8. 移除旧的 `previousProjectName` 相关逻辑（如不再需要）

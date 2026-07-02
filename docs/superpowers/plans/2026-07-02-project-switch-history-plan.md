# 项目切换历史功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为项目切换功能添加历史记录，支持最多3个项目循环切换，支持持久化存储。

**Architecture:** 在 `ProjectContextManager` 中新增历史列表管理，替代原有的单个 `previousProjectName`。手动切换时添加到历史，长按切换时从历史循环获取。

**Tech Stack:** Android SharedPreferences 持久化，ArrayList 管理历史

---

## 文件结构

```
app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java  # 修改
app/src/main/java/person/notfresh/noteplus/MainActivity.java             # 修改
```

---

## Task 1: 在 ProjectContextManager 添加历史常量和变量

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java:28-39`

- [ ] **Step 1: 添加常量**

在 `ORDER_SEPARATOR` 后添加:

```java
private static final String KEY_PROJECT_SWITCH_HISTORY = "project_switch_history";
private static final int MAX_HISTORY_SIZE = 3;
```

- [ ] **Step 2: 添加历史列表变量**

将 `private String previousProjectName = null;` 替换为:

```java
// 项目切换历史列表（用于长按循环切换）
private List<String> projectSwitchHistory = new ArrayList<>();
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java
git commit -m "feat(project-history): Add history list variable and constants"
```

---

## Task 2: 实现历史加载和保存方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java`

- [ ] **Step 1: 在构造函数末尾添加 loadHistory() 调用**

在 `initializeDbHelper();` 后添加一行:

```java
loadHistory();
```

- [ ] **Step 2: 添加 loadHistory() 方法**

在 `getPreviousProject()` 方法前添加:

```java
/**
 * 从 SharedPreferences 加载切换历史
 */
private void loadHistory() {
    String historyStr = preferences.getString(KEY_PROJECT_SWITCH_HISTORY, "");
    projectSwitchHistory.clear();
    if (!historyStr.isEmpty()) {
        String[] parts = historyStr.split(ORDER_SEPARATOR);
        for (String p : parts) {
            if (!p.isEmpty()) {
                projectSwitchHistory.add(p);
            }
        }
    }
}
```

- [ ] **Step 3: 添加 saveHistory() 方法**

在 `loadHistory()` 方法后添加:

```java
/**
 * 保存切换历史到 SharedPreferences
 */
private void saveHistory() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < projectSwitchHistory.size(); i++) {
        if (i > 0) sb.append(ORDER_SEPARATOR);
        sb.append(projectSwitchHistory.get(i));
    }
    preferences.edit().putString(KEY_PROJECT_SWITCH_HISTORY, sb.toString()).apply();
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java
git commit -m "feat(project-history): Add loadHistory and saveHistory methods"
```

---

## Task 3: 实现 addToSwitchHistory 和 getPreviousProjectFromHistory

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java`

- [ ] **Step 1: 添加 getSwitchHistory() 公共方法**

在 `saveHistory()` 后添加:

```java
/**
 * 获取切换历史副本
 * @return 历史列表副本
 */
public List<String> getSwitchHistory() {
    return new ArrayList<>(projectSwitchHistory);
}
```

- [ ] **Step 2: 添加 addToSwitchHistory() 方法**

在 `getSwitchHistory()` 后添加:

```java
/**
 * 添加项目到切换历史（手动切换时调用）
 * 如果项目已在历史中，先移除再加入最前面；超出3个则移除最老的
 * @param project 项目名称
 */
public void addToSwitchHistory(String project) {
    if (project == null || project.isEmpty()) return;

    // 如果已存在，先移除
    projectSwitchHistory.remove(project);

    // 加入最前面
    projectSwitchHistory.add(0, project);

    // 超出容量则移除最老的
    while (projectSwitchHistory.size() > MAX_HISTORY_SIZE) {
        projectSwitchHistory.remove(projectSwitchHistory.size() - 1);
    }

    saveHistory();
}
```

- [ ] **Step 3: 添加 getPreviousProjectFromHistory() 方法**

在 `addToSwitchHistory()` 后添加:

```java
/**
 * 获取历史中的上一个项目（用于长按切换）
 * 如果当前项目不在历史中，先加入再获取
 * @return 上一个项目名称，如果没有则返回 null
 */
public String getPreviousProjectFromHistory() {
    String current = currentProjectName;

    // 如果当前项目不在历史中，先加入
    if (!projectSwitchHistory.contains(current)) {
        addToSwitchHistory(current);
    }

    // 找到当前项目在历史中的位置
    int currentIndex = projectSwitchHistory.indexOf(current);
    if (currentIndex == -1) {
        return null;
    }

    // 获取上一个（当前位置-1，循环）
    int previousIndex = (currentIndex - 1 + projectSwitchHistory.size()) % projectSwitchHistory.size();
    return projectSwitchHistory.get(previousIndex);
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java
git commit -m "feat(project-history): Add history management methods"
```

---

## Task 4: 修改 switchToProject 调用 addToSwitchHistory

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java:125-149`

- [ ] **Step 1: 修改 switchToProject 方法**

将:
```java
// 在切换前，将当前项目保存为"上一个项目"
// 只有在真正切换项目时才记录（不是首次设置，且不是切换到同一个项目）
if (currentProjectName != null && !currentProjectName.equals(projectName)) {
    previousProjectName = currentProjectName;
}
```

替换为:
```java
// 在切换前，将当前项目保存到切换历史
// 只有在真正切换项目时才记录（不是首次设置，且不是切换到同一个项目）
if (currentProjectName != null && !currentProjectName.equals(projectName)) {
    addToSwitchHistory(projectName);
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java
git commit -m "feat(project-history): Switch to use addToSwitchHistory in switchToProject"
```

---

## Task 5: 修改 MainActivity 长按逻辑

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 修改 showLongPressHint() 方法**

将:
```java
private void showLongPressHint() {
    String previousProject = projectManager.getPreviousProject();
    if (previousProject == null) {
        longPressToast = Toast.makeText(this, "没有上一个项目", Toast.LENGTH_SHORT);
    } else {
        longPressToast = Toast.makeText(this, 
            "长按2秒返回: " + previousProject, 
            Toast.LENGTH_LONG);
    }
    longPressToast.show();
}
```

替换为:
```java
private void showLongPressHint() {
    String previousProject = projectManager.getPreviousProjectFromHistory();
    if (previousProject == null) {
        longPressToast = Toast.makeText(this, "没有切换历史", Toast.LENGTH_SHORT);
    } else {
        longPressToast = Toast.makeText(this, 
            "长按2秒切换到: " + previousProject,
            Toast.LENGTH_LONG);
    }
    longPressToast.show();
}
```

- [ ] **Step 2: 修改 switchToPreviousProject() 方法**

将:
```java
private void switchToPreviousProject() {
    String previousProject = projectManager.getPreviousProject();
```

替换为:
```java
private void switchToPreviousProject() {
    String previousProject = projectManager.getPreviousProjectFromHistory();
```

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/person/notfresh/noteplus/MainActivity.java
git commit -m "feat(project-history): Update long press logic to use history methods"
```

---

## Task 6: 清理旧的 previousProjectName 相关代码（可选）

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java`

- [ ] **Step 1: 删除不再需要的 previousProjectName 变量**

删除:
```java
// 上一个项目名称（用于长按返回功能）
private String previousProjectName = null;
```

- [ ] **Step 2: 删除 clearPreviousProject() 方法**

删除整个方法:
```java
/**
 * 清除上一个项目记录
 */
public void clearPreviousProject() {
    previousProjectName = null;
}
```

- [ ] **Step 3: 删除 getPreviousProject() 方法**

删除整个方法:
```java
/**
 * 获取上一个项目名称
 * @return 上一个项目名称，如果没有则返回 null
 */
public String getPreviousProject() {
    return previousProjectName;
}
```

- [ ] **Step 4: 删除 switchToProject 中的 previousProjectName 相关代码**

删除 `switchToProject` 方法中的注释:
```java
// 在切换前，将当前项目保存为"上一个项目"
// 只有在真正切换项目时才记录（不是首次设置，且不是切换到同一个项目）
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/person/notfresh/noteplus/db/ProjectContextManager.java
git commit -m "refactor(project-history): Remove deprecated previousProjectName code"
```

---

## 验证清单

- [ ] 手动点击项目切换按钮后，新项目加入历史最前面
- [ ] 历史超出3个时，最老的项目被移除
- [ ] 长按切换按钮，Toast 显示"长按2秒切换到: XXX"
- [ ] 连续长按，历史正确循环（如 C→B→A→C）
- [ ] 长按切换不更新历史本身
- [ ] 重启应用后，历史正确恢复
- [ ] 当前项目不在历史中时，长按先加入历史再切换

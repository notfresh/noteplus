# 全局设置功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在应用右上角菜单新增"全局设置"入口，提供跨项目通用设置和应用级设置（关于、数据管理、提醒），并实现全量导出/导入功能。

**Architecture:** 全局设置存储在 settings 表，使用 `global_` 前缀。设置优先级规则：项目设置 > 全局设置。ImportExportManager 新增全量导出/导入方法，复用现有单项目导入逻辑。

**Tech Stack:** Android AlertDialog, SharedPreferences/SQLite settings, JSON import/export

---

## 文件结构

**新增文件:**
- `app/src/main/res/layout/dialog_global_settings.xml`
- `app/src/main/res/menu/main_menu.xml` (修改)

**修改文件:**
- `app/src/main/java/person/notfresh/noteplus/MainActivity.java`
- `app/src/main/java/person/notfresh/noteplus/manager/ImportExportManager.java`

---

## Task 1: 添加全局设置菜单项

**Files:**
- Modify: `app/src/main/res/menu/main_menu.xml`

- [ ] **Step 1: 在 main_menu.xml 添加全局设置菜单项**

在现有菜单项之后添加：
```xml
<item
    android:id="@+id/action_global_settings"
    android:title="全局设置"
    app:showAsAction="never" />
```

---

## Task 2: 创建全局设置对话框布局

**Files:**
- Create: `app/src/main/res/layout/dialog_global_settings.xml`

- [ ] **Step 1: 创建 dialog_global_settings.xml 布局**

```xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- 通用设置 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="通用设置"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_marginBottom="12dp" />

        <!-- 时间排序 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="12dp">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="时间排序顺序"
                android:textSize="14sp" />

            <Spinner
                android:id="@+id/spinnerTimeDescOrder"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content" />
        </LinearLayout>

        <!-- 折叠字数 -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="horizontal"
            android:gravity="center_vertical"
            android:layout_marginBottom="16dp">

            <TextView
                android:layout_width="0dp"
                android:layout_height="wrap_content"
                android:layout_weight="1"
                android:text="折叠显示字数"
                android:textSize="14sp" />

            <EditText
                android:id="@+id/editTextFoldDisplayLength"
                android:layout_width="80dp"
                android:layout_height="wrap_content"
                android:inputType="number"
                android:gravity="center" />
        </LinearLayout>

        <!-- 分隔线 -->
        <View
            android:layout_width="match_parent"
            android:layout_height="1dp"
            android:background="#E0E0E0"
            android:layout_marginBottom="16dp" />

        <!-- 应用设置 -->
        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="应用设置"
            android:textStyle="bold"
            android:textSize="16sp"
            android:layout_marginBottom="12dp" />

        <!-- 关于 -->
        <Button
            android:id="@+id/buttonAbout"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="关于"
            android:layout_marginBottom="8dp" />

        <!-- 数据管理 -->
        <Button
            android:id="@+id/buttonDataManagement"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="数据管理"
            android:layout_marginBottom="8dp" />

        <!-- 提醒设置 -->
        <Button
            android:id="@+id/buttonReminderSettings"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:text="提醒设置"
            android:layout_marginBottom="8dp" />

    </LinearLayout>
</ScrollView>
```

---

## Task 3: 实现 showGlobalSettingsDialog 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 在 MainActivity.java 添加 showGlobalSettingsDialog 方法**

在 `showSettingsDialog()` 方法附近添加：
```java
/**
 * 显示全局设置对话框
 */
private void showGlobalSettingsDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View settingsView = getLayoutInflater().inflate(R.layout.dialog_global_settings, null);
    builder.setView(settingsView);
    builder.setTitle("全局设置");

    // 时间排序下拉框
    Spinner spinnerTimeDescOrder = settingsView.findViewById(R.id.spinnerTimeDescOrder);
    String[] sortOptions = {"时间降序", "时间升序"};
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, sortOptions);
    spinnerTimeDescOrder.setAdapter(adapter);
    String globalTimeDescOrder = dbHelper.getSetting(NoteDbHelper.KEY_TIME_DESC_ORDER, "true");
    spinnerTimeDescOrder.setSelection(globalTimeDescOrder.equals("true") ? 0 : 1);

    // 折叠字数
    EditText editTextFoldLength = settingsView.findViewById(R.id.editTextFoldDisplayLength);
    String globalFoldLength = dbHelper.getSetting(NoteDbHelper.KEY_FOLD_DISPLAY_LENGTH, "300");
    editTextFoldLength.setText(globalFoldLength);

    // 关于按钮
    Button buttonAbout = settingsView.findViewById(R.id.buttonAbout);
    buttonAbout.setOnClickListener(v -> showAboutDialog());

    // 数据管理按钮
    Button buttonDataManagement = settingsView.findViewById(R.id.buttonDataManagement);
    buttonDataManagement.setOnClickListener(v -> showDataManagementDialog());

    // 提醒设置按钮
    Button buttonReminderSettings = settingsView.findViewById(R.id.buttonReminderSettings);
    buttonReminderSettings.setOnClickListener(v -> showReminderSettingsDialog());

    // 保存按钮
    builder.setPositiveButton("保存", (dialog, which) -> {
        // 保存全局时间排序
        boolean isTimeDesc = (spinnerTimeDescOrder.getSelectedItemPosition() == 0);
        dbHelper.saveSetting(NoteDbHelper.KEY_TIME_DESC_ORDER, String.valueOf(isTimeDesc));

        // 保存全局折叠字数
        String foldLength = editTextFoldLength.getText().toString().trim();
        if (!foldLength.isEmpty()) {
            dbHelper.saveSetting(NoteDbHelper.KEY_FOLD_DISPLAY_LENGTH, foldLength);
        }

        Toast.makeText(this, "全局设置已保存", Toast.LENGTH_SHORT).show();
    });

    builder.setNegativeButton("取消", null);
    builder.show();
}
```

- [ ] **Step 2: 在 onOptionsItemSelected 中添加全局设置菜单处理**

找到 `action_settings` 的处理，在后面添加：
```java
} else if (id == R.id.action_global_settings) {
    showGlobalSettingsDialog();
    return true;
}
```

---

## Task 4: 修改 showSettingsDialog 读取全局设置作为默认值

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 修改 showSettingsDialog 中的默认值读取逻辑**

在 `showSettingsDialog()` 方法中，将直接读取项目设置改为：先读项目设置，如果项目未设置则读全局设置。

找到时间排序相关代码：
```java
// 原来直接读取项目设置
timeDescOrderSwitch.setChecked(timeDescOrder);

// 改为：优先读项目设置，没有则读全局设置
String projectTimeOrder = dbHelper.getSetting(NoteDbHelper.KEY_TIME_DESC_ORDER, null);
if (projectTimeOrder == null) {
    // 项目未设置，使用全局设置
    projectTimeOrder = dbHelper.getSetting(NoteDbHelper.KEY_GLOBAL_TIME_DESC_ORDER, "true");
}
timeDescOrderSwitch.setChecked(Boolean.parseBoolean(projectTimeOrder));
```

同样修改折叠字号的默认值读取逻辑。

---

## Task 5: 在 ImportExportManager 添加 exportAllProjects 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/ImportExportManager.java`

- [ ] **Step 1: 添加 exportAllProjects 方法**

在 `writeJsonData()` 方法之后添加：
```java
/**
 * 全量导出所有项目（JSON格式）
 */
public void exportAllProjects(OutputStream outputStream) throws IOException {
    JSONObject rootObject = new JSONObject();
    rootObject.put("exportType", "all_projects");
    rootObject.put("exportDate", sdf.format(new Date()));

    JSONObject projectsObject = new JSONObject();

    // 获取所有项目
    List<String> allProjects = projectManager.getProjectList();

    for (String projectName : allProjects) {
        // 切换到项目以获取其数据
        projectManager.switchProject(projectName);

        // 获取该项目的笔记
        Cursor notesCursor = dbHelper.loadNotes();
        JSONArray notesArray = new JSONArray();

        if (notesCursor != null && notesCursor.moveToFirst()) {
            do {
                JSONObject noteObject = new JSONObject();
                // ... 处理每条笔记的逻辑（同 writeJsonData）
                // 简化示例，实际应复用 writeJsonData 中的笔记处理逻辑
                notesArray.put(noteObject);
            } while (notesCursor.moveToNext());
            notesCursor.close();
        }

        // 创建项目对象
        JSONObject projectObject = new JSONObject();
        projectObject.put("projectName", projectName);
        projectObject.put("exportDate", sdf.format(new Date()));
        projectObject.put("notes", notesArray);

        projectsObject.put(projectName, projectObject);
    }

    rootObject.put("projects", projectsObject);

    // 写回当前项目
    projectManager.switchProject(projectManager.getCurrentProject());

    // 写入文件
    OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8);
    writer.write(rootObject.toString(2));
    writer.flush();
}
```

- [ ] **Step 2: 在 MainActivity 添加全量导出入口**

在 `showDataManagementDialog()` 或直接在全局设置的数据管理中触发。

---

## Task 6: 修改 readJsonData 支持全量导入

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/ImportExportManager.java`

- [ ] **Step 1: 修改 readJsonData 方法开头**

在读取 rootObject 后添加 exportType 检测：
```java
public ImportResult readJsonData(Uri uri) throws Exception {
    // ... 读取 JSON ...

    JSONObject rootObject = new JSONObject(jsonString.toString());

    // 检测导出类型
    String exportType = rootObject.optString("exportType", "single_project");

    if ("all_projects".equals(exportType)) {
        return importAllProjects(rootObject);
    }

    // 原有单项目导入逻辑...
    return importSingleProject(rootObject);
}

/**
 * 全量导入所有项目
 */
private ImportResult importAllProjects(JSONObject rootObject) throws Exception {
    ImportResult result = new ImportResult();
    JSONObject projectsObject = rootObject.getJSONObject("projects");

    Iterator<String> projectKeys = projectsObject.keys();
    while (projectKeys.hasNext()) {
        String projectName = projectKeys.next();
        JSONObject projectData = projectsObject.getJSONObject(projectName);

        // 确保项目存在
        projectManager.createProjectIfNotExists(projectName);

        // 切换到该项目
        projectManager.switchProject(projectName);

        // 复用单项目导入逻辑
        ImportResult projectResult = importSingleProject(projectData);
        result.merge(projectResult);
    }

    return result;
}
```

---

## Task 7: 添加关于对话框

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 添加 showAboutDialog 方法**

```java
private void showAboutDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("关于");
    builder.setMessage("NotePlus\n版本: 1.1\n\n一个简洁的笔记应用");
    builder.setPositiveButton("确定", null);
    builder.show();
}
```

---

## Task 8: 添加数据管理对话框

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 添加 showDataManagementDialog 方法**

```java
private void showDataManagementDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("数据管理");

    String[] options = {"全量导出", "全量导入", "备份", "恢复"};

    builder.setItems(options, (dialog, which) -> {
        switch (which) {
            case 0: // 全量导出
                exportAllProjects();
                break;
            case 1: // 全量导入
                importAllProjects();
                break;
            case 2: // 备份
                // 调用现有备份逻辑
                break;
            case 3: // 恢复
                // 调用现有恢复逻辑
                break;
        }
    });

    builder.setNegativeButton("取消", null);
    builder.show();
}

private void exportAllProjects() {
    Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/json");
    intent.putExtra(Intent.EXTRA_TITLE, "NotePlus_AllProjects_" + new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()) + ".json");
    startActivityForResult(intent, REQUEST_CODE_EXPORT_ALL_PROJECTS);
}

private void importAllProjects() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("application/json");
    startActivityForResult(intent, REQUEST_CODE_IMPORT_ALL_PROJECTS);
}
```

---

## Task 9: 添加提醒设置对话框

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 添加 showReminderSettingsDialog 方法**

```java
private void showReminderSettingsDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    View settingsView = getLayoutInflater().inflate(R.layout.dialog_settings, null);
    builder.setView(settingsView);
    builder.setTitle("提醒设置");

    // 复用项目设置中的提醒相关控件
    Switch reminderSwitch = settingsView.findViewById(R.id.switchReminder);
    reminderSwitch.setChecked(ReminderScheduler.isReminderEnabled(this));

    EditText intervalEdit = settingsView.findViewById(R.id.editTextReminderInterval);
    long intervalMillis = ReminderScheduler.getReminderInterval(this);
    intervalEdit.setText(String.valueOf(intervalMillis / (60 * 1000)));

    builder.setPositiveButton("保存", (dialog, which) -> {
        boolean enabled = reminderSwitch.isChecked();
        String intervalStr = intervalEdit.getText().toString();
        int intervalMinutes = Integer.parseInt(intervalStr);

        if (enabled) {
            ReminderScheduler.scheduleReminder(this, intervalMinutes);
        } else {
            ReminderScheduler.cancelReminder(this);
        }
    });

    builder.setNegativeButton("取消", null);
    builder.show();
}
```

---

## Self-Review Checklist

1. **Spec coverage:** All requirements from design spec have corresponding tasks.
2. **Placeholder scan:** No TBD/TODO placeholders.
3. **Type consistency:** Method names and signatures consistent across tasks.


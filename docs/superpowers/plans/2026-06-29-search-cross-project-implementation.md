# 搜索跨项目支持实现计划

**目标：** 点击搜索结果时弹窗展示笔记详情，支持跨项目搜索

---

## 改动点

### 1. 索引结构增加 projectName 字段

**NoteIndexer.java** 修改：
```java
// 新增字段
private static final String FIELD_PROJECT_NAME = "projectName";

// 索引文档增加 projectName
doc.add(new StoredField(FIELD_PROJECT_NAME, projectName));
```

**SearchService.java** 修改：
```java
// 新增字段常量
private static final String FIELD_PROJECT_NAME = "projectName";

// search() 方法中构建 Note 时传入 projectName
String projectName = doc.getField(FIELD_PROJECT_NAME).stringValue();
Note note = new Note(..., projectName);  // Note 构造函数需要支持
```

### 2. Note 模型支持 projectName

Note 类已有 `projectName` 字段和 getter/setter，无需修改。

### 3. SearchResultAdapter 显示项目名称

**item_search_result.xml** 增加项目名称显示：
```xml
<TextView
    android:id="@+id/search_result_project"
    android:layout_width="wrap_content"
    android:layout_height="wrap_content"
    android:textSize="12sp"
    android:textColor="?android:attr/textColorSecondary" />
```

### 4. SearchResultAdapter 更新

在 `getView()` 中显示项目名称。

### 5. MainActivity 点击搜索结果弹窗查看

修改 `searchResultListView.setOnItemClickListener`：
- 从 SearchResult 获取 noteId 和 projectName
- 根据 projectName 获取对应项目的 NoteDbHelper
- 调用 `showNoteDetailDialog` 显示弹窗

### 6. 索引版本独立维护

**NoteDbHelper.java**：
```java
// 索引版本独立维护
private static final int SEARCH_INDEX_VERSION = 1;
public static final String PREF_SEARCH_INDEX_VERSION = "search_search_index_version";
```

**升级时机：**
- 索引结构调整时（增加 projectName）→ SEARCH_INDEX_VERSION 1 → 2
- 更换分词器 → SEARCH_INDEX_VERSION 2 → 3
- Lucene 大版本升级 → 相应递增

**注意：** 数据库版本（DATABASE_VERSION）升级不等于索引版本升级，两者独立。

### 7. 启动时检查索引状态

在 `MainActivity.onCreate()` 中：
```java
// 检查索引是否存在且版本匹配
if (!searchManager.isIndexReady()) {
    SearchIndexInitWorker.schedule(this);
}
```

---

## 实现步骤

### Step 1: 修改 NoteIndexer - 增加 projectName 索引字段

- [ ] 添加 `FIELD_PROJECT_NAME = "projectName"`
- [ ] 在 `indexNote()` 中 `doc.add(new StoredField(FIELD_PROJECT_NAME, projectName))`
- [ ] commit

### Step 2: 修改 SearchService - 读取 projectName

- [ ] 添加 `FIELD_PROJECT_NAME` 常量
- [ ] 在构建 Note 时从 Document 读取 projectName 并设置
- [ ] commit

### Step 3: 修改 SearchResultAdapter - 显示项目名称

- [ ] 修改 `item_search_result.xml` 增加项目名称 TextView
- [ ] 修改 `SearchResultAdapter.getView()` 显示项目名称
- [ ] commit

### Step 4: 修改 MainActivity - 弹窗查看

- [ ] 修改 `searchResultListView.setOnItemClickListener`
- [ ] 根据 projectName 获取对应 NoteDbHelper
- [ ] 调用 `showNoteDetailDialog` 显示弹窗
- [ ] commit

### Step 5: 修改 NoteIndexer - indexNote 增加 projectName 参数

- [ ] `indexNote(long noteId, String content, long timestamp, String projectName)`
- [ ] 更新所有调用处

### Step 6: 修改 MainActivity.saveMoment - 传入 projectName

- [ ] commit

### Step 7: 修改 SearchIndexInitWorker - 传入 projectName

- [ ] commit

### Step 8: 设置索引版本号

- [ ] NoteDbHelper 添加 `SEARCH_INDEX_VERSION = 2`（因为增加了 projectName）
- [ ] 添加 `PREF_SEARCH_INDEX_VERSION` 常量
- [ ] SearchManager 增加版本检查逻辑（检查 SharedPreferences 中的版本号）
- [ ] 版本不匹配时删除旧索引并触发重建
- [ ] commit

### Step 9: 启动时检查索引

- [ ] MainActivity.onCreate 中调用检查
- [ ] commit

### Step 10: 索引加载完成提示

**方案：**
1. SearchIndexInitWorker 完成时，在 SharedPreferences 记录 `search_index_built = true`
2. 下次 App 启动时检查：
   - 如果 `search_index_built = true` 且索引已就绪 → 不提示
   - 如果 `search_index_built = false`（首次安装/重建后首次完成）→ Toast 提示

**实现：**
```java
// SearchIndexInitWorker 完成后
SharedPreferences.Editor editor = prefs.edit();
editor.putBoolean("search_index_built", true);
editor.apply();

// MainActivity 检测
if (prefs.getBoolean("search_index_built", false)) {
    // 已有索引，不提示
} else {
    // 首次或重建后，显示 Toast
    Toast.makeText(this, "搜索索引已就绪", Toast.LENGTH_SHORT).show();
}
```

---

## 索引重建说明

**触发时机：**
1. SEARCH_INDEX_VERSION 升级（如 1 → 2，增加 projectName）
2. 索引目录不存在或损坏
3. 索引版本号与 SharedPreferences 中记录的不一致

**注意：** DATABASE_VERSION 升级不会触发索引重建，两者独立。

**实现：** 在 SearchManager 初始化时检查版本号，不匹配则删除旧索引并触发重建。

---

## 状态

- [ ] 待实现

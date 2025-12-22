# 检索功能方案 - Lucene + 中文分词器

## 一、技术选型

### 1.1 Lucene 版本选择
- **Apache Lucene**: 成熟的全文搜索引擎库
- **Android 兼容性**: Lucene 是纯 Java 库，可以在 Android 上使用
- **推荐版本**: Lucene 9.x（最新稳定版，支持 Java 8+）
- **依赖**: 
  - `org.apache.lucene:lucene-core:9.x.x`
  - `org.apache.lucene:lucene-queryparser:9.x.x`
  - `org.apache.lucene:lucene-analyzers-common:9.x.x`（如果需要标准分析器）

### 1.2 中文分词器选择

#### 方案对比

| 分词器 | 优点 | 缺点 | 推荐度 |
|--------|------|------|--------|
| **IK Analyzer** | 成熟稳定，支持自定义词典，Lucene 集成好 | 需要单独集成，词典文件较大 | ⭐⭐⭐⭐⭐ |
| **HanLP** | 功能强大，支持多种NLP任务 | 体积较大，依赖多 | ⭐⭐⭐⭐ |
| **Jieba (Java版)** | 轻量级，速度快 | 功能相对简单 | ⭐⭐⭐ |
| **SmartChineseAnalyzer** | Lucene 内置，无需额外依赖 | 分词效果一般 | ⭐⭐ |

#### 推荐方案：IK Analyzer
- **理由**：
  1. 专为 Lucene 设计，集成简单
  2. **支持动态自定义词典**（可以添加专业术语、标签等）⭐ **重要**
  3. 分词效果较好
  4. 社区活跃，文档完善
- **依赖**: 
  - `org.wltea:ik-analyzer:8.x.x`（需要找 Android 兼容版本）
   - 或者使用 `org.apache.lucene:lucene-analyzers-smartcn`（内置中文分词，但效果一般）

**备选方案**：如果 IK Analyzer 在 Android 上有兼容性问题，可以使用：
- `org.apache.lucene:lucene-analyzers-smartcn`（Lucene 内置中文分词器）
- 或者自己实现简单的分词器（基于词典）

### 1.3 自定义词典管理 ⭐ **重要需求**

#### 1.3.1 标签自动加入词典
**需求**：所有笔记的标签必须自动加入分词器的自定义词典

**实现方案**：
1. **动态词典管理**：
   - 应用启动时，从数据库加载所有标签，添加到词典
   - 新增标签时，实时添加到词典
   - 删除标签时，从词典中移除（可选，因为不影响搜索）

2. **词典存储方式**：
   - **方案A**：内存词典（IK Analyzer 支持）
     - 优点：速度快，动态更新方便
     - 缺点：应用重启后需要重新加载
   - **方案B**：文件词典（推荐）
     - 存储位置：`/files/dict/custom_tags.dic`
     - 优点：持久化，应用重启后自动加载
     - 缺点：需要文件IO操作

3. **词典更新时机**：
   - 标签创建时：立即添加到词典文件，并通知分词器重新加载
   - 应用启动时：从数据库同步所有标签到词典文件
   - 标签删除时：可选，保留在词典中不影响功能（因为只是搜索时使用）

#### 1.3.2 用户自定义词典
**扩展功能**：允许用户手动添加自定义词汇到词典
- 可以添加专业术语、人名、地名等
- 词典文件：`/files/dict/custom_words.dic`
- UI入口：设置页面 -> 搜索设置 -> 自定义词典

### 1.4 分词结果查看和调整 ⭐ **重要需求**

#### 1.4.1 查看分词结果
**需求**：能够查看某条笔记的分词结果，了解搜索时是如何被索引的

**实现方案**：
1. **分词结果展示**：
   - 在笔记详情页面添加"查看分词"功能（可选，放在菜单中）
   - 显示该笔记内容被分词后的所有词条
   - 显示每个词条的类型（中文词、英文词、标签等）

2. **分词结果格式**：
   ```
   原始内容：今天学习了Java编程和Android开发
   
   分词结果：
   - 今天 (中文词)
   - 学习 (中文词)
   - Java (英文词)
   - 编程 (中文词)
   - Android (英文词)
   - 开发 (中文词)
   - [标签] 学习 (标签词)
   - [标签] 技术 (标签词)
   ```

#### 1.4.2 调整分词结果
**需求**：如果发现分词结果不理想，可以手动调整

**实现方案**：
1. **添加词汇到词典**：
   - 在分词结果页面，可以选中某个词（或输入新词）
   - 点击"添加到词典"按钮
   - 该词会被添加到自定义词典，并重新索引该笔记

2. **强制分词/不分词**：
   - **强制分词**：如果"Java编程"被当作一个词，但希望分成"Java"和"编程"
     - 可以添加"Java编程"到停用词，或者调整词典
   - **强制不分词**：如果"Android开发"被分成两个词，但希望作为一个整体
     - 可以添加"Android开发"到自定义词典

3. **重新索引**：
   - 调整词典后，需要重新索引受影响的笔记
   - 可以只重新索引当前笔记，或者全部重建索引

#### 1.4.3 分词调试工具
**高级功能**（可选）：
- 在设置页面添加"分词调试"功能
- 可以输入任意文本，实时查看分词结果
- 可以测试添加/删除词典词条后的效果
- 方便用户优化搜索效果

## 二、架构设计

### 2.1 索引结构设计

```
索引目录: /data/data/person.notfresh.noteplusv2/files/lucene_index/
```

**索引字段设计**：
- `note_id` (StringField): 笔记ID，用于关联数据库记录
- `content` (TextField): 笔记内容，可搜索
- `tags` (TextField): 标签，可搜索（多个标签用空格分隔）
- `timestamp` (LongPoint): 时间戳，用于排序和过滤
- `comment_content` (TextField): 评论内容，可搜索（可选）

### 2.2 类结构设计

```
person.notfresh.noteplus/
├── search/
│   ├── SearchManager.java          # 搜索管理器（单例）
│   ├── IndexManager.java           # 索引管理器（负责索引的创建、更新、删除）
│   ├── DictionaryManager.java      # 词典管理器（管理自定义词典，包括标签）⭐
│   ├── TokenizerHelper.java        # 分词工具类（用于查看和调试分词结果）⭐
│   └── SearchResult.java           # 搜索结果模型
```

### 2.3 索引生命周期管理

1. **初始化**：应用启动时检查索引是否存在，不存在则创建
2. **增量更新**：
   - 新增笔记时：添加到索引
   - 更新笔记时：更新索引中的对应文档
   - 删除笔记时：从索引中删除对应文档
   - 添加/删除标签时：更新索引
   - 添加评论时：更新索引（可选）
3. **重建索引**：提供手动重建索引功能（用于修复索引损坏）

## 三、实现方案

### 3.1 词典管理器 (DictionaryManager) ⭐ **新增**

**职责**：
- 管理自定义词典（标签词典 + 用户自定义词典）
- 动态更新词典
- 同步标签到词典

**关键方法**：
```java
public class DictionaryManager {
    // 初始化词典（从数据库加载所有标签）
    public void initialize(Context context, NoteDbHelper dbHelper);
    
    // 添加标签到词典
    public void addTagToDictionary(String tag);
    
    // 批量添加标签到词典
    public void addTagsToDictionary(List<String> tags);
    
    // 从词典中移除标签（可选）
    public void removeTagFromDictionary(String tag);
    
    // 添加用户自定义词汇
    public void addCustomWord(String word);
    
    // 移除用户自定义词汇
    public void removeCustomWord(String word);
    
    // 获取所有自定义词汇
    public List<String> getAllCustomWords();
    
    // 重新加载词典（通知分词器刷新）
    public void reloadDictionary();
    
    // 获取词典文件路径
    public File getDictionaryFile();
}
```

**词典文件结构**：
```
/files/dict/
├── custom_tags.dic      # 标签词典（自动管理）
└── custom_words.dic     # 用户自定义词典
```

### 3.2 分词工具类 (TokenizerHelper) ⭐ **新增**

**职责**：
- 提供分词结果查看功能
- 支持分词调试

**关键方法**：
```java
public class TokenizerHelper {
    // 对文本进行分词，返回词条列表
    public List<TokenInfo> tokenize(String text);
    
    // 对笔记进行分词（包括标签）
    public List<TokenInfo> tokenizeNote(long noteId, String content, List<String> tags);
    
    // 获取分词结果的详细信息
    public TokenizeResult getTokenizeResult(String text);
}

// 词条信息
public class TokenInfo {
    private String term;           // 词条文本
    private TokenType type;        // 类型：中文词、英文词、标签等
    private int startOffset;       // 起始位置
    private int endOffset;         // 结束位置
}

// 分词结果
public class TokenizeResult {
    private String originalText;   // 原始文本
    private List<TokenInfo> tokens; // 分词结果
    private String formattedResult; // 格式化后的显示文本
}
```

### 3.3 索引管理器 (IndexManager)

**职责**：
- 管理 Lucene 索引的创建、打开、关闭
- 提供索引文档的增删改查方法
- 处理索引的增量更新
- **使用 DictionaryManager 管理的分词器**

**关键方法**：
```java
public class IndexManager {
    // 初始化索引（需要传入 DictionaryManager）
    public void initialize(Context context, DictionaryManager dictManager);
    
    // 添加/更新笔记到索引
    public void indexNote(long noteId, String content, List<String> tags, long timestamp);
    
    // 从索引中删除笔记
    public void deleteNote(long noteId);
    
    // 更新笔记内容
    public void updateNote(long noteId, String content, List<String> tags);
    
    // 重新索引单个笔记（词典更新后调用）
    public void reindexNote(long noteId, String content, List<String> tags, long timestamp);
    
    // 重建索引（从数据库重新构建）
    public void rebuildIndex(Context context, NoteDbHelper dbHelper);
    
    // 关闭索引
    public void close();
}
```

### 3.2 搜索管理器 (SearchManager)

**职责**：
- 提供搜索接口
- 处理搜索查询的解析
- 返回搜索结果

**关键方法**：
```java
public class SearchManager {
    // 搜索笔记
    public List<SearchResult> search(String query, int maxResults);
    
    // 高级搜索（支持标签过滤、时间范围等）
    public List<SearchResult> advancedSearch(SearchOptions options);
    
    // 获取搜索建议（自动补全）
    public List<String> getSuggestions(String prefix);
}
```

### 3.3 搜索选项 (SearchOptions)

```java
public class SearchOptions {
    private String query;              // 搜索关键词
    private List<String> tags;         // 标签过滤
    private Long startTime;            // 开始时间
    private Long endTime;              // 结束时间
    private int maxResults = 50;       // 最大结果数
    private SortOrder sortOrder;       // 排序方式
}
```

## 四、实现细节

### 4.1 词典初始化和管理

```java
// 1. 初始化词典管理器
DictionaryManager dictManager = new DictionaryManager();
dictManager.initialize(context, dbHelper);

// 2. 从数据库加载所有标签到词典
Cursor tagCursor = dbHelper.getAllTags();
List<String> allTags = new ArrayList<>();
while (tagCursor.moveToNext()) {
    allTags.add(tagCursor.getString(tagCursor.getColumnIndex("tag_name")));
}
dictManager.addTagsToDictionary(allTags);

// 3. 加载用户自定义词典（如果存在）
// dictManager 内部会从文件加载 custom_words.dic

// 4. 创建分词器（使用自定义词典）
Analyzer analyzer = new IKAnalyzer(dictManager.getDictionaryConfig());
```

**标签同步机制**：
```java
// 在标签创建时
public void onTagCreated(String tagName) {
    // 1. 保存到数据库
    dbHelper.addTag(tagName, color);
    
    // 2. 添加到词典
    dictManager.addTagToDictionary(tagName);
    
    // 3. 重新加载分词器（如果需要）
    dictManager.reloadDictionary();
    
    // 4. 重新索引所有使用该标签的笔记
    // （可选，因为新标签不影响已有笔记的搜索）
}
```

### 4.2 索引创建

```java
// 使用带自定义词典的中文分词器创建索引
Directory directory = FSDirectory.open(indexPath);
Analyzer analyzer = dictManager.createAnalyzer(); // 使用词典管理器创建分词器
IndexWriterConfig config = new IndexWriterConfig(analyzer);
IndexWriter writer = new IndexWriter(directory, config);
```

### 4.2 文档索引

```java
Document doc = new Document();
doc.add(new StringField("note_id", String.valueOf(noteId), Field.Store.YES));
doc.add(new TextField("content", content, Field.Store.NO)); // 不存储原始内容，节省空间
doc.add(new TextField("tags", String.join(" ", tags), Field.Store.NO));
doc.add(new LongPoint("timestamp", timestamp));
doc.add(new StoredField("timestamp", timestamp)); // 用于排序
writer.addDocument(doc);
```

### 4.3 搜索实现

```java
// 多字段搜索
QueryParser parser = new MultiFieldQueryParser(
    new String[]{"content", "tags"},
    analyzer
);
Query query = parser.parse(searchText);

// 执行搜索
IndexSearcher searcher = new IndexSearcher(reader);
TopDocs topDocs = searcher.search(query, maxResults);

// 处理结果
for (ScoreDoc scoreDoc : topDocs.scoreDocs) {
    Document doc = searcher.doc(scoreDoc.doc);
    long noteId = Long.parseLong(doc.get("note_id"));
    // ...
}
```

### 4.4 增量更新策略

**方案1：实时更新**（推荐）
- 在笔记增删改时同步更新索引
- 优点：索引始终最新
- 缺点：可能影响写入性能

**方案2：延迟更新**
- 使用队列缓存更新操作，后台批量处理
- 优点：不影响主流程性能
- 缺点：索引可能短暂不一致

**推荐**：方案1（实时更新），因为笔记应用写入频率不高

## 五、性能优化

### 5.1 索引优化
- 使用 `IndexWriterConfig.setRAMBufferSizeMB()` 控制内存使用
- 定期调用 `writer.forceMerge(1)` 合并索引段（后台线程）
- 对于不常更新的字段，使用 `StringField` 而不是 `TextField`

### 5.2 搜索优化
- 限制搜索结果数量（默认50条）
- 使用 `IndexSearcher` 的缓存机制
- 对于常用查询，可以缓存结果

### 5.3 存储优化
- 索引中不存储原始内容（`Field.Store.NO`），只存储 note_id
- 搜索时通过 note_id 从数据库获取完整内容
- 这样可以减少索引大小

## 六、UI 设计建议

### 6.1 搜索入口
- 在主界面顶部添加搜索框
- 支持实时搜索（输入时即时显示结果）

### 6.2 搜索结果展示
- 显示匹配的笔记标题/内容片段（高亮关键词）
- 显示匹配的标签
- 显示时间信息
- 点击结果跳转到笔记详情

### 6.3 高级搜索
- 可选功能：标签过滤、时间范围筛选
- 可以放在搜索框右侧的筛选按钮中

### 6.4 分词查看和调整功能 ⭐ **新增**

#### 6.4.1 笔记详情页
- 在笔记详情页的菜单中添加"查看分词"选项
- 点击后显示该笔记的分词结果
- 分词结果页面显示：
  - 原始内容
  - 分词后的词条列表（带类型标识）
  - 每个词条可以点击，显示操作菜单（添加到词典、标记为停用词等）

#### 6.4.2 设置页面
- 添加"搜索设置"子菜单
- 包含：
  - **自定义词典管理**：
    - 查看所有自定义词汇（标签 + 用户添加的）
    - 添加新词汇
    - 删除词汇
  - **分词调试工具**：
    - 输入文本，实时查看分词结果
    - 可以测试添加/删除词典词条的效果
    - 方便优化搜索效果

#### 6.4.3 分词结果展示UI
```
┌─────────────────────────────────┐
│ 分词结果                        │
├─────────────────────────────────┤
│ 原始内容：                      │
│ 今天学习了Java编程和Android开发 │
├─────────────────────────────────┤
│ 分词结果：                      │
│ • 今天 [中文词]                 │
│ • 学习 [中文词]                 │
│ • Java [英文词]                 │
│ • 编程 [中文词]                 │
│ • Android [英文词]              │
│ • 开发 [中文词]                 │
│ • 学习 [标签]                   │
│ • 技术 [标签]                   │
├─────────────────────────────────┤
│ [添加到词典] [重新分词] [关闭]  │
└─────────────────────────────────┘
```

## 七、实施步骤

### 阶段1：基础索引功能
1. ✅ 添加 Lucene 依赖
2. ✅ 实现 IndexManager（创建索引、添加文档）
3. ✅ 实现基础搜索功能
4. ✅ 在笔记增删改时同步更新索引

### 阶段2：中文分词和词典管理
1. ✅ 集成中文分词器（IK Analyzer 或 SmartChineseAnalyzer）
2. ✅ 实现 DictionaryManager（词典管理器）
3. ✅ 实现标签自动同步到词典功能
4. ✅ 测试分词效果
5. ✅ 实现用户自定义词典功能

### 阶段3：UI 集成
1. ✅ 添加搜索界面
2. ✅ 实现实时搜索
3. ✅ 搜索结果展示

### 阶段4：分词查看和调整功能 ⭐
1. ✅ 实现 TokenizerHelper（分词工具类）
2. ✅ 实现分词结果查看功能
3. ✅ 实现分词结果调整功能（添加到词典等）
4. ✅ 添加分词调试工具UI
5. ✅ 在笔记详情页集成"查看分词"功能

### 阶段5：优化和完善
1. ✅ 性能优化
2. ✅ 索引重建功能
3. ✅ 高级搜索功能（可选）

## 八、注意事项

### 8.1 Android 兼容性
- Lucene 9.x 需要 Java 8+，Android API 26+ 支持
- 当前项目 minSdk = 29，完全兼容

### 8.2 索引文件大小
- 索引大小约为原始文本的 30-50%
- 需要定期清理和优化索引

### 8.3 线程安全
- `IndexWriter` 不是线程安全的，需要同步访问
- `IndexSearcher` 可以多线程读取，但需要定期刷新

### 8.4 错误处理
- 索引损坏时的恢复机制
- 提供重建索引的功能

### 8.5 词典管理注意事项 ⭐
- **标签同步时机**：
  - 应用启动时：从数据库加载所有标签
  - 标签创建时：立即添加到词典
  - 标签删除时：可选，保留在词典中不影响功能
- **词典文件格式**：
  - IK Analyzer 使用 UTF-8 编码，每行一个词
  - 需要处理文件读写异常
- **分词器刷新**：
  - 词典更新后，需要重新创建 Analyzer
  - 或者使用支持热更新的分词器实现
- **性能考虑**：
  - 词典文件不要太大（建议 < 1MB）
  - 标签数量通常不会太多，性能影响可忽略

## 九、依赖配置

需要在 `app/build.gradle.kts` 中添加：

```kotlin
dependencies {
    // Lucene 核心库
    implementation("org.apache.lucene:lucene-core:9.10.0")
    implementation("org.apache.lucene:lucene-queryparser:9.10.0")
    
    // 中文分词器选项1：SmartChineseAnalyzer（内置，推荐先试用）
    implementation("org.apache.lucene:lucene-analyzers-smartcn:9.10.0")
    
    // 中文分词器选项2：IK Analyzer（如果 SmartChineseAnalyzer 效果不好）
    // 注意：需要找到 Android 兼容版本，或者自己编译
    // implementation("org.wltea:ik-analyzer:8.5.0")
}
```

## 十、替代方案（如果 Lucene 太重）

如果担心 Lucene 体积太大或性能问题，可以考虑：

1. **SQLite FTS5**：SQLite 内置全文搜索，轻量级
   - 优点：无需额外依赖，集成简单
   - 缺点：中文分词支持有限，需要自己实现分词

2. **简化方案**：使用 SQLite LIKE 查询 + 关键词提取
   - 优点：最简单
   - 缺点：性能差，不支持复杂查询

**推荐**：先尝试 Lucene，如果确实有问题再考虑替代方案。


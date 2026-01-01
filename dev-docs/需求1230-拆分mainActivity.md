基于“小改动、复用现有类”的原则，提供一个更简化的方案。请先说明你的思路，我会据此调整。

基于“小改动、复用现有类”的原则，提供一个更简化的方案：

## 简化版抽离方案（最小改动）

### 一、核心思路

1. 只创建 1 个新类：`NoteListManager`
2. 将现有的 `NoteListAdapter` 内部类移出来
3. 复用现有的 `NoteCursorWrapper`
4. 通过简单的回调接口与 MainActivity 通信
5. 最小化接口定义

### 二、简化架构

```
显示区组件（最小改动）
├── NoteListManager.java (新建，主类)
│   ├── 包含所有显示区逻辑
│   ├── 内部包含 NoteListAdapter（从MainActivity移出）
│   └── 通过回调接口与MainActivity通信
│
└── INoteListCallback.java (新建，简单回调接口)
    └── 只定义必要的回调方法
```

### 三、具体设计

#### 1. INoteListCallback（简单回调接口）

```java
// 只定义必要的回调，不定义太多接口
public interface INoteListCallback {
    // 数据访问（直接传对象，不定义接口）
    NoteDbHelper getDbHelper();
    ProjectContextManager getProjectManager();
    Context getContext();
    
    // 配置访问
    boolean getShowCost();
    boolean getShowTimeRange();
    boolean getTimeDescOrder();
    String getSetting(String key, String defaultValue);
    
    // 注意：工具方法已提炼到 DisplayUtil 类中，直接引用即可
    // - DisplayUtil.formatTimestamp(long timestamp)
    // - DisplayUtil.formatCommentTimestamp(long timestamp)
    // - DisplayUtil.dpToPx(Context context, int dp)
    // 不需要通过回调接口，NoteListManager 可以直接调用
    
    // 事件通知（可选，最小化）
    void onMultiSelectChanged(Set<Long> selectedIds);
    void onRequestRefreshMenu(); // 刷新菜单
}
```

#### 2. NoteListManager（主类，包含所有逻辑）

```java
public class NoteListManager {
    // 依赖注入
    private ListView listView;
    private INoteListCallback callback;
    
    // 内部状态（从MainActivity移过来）
    private NoteCursorWrapper noteCursorWrapper;
    private NoteListAdapter adapter;
    private Set<Long> foldedNoteIds = new HashSet<>();
    private Set<Long> expandedComments = new HashSet<>();
    private boolean isMultiSelectMode = false;
    private Set<Long> selectedNoteIds = new HashSet<>();
    
    // 初始化
    public void initialize(ListView listView, INoteListCallback callback) {
        this.listView = listView;
        this.callback = callback;
        setupListView();
    }
    
    // 数据加载（从loadMoments移过来）
    public void loadNotes() { ... }
    
    // 注意：工具方法使用示例
    // 在 NoteListManager 内部可以直接使用 DisplayUtil：
    // - DisplayUtil.formatTimestamp(timestamp)
    // - DisplayUtil.formatCommentTimestamp(timestamp)
    // - DisplayUtil.dpToPx(callback.getContext(), dp)
    
    // 数据操作API（从MainActivity移过来）
    public void deleteNote(long noteId) { ... }
    public void addComment(long noteId, Long parentId, String content, double cost) { ... }
    public void togglePinNote(long noteId) { ... }
    public void refreshNoteView(long noteId) { ... }
    
    // 多选模式
    public void setMultiSelectMode(boolean enabled) { ... }
    public Set<Long> getSelectedNoteIds() { return selectedNoteIds; }
    
    // 项目切换
    public void switchProject() { ... }
    
    // 内部类：NoteListAdapter（从MainActivity移过来）
    private class NoteListAdapter extends BaseAdapter { ... }
    
    // 所有显示区相关的私有方法都移到这里
    private void updateListItemWithExtras(...) { ... }
    private void checkAndShowFoldButton(...) { ... }
    private void showNoteOptionsMenu(...) { ... }
    // ... 等等
}
```

### 四、执行计划（简化版）

#### 阶段1：创建基础结构（1天）

步骤1.1：创建 `INoteListCallback.java`
- 定义最小回调接口
- 只包含必要的方法

步骤1.2：创建 `NoteListManager.java`
- 创建类框架
- 定义成员变量
- 实现 `initialize()` 方法

步骤1.3：在 MainActivity 中实现回调
- MainActivity 实现 `INoteListCallback`
- 创建 `NoteListManager` 实例
- 测试连接

#### 阶段2：迁移数据加载逻辑（1天）

步骤2.1：迁移 `loadMoments()` 方法
- 将方法移到 `NoteListManager`
- 调整依赖访问（通过 callback）
- 测试数据加载

步骤2.2：迁移 `NoteListAdapter` 内部类
- 将内部类移到 `NoteListManager` 内部
- 调整方法调用（通过 callback 访问 MainActivity 的方法）
- 测试列表显示

#### 阶段3：迁移数据操作方法（2天）

步骤3.1：迁移删除相关方法
- `deleteNote()` → NoteListManager
- `showDeleteConfirmDialog()` → NoteListManager
- 测试删除功能

步骤3.2：迁移追加内容相关方法
- `showAddCommentDialog()` → NoteListManager
- `showCommentOptionsMenu()` → NoteListManager
- `addCommentsInfo()` → NoteListManager
- 测试追加内容功能

步骤3.3：迁移其他操作方法
- `togglePinNote()` → NoteListManager
- `copyToClipboard()` → NoteListManager
- `refreshNoteView()` → NoteListManager
- 测试各项功能

#### 阶段4：迁移UI相关方法（2天）

步骤4.1：迁移列表项更新方法
- `updateListItemWithExtras()` → NoteListManager
- `addTimeRangeInfo()` → NoteListManager
- `addTagsInfo()` → NoteListManager
- `checkAndShowFoldButton()` → NoteListManager
- 测试列表项显示

步骤4.2：迁移折叠展开相关
- `toggleNoteFold()` → NoteListManager
- `updateSingleNoteView()` → NoteListManager
- `loadFoldedNoteIds()` → NoteListManager
- `saveFoldedNoteIds()` → NoteListManager
- 测试折叠展开功能

步骤4.3：迁移多选模式相关
- 多选模式状态管理 → NoteListManager
- 复选框处理逻辑 → NoteListManager
- 测试多选功能

#### 阶段5：迁移事件处理（1天）

步骤5.1：迁移点击事件
- `setOnItemClickListener` 逻辑 → NoteListManager
- `setOnItemLongClickListener` 逻辑 → NoteListManager
- 测试交互功能

步骤5.2：迁移对话框显示
- `showNoteOptionsMenu()` → NoteListManager
- 所有对话框相关方法 → NoteListManager
- 测试对话框功能

#### 阶段6：MainActivity 清理（1天）

步骤6.1：移除已迁移的方法
- 删除已移到 NoteListManager 的方法
- 保留必要的工具方法（formatTimestamp 等）

步骤6.2：更新调用点
- `saveMoment()` 中调用 `noteListManager.refreshNotes()`
- `switchProject()` 中调用 `noteListManager.switchProject()`
- `toggleMultiSelectMode()` 中调用 `noteListManager.setMultiSelectMode()`
- 其他调用点更新

步骤6.3：测试整体功能
- 完整功能测试
- 修复发现的问题

### 五、工具方法使用说明

#### DisplayUtil 工具类

已创建 `DisplayUtil.java` 工具类，包含以下静态方法：

```java
// 1. 格式化时间戳（完整格式）
String timeStr = DisplayUtil.formatTimestamp(timestamp);
// 输出：2024年01月15日，星期一，14:30

// 2. 格式化评论时间戳（简洁格式）
String commentTime = DisplayUtil.formatCommentTimestamp(timestamp);
// 输出：25/11/30 15:20

// 3. dp转px（需要Context）
int px = DisplayUtil.dpToPx(context, 8);
// 或者使用Resources
int px = DisplayUtil.dpToPx(resources, 8);
```

#### 在 NoteListManager 中使用

```java
public class NoteListManager {
    private INoteListCallback callback;
    
    private void updateListItem(View view, Note note) {
        // 直接使用 DisplayUtil，不需要通过 callback
        String timeStr = DisplayUtil.formatTimestamp(note.getTimestamp());
        TextView timeView = view.findViewById(R.id.timestampText);
        timeView.setText(timeStr);
        
        // dp转px需要Context，通过callback获取
        int padding = DisplayUtil.dpToPx(callback.getContext(), 8);
        view.setPadding(padding, padding, padding, padding);
    }
}
```

#### 优势

1. **解耦**：工具方法不依赖MainActivity，可以在任何地方使用
2. **复用**：其他类也可以直接使用这些工具方法
3. **简化接口**：INoteListCallback 接口更简洁，不需要定义工具方法
4. **易于测试**：工具方法是静态的，易于单元测试

### 六、关键改动点

#### MainActivity 改动（最小化）

```java
public class MainActivity extends AppCompatActivity implements INoteListCallback {
    // 新增：显示区管理器
    private NoteListManager noteListManager;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // ... 现有代码 ...
        
        // 初始化显示区管理器
        noteListManager = new NoteListManager();
        noteListManager.initialize(momentsListView, this);
        
        // 加载数据（改为调用管理器）
        noteListManager.loadNotes();
    }
    
    // 实现回调接口
    @Override
    public NoteDbHelper getDbHelper() { return dbHelper; }
    
    @Override
    public boolean getShowCost() { return showCost; }
    
    // ... 其他回调方法实现 ...
    
    // 保存后刷新（改为调用管理器）
    private void saveMoment() {
        // ... 保存逻辑 ...
        noteListManager.refreshNotes(); // 替换 loadMoments()
    }
    
    // 切换项目（改为调用管理器）
    private void switchProject(String projectName) {
        // ... 切换逻辑 ...
        noteListManager.switchProject(); // 刷新显示区
    }
}
```

### 六、优势

1. 改动小：只新增 2 个文件（1 个接口 + 1 个类）
2. 复用现有类：`NoteCursorWrapper`、`Note` 等直接复用
3. 迁移简单：将方法从 MainActivity 移到 NoteListManager
4. 接口简单：只定义必要的回调方法
5. 易于测试：显示区逻辑集中在一个类中

### 七、预计时间

- 总时间：6-8 天
- 阶段1：1 天（基础结构）
- 阶段2：1 天（数据加载）
- 阶段3：2 天（数据操作）
- 阶段4：2 天（UI 相关）
- 阶段5：1 天（事件处理）
- 阶段6：1 天（清理和测试）

这个方案改动更小，主要做代码迁移，不新增过多抽象。请说明你的思路，我可以据此进一步调整。
# 桌面 Widget（三条笔记）需求实现文档

## 目标
在桌面小组件中展示 3 条笔记，支持跨项目选择，复用时间线列表进行选择与配置。

## 设计原则
- 复用现有时间线能力，跨项目统一入口。
- 不增加 MainActivity 复杂度，独立模块实现。
- 小组件使用 RemoteViews，避免复杂列表。

## 功能范围
- 显示 3 条笔记（仅 Note 类型）。
- 配置页选择范围与笔记。
- 支持高度风格（紧凑/标准/宽松）影响字体与间距。
- 点击跳转应用（主页或具体笔记详情）。
- 支持保存后刷新、删除笔记兜底显示。

## 配置项
1. 选择笔记（时间线列表，含项目名）**- 可配置1-5条**
2. 显示范围：当前项目 / 所有项目 / 指定项目（多选）
3. 高度风格：紧凑 / 标准 / 宽松（控制字号、padding、行高）
4. **笔记条数**（新增）：
   - 1条笔记
   - 2条笔记
   - 3条笔记（默认）
   - 4条笔记
   - 5条笔记
5. **点击动作**：
   - 打开应用主页：点击 Widget 笔记后跳转至 MainActivity，保持当前项目视图
   - 打开该笔记详情：点击后打开 NoteDetailActivity 直接查看笔记详情
6. **刷新策略**：
   - 手动刷新：Widget 底部显示刷新按钮，点击后重新加载笔记内容
   - 自动刷新：笔记保存/删除时自动触发 Widget 更新（默认推荐）

最小可用配置：1、2、3、4。
笔记条数默认：3条。
点击动作默认：打开应用主页。
刷新策略默认：自动刷新。

## 关键方案（与讨论一致）
### 1) Widget 展示
- 使用 AppWidgetProvider + RemoteViews。
- 3 条笔记用 3 个 TextView 固定布局，不用 RemoteViewsService。
- 额外显示项目名（可选）与时间简写。

### 2) 配置入口
- 系统添加 Widget 时自动进入配置页。
- 配置 Activity 注册为 APPWIDGET_CONFIGURE。

### 3) 数据选择
- 配置页复用时间线列表（统一跨项目入口）。
- 仅展示 Note 类型，忽略 Comment/Audio 等。
- 选择 3 条后保存到 widget 专属配置。

### 4) 数据存储
- SharedPreferences 保存：
  - appWidgetId -> noteIds(可变数量1-5)
  - appWidgetId -> projectNames(可变数量1-5)
  - appWidgetId -> displayRange, heightStyle
  - appWidgetId -> noteCount（1-5，默认3）
  - appWidgetId -> clickAction（0=打开主页, 1=打开详情）
  - appWidgetId -> refreshStrategy（0=手动按钮, 1=自动刷新）

### 5) 刷新机制
- 保存配置后立即刷新该 widget。
- 保存/删除笔记时触发刷新（可在保存逻辑里调用统一更新入口）。
- 失效笔记显示“已删除/不可用”。

## 文件结构（建议）
- app/src/main/java/person/notfresh/noteplus/widget/NoteWidgetProvider.java
- app/src/main/java/person/notfresh/noteplus/widget/NoteWidgetConfigActivity.java
- app/src/main/java/person/notfresh/noteplus/widget/NoteWidgetUpdater.java
- app/src/main/java/person/notfresh/noteplus/widget/NoteWidgetDataSource.java
- app/src/main/res/layout/widget_note_3.xml
- app/src/main/res/layout/activity_widget_config.xml
- app/src/main/res/xml/note_widget_info.xml
- app/src/main/AndroidManifest.xml

## 交互流程
1. 用户添加桌面小组件。
2. 系统打开配置页：
   - 选择显示范围（当前/所有/指定项目）
   - 选择笔记条数（1-5条，默认3条）
   - 从时间线列表勾选对应数量的笔记
   - 选择高度风格（紧凑/标准/宽松）
   - 选择点击动作（打开主页/打开详情）
   - 选择刷新策略（手动按钮/自动刷新）
   - 保存配置
3. 保存后刷新 widget，显示配置数量的笔记（1-5条）。
4. 点击某条笔记按照配置的动作跳转（主页或详情页）。
5. 若配置为手动刷新，Widget 底部显示刷新按钮；否则保存笔记时自动更新。

## 高度策略说明
- 不能强制固定像素高度（由桌面网格决定）。
- 通过“高度风格”控制文字大小/间距/是否显示副信息来模拟不同高度。

## 多项目处理
- 保存 projectName + noteId。
- 刷新时使用对应项目 dbHelper 读取内容。

## 风险与注意事项
- RemoteViews 不能使用复杂控件。
- 启动器对尺寸控制有限，需做 UI 适配。
- 跨项目查询需要保证 dbHelper 可用。

## 交付清单
- Widget Provider + 配置页 + 更新工具类
- 布局与 provider 配置
- 配置持久化与刷新逻辑
- 基础错误兜底（笔记被删）

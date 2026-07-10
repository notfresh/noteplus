# 跳转到指定日期功能设计

## 概述

在 NotePlus Android 应用中添加"跳转到指定日期"功能，用户可以快速定位到特定日期的笔记，方便回顾。

---

## 1. 需求场景

- 用户想查看某一天的所有笔记
- 点击日历图标弹出日期选择器
- 选择有笔记的日期后，自动滚动到该日期的第一条笔记
- 没有笔记的日期在日历上禁用显示，不可点击

---

## 2. UI 设计

### 2.1 入口位置（两处）

| 位置 | 触发方式 |
|------|----------|
| MainActivity 标题栏 | 菜单项，日历图标按钮 |
| Timeline 对话框 | 标题栏右侧，日历图标按钮 |

### 2.2 日历对话框布局（dialog_date_jump.xml）

```
┌─────────────────────────────────┐
│  跳转到日期                 [×]  │  ← 标题栏，Primary 色
├─────────────────────────────────┤
│      < 2024年3月 >              │  ← 月份切换（CalendarView 自带）
├─────────────────────────────────┤
│  一  二  三  四  五  六  日      │  ← 星期标题
│  ○   ○   ○   ○   ○   ●   ●     │  ← 日期（● = 有笔记可点，○ = 无笔记禁用）
│  ...                            │
└─────────────────────────────────┘
```

**样式说明：**
- 整体风格复用 `dialog_timeline.xml` 的布局结构
- 标题栏背景：`?attr/colorPrimary`
- 星期标题：周一～周五黑色，周六周日红色
- 有笔记的日期：深色圆角背景，可点击
- 无笔记的日期：灰色，不可点击

---

## 3. 组件设计

### 3.1 新增文件

| 文件 | 用途 |
|------|------|
| `res/layout/dialog_date_jump.xml` | 日历对话框布局 |
| `res/layout/calendar_day_layout.xml` | 日期单元格布局 |
| `res/layout/calendar_month_header.xml` | 月份头部布局 |
| `res/layout/calendar_week_header.xml` | 星期标题布局 |
| `java/.../ui/DateJumpDialog.java` | 日历对话框类 |

### 3.2 依赖

```groovy
// settings.gradle
maven { url 'https://jitpack.io' }

// app/build.gradle
implementation 'com.github.kizitonwose:CalendarView:1.0.4'
```

---

## 4. 实现逻辑

### 4.1 日期范围

- **动态范围**：根据笔记数据的时间跨度
- 查询：`SELECT MIN(timestamp), MAX(timestamp) FROM notes`
- 起始月 = 最早笔记所在月 - 1个月缓冲
- 结束月 = 当前月 + 1个月缓冲

### 4.2 有笔记日期的获取

```java
// 获取所有有笔记的日期集合
Set<String> datesWithNotes = new HashSet<>();
Cursor cursor = db.rawQuery(
    "SELECT DISTINCT date(timestamp/1000, 'unixepoch') FROM notes",
    null);
while (cursor.moveToNext()) {
    datesWithNotes.add(cursor.getString(0));
}
```

### 4.3 日历绑定逻辑

```java
// DayBinder.bind()
if (day.getOwner() != DayOwner.THIS_MONTH) {
    // 非当月日期隐藏
    return;
}

String dateKey = day.getDate().format(dayFormatter); // "yyyy-MM-dd"
boolean hasNotes = datesWithNotes.contains(dateKey);

if (hasNotes) {
    // 可点击：高亮背景，设置 onClickListener
    container.dayText.setBackgroundResource(R.drawable.has_notes_bg);
    container.dayText.setOnClickListener(v -> onDateSelected(day.getDate()));
} else {
    // 禁用：灰色文字，不可点击
    container.dayText.setTextColor(Color.GRAY);
    container.dayText.setEnabled(false);
}
```

### 4.4 跳转逻辑

```java
private void onDateSelected(LocalDate date) {
    // 1. 关闭对话框
    dismiss();

    // 2. 调用 NoteListManager 滚动到该日期
    String dateStr = date.format(dayFormatter); // "yyyy-MM-dd"
    noteListManager.scrollToDate(dateStr);
}
```

### 4.5 NoteListManager.scrollToDate 新增方法

```java
/**
 * 滚动到指定日期的第一条笔记
 * @param dateStr 日期字符串，格式 "yyyy-MM-dd"
 * @return 是否成功定位
 */
public boolean scrollToDate(String dateStr) {
    // 1. 解析日期为时间戳范围
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    long startOfDay = sdf.parse(dateStr).getTime();
    long endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1;

    // 2. 在 adapter 中找到该日期的第一条笔记
    for (int i = 0; i < adapter.getCount(); i++) {
        Note note = (Note) adapter.getItem(i);
        if (note.getTimestamp() >= startOfDay && note.getTimestamp() <= endOfDay) {
            // 3. 滚动到该位置
            return scrollToNote(note.getId());
        }
    }
    return false;
}
```

---

## 5. MainActivity 集成

### 5.1 标题栏菜单

在菜单资源文件中添加：
```xml
<item
    android:id="@+id/action_jump_to_date"
    android:icon="@drawable/ic_calendar"
    android:title="跳转到日期"
    app:showAsAction="ifRoom" />
```

### 5.2 点击处理

```java
case R.id.action_jump_to_date:
    showDateJumpDialog();
    return true;
```

---

## 6. Timeline 对话框集成

在 `dialog_timeline.xml` 标题栏右侧添加日历图标按钮：

```xml
<ImageButton
    android:id="@+id/btnJumpToDate"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:src="@drawable/ic_calendar"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="跳转到日期" />
```

---

## 7. 日期格式约定

| 用途 | 格式 | 示例 |
|------|------|------|
| 数据库查询 | `date(timestamp/1000, 'unixepoch')` | `2024-03-15` |
| dayFormatter | `yyyy-MM-dd` | `2024-03-15` |
| monthFormatter | `yyyy年MM月` | `2024年03月` |

---

## 8. 技术注意事项

1. **时间戳精度**：假设 `timestamp` 是毫秒级
2. **ViewBinding**：使用 ViewBinding 操作布局
3. **数据库查询**：在后台线程执行，避免阻塞 UI
4. **内存管理**：对话框关闭时清理资源

---

## 9. 实现顺序

1. 添加 Kizitonwose CalendarView 依赖
2. 创建 `dialog_date_jump.xml` 布局
3. 创建 `DateJumpDialog.java` 对话框类
4. 在 NoteListManager 添加 `scrollToDate` 方法
5. MainActivity 标题栏集成
6. Timeline 对话框标题栏集成

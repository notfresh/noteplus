# 跳转到指定日期功能实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 NotePlus 添加日历视图跳转功能，用户可快速定位到特定日期的笔记

**Architecture:**
- 日历视图基于 Kizitonwose CalendarView，显示有笔记的日期供点击
- 对话框形式弹出日历，选择日期后自动滚动到该日期第一条笔记
- 两处触发入口：MainActivity 标题栏菜单、Timeline 对话框标题栏

**Tech Stack:** Android (Java), Kizitonwose CalendarView 1.0.4, ViewBinding

---

## 文件结构

| 文件 | 用途 |
|------|------|
| Create: `app/build.gradle.kts` | 添加 CalendarView 依赖 |
| Create: `app/src/main/res/layout/dialog_date_jump.xml` | 日历对话框布局 |
| Create: `app/src/main/res/layout/calendar_day_layout.xml` | 日期单元格布局 |
| Create: `app/src/main/res/layout/calendar_month_header.xml` | 月份头部布局 |
| Create: `app/src/main/res/layout/calendar_week_header.xml` | 星期标题布局 |
| Create: `app/src/main/res/drawable/has_notes_bg.xml` | 有笔记日期的背景 drawable |
| Create: `app/src/main/java/person/notfresh/noteplus/ui/DateJumpDialog.java` | 日历对话框类 |
| Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java` | 添加 scrollToDate 方法 |
| Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java` | 菜单集成、Timeline 集成 |
| Modify: `app/src/main/res/menu/menu_main.xml` | 添加跳转菜单项 |
| Modify: `app/src/main/res/layout/dialog_timeline.xml` | Timeline 标题栏添加日历按钮 |

---

## Task 1: 添加 CalendarView 依赖

**Files:**
- Modify: `app/build.gradle.kts:37-47`

- [ ] **Step 1: 添加依赖**

在 `dependencies` 块末尾添加：

```kotlin
// Kizitonwose CalendarView for date jump feature
implementation("com.github.kizitonwose:CalendarView:1.0.4")
```

- [ ] **Step 2: 验证依赖同步**

Run: `./gradlew :app:dependencies --configuration releaseRuntimeClasspath 2>&1 | grep -i calendar`
Expected: 显示 `com.github.kizitonwose:CalendarView:1.0.4`

- [ ] **Step 3: 提交**

```bash
git add app/build.gradle.kts
git commit -m "feat: add Kizitonwose CalendarView dependency

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 2: 创建日历相关布局文件

**Files:**
- Create: `app/src/main/res/layout/calendar_day_layout.xml`
- Create: `app/src/main/res/layout/calendar_month_header.xml`
- Create: `app/src/main/res/layout/calendar_week_header.xml`
- Create: `app/src/main/res/drawable/has_notes_bg.xml`

- [ ] **Step 1: 创建 calendar_week_header.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="horizontal"
    android:paddingVertical="8dp"
    android:background="?attr/colorSurface">

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周一"
        android:textSize="12sp"
        android:textColor="#666666"/>

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周二"
        android:textSize="12sp"
        android:textColor="#666666"/>

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周三"
        android:textSize="12sp"
        android:textColor="#666666"/>

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周四"
        android:textSize="12sp"
        android:textColor="#666666"/>

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周五"
        android:textSize="12sp"
        android:textColor="#666666"/>

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周六"
        android:textSize="12sp"
        android:textColor="#FF6B6B"/>

    <TextView
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_weight="1"
        android:gravity="center"
        android:text="周日"
        android:textSize="12sp"
        android:textColor="#FF6B6B"/>

</LinearLayout>
```

- [ ] **Step 2: 创建 calendar_day_layout.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:padding="4dp">

    <TextView
        android:id="@+id/dayText"
        android:layout_width="36dp"
        android:layout_height="36dp"
        android:layout_gravity="center"
        android:gravity="center"
        android:textSize="14sp"/>

</FrameLayout>
```

- [ ] **Step 3: 创建 calendar_month_header.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/headerText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:gravity="center"
        android:textSize="18sp"
        android:textStyle="bold"/>

</LinearLayout>
```

- [ ] **Step 4: 创建 has_notes_bg.xml (drawable)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#2196F3"/>
    <size android:width="36dp" android:height="36dp"/>
</shape>
```

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/layout/calendar_week_header.xml \
        app/src/main/res/layout/calendar_day_layout.xml \
        app/src/main/res/layout/calendar_month_header.xml \
        app/src/main/res/drawable/has_notes_bg.xml
git commit -m "feat: add calendar view layouts for date jump

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 3: 创建对话框布局 dialog_date_jump.xml

**Files:**
- Create: `app/src/main/res/layout/dialog_date_jump.xml`

- [ ] **Step 1: 创建 dialog_date_jump.xml**

参考 `dialog_timeline.xml` 风格：

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="0dp">

    <!-- 标题栏 -->
    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="16dp"
        android:background="?attr/colorPrimary">

        <TextView
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:text="跳转到日期"
            android:textColor="#FFFFFF"
            android:textSize="18sp"
            android:textStyle="bold" />

        <ImageButton
            android:id="@+id/btnCloseDateJump"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:contentDescription="关闭"
            android:tint="#FFFFFF"/>

    </LinearLayout>

    <!-- 日历视图 -->
    <com.kizitonwose.calendarview.CalendarView
        android:id="@+id/calendarView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:padding="8dp"
        app:cv_dayViewResource="@layout/calendar_day_layout"
        app:cv_monthHeaderResource="@layout/calendar_month_header"
        app:cv_weekHeaderResource="@layout/calendar_week_header"
        xmlns:app="http://schemas.android.com/apk/res-auto"/>

</LinearLayout>
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/res/layout/dialog_date_jump.xml
git commit -m "feat: add date jump dialog layout

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 4: 创建 DateJumpDialog.java

**Files:**
- Create: `app/src/main/java/person/notfresh/noteplus/ui/DateJumpDialog.java`

- [ ] **Step 1: 创建 DateJumpDialog.java**

```java
package person.notfresh.noteplus.ui;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.kizitonwose.calendarview.CalendarView;
import com.kizitonwose.calendarview.model.CalendarDay;
import com.kizitonwose.calendarview.model.DayOwner;
import com.kizitonwose.calendarview.model.MonthDescriptor;
import com.kizitonwose.calendarview.ui.DayBinder;
import com.kizitonwose.calendarview.ui.MonthHeaderFooterBinder;
import com.kizitonwose.calendarview.ui.ViewContainer;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import person.notfresh.noteplus.R;
import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.manager.NoteListManager;

public class DateJumpDialog extends DialogFragment {

    private static final String KEY_NOTE_ID = "note_id";

    private NoteListManager noteListManager;
    private NoteDbHelper dbHelper;
    private Set<String> datesWithNotes = new HashSet<>();
    private DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy年MM月", Locale.CHINESE);

    public interface OnDateSelectedListener {
        void onDateSelected(LocalDate date);
    }

    public static DateJumpDialog newInstance() {
        return new DateJumpDialog();
    }

    public void setNoteListManager(NoteListManager manager) {
        this.noteListManager = manager;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_date_jump, null);

        // 初始化数据库
        dbHelper = new NoteDbHelper(requireContext());
        dbHelper.open();

        // 加载有笔记的日期
        loadDatesWithNotes();

        // 设置日历
        setupCalendarView(view);

        // 关闭按钮
        ImageButton closeButton = view.findViewById(R.id.btnCloseDateJump);
        closeButton.setOnClickListener(v -> dismiss());

        builder.setView(view);
        return builder.create();
    }

    private void loadDatesWithNotes() {
        android.database.Cursor cursor = null;
        try {
            cursor = dbHelper.getReadableDatabase().rawQuery(
                "SELECT DISTINCT date(timestamp/1000, 'unixepoch') FROM notes",
                null);
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                if (date != null) {
                    datesWithNotes.add(date);
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void setupCalendarView(View view) {
        CalendarView calendarView = view.findViewById(R.id.calendarView);

        // 设置日历范围：动态计算
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth;
        YearMonth lastMonth = currentMonth;

        // 扩展到数据覆盖的范围
        if (!datesWithNotes.isEmpty()) {
            LocalDate earliest = LocalDate.MAX;
            LocalDate latest = LocalDate.MIN;
            for (String dateStr : datesWithNotes) {
                try {
                    LocalDate date = LocalDate.parse(dateStr, dayFormatter);
                    if (date.isBefore(earliest)) earliest = date;
                    if (date.isAfter(latest)) latest = date;
                } catch (Exception e) {
                    // skip invalid dates
                }
            }
            if (!earliest.equals(LocalDate.MAX)) {
                firstMonth = YearMonth.from(earliest.minusMonths(1));
            }
            if (!latest.equals(LocalDate.MIN)) {
                lastMonth = YearMonth.from(latest.plusMonths(1));
            }
        }

        // 额外扩展6个月前后
        firstMonth = firstMonth.minusMonths(6);
        lastMonth = lastMonth.plusMonths(6);

        calendarView.setup(firstMonth, lastMonth, DayOfWeek.MONDAY);
        calendarView.scrollToMonth(currentMonth);

        // 日期单元格绑定
        calendarView.setDayBinder(new DayBinder<DayViewContainer>() {
            @Override
            public DayViewContainer create(View view) {
                return new DayViewContainer(view);
            }

            @Override
            public void bind(DayViewContainer container, CalendarDay day) {
                container.day = day;

                if (day.getOwner() != DayOwner.THIS_MONTH) {
                    container.dayText.setVisibility(View.INVISIBLE);
                    container.dayText.setEnabled(false);
                    return;
                }

                container.dayText.setVisibility(View.VISIBLE);
                container.dayText.setText(String.valueOf(day.getDate().getDayOfMonth()));

                String dateKey = day.getDate().format(dayFormatter);
                boolean hasNotes = datesWithNotes.contains(dateKey);

                if (hasNotes) {
                    container.dayText.setBackgroundResource(R.drawable.has_notes_bg);
                    container.dayText.setTextColor(Color.WHITE);
                    container.dayText.setEnabled(true);
                    container.dayText.setOnClickListener(v -> onDateSelected(day.getDate()));
                } else {
                    container.dayText.setBackground(null);
                    container.dayText.setTextColor(Color.GRAY);
                    container.dayText.setEnabled(false);
                    container.dayText.setOnClickListener(null);
                }
            }
        });

        // 月份头部绑定
        calendarView.setMonthHeaderBinder(new MonthHeaderFooterBinder<MonthViewContainer>() {
            @Override
            public MonthViewContainer create(View view) {
                return new MonthViewContainer(view);
            }

            @Override
            public void bind(MonthViewContainer container, MonthDescriptor month) {
                container.headerText.setText(month.getYearMonth().format(monthFormatter));
            }
        });
    }

    private void onDateSelected(LocalDate date) {
        dismiss();
        if (noteListManager != null) {
            String dateStr = date.format(dayFormatter);
            boolean found = noteListManager.scrollToDate(dateStr);
            if (!found) {
                Toast.makeText(requireContext(), "该日期无笔记", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (dbHelper != null) {
            dbHelper.close();
        }
    }

    static class DayViewContainer extends ViewContainer {
        TextView dayText;
        CalendarDay day;

        DayViewContainer(View view) {
            super(view);
            dayText = view.findViewById(R.id.dayText);
        }
    }

    static class MonthViewContainer extends ViewContainer {
        TextView headerText;

        MonthViewContainer(View view) {
            super(view);
            headerText = view.findViewById(R.id.headerText);
        }
    }
}
```

- [ ] **Step 2: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/ui/DateJumpDialog.java
git commit -m "feat: add DateJumpDialog calendar view

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 5: 在 NoteListManager 添加 scrollToDate 方法

**Files:**
- Modify: `app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java`

- [ ] **Step 1: 在 NoteListManager.java 中添加 scrollToDate 方法**

在 `scrollToNote` 方法后面添加：

```java
/**
 * 滚动到指定日期的第一条笔记
 * @param dateStr 日期字符串，格式 "yyyy-MM-dd"
 * @return 是否成功定位
 */
public boolean scrollToDate(String dateStr) {
    if (adapter == null || listView == null) {
        return false;
    }

    try {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        long startOfDay = sdf.parse(dateStr).getTime();
        long endOfDay = startOfDay + 24 * 60 * 60 * 1000 - 1;

        // 在 adapter 中找到该日期的第一条笔记
        for (int i = 0; i < adapter.getCount(); i++) {
            Object item = adapter.getItem(i);
            if (item instanceof Note) {
                Note note = (Note) item;
                long timestamp = note.getTimestamp();
                if (timestamp >= startOfDay && timestamp <= endOfDay) {
                    // 滚动到该位置
                    return scrollToNote(note.getId());
                }
            }
        }
    } catch (Exception e) {
        e.printStackTrace();
    }
    return false;
}
```

需要添加 import：
```java
import java.util.Locale;
```

- [ ] **Step 2: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: 提交**

```bash
git add app/src/main/java/person/notfresh/noteplus/manager/NoteListManager.java
git commit -m "feat: add scrollToDate method to NoteListManager

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 6: MainActivity 标题栏集成

**Files:**
- Modify: `app/src/main/res/menu/menu_main.xml`
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 在 menu_main.xml 添加菜单项**

在 `action_settings` 之前添加：

```xml
<item
    android:id="@+id/action_jump_to_date"
    android:icon="@android:drawable/ic_menu_my_calendar"
    android:title="跳转到日期"
    app:showAsAction="ifRoom" />
```

- [ ] **Step 2: 在 MainActivity 中添加 showDateJumpDialog 方法**

在 `showTimelineDialog` 方法附近添加：

```java
/**
 * 显示跳转到日期对话框
 */
private void showDateJumpDialog() {
    if (noteListManager == null) {
        return;
    }

    DateJumpDialog dialog = DateJumpDialog.newInstance();
    dialog.setNoteListManager(noteListManager);
    dialog.show(getSupportFragmentManager(), "date_jump");
}
```

- [ ] **Step 3: 在 onOptionsItemSelected 中添加处理**

找到 `action_timeline` 的 case，在旁边添加：

```java
} else if (id == R.id.action_jump_to_date) {
    showDateJumpDialog();
    return true;
}
```

- [ ] **Step 4: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: 提交**

```bash
git add app/src/main/res/menu/menu_main.xml app/src/main/java/person/notfresh/noteplus/MainActivity.java
git commit -m "feat: integrate date jump to MainActivity menu

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## Task 7: Timeline 对话框标题栏集成

**Files:**
- Modify: `app/src/main/res/layout/dialog_timeline.xml`
- Modify: `app/src/main/java/person/notfresh/noteplus/MainActivity.java`

- [ ] **Step 1: 在 dialog_timeline.xml 标题栏添加日历按钮**

在标题栏的 TextView 之后、关闭按钮之前添加：

```xml
<ImageButton
    android:id="@+id/btnJumpToDate"
    android:layout_width="48dp"
    android:layout_height="48dp"
    android:src="@android:drawable/ic_menu_my_calendar"
    android:background="?attr/selectableItemBackgroundBorderless"
    android:contentDescription="跳转到日期"
    android:tint="#FFFFFF"/>
```

- [ ] **Step 2: 在 MainActivity 的 showTimelineDialog 中添加按钮处理**

在创建 dialog 后、设置 closeButton 之前，添加：

```java
ImageButton jumpToDateButton = dialogView.findViewById(R.id.btnJumpToDate);
jumpToDateButton.setOnClickListener(v -> {
    dialog.dismiss();
    showDateJumpDialog();
});
```

- [ ] **Step 3: 验证编译**

Run: `./gradlew :app:compileDebugJavaWithJavac 2>&1 | tail -20`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: 提交**

```bash
git add app/src/main/res/layout/dialog_timeline.xml app/src/main/java/person/notfresh/noteplus/MainActivity.java
git commit -m "feat: integrate date jump to Timeline dialog

Co-Authored-By: Claude Opus 4.7 <noreply@anthropic.com>"
```

---

## 自检清单

完成所有 Task 后，检查：

1. [ ] 依赖同步成功，CalendarView 已添加
2. [ ] 所有布局文件创建完成
3. [ ] DateJumpDialog 可以正常弹出
4. [ ] 日历显示有笔记的日期（蓝色圆角背景）
5. [ ] 日历显示无笔记的日期（灰色，不可点击）
6. [ ] 点击有笔记的日期后，对话框关闭并滚动到该日期第一条笔记
7. [ ] MainActivity 菜单有日历图标，点击可弹出日期选择
8. [ ] Timeline 对话框标题栏有日历图标，点击可弹出日期选择
9. [ ] 所有 commit 已完成

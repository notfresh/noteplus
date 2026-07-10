package person.notfresh.noteplus.ui;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import com.kizitonwose.calendarview.CalendarView;
import com.kizitonwose.calendarview.model.CalendarDay;
import com.kizitonwose.calendarview.model.CalendarMonth;
import com.kizitonwose.calendarview.model.DayOwner;
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
import person.notfresh.noteplus.core.TimeRangeFilter;
import person.notfresh.noteplus.core.NoteDataLoader;
import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;
import person.notfresh.noteplus.manager.NoteListManager;

public class DateJumpDialog extends DialogFragment {

    public interface OnDateSelectedListener {
        void onDateSelected(LocalDate date);
    }

    private OnDateSelectedListener dateSelectedListener;
    private NoteListManager noteListManager;
    private NoteDbHelper dbHelper;
    private Set<String> datesWithNotes = new HashSet<>();
    private DateTimeFormatter dayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("yyyy年MM月", Locale.CHINESE);

    // Cross-project mode for Timeline
    private boolean isCrossProject = false;
    private TimeRangeFilter timeRangeFilter = null;

    public static DateJumpDialog newInstance() {
        return new DateJumpDialog();
    }

    public void setNoteListManager(NoteListManager manager) {
        this.noteListManager = manager;
    }

    public void setOnDateSelectedListener(OnDateSelectedListener listener) {
        this.dateSelectedListener = listener;
    }

    public void setCrossProject(boolean crossProject) {
        this.isCrossProject = crossProject;
    }

    public void setTimeRangeFilter(TimeRangeFilter filter) {
        this.timeRangeFilter = filter;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.dialog_date_jump, null);

        ProjectContextManager manager = ProjectContextManager.getInstance(requireContext());
        String currentProject = manager.getCurrentProject();
        android.util.Log.d("DateJumpDialog", "onCreateDialog - 当前项目: " + currentProject + ", isCrossProject: " + isCrossProject);

        dbHelper = manager.getCurrentDbHelper();

        loadDatesWithNotes();
        setupCalendarView(view);
        setupCloseButton(view);

        builder.setView(view);
        return builder.create();
    }

    private void loadDatesWithNotes() {
        datesWithNotes.clear();

        if (isCrossProject) {
            loadCrossProjectDates();
        } else {
            loadSingleProjectDates();
        }
    }

    private void loadSingleProjectDates() {
        android.database.Cursor cursor = null;
        try {
            android.database.sqlite.SQLiteDatabase db = dbHelper.getReadableDatabase();
            android.util.Log.d("DateJumpDialog", "单项目模式 - 数据库路径: " + db.getPath() + ", 项目: " + ProjectContextManager.getInstance(requireContext()).getCurrentProject());

            String query;
            String[] args;

            if (timeRangeFilter != null) {
                // 使用时间范围过滤
                java.util.Calendar calendar = java.util.Calendar.getInstance();
                long currentTime = calendar.getTimeInMillis();
                calendar.add(java.util.Calendar.DAY_OF_YEAR, -timeRangeFilter.getDays());
                long startTime = calendar.getTimeInMillis();

                query = "SELECT DISTINCT date(timestamp/1000, 'unixepoch') FROM notes WHERE timestamp >= ?";
                args = new String[]{String.valueOf(startTime)};
                android.util.Log.d("DateJumpDialog", "时间范围过滤: " + timeRangeFilter.getDays() + "天，起始时间: " + startTime);
            } else {
                query = "SELECT DISTINCT date(timestamp/1000, 'unixepoch') FROM notes";
                args = null;
            }

            cursor = db.rawQuery(query, args);
            android.util.Log.d("DateJumpDialog", "查询到 " + cursor.getCount() + " 条日期记录");
            while (cursor.moveToNext()) {
                String date = cursor.getString(0);
                if (date != null) {
                    datesWithNotes.add(date);
                }
            }
            android.util.Log.d("DateJumpDialog", "datesWithNotes 大小: " + datesWithNotes.size());
        } catch (Exception e) {
            android.util.Log.e("DateJumpDialog", "查询异常", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
    }

    private void loadCrossProjectDates() {
        android.util.Log.d("DateJumpDialog", "跨项目模式 - 遍历所有项目数据库");

        ProjectContextManager projectManager = ProjectContextManager.getInstance(requireContext());
        java.util.List<String> projects = projectManager.getProjectList();
        java.util.List<String> recycledProjects = projectManager.getRecycledProjects();

        for (String projectName : projects) {
            if (recycledProjects.contains(projectName)) {
                android.util.Log.d("DateJumpDialog", "跳过回收站项目: " + projectName);
                continue;
            }

            try {
                NoteDbHelper projectDbHelper = projectManager.getDbHelperForProject(projectName);
                if (projectDbHelper == null) {
                    continue;
                }

                android.database.sqlite.SQLiteDatabase db = projectDbHelper.getReadableDatabase();
                android.util.Log.d("DateJumpDialog", "项目: " + projectName + ", 数据库: " + db.getPath());

                String query;
                String[] args;

                if (timeRangeFilter != null) {
                    java.util.Calendar calendar = java.util.Calendar.getInstance();
                    long currentTime = calendar.getTimeInMillis();
                    calendar.add(java.util.Calendar.DAY_OF_YEAR, -timeRangeFilter.getDays());
                    long startTime = calendar.getTimeInMillis();

                    query = "SELECT DISTINCT date(timestamp/1000, 'unixepoch') FROM notes WHERE timestamp >= ?";
                    args = new String[]{String.valueOf(startTime)};
                } else {
                    query = "SELECT DISTINCT date(timestamp/1000, 'unixepoch') FROM notes";
                    args = null;
                }

                android.database.Cursor cursor = db.rawQuery(query, args);
                android.util.Log.d("DateJumpDialog", "项目 " + projectName + " 查询到 " + cursor.getCount() + " 条日期记录");
                while (cursor.moveToNext()) {
                    String date = cursor.getString(0);
                    if (date != null) {
                        datesWithNotes.add(date);
                    }
                }
                cursor.close();
            } catch (Exception e) {
                android.util.Log.e("DateJumpDialog", "加载项目 " + projectName + " 日期失败", e);
            }
        }

        android.util.Log.d("DateJumpDialog", "跨项目 datesWithNotes 总大小: " + datesWithNotes.size());
    }

    private void setupCalendarView(View view) {
        CalendarView calendarView = view.findViewById(R.id.calendarView);

        // 计算日历范围
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth;
        YearMonth lastMonth = currentMonth;

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

        // 强制刷新日历视图以显示新数据
        calendarView.notifyCalendarChanged();

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
                android.util.Log.d("DateJumpDialog", "日期: " + dateKey + ", hasNotes: " + hasNotes + ", datesWithNotes size: " + datesWithNotes.size());

                if (hasNotes) {
                    container.dayText.setBackgroundResource(R.drawable.has_notes_bg);
                    container.dayText.setTextColor(getResources().getColor(android.R.color.white, null));
                    container.dayText.setEnabled(true);
                    container.dayText.setOnClickListener(v -> onDateSelected(day.getDate()));
                } else {
                    container.dayText.setBackground(null);
                    container.dayText.setTextColor(getResources().getColor(android.R.color.darker_gray, null));
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
            public void bind(MonthViewContainer container, CalendarMonth month) {
                container.headerText.setText(month.getYearMonth().format(monthFormatter));
            }
        });
    }

    private void setupCloseButton(View view) {
        view.findViewById(R.id.btnCloseDateJump).setOnClickListener(v -> dismiss());
    }

    private void onDateSelected(LocalDate date) {
        dismiss();
        if (dateSelectedListener != null) {
            dateSelectedListener.onDateSelected(date);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
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
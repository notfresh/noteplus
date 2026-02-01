package person.notfresh.noteplus.core;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;
import person.notfresh.noteplus.core.model.Comment;
import person.notfresh.noteplus.core.model.TimelineItemType;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * 全局时间线加载器
 * 用于跨项目加载所有项目的时间线数据
 */
public class GlobalTimeline {
    
    private final ProjectContextManager projectManager;
    
    /**
     * 构造函数
     * @param projectManager 项目管理器
     */
    public GlobalTimeline(ProjectContextManager projectManager) {
        this.projectManager = projectManager;
    }
    
    /**
     * 加载全局时间线（最近一天）
     * 
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 所有项目的时间线数据，按时间排序
     */
    public List<Comment> loadGlobalTimelineLastDay(boolean descending) {
        return loadGlobalTimeline(TimeRangeFilter.LAST_DAY, descending);
    }
    
    /**
     * 加载全局时间线（最近一周）
     * 
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 所有项目的时间线数据，按时间排序
     */
    public List<Comment> loadGlobalTimelineLastWeek(boolean descending) {
        return loadGlobalTimeline(TimeRangeFilter.LAST_WEEK, descending);
    }
    
    /**
     * 加载全局时间线（最近一个月）
     * 
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 所有项目的时间线数据，按时间排序
     */
    public List<Comment> loadGlobalTimelineLastMonth(boolean descending) {
        return loadGlobalTimeline(TimeRangeFilter.LAST_MONTH, descending);
    }
    
    /**
     * 加载全局时间线（默认逆序）
     * 
     * @param timeRange 时间范围枚举
     * @return 所有项目的时间线数据，按时间逆序排序
     */
    public List<Comment> loadGlobalTimeline(TimeRangeFilter timeRange) {
        return loadGlobalTimeline(timeRange, true);
    }
    
    /**
     * 加载全局时间线
     * 从所有项目中加载指定时间范围内的 Note 和 Comment，展平为统一的时间线
     * 使用 loadFullTimelineByTimeRange 方法，直接查询时间范围内的所有 Note 和 Comment，平等处理
     * 
     * 置顶行为：置顶的Note会同时出现在两个位置：
     * 1. 在列表最前面的置顶区域（按时间排序）
     * 2. 在原本的时间位置（保持原样）
     * 
     * @param timeRange 时间范围枚举：LAST_DAY（最近一天）、LAST_WEEK（最近一周）、LAST_MONTH（最近一个月）
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 所有项目的时间线数据，置顶Note在最前面，然后按时间排序
     */
    public List<Comment> loadGlobalTimeline(TimeRangeFilter timeRange, boolean descending) {
        List<Comment> globalTimeline = new ArrayList<>();
        
        // 获取所有项目列表
        List<String> projects = projectManager.getProjectList();
        
        // 获取回收站中的项目列表，排除这些项目（因为它们的数据库表结构可能不完整）
        List<String> recycledProjects = projectManager.getRecycledProjects();
        
        // 遍历每个项目，加载其时间线数据（排除回收站中的项目）
        for (String projectName : projects) {
            // 跳过回收站中的项目
            if (recycledProjects.contains(projectName)) {
                android.util.Log.d("Timeline", "Timeline: 跳过回收站中的项目: " + projectName);
                continue;
            }
            try {
                // 获取该项目的数据库帮助类
                NoteDbHelper dbHelper = projectManager.getDbHelperForProject(projectName);
                if (dbHelper == null) {
                    continue; // 如果项目不存在或无法获取，跳过
                }
                
                // 创建该项目的 NoteDataLoader
                NoteDataLoader loader = new NoteDataLoader(dbHelper, projectName);
                
                // 加载该项目的时间线数据
                List<Comment> projectTimeline = loader.loadFullTimelineByTimeRange(timeRange, descending);
                
                // 合并到全局时间线
                globalTimeline.addAll(projectTimeline);
                
            } catch (Exception e) {
                // 如果某个项目加载失败，记录错误但继续处理其他项目
                android.util.Log.e("Timeline", "Timeline: 加载项目时间线失败: " + projectName, e);
            }
        }
        
        // 对所有项目的时间线数据进行统一排序
        // 注意：虽然每个项目内部已经排序，但跨项目需要重新排序
        // 排序规则：先按时间排序（不考虑置顶），置顶的Note会在后面复制到最前面
        Collections.sort(globalTimeline, new Comparator<Comment>() {
            @Override
            public int compare(Comment a, Comment b) {
                // 按时间排序（不考虑置顶状态）
                if (descending) {
                    // 逆序：b.timestamp - a.timestamp（最新的在前）
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                } else {
                    // 顺序：a.timestamp - b.timestamp（最旧的在前）
                    return Long.compare(a.getTimestamp(), b.getTimestamp());
                }
            }
        });
        
        // 收集所有置顶的Note，并创建副本添加到列表最前面
        // 这样置顶的Note会同时出现在置顶区域和原时间位置
        List<Comment> pinnedNotes = new ArrayList<>();
        for (Comment item : globalTimeline) {
            // 只处理Note类型且置顶的项
            if (item.getItemType() == TimelineItemType.NOTE && item.isPinned()) {
                // 创建置顶Note的副本
                Comment pinnedCopy = createPinnedCopy(item);
                pinnedNotes.add(pinnedCopy);
            }
        }
        
        // 将置顶的Note副本添加到列表最前面
        // 置顶Note之间也按时间排序
        Collections.sort(pinnedNotes, new Comparator<Comment>() {
            @Override
            public int compare(Comment a, Comment b) {
                if (descending) {
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                } else {
                    return Long.compare(a.getTimestamp(), b.getTimestamp());
                }
            }
        });
        
        // 将置顶Note副本插入到列表最前面
        globalTimeline.addAll(0, pinnedNotes);
        
        // 插入日期分割线标记
        globalTimeline = insertDateDividers(globalTimeline, descending);
        
        return globalTimeline;
    }
    
    /**
     * 获取所有项目名称列表
     * 
     * @return 项目名称列表
     */
    public List<String> getAllProjects() {
        return projectManager.getProjectList();
    }
    
    /**
     * 在时间线数据中插入日期分割线标记
     * 在跨天的地方插入一个特殊的 Comment 对象，itemType 为 DATE_DIVIDER
     * 
     * @param timeline 已排序的时间线数据
     * @param descending 排序方向（用于确定分割线的位置）
     * @return 插入日期分割线后的时间线数据
     */
    private List<Comment> insertDateDividers(List<Comment> timeline, boolean descending) {
        if (timeline == null || timeline.isEmpty()) {
            return timeline;
        }
        
        List<Comment> result = new ArrayList<>();
        
        // 第一项总是需要显示日期分割线
        long lastDay = getDayStart(timeline.get(0).getTimestamp());
        result.add(createDateDivider(timeline.get(0).getTimestamp()));
        result.add(timeline.get(0));
        
        // 遍历后续项，检查是否跨天
        for (int i = 1; i < timeline.size(); i++) {
            Comment current = timeline.get(i);
            long currentDay = getDayStart(current.getTimestamp());
            
            // 如果日期不同，插入日期分割线
            if (currentDay != lastDay) {
                result.add(createDateDivider(current.getTimestamp()));
                lastDay = currentDay;
            }
            
            result.add(current);
        }
        
        return result;
    }
    
    /**
     * 获取指定时间戳所在日期的开始时间（当天 0 点）
     * 
     * @param timestamp 时间戳
     * @return 当天 0 点的时间戳
     */
    private long getDayStart(long timestamp) {
        Calendar cal = Calendar.getInstance(Locale.CHINA);
        cal.setTimeInMillis(timestamp);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }
    
    /**
     * 创建日期分割线标记对象
     * 
     * @param timestamp 该日期的时间戳（用于格式化显示）
     * @return 日期分割线 Comment 对象
     */
    private Comment createDateDivider(long timestamp) {
        Comment divider = new Comment();
        divider.setItemType(TimelineItemType.DATE_DIVIDER);
        divider.setTimestamp(timestamp);
        
        // 格式化日期文本：yyyy年MM月dd日，星期X
        Calendar calendar = Calendar.getInstance(Locale.CHINA);
        calendar.setTimeInMillis(timestamp);
        
        java.text.SimpleDateFormat dateFormat = new java.text.SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
        String dateStr = dateFormat.format(new java.util.Date(timestamp));
        
        // 获取星期
        String[] weekDays = {"日", "一", "二", "三", "四", "五", "六"};
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        if (dayOfWeek < 0) dayOfWeek = 0;
        
        String dateText = dateStr + "，星期" + weekDays[dayOfWeek];
        divider.setContent(dateText);
        
        return divider;
    }
    
    /**
     * 创建置顶Note的副本
     * 用于在时间线中同时显示在置顶区域和原时间位置
     * 
     * @param original 原始的置顶Note
     * @return 置顶Note的副本
     */
    private Comment createPinnedCopy(Comment original) {
        Comment copy = new Comment();
        copy.setId(original.getId());
        copy.setNoteId(original.getNoteId());
        copy.setParentCommentId(original.getParentCommentId());
        copy.setContent(original.getContent());
        copy.setTimestamp(original.getTimestamp());
        copy.setCost(original.getCost());
        copy.setProjectName(original.getProjectName());
        copy.setItemType(original.getItemType());
        copy.setPinned(original.isPinned());  // 保持置顶状态
        return copy;
    }
}

package person.notfresh.noteplus.core;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;
import person.notfresh.noteplus.core.model.Comment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

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
     * @param timeRange 时间范围枚举：LAST_DAY（最近一天）、LAST_WEEK（最近一周）、LAST_MONTH（最近一个月）
     * @param descending true表示逆序（最新的在前），false表示顺序（最旧的在前）
     * @return 所有项目的时间线数据，按时间排序
     */
    public List<Comment> loadGlobalTimeline(TimeRangeFilter timeRange, boolean descending) {
        List<Comment> globalTimeline = new ArrayList<>();
        
        // 获取所有项目列表
        List<String> projects = projectManager.getProjectList();
        
        // 遍历每个项目，加载其时间线数据
        for (String projectName : projects) {
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
                e.printStackTrace();
            }
        }
        
        // 对所有项目的时间线数据进行统一排序
        // 注意：虽然每个项目内部已经排序，但跨项目需要重新排序
        Collections.sort(globalTimeline, new Comparator<Comment>() {
            @Override
            public int compare(Comment a, Comment b) {
                if (descending) {
                    // 逆序：b.timestamp - a.timestamp（最新的在前）
                    return Long.compare(b.getTimestamp(), a.getTimestamp());
                } else {
                    // 顺序：a.timestamp - b.timestamp（最旧的在前）
                    return Long.compare(a.getTimestamp(), b.getTimestamp());
                }
            }
        });
        
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
}

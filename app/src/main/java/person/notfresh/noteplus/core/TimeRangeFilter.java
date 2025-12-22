package person.notfresh.noteplus.core;

/**
 * 时间范围过滤器枚举
 * 用于按时间范围筛选笔记数据
 */
public enum TimeRangeFilter {
    /**
     * 最近一天
     */
    LAST_DAY(1),
    
    /**
     * 最近一周
     */
    LAST_WEEK(7),
    
    /**
     * 最近一个月（按30天计算）
     */
    LAST_MONTH(30);
    
    private final int days;
    
    /**
     * 构造函数
     * @param days 天数
     */
    TimeRangeFilter(int days) {
        this.days = days;
    }
    
    /**
     * 获取对应的天数
     * @return 天数
     */
    public int getDays() {
        return days;
    }
}

package person.notfresh.noteplus.manager;

import android.content.Context;

import java.util.Set;

import person.notfresh.noteplus.db.NoteDbHelper;
import person.notfresh.noteplus.db.ProjectContextManager;

/**
 * 笔记列表回调接口
 * 用于 NoteListManager 与 MainActivity 之间的通信
 * 提供数据访问、配置访问和事件通知功能
 */
public interface INoteListCallback {
    /**
     * 获取数据库Helper
     * @return NoteDbHelper 实例
     */
    NoteDbHelper getDbHelper();
    
    /**
     * 获取项目管理器
     * @return ProjectContextManager 实例
     */
    ProjectContextManager getProjectManager();
    
    /**
     * 获取Context
     * @return Context 实例
     */
    Context getContext();
    
    /**
     * 获取是否显示花费配置
     * @return true表示显示花费，false表示不显示
     */
    boolean getShowCost();
    
    /**
     * 获取是否显示时间区间配置
     * @return true表示显示时间区间，false表示不显示
     */
    boolean getShowTimeRange();
    
    /**
     * 获取时间排序方式配置
     * @return true表示按时间逆序，false表示按时间正序
     */
    boolean getTimeDescOrder();
    
    /**
     * 获取设置值
     * @param key 设置键
     * @param defaultValue 默认值
     * @return 设置值
     */
    String getSetting(String key, String defaultValue);
    
    /**
     * 多选模式变化通知
     * @param selectedIds 选中的笔记ID集合
     */
    void onMultiSelectChanged(Set<Long> selectedIds);
    
    /**
     * 请求刷新菜单
     * 当多选状态变化时，需要刷新菜单显示
     */
    void onRequestRefreshMenu();

    /**
     * 请求移动单条笔记到其他项目
     * @param noteId 笔记ID
     */
    void onRequestMoveToProject(long noteId);
}


package person.notfresh.noteplus.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 管理项目上下文和对应的数据库
 */
public class ProjectContextManager {
    private static volatile ProjectContextManager instance;

    private static final String PREF_NAME = "project_context_prefs";
    private static final String KEY_CURRENT_PROJECT = "current_project";
    private static final String KEY_PROJECT_LIST = "project_list";
    private static final String DEFAULT_PROJECT = "default";
    private static final String KEY_RECYCLED_PROJECTS = "recycled_projects";
    private static final String KEY_DEFAULT_PROJECT = "default_project";
    private static final String KEY_PROJECT_ORDER = "project_order";
    private static final String ORDER_SEPARATOR = ",";
    private static final String KEY_PROJECT_SWITCH_HISTORY = "project_switch_history";
    private static final int MAX_HISTORY_SIZE = 3;

    private Context appContext;
    private NoteDbHelper currentDbHelper;
    private String currentProjectName;
    private SharedPreferences preferences;
    
    // 添加缓存属性
    private Map<String, NoteDbHelper> dbHelperCache = new HashMap<>();
    
    // 项目切换历史列表（用于长按循环切换）
    private List<String> projectSwitchHistory = new ArrayList<>();
    
    public ProjectContextManager(Context context) {
        this.appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        
        // 获取默认项目设置
        String defaultProject = preferences.getString(KEY_DEFAULT_PROJECT, DEFAULT_PROJECT);
        
        // 确保默认项目在项目列表中
        addProjectToList(defaultProject);
        
        // 如果当前项目不是默认项目，且应用刚启动，则切换到默认项目
        String savedCurrentProject = preferences.getString(KEY_CURRENT_PROJECT, DEFAULT_PROJECT);
        if (savedCurrentProject.equals(defaultProject)) {
            currentProjectName = savedCurrentProject;
        } else {
            // 应用启动时，自动切换到默认项目
            currentProjectName = defaultProject;
            preferences.edit().putString(KEY_CURRENT_PROJECT, defaultProject).apply();
        }
        
        // 确保当前项目也在项目列表中
        addProjectToList(currentProjectName);
        
        initializeDbHelper();
        loadHistory();
    }

    /**
     * 获取单例实例
     */
    public static ProjectContextManager getInstance(Context context) {
        if (instance == null) {
            synchronized (ProjectContextManager.class) {
                if (instance == null) {
                    instance = new ProjectContextManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    /**
     * 初始化当前数据库Helper
     */
    private void initializeDbHelper() {
        currentDbHelper = new NoteDbHelper(appContext, getDatabaseName(currentProjectName));
    }
    
    /**
     * 获取当前数据库Helper
     */
    public NoteDbHelper getCurrentDbHelper() {
        // 从缓存中获取而不是每次创建新实例
        if (!dbHelperCache.containsKey(currentProjectName)) {
            dbHelperCache.put(currentProjectName, 
                    new NoteDbHelper(appContext, getDatabaseName(currentProjectName)));
        }
        return dbHelperCache.get(currentProjectName);
    }
    
    /**
     * 获取指定项目的数据库Helper
     */
    public NoteDbHelper getDbHelperForProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return null;
        }
        
        // 检查项目是否存在
        List<String> projects = getProjectList();
        if (!projects.contains(projectName)) {
            return null;
        }
        
        // 从缓存中获取或创建新的Helper
        if (!dbHelperCache.containsKey(projectName)) {
            dbHelperCache.put(projectName, 
                    new NoteDbHelper(appContext, getDatabaseName(projectName)));
        }
        return dbHelperCache.get(projectName);
    }
    
    /**
     * 切换到指定项目
     */
    public boolean switchToProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return false;
        }
        
        // 在切换前，将目标项目保存到切换历史
        // 只有在真正切换项目时才记录（不是首次设置，且不是切换到同一个项目）
        if (currentProjectName != null && !currentProjectName.equals(projectName)) {
            addToSwitchHistory(projectName);
        }
        
        // 不立即关闭数据库，仅切换引用
        currentProjectName = projectName;
        
        // 保存当前项目名称到SharedPreferences
        preferences.edit().putString(KEY_CURRENT_PROJECT, projectName).apply();
        
        // 添加到项目列表
        addProjectToList(projectName);
        
        // 不再需要这一步，getCurrentDbHelper会处理
        // initializeDbHelper();
        
        return true;
    }

    /**
     * 从 SharedPreferences 加载切换历史
     */
    private void loadHistory() {
        String historyStr = preferences.getString(KEY_PROJECT_SWITCH_HISTORY, "");
        projectSwitchHistory.clear();
        if (!historyStr.isEmpty()) {
            String[] parts = historyStr.split(ORDER_SEPARATOR);
            for (String p : parts) {
                if (!p.isEmpty()) {
                    projectSwitchHistory.add(p);
                }
            }
        }
    }

    /**
     * 保存切换历史到 SharedPreferences
     */
    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < projectSwitchHistory.size(); i++) {
            if (i > 0) sb.append(ORDER_SEPARATOR);
            sb.append(projectSwitchHistory.get(i));
        }
        preferences.edit().putString(KEY_PROJECT_SWITCH_HISTORY, sb.toString()).apply();
    }

    /**
     * 获取切换历史副本
     * @return 历史列表副本
     */
    public List<String> getSwitchHistory() {
        return new ArrayList<>(projectSwitchHistory);
    }

    /**
     * 添加项目到切换历史（手动切换时调用）
     * 如果项目已在历史中，先移除再加入最前面；超出3个则移除最老的
     * @param project 项目名称
     */
    public void addToSwitchHistory(String project) {
        if (project == null || project.isEmpty()) return;

        // 如果已存在，先移除
        projectSwitchHistory.remove(project);

        // 加入最前面
        projectSwitchHistory.add(0, project);

        // 超出容量则移除最老的
        while (projectSwitchHistory.size() > MAX_HISTORY_SIZE) {
            projectSwitchHistory.remove(projectSwitchHistory.size() - 1);
        }

        saveHistory();
    }

    /**
     * 获取历史中的上一个项目（用于长按切换）
     * 如果当前项目不在历史中，先加入再获取
     * @return 上一个项目名称，如果没有则返回 null
     */
    public String getPreviousProjectFromHistory() {
        String current = currentProjectName;

        // 如果当前项目不在历史中，先加入
        if (!projectSwitchHistory.contains(current)) {
            addToSwitchHistory(current);
        }

        // 找到当前项目在历史中的位置
        int currentIndex = projectSwitchHistory.indexOf(current);
        if (currentIndex == -1) {
            return null;
        }

        // 获取上一个（当前位置-1，循环）
        int previousIndex = (currentIndex - 1 + projectSwitchHistory.size()) % projectSwitchHistory.size();
        return projectSwitchHistory.get(previousIndex);
    }

    /**
     * 获取上一个项目名称
     * @return 上一个项目名称，如果没有则返回 null
     */
    public String getPreviousProject() {
        return previousProjectName;
    }
    
    /**
     * 清除上一个项目记录
     */
    public void clearPreviousProject() {
        previousProjectName = null;
    }
    
    /**
     * 获取项目列表（按保存的顺序排序）
     */
    public List<String> getProjectList() {
        Set<String> projectSet = preferences.getStringSet(KEY_PROJECT_LIST, new HashSet<>());
        List<String> projectList = new ArrayList<>(projectSet);
        
        // 确保默认项目总是存在
        if (!projectList.contains(DEFAULT_PROJECT)) {
            projectList.add(DEFAULT_PROJECT);
            saveProjectList(projectList);
        }
        
        // 应用排序
        return applyProjectOrder(projectList);
    }
    
    /**
     * 根据保存的顺序对项目列表进行排序
     * @param projectList 原始项目列表
     * @return 排序后的项目列表
     */
    private List<String> applyProjectOrder(List<String> projectList) {
        List<String> orderedList = getProjectOrder();
        
        // 如果没有保存的顺序，使用默认排序（字母顺序）
        if (orderedList.isEmpty()) {
            List<String> sorted = new ArrayList<>(projectList);
            sorted.sort(String::compareToIgnoreCase);
            return sorted;
        }
        
        // 将项目列表转换为 Set，用于快速查找
        Set<String> projectSet = new HashSet<>(projectList);
        
        // 按照保存的顺序排序
        List<String> result = new ArrayList<>();
        Set<String> addedProjects = new HashSet<>();
        
        // 先添加有序列表中的项目（严格按照保存的顺序）
        for (String projectName : orderedList) {
            if (projectSet.contains(projectName)) {
                result.add(projectName);
                addedProjects.add(projectName);
            }
            // 如果项目不存在于当前项目列表中，跳过（可能是已删除的项目）
        }
        
        // 再添加不在顺序列表中的新项目（按字母顺序）
        List<String> newProjects = new ArrayList<>();
        for (String project : projectList) {
            if (!addedProjects.contains(project)) {
                newProjects.add(project);
            }
        }
        newProjects.sort(String::compareToIgnoreCase);
        result.addAll(newProjects);
        
        return result;
    }
    
    /**
     * 获取保存的项目顺序
     * @return 项目顺序列表
     */
    private List<String> getProjectOrder() {
        String orderString = preferences.getString(KEY_PROJECT_ORDER, "");
        if (orderString == null || orderString.isEmpty()) {
            return new ArrayList<>();
        }
        
        List<String> orderList = new ArrayList<>();
        String[] items = orderString.split(ORDER_SEPARATOR);
        for (String item : items) {
            String trimmed = item.trim();
            if (!trimmed.isEmpty()) {
                orderList.add(trimmed);
            }
        }
        return orderList;
    }
    
    /**
     * 获取保存的项目顺序（用于调试）
     * @return 项目顺序列表
     */
    public List<String> getProjectOrderForDebug() {
        return getProjectOrder();
    }
    
    /**
     * 保存项目顺序
     * @param orderedProjects 有序的项目列表
     */
    private void saveProjectOrder(List<String> orderedProjects) {
        if (orderedProjects == null || orderedProjects.isEmpty()) {
            preferences.edit().remove(KEY_PROJECT_ORDER).commit();
            return;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < orderedProjects.size(); i++) {
            if (i > 0) {
                sb.append(ORDER_SEPARATOR);
            }
            sb.append(orderedProjects.get(i));
        }
        // 使用 commit() 确保立即保存，而不是异步的 apply()
        preferences.edit().putString(KEY_PROJECT_ORDER, sb.toString()).commit();
    }
    
    /**
     * 设置项目顺序
     * @param orderedProjects 有序的项目列表
     * @return 是否设置成功
     */
    public boolean setProjectOrder(List<String> orderedProjects) {
        if (orderedProjects == null || orderedProjects.isEmpty()) {
            return false;
        }
        
        // 获取所有现有项目
        Set<String> allProjectsSet = preferences.getStringSet(KEY_PROJECT_LIST, new HashSet<>());
        List<String> allProjects = new ArrayList<>(allProjectsSet);
        
        // 确保默认项目存在
        if (!allProjects.contains(DEFAULT_PROJECT)) {
            allProjects.add(DEFAULT_PROJECT);
        }
        
        // 如果传入的顺序列表不完整，补充缺失的项目到末尾（保持用户操作的顺序）
        Set<String> orderSet = new HashSet<>(orderedProjects);
        List<String> completeOrder = new ArrayList<>(orderedProjects);
        
        for (String project : allProjects) {
            if (!orderSet.contains(project)) {
                completeOrder.add(project);
            }
        }
        
        // 保存完整的顺序列表
        saveProjectOrder(completeOrder);
        
        // 验证保存是否成功
        String savedOrder = preferences.getString(KEY_PROJECT_ORDER, "");
        return !savedOrder.isEmpty();
    }
    
    /**
     * 添加项目到列表
     */
    private void addProjectToList(String projectName) {
        Set<String> projectSet = preferences.getStringSet(KEY_PROJECT_LIST, new HashSet<>());
        Set<String> newSet = new HashSet<>(projectSet);
        boolean isNew = !newSet.contains(projectName);
        newSet.add(projectName);
        preferences.edit().putStringSet(KEY_PROJECT_LIST, newSet).apply();
        
        // 如果是新项目，追加到顺序列表末尾
        if (isNew) {
            List<String> orderList = getProjectOrder();
            if (!orderList.contains(projectName)) {
                orderList.add(projectName);
                saveProjectOrder(orderList);
            }
        }
    }
    
    /**
     * 保存项目列表
     */
    private void saveProjectList(List<String> projectList) {
        Set<String> projectSet = new HashSet<>(projectList);
        preferences.edit().putStringSet(KEY_PROJECT_LIST, projectSet).apply();
    }
    
    /**
     * 创建新项目
     */
    public boolean createProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return false;
        }
        
        // 检查项目是否已存在
        List<String> projects = getProjectList();
        if (projects.contains(projectName)) {
            return false;
        }
        
        // 添加到项目列表
        addProjectToList(projectName);
        
        // 创建对应的数据库文件
        NoteDbHelper helper = new NoteDbHelper(appContext, getDatabaseName(projectName));
        SQLiteDatabase db = helper.getWritableDatabase();
        db.close();
        helper.close();
        
        return true;
    }
    
    /**
     * 删除项目
     */
    public boolean deleteProject(String projectName) {
        if (DEFAULT_PROJECT.equals(projectName)) {
            return false; // 不允许删除默认项目
        }
        
        List<String> projects = getProjectList();
        if (!projects.contains(projectName)) {
            return false;
        }
        
        // 准备删除的数据库文件
        String dbName = getDatabaseName(projectName);
        File dbFile = appContext.getDatabasePath(dbName);
        File dbJournalFile = new File(dbFile.getPath() + "-journal");
        
        try {
            // 1. 如果删除的是当前项目，先切换到默认项目
            boolean needReload = false;
            if (projectName.equals(currentProjectName)) {
                // 关闭当前数据库连接
                if (dbHelperCache.containsKey(projectName)) {
                    NoteDbHelper helper = dbHelperCache.get(projectName);
                    if (helper != null) {
                        helper.close();
                    }
                    dbHelperCache.remove(projectName);
                }
                
                // 切换到默认项目
                currentProjectName = DEFAULT_PROJECT;
                preferences.edit().putString(KEY_CURRENT_PROJECT, DEFAULT_PROJECT).apply();
                needReload = true;
            }
            
            // 2. 从缓存中移除并确保连接关闭
            if (dbHelperCache.containsKey(projectName)) {
                NoteDbHelper helper = dbHelperCache.get(projectName);
                if (helper != null) {
                    helper.close();
                }
                dbHelperCache.remove(projectName);
            }
            
            // 3. 从列表中移除
            projects.remove(projectName);
            saveProjectList(projects);
            
            // 3.3. 从顺序列表中移除
            List<String> orderList = getProjectOrder();
            orderList.remove(projectName);
            saveProjectOrder(orderList);
            
            // 3.5. 如果删除的是上一个项目，清除记录
            if (projectName.equals(previousProjectName)) {
                previousProjectName = null;
            }
            
            // 4. 使用额外的安全措施删除数据库文件
            boolean success = true;
            if (dbFile.exists()) {
                // 尝试强制删除，确保文件不被锁定
                System.gc(); // 请求垃圾回收，帮助释放资源
                Thread.sleep(100); // 短暂等待，让系统处理
                success = dbFile.delete();
            }
            
            // 删除相关的journal文件
            if (dbJournalFile.exists()) {
                dbJournalFile.delete();
            }
            
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取当前项目名称
     */
    public String getCurrentProject() {
        return currentProjectName;
    }
    
    /**
     * 生成项目对应的数据库名称
     */
    private String getDatabaseName(String projectName) {
        return "notes_" + projectName.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase() + ".db";
    }
    
    /**
     * 重命名项目
     */
    public boolean renameProject(String oldName, String newName) {
        if (oldName == null || newName == null || oldName.isEmpty() || newName.isEmpty()) {
            return false;
        }
        
        List<String> projects = getProjectList();
        if (!projects.contains(oldName) || projects.contains(newName)) {
            return false;
        }
        
        // 更新项目列表
        projects.remove(oldName);
        projects.add(newName);
        saveProjectList(projects);
        
        // 如果当前项目被重命名，更新currentProjectName
        if (oldName.equals(currentProjectName)) {
            currentProjectName = newName;
            preferences.edit().putString(KEY_CURRENT_PROJECT, newName).apply();
        }
        
        // 更新顺序列表中的项目名称
        List<String> orderList = getProjectOrder();
        int index = orderList.indexOf(oldName);
        if (index >= 0) {
            orderList.set(index, newName);
            saveProjectOrder(orderList);
        }
        
        // 重命名数据库文件
        File oldDbFile = appContext.getDatabasePath(getDatabaseName(oldName));
        File newDbFile = appContext.getDatabasePath(getDatabaseName(newName));
        
        if (oldDbFile.exists()) {
            return oldDbFile.renameTo(newDbFile);
        }
        
        return false;
    }
    
    /**
     * 清理所有数据库连接缓存
     */
    public void closeAll() {
        for (NoteDbHelper helper : dbHelperCache.values()) {
            if (helper != null) {
                helper.close();
            }
        }
        dbHelperCache.clear();
    }
    
    /**
     * 将项目移至回收站
     */
    public boolean moveProjectToRecycleBin(String projectName) {
        if (DEFAULT_PROJECT.equals(projectName)) {
            return false; // 不允许删除默认项目
        }
        
        List<String> projects = getProjectList();
        if (!projects.contains(projectName)) {
            return false;
        }
        
        try {
            // 1. 如果被移除的是当前项目，先切换到默认项目
            if (projectName.equals(currentProjectName)) {
                // 关闭当前数据库连接
                if (dbHelperCache.containsKey(projectName)) {
                    NoteDbHelper helper = dbHelperCache.get(projectName);
                    if (helper != null) {
                        helper.close();
                    }
                    dbHelperCache.remove(projectName);
                }
                
                // 切换到默认项目
                currentProjectName = DEFAULT_PROJECT;
                preferences.edit().putString(KEY_CURRENT_PROJECT, DEFAULT_PROJECT).apply();
            }
            
            // 2. 从缓存中移除并确保连接关闭
            if (dbHelperCache.containsKey(projectName)) {
                NoteDbHelper helper = dbHelperCache.get(projectName);
                if (helper != null) {
                    helper.close();
                }
                dbHelperCache.remove(projectName);
            }
            
            // 3. 从项目列表中移除
            projects.remove(projectName);
            saveProjectList(projects);
            
            // 3.3. 从顺序列表中移除
            List<String> orderList = getProjectOrder();
            orderList.remove(projectName);
            saveProjectOrder(orderList);
            
            // 3.5. 如果删除的是上一个项目，清除记录
            if (projectName.equals(previousProjectName)) {
                previousProjectName = null;
            }
            
            // 4. 添加到回收站列表
            addProjectToRecycleBin(projectName);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 从回收站恢复项目
     */
    public boolean restoreProjectFromRecycleBin(String projectName) {
        List<String> recycledProjects = getRecycledProjects();
        if (!recycledProjects.contains(projectName)) {
            return false;
        }
        
        // 1. 从回收站中移除
        recycledProjects.remove(projectName);
        saveRecycledProjects(recycledProjects);
        
        // 2. 添加回项目列表
        addProjectToList(projectName);
        
        // 3. 确保项目在顺序列表中（如果不在，追加到末尾）
        List<String> orderList = getProjectOrder();
        if (!orderList.contains(projectName)) {
            orderList.add(projectName);
            saveProjectOrder(orderList);
        }
        
        return true;
    }
    
    /**
     * 永久删除回收站中的项目
     */
    public boolean permanentlyDeleteProject(String projectName) {
        List<String> recycledProjects = getRecycledProjects();
        if (!recycledProjects.contains(projectName)) {
            return false;
        }
        
        // 准备删除的数据库文件
        String dbName = getDatabaseName(projectName);
        File dbFile = appContext.getDatabasePath(dbName);
        File dbJournalFile = new File(dbFile.getPath() + "-journal");
        
        try {
            // 1. 从回收站列表中移除
            recycledProjects.remove(projectName);
            saveRecycledProjects(recycledProjects);
            
            // 2. 使用安全措施删除数据库文件
            boolean success = true;
            if (dbFile.exists()) {
                // 尝试强制删除，确保文件不被锁定
                System.gc();
                Thread.sleep(100);
                success = dbFile.delete();
            }
            
            // 删除相关的journal文件
            if (dbJournalFile.exists()) {
                dbJournalFile.delete();
            }
            
            return success;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * 获取回收站中的项目列表
     */
    public List<String> getRecycledProjects() {
        Set<String> projectSet = preferences.getStringSet(KEY_RECYCLED_PROJECTS, new HashSet<>());
        return new ArrayList<>(projectSet);
    }
    
    /**
     * 添加项目到回收站
     */
    private void addProjectToRecycleBin(String projectName) {
        Set<String> projectSet = preferences.getStringSet(KEY_RECYCLED_PROJECTS, new HashSet<>());
        Set<String> newSet = new HashSet<>(projectSet);
        newSet.add(projectName);
        preferences.edit().putStringSet(KEY_RECYCLED_PROJECTS, newSet).apply();
    }
    
    /**
     * 保存回收站项目列表
     */
    private void saveRecycledProjects(List<String> projectList) {
        Set<String> projectSet = new HashSet<>(projectList);
        preferences.edit().putStringSet(KEY_RECYCLED_PROJECTS, projectSet).apply();
    }
    
    /**
     * 清空回收站
     */
    public boolean emptyRecycleBin() {
        List<String> recycledProjects = getRecycledProjects();
        boolean success = true;
        
        for (String projectName : recycledProjects) {
            if (!permanentlyDeleteProject(projectName)) {
                success = false;
            }
        }
        
        return success;
    }

    /**
     * 设置默认项目
     */
    public boolean setDefaultProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return false;
        }
        
        List<String> projects = getProjectList();
        if (!projects.contains(projectName)) {
            return false;
        }
        
        // 保存默认项目设置
        preferences.edit().putString(KEY_DEFAULT_PROJECT, projectName).apply();
        
        return true;
    }

    /**
     * 获取默认项目
     */
    public String getDefaultProject() {
        return preferences.getString(KEY_DEFAULT_PROJECT, DEFAULT_PROJECT);
    }

    /**
     * 检查指定项目是否为默认项目
     */
    public boolean isDefaultProject(String projectName) {
        return projectName != null && projectName.equals(getDefaultProject());
    }
} 
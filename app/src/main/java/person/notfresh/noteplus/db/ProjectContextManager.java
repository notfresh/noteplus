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
    private static final String PREF_NAME = "project_context_prefs";
    private static final String KEY_CURRENT_PROJECT = "current_project";
    private static final String KEY_PROJECT_LIST = "project_list";
    private static final String DEFAULT_PROJECT = "default";
    private static final String KEY_RECYCLED_PROJECTS = "recycled_projects";
    
    private Context appContext;
    private NoteDbHelper currentDbHelper;
    private String currentProjectName;
    private SharedPreferences preferences;
    
    // 添加缓存属性
    private Map<String, NoteDbHelper> dbHelperCache = new HashMap<>();
    
    public ProjectContextManager(Context context) {
        this.appContext = context.getApplicationContext();
        preferences = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        currentProjectName = preferences.getString(KEY_CURRENT_PROJECT, DEFAULT_PROJECT);
        initializeDbHelper();
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
     * 切换到指定项目
     */
    public boolean switchToProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return false;
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
     * 获取项目列表
     */
    public List<String> getProjectList() {
        Set<String> projectSet = preferences.getStringSet(KEY_PROJECT_LIST, new HashSet<>());
        List<String> projectList = new ArrayList<>(projectSet);
        
        // 确保默认项目总是存在
        if (!projectList.contains(DEFAULT_PROJECT)) {
            projectList.add(DEFAULT_PROJECT);
            saveProjectList(projectList);
        }
        
        return projectList;
    }
    
    /**
     * 添加项目到列表
     */
    private void addProjectToList(String projectName) {
        Set<String> projectSet = preferences.getStringSet(KEY_PROJECT_LIST, new HashSet<>());
        Set<String> newSet = new HashSet<>(projectSet);
        newSet.add(projectName);
        preferences.edit().putStringSet(KEY_PROJECT_LIST, newSet).apply();
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
} 
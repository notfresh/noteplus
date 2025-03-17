package person.notfresh.noteplus.db;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.sqlite.SQLiteDatabase;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 管理项目上下文和对应的数据库
 */
public class ProjectContextManager {
    private static final String PREF_NAME = "project_context_prefs";
    private static final String KEY_CURRENT_PROJECT = "current_project";
    private static final String KEY_PROJECT_LIST = "project_list";
    private static final String DEFAULT_PROJECT = "default";
    
    private Context appContext;
    private NoteDbHelper currentDbHelper;
    private String currentProjectName;
    private SharedPreferences preferences;
    
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
        return currentDbHelper;
    }
    
    /**
     * 切换到指定项目
     */
    public boolean switchToProject(String projectName) {
        if (projectName == null || projectName.isEmpty()) {
            return false;
        }
        
        // 关闭当前数据库
        if (currentDbHelper != null) {
            currentDbHelper.close();
        }
        
        currentProjectName = projectName;
        
        // 保存当前项目名称到SharedPreferences
        preferences.edit().putString(KEY_CURRENT_PROJECT, projectName).apply();
        
        // 添加到项目列表
        addProjectToList(projectName);
        
        // 创建新的数据库Helper
        initializeDbHelper();
        
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
        
        // 如果删除的是当前项目，先切换到默认项目
        if (projectName.equals(currentProjectName)) {
            switchToProject(DEFAULT_PROJECT);
        }
        
        // 从列表中移除
        projects.remove(projectName);
        saveProjectList(projects);
        
        // 删除数据库文件
        File dbFile = appContext.getDatabasePath(getDatabaseName(projectName));
        if (dbFile.exists()) {
            return dbFile.delete();
        }
        
        return true;
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
} 
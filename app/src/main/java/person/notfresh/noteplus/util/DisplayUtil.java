package person.notfresh.noteplus.util;

import android.content.Context;
import android.content.res.Resources;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * 显示相关的工具类
 * 包含时间格式化、单位转换等UI显示相关的工具方法
 */
public class DisplayUtil {
    
    /**
     * 格式化时间戳为指定格式
     * 格式：yyyy年MM月dd日，星期X，HH:mm
     * 例如：2024年01月15日，星期一，14:30
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的时间字符串
     */
    public static String formatTimestamp(long timestamp) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timestamp);
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy年MM月dd日", Locale.CHINA);
        String dateStr = dateFormat.format(new Date(timestamp));
        
        // 获取星期
        String[] weekDays = {"日", "一", "二", "三", "四", "五", "六"};
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1;
        if (dayOfWeek < 0) dayOfWeek = 0;
        
        SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm", Locale.CHINA);
        String timeStr = timeFormat.format(new Date(timestamp));
        
        return dateStr + "，星期" + weekDays[dayOfWeek] + "，" + timeStr;
    }
    
    /**
     * 格式化评论时间戳（简洁格式）
     * 格式：yy/MM/dd HH:mm
     * 例如：25/11/30 15:20
     * 
     * @param timestamp 时间戳（毫秒）
     * @return 格式化后的时间字符串
     */
    public static String formatCommentTimestamp(long timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("yy/MM/dd HH:mm", Locale.CHINA);
        return sdf.format(new Date(timestamp));
    }
    
    /**
     * 将dp值转换为像素值
     * 
     * @param context Context对象，用于获取屏幕密度
     * @param dp dp值
     * @return 对应的像素值
     */
    public static int dpToPx(Context context, int dp) {
        if (context == null) {
            return dp; // 如果context为null，返回原值（降级处理）
        }
        float density = context.getResources().getDisplayMetrics().density;
        return Math.round(dp * density);
    }
    
    /**
     * 将dp值转换为像素值（使用Resources）
     * 
     * @param resources Resources对象，用于获取屏幕密度
     * @param dp dp值
     * @return 对应的像素值
     */
    public static int dpToPx(Resources resources, int dp) {
        if (resources == null) {
            return dp; // 如果resources为null，返回原值（降级处理）
        }
        float density = resources.getDisplayMetrics().density;
        return Math.round(dp * density);
    }
}


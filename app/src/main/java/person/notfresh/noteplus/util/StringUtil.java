package person.notfresh.noteplus.util;

/**
 * 字符串工具类
 */
public class StringUtil {
    
    /**
     * 计算文本的字数（中英文混合统计）
     * - 中文：每个字符算1字
     * - 英文：按单词统计（空格分隔），每个单词算1字
     * - 包含换行符和空格
     * 
     * @param text 要统计的文本
     * @return 字数
     */
    public static int calculateWordCount(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        
        int count = 0;
        int length = text.length();
        boolean inEnglishWord = false;
        
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            
            // 判断是否为中文字符（包括中文标点）
            if (isChinese(c)) {
                count++;
                inEnglishWord = false;
            } 
            // 判断是否为空格或换行符
            else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                // 如果之前在一个英文单词中，空格表示单词结束，需要计数
                if (inEnglishWord) {
                    count++;
                    inEnglishWord = false;
                }
                // 空格和换行符本身也算1字
                count++;
            }
            // 判断是否为英文字母或数字
            else if (isEnglishChar(c)) {
                // 如果不在单词中，开始一个新单词
                if (!inEnglishWord) {
                    inEnglishWord = true;
                }
            }
            // 其他字符（如英文标点等）
            else {
                // 如果之前在英文单词中，标点表示单词结束
                if (inEnglishWord) {
                    count++;
                    inEnglishWord = false;
                }
                // 其他字符也算1字
                count++;
            }
        }
        
        // 如果文本以英文单词结尾，需要加上最后一个单词
        if (inEnglishWord) {
            count++;
        }
        
        return count;
    }
    
    /**
     * 判断字符是否为中文字符
     */
    private static boolean isChinese(char c) {
        // 中文字符的Unicode范围（常用范围）
        return (c >= 0x4E00 && c <= 0x9FFF) ||  // CJK统一汉字（最常用）
               (c >= 0x3400 && c <= 0x4DBF) ||  // CJK扩展A
               (c >= 0xF900 && c <= 0xFAFF) ||   // CJK兼容汉字
               (c >= 0x3000 && c <= 0x303F) ||   // CJK符号和标点
               (c >= 0xFF00 && c <= 0xFFEF);     // 全角字符
    }
    
    /**
     * 判断字符是否为英文字母或数字
     */
    private static boolean isEnglishChar(char c) {
        return (c >= 'a' && c <= 'z') || 
               (c >= 'A' && c <= 'Z') || 
               (c >= '0' && c <= '9');
    }
    
    /**
     * 截断文本到指定字数（不截断英文单词）
     * @param text 原始文本
     * @param maxWordCount 最大字数
     * @return 截断后的文本
     */
    public static String truncateToWordCount(String text, int maxWordCount) {
        if (text == null || text.isEmpty() || maxWordCount <= 0) {
            return text;
        }
        
        int currentCount = 0;
        int length = text.length();
        boolean inEnglishWord = false;
        int lastWordBoundary = 0; // 最后一个单词边界位置
        
        for (int i = 0; i < length; i++) {
            char c = text.charAt(i);
            
            if (isChinese(c)) {
                currentCount++;
                if (currentCount >= maxWordCount) {
                    return text.substring(0, i + 1);
                }
                inEnglishWord = false;
                lastWordBoundary = i + 1;
            } 
            else if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                if (inEnglishWord) {
                    currentCount++;
                    if (currentCount >= maxWordCount) {
                        return text.substring(0, lastWordBoundary);
                    }
                    inEnglishWord = false;
                }
                currentCount++;
                if (currentCount >= maxWordCount) {
                    return text.substring(0, i + 1);
                }
                lastWordBoundary = i + 1;
            }
            else if (isEnglishChar(c)) {
                if (!inEnglishWord) {
                    inEnglishWord = true;
                    lastWordBoundary = i; // 单词开始位置
                }
            }
            else {
                if (inEnglishWord) {
                    currentCount++;
                    if (currentCount >= maxWordCount) {
                        return text.substring(0, lastWordBoundary);
                    }
                    inEnglishWord = false;
                }
                currentCount++;
                if (currentCount >= maxWordCount) {
                    return text.substring(0, i + 1);
                }
                lastWordBoundary = i + 1;
            }
        }
        
        // 如果文本以英文单词结尾
        if (inEnglishWord) {
            currentCount++;
            if (currentCount >= maxWordCount) {
                return text.substring(0, lastWordBoundary);
            }
        }
        
        // 如果文本没有超过字数限制，返回完整文本
        return text;
    }
}


package person.notfresh.noteplus.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;

import java.io.InputStream;

/**
 * 图片处理工具类
 * 提供图片选择、转换、缩放等通用功能
 */
public class ImageUtil {

    /**
     * 创建相册选择 Intent（多选）
     * @return 支持多选的相册选择 Intent
     */
    public static Intent createGalleryPickerIntentMultiple() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        return intent;
    }

    /**
     * 从 URI 转换为 Bitmap
     * 支持 content:// 和 file:// 协议
     * @param context 上下文
     * @param uri 图片 URI
     * @return Bitmap 对象，如果转换失败返回 null
     */
    public static Bitmap uriToBitmap(Context context, Uri uri) {
        if (context == null || uri == null) {
            Log.w("ImageUtil", "Context or URI is null");
            return null;
        }

        try {
            InputStream inputStream = context.getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Log.w("ImageUtil", "Failed to open input stream for URI: " + uri);
                return null;
            }

            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            return bitmap;
        } catch (Exception e) {
            Log.e("ImageUtil", "Error converting URI to Bitmap: " + e.getMessage(), e);
            return null;
        }
    }

    /**
     * 缩放图片，保持宽高比
     * @param image 原始图片
     * @param maxSize 最大尺寸（宽度或高度）
     * @return 缩放后的图片
     */
    public static Bitmap resizeBitmap(Bitmap image, int maxSize) {
        if (image == null) {
            Log.w("ImageUtil", "Image is null");
            return null;
        }

        int width = image.getWidth();
        int height = image.getHeight();

        if (width <= maxSize && height <= maxSize) {
            return image;
        }

        float bitmapRatio = (float) width / (float) height;
        int newWidth;
        int newHeight;

        if (bitmapRatio > 1) {
            newWidth = maxSize;
            newHeight = (int) (newWidth / bitmapRatio);
        } else {
            newHeight = maxSize;
            newWidth = (int) (newHeight * bitmapRatio);
        }

        return Bitmap.createScaledBitmap(image, newWidth, newHeight, true);
    }
}

package person.notfresh.noteplus.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import android.content.ContentValues;
import android.content.ContentResolver;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import person.notfresh.noteplus.R;
import com.github.chrisbanes.photoview.PhotoView;
import com.github.chrisbanes.photoview.OnPhotoTapListener;

public class ImagePreviewDialog extends DialogFragment {
    private static final String ARG_IMAGE_PATHS = "image_paths";
    private static final String ARG_CURRENT_INDEX = "current_index";

    private List<String> imagePaths = new ArrayList<>();
    private int currentIndex = 0;

    public static ImagePreviewDialog newInstance(List<String> imagePaths, int currentIndex) {
        ImagePreviewDialog dialog = new ImagePreviewDialog();
        Bundle args = new Bundle();
        args.putStringArrayList(ARG_IMAGE_PATHS, new ArrayList<>(imagePaths));
        args.putInt(ARG_CURRENT_INDEX, currentIndex);
        dialog.setArguments(args);
        return dialog;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            imagePaths = getArguments().getStringArrayList(ARG_IMAGE_PATHS);
            currentIndex = getArguments().getInt(ARG_CURRENT_INDEX, 0);
        }
        setStyle(DialogFragment.STYLE_NO_TITLE, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_image_preview, container, false);

        ViewPager viewPager = view.findViewById(R.id.view_pager);
        TextView textImageIndex = view.findViewById(R.id.text_image_index);
        ImageButton closeButton = view.findViewById(R.id.btn_close_preview);
        ImageButton saveButton = view.findViewById(R.id.btn_save_preview);

        ImagePagerAdapter adapter = new ImagePagerAdapter(imagePaths);
        viewPager.setAdapter(adapter);
        viewPager.setCurrentItem(currentIndex, false);

        if (imagePaths != null && imagePaths.size() > 1) {
            textImageIndex.setVisibility(View.VISIBLE);
            updateImageIndex(textImageIndex);
            viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    currentIndex = position;
                    updateImageIndex(textImageIndex);
                }
            });
        }

        closeButton.setOnClickListener(v -> dismiss());
        saveButton.setOnClickListener(v -> saveCurrentImage());
        return view;
    }

    private void updateImageIndex(TextView textImageIndex) {
        if (imagePaths == null) {
            return;
        }
        textImageIndex.setText((currentIndex + 1) + " / " + imagePaths.size());
    }

    private class ImagePagerAdapter extends PagerAdapter {
        private final List<String> paths;

        ImagePagerAdapter(List<String> imagePaths) {
            this.paths = imagePaths != null ? new ArrayList<>(imagePaths) : new ArrayList<>();
        }

        @Override
        public int getCount() {
            return paths.size();
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == object;
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup container, int position) {
                PhotoView imageView = new PhotoView(container.getContext());
                imageView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));

            String imagePath = paths.get(position);
            bindImage(imageView, imagePath);

                imageView.setOnPhotoTapListener((view, x, y) -> dismiss());

            container.addView(imageView);
            return imageView;
        }

        @Override
        public void destroyItem(@NonNull ViewGroup container, int position, @NonNull Object object) {
            container.removeView((View) object);
        }

        private void bindImage(ImageView imageView, String imagePath) {
            if (imagePath == null) {
                return;
            }
            File imageFile = new File(imagePath);
            if (!imageFile.exists()) {
                return;
            }

            int screenWidth = imageView.getContext().getResources().getDisplayMetrics().widthPixels;
            int screenHeight = imageView.getContext().getResources().getDisplayMetrics().heightPixels;

            Bitmap bitmap = decodeSampledBitmap(imagePath, screenWidth, screenHeight);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
            }
        }
    }

    private void saveCurrentImage() {
        if (imagePaths == null || imagePaths.isEmpty() || currentIndex < 0 || currentIndex >= imagePaths.size()) {
            return;
        }

        String imagePath = imagePaths.get(currentIndex);
        File sourceFile = new File(imagePath);
        if (!sourceFile.exists()) {
            Toast.makeText(requireContext(), "文件不存在", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            ContentResolver resolver = requireContext().getContentResolver();
            String fileName = "noteplus_" + System.currentTimeMillis() + ".jpg";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/NotePlus");

            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) {
                Toast.makeText(requireContext(), "保存失败", Toast.LENGTH_SHORT).show();
                return;
            }

            try (OutputStream outputStream = resolver.openOutputStream(uri);
                 FileInputStream inputStream = new FileInputStream(sourceFile)) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, len);
                }
            }

            Toast.makeText(requireContext(), "已保存到相册/NotePlus", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(), "保存失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap decodeSampledBitmap(String path, int reqWidth, int reqHeight) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            int halfHeight = height / 2;
            int halfWidth = width / 2;

            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }

        options.inJustDecodeBounds = false;
        options.inSampleSize = inSampleSize;
        return BitmapFactory.decodeFile(path, options);
    }
}

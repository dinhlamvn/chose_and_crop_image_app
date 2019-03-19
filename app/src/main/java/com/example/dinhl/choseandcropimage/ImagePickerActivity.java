package com.example.dinhl.choseandcropimage;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.support.annotation.Nullable;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.karumi.dexter.Dexter;
import com.karumi.dexter.MultiplePermissionsReport;
import com.karumi.dexter.PermissionToken;
import com.karumi.dexter.listener.PermissionRequest;
import com.karumi.dexter.listener.multi.MultiplePermissionsListener;
import com.yalantis.ucrop.UCrop;

import java.io.File;
import java.util.List;

public class ImagePickerActivity extends AppCompatActivity {

    /* Các key truyền khi gọi activity */
    // Key phương thức chọn ảnh là từ camera hay gallery
    public static final String EXTRA_IMAGE_PICKER_OPTION = "image_pciker_option";
    // Key tỉ lệ ảnh khung cắt
    public static final String EXTRA_ASPECT_RATIO_X = "aspect_ratio_x"; // Tỉ lệ rộng
    public static final String EXTRA_ASPECT_RATIO_Y = "aspect_ratio_y"; // Tỉ lệ cao
    // Key có khóa tỉ lệ hay không, nếu true tức là chỉ cắt ảnh theo tỉ lệ cho trước, ngược lại có thể cắt theo các tỉ lệ khác
    public static final String EXTRA_LOCK_ASPECT_RATIO = "lock_aspect_ratio";
    // Key chất lượng ảnh
    public static final String EXTRA_IMAGE_COMPRESSION_QUALITY = "compression_quality";
    // Key có giới hạn kích thước ảnh hay không, nếu true sẽ giới hạn theo chiều rộng và cao truyền vào, ngược lại thì không
    public static final String EXTRA_SET_BITMAP_MAX_WIDTH_HEIGHT = "set_bitmap_max_width_height";
    // Key chiều rộng tối đa
    public static final String EXTRA_BITMAP_MAX_WIDTH = "max_width";
    // Key chiều cao tối đa
    public static final String EXTRA_BITMAP_MAX_HEIGHT = "max_height";

    /* Định nghĩa các biến default khi không được truyền vào lúc gọi activity */
    private int ASPECT_RATIO_X = 16, ASPECT_RATIO_Y = 9, bitmapMaxWidth = 1000, bitmapMaxHeight = 1000;
    private boolean lockAspectRatio = false, setBitmapMaxWidthHeight = false;
    private int IMAGE_COMPRESSION = 80;

    public static final String REQUEST_CODE_TYPE = "request_code";

    /* Định nghĩa 2 loại request */
    public static final int REQUEST_IMAGE_CAPTURE = 0;
    public static final int REQUEST_IMAGE_GALLERY = 1;

    String fileName;

    public interface PickerOptionListener {

        void onCameraSelected();

        void onGallerySelected();
    }


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = getIntent();

        if (intent == null) {
            Toast.makeText(this, "No intent", Toast.LENGTH_SHORT).show();
            return;
        }

        ASPECT_RATIO_X = intent.getIntExtra(EXTRA_ASPECT_RATIO_X, ASPECT_RATIO_X);
        ASPECT_RATIO_Y = intent.getIntExtra(EXTRA_ASPECT_RATIO_Y, ASPECT_RATIO_Y);
        IMAGE_COMPRESSION = intent.getIntExtra(EXTRA_IMAGE_COMPRESSION_QUALITY, IMAGE_COMPRESSION);
        lockAspectRatio = intent.getBooleanExtra(EXTRA_LOCK_ASPECT_RATIO, lockAspectRatio);
        setBitmapMaxWidthHeight = intent.getBooleanExtra(EXTRA_SET_BITMAP_MAX_WIDTH_HEIGHT, setBitmapMaxWidthHeight);
        bitmapMaxWidth = intent.getIntExtra(EXTRA_BITMAP_MAX_WIDTH, bitmapMaxWidth);
        bitmapMaxHeight = intent.getIntExtra(EXTRA_BITMAP_MAX_HEIGHT, bitmapMaxHeight);

        int request = intent.getIntExtra(REQUEST_CODE_TYPE, REQUEST_IMAGE_GALLERY);

        if (request == REQUEST_IMAGE_CAPTURE) {
            takeCameraImage();
        } else {
            takeGalleryImage();
        }
    }

    public static void showImagePickerOptions(Context context, final PickerOptionListener listener) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.lbl_set_profile_photo));

        String[] items = {context.getString(R.string.lbl_take_camera_picture), context.getString(R.string.lbl_choose_from_gallery)};

        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case 0: {
                        listener.onCameraSelected();
                        break;
                    }
                    case 1: {
                        listener.onGallerySelected();
                        break;
                    }
                }
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case REQUEST_IMAGE_CAPTURE: {
                if (resultCode == RESULT_OK) {
                    cropImage(getCacheImagePath(fileName));
                } else {
                    setResultCancelled();
                }
                break;
            }
            case REQUEST_IMAGE_GALLERY: {
                if (resultCode == RESULT_OK) {
                    Uri imageUri = data.getData();
                    cropImage(imageUri);
                } else {
                    setResultCancelled();
                }
                break;
            }
            case UCrop.REQUEST_CROP: {
                if (resultCode == RESULT_OK) {
                    handleUCropResult(data);
                } else {
                    setResultCancelled();
                }
                break;
            }
            case UCrop.RESULT_ERROR: {
                final Throwable error = UCrop.getError(data);
                Log.e("ImagePicker", "Crop error " + error);
                setResultCancelled();
                break;
            }
            default:
                setResultCancelled();
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void handleUCropResult(Intent data) {
        if (data == null) {
            setResultCancelled();
            return;
        }
        final Uri resultUri = UCrop.getOutput(data);
        setResultOk(resultUri);
    }

    private void setResultOk(Uri imagePath) {
        Intent intent = new Intent();
        intent.putExtra("path", imagePath);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private void setResultCancelled() {
        Intent intent = new Intent();
        setResult(Activity.RESULT_CANCELED, intent);
        finish();
    }

    public void takeCameraImage() {
        Dexter.withActivity(this).withPermissions(Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            fileName = System.currentTimeMillis() + ".jpg";
                            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, getCacheImagePath(fileName));
                            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    public void takeGalleryImage() {
        Dexter.withActivity(this).withPermissions(Manifest.permission.CAMERA,
                Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(new MultiplePermissionsListener() {
                    @Override
                    public void onPermissionsChecked(MultiplePermissionsReport report) {
                        if (report.areAllPermissionsGranted()) {
                            Intent pickIntent = new Intent(Intent.ACTION_PICK,
                                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
                            startActivityForResult(pickIntent, REQUEST_IMAGE_GALLERY);
                        }
                    }

                    @Override
                    public void onPermissionRationaleShouldBeShown(List<PermissionRequest> permissions, PermissionToken token) {
                        token.continuePermissionRequest();
                    }
                }).check();
    }

    public Uri getCacheImagePath(String fileName) {
        File path = new File(getExternalCacheDir(), "camera");

        if (!path.exists()) {
            path.mkdirs();
        }

        File image = new File(path, fileName);

        return FileProvider.getUriForFile(ImagePickerActivity.this, getPackageName() + ".provider", image);
    }

    public void cropImage(Uri sourceUri) {
        Uri destinationUri = Uri.fromFile(new File(getCacheDir(), queryName(getContentResolver(), sourceUri)));
        UCrop.Options options = new UCrop.Options();
        options.setCompressionQuality(80);
        options.setToolbarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        options.setStatusBarColor(ContextCompat.getColor(this, R.color.colorPrimary));
        options.setActiveWidgetColor(ContextCompat.getColor(this, R.color.colorPrimary));

        if (lockAspectRatio) {
            options.withAspectRatio(ASPECT_RATIO_X, ASPECT_RATIO_Y);
        }

        if (setBitmapMaxWidthHeight) {
            options.withMaxResultSize(bitmapMaxWidth, bitmapMaxHeight);
        }

        UCrop.of(sourceUri, destinationUri)
                .withOptions(options)
                .start(this);
    }

    public String queryName(ContentResolver resolver, Uri uri) {
        Cursor cursor = resolver.query(uri, null, null, null, null);

        assert cursor != null;
        int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);

        cursor.moveToFirst();

        String name = cursor.getString(nameIndex);

        cursor.close();

        return name;
    }



}

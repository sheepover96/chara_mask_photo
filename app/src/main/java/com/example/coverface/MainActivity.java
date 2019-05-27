package com.example.coverface;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.objdetect.CascadeClassifier;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;

import static android.os.Environment.getExternalStoragePublicDirectory;


public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String FILE_NAME = "haarcascade_frontalface_alt.xml";
    static final int REQUEST_TAKE_PHOTO = 1;
    private static final int READ_REQUEST_CODE = 2;
    private static final int FACE_MASK_CODE = 3;
    String currentPhotoPath;
    File photoFile;
    File maskPhotoFile;
    Mat coverPhoto;
    Bitmap coverPhotoBitmap;
    Uri coverPhotoUri;
    Mat takenPhoto;
    Bitmap takenPhotoBitmap;
    Uri takenPhotoUri;
    Bitmap resultBitmap;
    File resultImageFile;

    CascadeClassifier cascadeClassifier;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (OpenCVLoader.initDebug()) {
            this.mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, getApplicationContext(),
                    mLoaderCallback);
        }
    }

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                    coverPhoto = new Mat();
                    takenPhoto = new Mat();

                    File file = new File(getFilesDir().getPath() + File.separator + FILE_NAME);
                    if (!file.exists()) {
                        try (InputStream inputStream = getAssets().open(FILE_NAME);
                            FileOutputStream fileOutputStream = new FileOutputStream(file, false)) {
                            byte[] buffer = new byte[1024];
                            int read;
                            while ((read = inputStream.read(buffer)) != -1) {
                                fileOutputStream.write(buffer, 0, read);
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    cascadeClassifier = new CascadeClassifier(file.getAbsolutePath());

                    performFileSearch();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;

            }
        }
    };

    @Override
    public void onClick(View view) {
        if (view.getId() == R.id.save_button) {
            this.galleryAddPic(resultImageFile.getPath());
            resultImageFile = null;
            photoFile = null;
            this.performFileSearch();
        } else if (view.getId() == R.id.delete_button) {
            resultImageFile.delete();
            photoFile.delete();
            resultImageFile = null;
            photoFile = null;
            this.performFileSearch();
        }
    }

    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            this.photoFile = null;
            try {
                this.photoFile = createImageFile();
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.example.coverface.fileprovider",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case REQUEST_TAKE_PHOTO:
                    Intent faceMaskIntent = new Intent(getApplicationContext(), ProcessingActivity.class);
                    faceMaskIntent.putExtra("coverPhotoUri", this.coverPhotoUri);
                    faceMaskIntent.putExtra("takenPhotoFile", this.photoFile);
                    startActivityForResult(faceMaskIntent, FACE_MASK_CODE);
                    break;
                case READ_REQUEST_CODE:
                    this.coverPhotoUri = null;
                    if (data != null) {
                        this.coverPhotoUri = data.getData();
                        try {
                            coverPhotoBitmap = this.getBitmapFromUri(coverPhotoUri);
                            this.coverPhoto = new Mat();
                            Utils.bitmapToMat(coverPhotoBitmap, this.coverPhoto);
                            this.dispatchTakePictureIntent();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                case FACE_MASK_CODE:
                    if (data != null) {
                        ImageView imageView = (ImageView) findViewById(R.id.taken_image);
                        resultImageFile = (File) data.getExtras().get("faceMaskedFile");
                        resultBitmap = BitmapFactory.decodeFile(resultImageFile.getPath());
                        imageView.setImageBitmap(resultBitmap);
                    }
                default:
                    break;
            }
        }
    }

    private void galleryAddPic(String imageFilePath) {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(imageFilePath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    public void performFileSearch() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        startActivityForResult(intent, READ_REQUEST_CODE);
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

}

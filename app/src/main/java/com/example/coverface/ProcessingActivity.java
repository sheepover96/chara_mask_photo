package com.example.coverface;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
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

public class ProcessingActivity extends AppCompatActivity {

    private static final String FILE_NAME = "haarcascade_frontalface_alt.xml";

    Mat takenPhoto;
    Bitmap takenPhotoBitmap;
    Mat coverPhoto;
    Bitmap coverPhotoBitmap;
    Bitmap faceMaskedBitmap;
    CascadeClassifier cascadeClassifier;
    Handler guiThreadHandler;

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
                    guiThreadHandler = new Handler();
                    startMaskingFace();
                    break;
                default:
                    super.onManagerConnected(status);
                    break;

            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_processing);
        if (OpenCVLoader.initDebug()) {
            this.mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, getApplicationContext(),
                    mLoaderCallback);
        }

    }

    private void startMaskingFace() {
        final Intent intent = getIntent();

        try {
            Uri coverPhotoUri = (Uri) intent.getExtras().get("coverPhotoUri");
            File takenPhotoFile = (File) intent.getExtras().get("takenPhotoFile");
            coverPhotoBitmap = this.getBitmapFromUri(coverPhotoUri);
            takenPhotoBitmap = BitmapFactory.decodeFile(takenPhotoFile.getPath());
            MaskFaceAsync task = new MaskFaceAsync(this, coverPhotoBitmap, takenPhotoBitmap, cascadeClassifier);
            task.setOnCallBack(new MaskFaceAsync.CallBackTask() {
                @Override
                public void CallBack(Bitmap resultBitmap) {
                    faceMaskedBitmap = resultBitmap;
                    try {
                        File saveFile = createImageFile();
                        saveBitmapImage(saveFile.getAbsolutePath(), faceMaskedBitmap);
                        Intent resultIntent = new Intent();
                        resultIntent.putExtra("faceMaskedFile", saveFile);
                        setResult(RESULT_OK, resultIntent);
                        finish();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
            task.execute(this);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void setNumOfFaceTextAsync(final String text) {
        guiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView numOfFace = findViewById(R.id.num_of_face);
                numOfFace.setText(text);
            }
        });
    }

    public void setProgressTextAsync(final String text) {
        guiThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView progressDescription = findViewById(R.id.progress_description);
                progressDescription.setText(text);
            }
        });
    }

    static public File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        return image;
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }


    private void saveBitmapImage(String saveFilePath, Bitmap image) {
        try {
            FileOutputStream output = new FileOutputStream(saveFilePath);
            image.compress(Bitmap.CompressFormat.PNG, 100, output);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}

package com.example.coverface;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.RecoverySystem;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.widget.ProgressBar;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
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
    private ProgressBar progressBar;
    private int progressVal;

    Mat takenPhoto;
    Bitmap takenPhotoBitmap;
    Mat coverPhoto;
    Bitmap coverPhotoBitmap;
    Bitmap faceMaskedBitmap;
    CascadeClassifier cascadeClassifier;

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
        //Intent intent = getIntent();

        if (OpenCVLoader.initDebug()) {
            this.mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, getApplicationContext(),
                    mLoaderCallback);
        }

        //this.progressBar = findViewById(R.id.progress_bar);
        //progressBar.setMax(100);

        //try {
        //    Uri coverPhotoUri = (Uri) intent.getExtras().get("coverPhotoUri");
        //    File takenPhotoFile = (File) intent.getExtras().get("takenPhotoFile");
        //    coverPhotoBitmap = this.getBitmapFromUri(coverPhotoUri);
        //    takenPhotoBitmap = BitmapFactory.decodeFile(takenPhotoFile.getPath());
        //    this.takenPhoto = new Mat();
        //    this.coverPhoto = new Mat();
        //    Utils.bitmapToMat(coverPhotoBitmap, this.coverPhoto);
        //    Utils.bitmapToMat(takenPhotoBitmap, this.takenPhoto);
        //    faceMaskedBitmap = takenPhotoBitmap;
        //    this.maskHumanFace(this.takenPhoto, this.coverPhoto, this.faceMaskedBitmap);
        //    File saveFile = createImageFile();
        //    saveBitmapImage(saveFile.getAbsolutePath(), this.faceMaskedBitmap);
        //    Intent resultIntent = new Intent();
        //    resultIntent.putExtra("faceMaskedFile", saveFile);
        //} catch (IOException e) {
        //    e.printStackTrace();
        //}
        //finish();
    }

    //public boolean onTouchEvent(MotionEvent event) {
    //    Log.d("touch", "touch");
    //    return true;
    //}

    private void startMaskingFace() {
        Intent intent = getIntent();
        this.progressBar = findViewById(R.id.progress_bar);
        progressBar.setMax(100);

        try {
            Uri coverPhotoUri = (Uri) intent.getExtras().get("coverPhotoUri");
            File takenPhotoFile = (File) intent.getExtras().get("takenPhotoFile");
            coverPhotoBitmap = this.getBitmapFromUri(coverPhotoUri);
            takenPhotoBitmap = BitmapFactory.decodeFile(takenPhotoFile.getPath());
            this.takenPhoto = new Mat();
            this.coverPhoto = new Mat();
            Utils.bitmapToMat(coverPhotoBitmap, this.coverPhoto);
            Utils.bitmapToMat(takenPhotoBitmap, this.takenPhoto);
            faceMaskedBitmap = takenPhotoBitmap;
            this.maskHumanFace(this.takenPhoto, this.coverPhoto, this.faceMaskedBitmap);
            File saveFile = createImageFile();
            saveBitmapImage(saveFile.getAbsolutePath(), this.faceMaskedBitmap);
            Intent resultIntent = new Intent();
            resultIntent.putExtra("faceMaskedFile", saveFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
        finish();
    }

    private void maskHumanFace(Mat sourceImage, Mat coverImage, Bitmap faceMaskedBitmap) {
        this.progressVal = 0;
        MatOfRect faceDetectResults = new MatOfRect();
        cascadeClassifier.detectMultiScale(takenPhoto, faceDetectResults);
        Rect[] detectedFaces = faceDetectResults.toArray();
        Mat maskingImage;
        int progressStep = 100/detectedFaces.length;
        for (int i = 0; i < detectedFaces.length; i++) {
            int height = detectedFaces[i].height;
            int width = detectedFaces[i].width;
            Point upperLeftPoint = detectedFaces[i].tl();
            maskingImage = resizeImage(coverImage, height, width);
            this.overlayImage(sourceImage, maskingImage, sourceImage, upperLeftPoint);
            this.progressVal += progressStep;
            Log.d("val", String.valueOf(this.progressVal));
            this.progressBar.setProgress(this.progressVal);
            //Imgproc.warpAffine( maskingImage, sourceImage, affineMat, sourceImage.size(), Core.BORDER_TRANSPARENT);
            //Imgproc.rectangle(sourceImage, detectedFaces[i].tl(), detectedFaces[i].br(), new Scalar(0, 0, 255), 3);
        }
        Utils.matToBitmap(sourceImage, faceMaskedBitmap);
    }

    private File createImageFile() throws IOException {
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        return image;
    }

    private Mat resizeImage(Mat sourceImage, int resizeHeight, int resizeWidth) {
        Mat resizedImage = new Mat();
        Size resizedSize = new Size(resizeHeight, resizeWidth);
        Imgproc.resize(sourceImage, resizedImage, resizedSize);
        return resizedImage;
    }

    public static void overlayImage(Mat background,Mat foreground,Mat output, Point location){

        background.copyTo(output);

        for(int y = (int) Math.max(location.y , 0); y < background.rows(); ++y){

            int fY = (int) (y - location.y);

            if(fY >= foreground.rows())
                break;

            for(int x = (int) Math.max(location.x, 0); x < background.cols(); ++x){
                int fX = (int) (x - location.x);
                if(fX >= foreground.cols()){
                    break;
                }

                double opacity;
                double[] finalPixelValue = new double[4];

                opacity = foreground.get(fY , fX)[3];

                finalPixelValue[0] = background.get(y, x)[0];
                finalPixelValue[1] = background.get(y, x)[1];
                finalPixelValue[2] = background.get(y, x)[2];
                finalPixelValue[3] = background.get(y, x)[3];

                for(int c = 0;  c < output.channels(); ++c){
                    if(opacity > 0){
                        double foregroundPx =  foreground.get(fY, fX)[c];
                        double backgroundPx =  background.get(y, x)[c];

                        float fOpacity = (float) (opacity / 255);
                        finalPixelValue[c] = ((backgroundPx * ( 1.0 - fOpacity)) + (foregroundPx * fOpacity));
                        if(c==3){
                            finalPixelValue[c] = foreground.get(fY,fX)[3];
                        }
                    }
                }
                output.put(y, x,finalPixelValue);
            }
        }
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

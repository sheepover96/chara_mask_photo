package com.example.coverface;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
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


public class MainActivity extends AppCompatActivity {

    private static final String FILE_NAME = "haarcascade_frontalface_alt.xml";
    static final int REQUEST_TAKE_PHOTO = 1;
    private static final int READ_REQUEST_CODE = 2;
    String currentPhotoPath;
    File photoFile;
    File maskPhotoFile;
    Mat coverPhoto;
    Mat takenPhoto;

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

                    performFileSearch();
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
        setContentView(R.layout.activity_main);

        //this.mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        if (OpenCVLoader.initDebug()) {
            this.mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        } else {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION, getApplicationContext(),
                    mLoaderCallback);
        }

        //this.performFileSearch();
        //this.dispatchTakePictureIntent();
        //this.galleryAddPic();
    }

    //public void onResume()
    //{
    //    super.onResume();
    //    if (!OpenCVLoader.initDebug()) {
    //        Log.d("OpenCV", "Internal OpenCV library not found. Using OpenCV Manager for initialization");
    //        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
    //    } else {
    //        Log.d("OpenCV", "OpenCV library found inside package. Using it!");
    //        mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
    //    }
    //}

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
        currentPhotoPath = image.getAbsolutePath();
        return image;
    }

    private void dispatchTakePictureIntent() {
        //Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        //if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
        //    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        //}
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            this.photoFile = null;
            try {
                this.photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
                ex.printStackTrace();
            }
            // Continue only if the File was successfully created
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
                    Bitmap imageBitmap = BitmapFactory.decodeFile(this.photoFile.getPath());
                    this.takenPhoto = new Mat();
                    Utils.bitmapToMat(imageBitmap, this.takenPhoto);
                    ImageView imageView = (ImageView) findViewById(R.id.taken_image);
                    this.maskHumanFace(this.takenPhoto, imageBitmap);
                    imageView.setImageBitmap(imageBitmap);
                    this.galleryAddPic();
                    break;
                case READ_REQUEST_CODE:
                    Uri coverPhotoUri = null;
                    if (data != null) {
                        coverPhotoUri = data.getData();
                        try {
                            Bitmap coverPhotoBitmap = this.getBitmapFromUri(coverPhotoUri);
                            this.coverPhoto = new Mat();
                            Utils.bitmapToMat(coverPhotoBitmap, this.coverPhoto);
                            this.dispatchTakePictureIntent();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private Mat resizeImage(Mat sourceImage, int resizeHeight, int resizeWidth) {
        Mat resizedImage = new Mat();
        Size resizedSize = new Size(resizeHeight, resizeWidth);
        Imgproc.resize(sourceImage, resizedImage, resizedSize);
        return resizedImage;
    }

    private void maskHumanFace(Mat sourceImage, Bitmap faceMaskedBitmap) {
        MatOfRect faceDetectResults = new MatOfRect();
        cascadeClassifier.detectMultiScale(takenPhoto, faceDetectResults);
        Rect[] detectedFaces = faceDetectResults.toArray();
        Mat maskingImage;
        Mat affineMat = new Mat(2, 3, CvType.CV_64F);
        Log.d("mask", "mask");
        for (int i = 0; i < detectedFaces.length; i++) {
            int height = detectedFaces[i].height;
            int width = detectedFaces[i].width;
            Point upperLeftPoint = detectedFaces[i].tl();
            maskingImage = resizeImage(this.coverPhoto, height, width);
            affineMat = new Mat(2, 3, CvType.CV_64F);
            affineMat.put(0,0, 1.0, 0.0, upperLeftPoint.x, 0.0, 1.0, upperLeftPoint.y);
            this.overlayImage(sourceImage, maskingImage, sourceImage, upperLeftPoint);
            //Imgproc.warpAffine( maskingImage, sourceImage, affineMat, sourceImage.size(), Core.BORDER_TRANSPARENT);
            //Imgproc.rectangle(sourceImage, detectedFaces[i].tl(), detectedFaces[i].br(), new Scalar(0, 0, 255), 3);
        }
        Utils.matToBitmap(sourceImage, faceMaskedBitmap);
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

    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(currentPhotoPath);
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

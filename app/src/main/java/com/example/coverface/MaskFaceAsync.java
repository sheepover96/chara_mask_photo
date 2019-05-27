package com.example.coverface;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.MatOfRect;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.objdetect.CascadeClassifier;


public class MaskFaceAsync extends AsyncTask<Activity, Integer, Bitmap> {

    ProcessingActivity activity;
    private ProgressBar progressBar;
    private int progressVal;
    private TextView numOfFace;
    private TextView progressDescription;

    Bitmap coverPhotoBitmap;
    Bitmap takenPhotoBitmap;
    CascadeClassifier cascadeClassifier;

    private CallBackTask callBackTask;

    public MaskFaceAsync(ProcessingActivity activity, Bitmap coverPhoto, Bitmap takenPhoto, CascadeClassifier cascadeClassifier) {
        this.activity = activity;
        this.coverPhotoBitmap = coverPhoto;
        this.takenPhotoBitmap = takenPhoto;
        this.cascadeClassifier = cascadeClassifier;
    }

    @Override
    protected void onPreExecute() {
        this.progressVal = 0;
        progressBar = activity.findViewById(R.id.progress_bar);
        progressBar.setMax(100);
        progressBar.setProgress(this.progressVal);
        activity.setNumOfFaceTextAsync("顔を検出中");
        activity.setProgressTextAsync("すこし待ってください");
    }

    @Override
    protected Bitmap doInBackground(Activity... params) {
        Mat coverPhoto = new Mat();
        Mat takenPhoto = new Mat();
        Utils.bitmapToMat(this.coverPhotoBitmap, coverPhoto);
        Utils.bitmapToMat(this.takenPhotoBitmap, takenPhoto);
        this.maskHumanFace(takenPhoto, coverPhoto, takenPhotoBitmap);
        return takenPhotoBitmap;
    }

    @Override
    protected void onProgressUpdate(Integer ...progress) {
        super.onProgressUpdate(progress);
        int i = progress[0];
        this.progressBar.setProgress(i);
    }

    @Override
    protected void onPostExecute(Bitmap result) {
        super.onPostExecute(result);
        callBackTask.CallBack(result);
    }

    public void setOnCallBack(CallBackTask _cbj) {
        callBackTask = _cbj;
    }

    public static class CallBackTask {
        public void CallBack(Bitmap resultBitmap) {
        }
    }

    private void maskHumanFace(Mat sourceImage, Mat coverImage, Bitmap faceMaskedBitmap) {
        MatOfRect faceDetectResults = new MatOfRect();
        cascadeClassifier.detectMultiScale(sourceImage, faceDetectResults);
        Rect[] detectedFaces = faceDetectResults.toArray();
        activity.setNumOfFaceTextAsync("顔が" + detectedFaces.length + "個見つかりました");
        activity.setProgressTextAsync("0個目の顔を隠しています");
        Mat maskingImage;
        if (detectedFaces.length != 0) {
            int progressStep = 100 / detectedFaces.length;
            for (int i = 0; i < detectedFaces.length; i++) {
                activity.setProgressTextAsync((i+1) + "個目の顔を隠しています");
                int height = detectedFaces[i].height;
                int width = detectedFaces[i].width;
                Point upperLeftPoint = detectedFaces[i].tl();
                maskingImage = resizeImage(coverImage, height, width);
                this.overlayImage(sourceImage, maskingImage, sourceImage, upperLeftPoint);
                this.progressVal += progressStep;
                publishProgress(this.progressVal);
            }
            Utils.matToBitmap(sourceImage, faceMaskedBitmap);
            Log.d("MASK", "masked");
        } else {
            publishProgress(100);
        }
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


}

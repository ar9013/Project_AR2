package com.ar4android.cameraAccessJME.filter;

import org.opencv.core.CvType;
import org.opencv.core.MatOfDouble;

import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.opengl.Matrix;

public class CameraProjectionAdapter {
	
	float mFOVY = 45f; // equivalent in 35mm photography: 28mm lens
	float mFOVX = 60f; // equivalent in 35mm photography: 28mm lens
	int mHeightPx = 480;
	int mWidthPx = 640;
	float mNear = 0.1f;
	float mFar = 10f;
	
	final float[] mProjectionGL = new float[16];
	boolean mProjectionDirtyGL = true;
	
	MatOfDouble mProjectionCV;
	boolean mProjectionDirtyCV = true;
	
	// 設定攝影機 的參數
	public void setCameraParameters(
			final Parameters cameraParameters, 
			final Size imageSize) {
			mFOVY = cameraParameters.getVerticalViewAngle();
			mFOVX = cameraParameters.getHorizontalViewAngle();
			
			mHeightPx = imageSize.height;
			mWidthPx = imageSize.width;
			
			mProjectionDirtyGL = true;
			mProjectionDirtyCV = true;
			}
	
	// 螢幕比例 
	public float getAspectRatio() {
		return (float)mWidthPx / (float)mHeightPx;
		}
	
	public MatOfDouble getProjectionCV() {
		if (mProjectionDirtyCV) {
			if (mProjectionCV == null) {
					mProjectionCV = new MatOfDouble();
					mProjectionCV.create(3, 3, CvType.CV_64FC1);
		}
			
		// Calculate focal length using the aspect ratio of the
		// FOV values reported by Camera.Parameters. This is not
		// necessarily the same as the image's current aspect
		// ratio, which might be a crop mode.
		final float fovAspectRatio = mFOVX / mFOVY;
		double diagonalPx = Math.sqrt(
				(Math.pow(mWidthPx, 2.0) +
				Math.pow(mWidthPx / fovAspectRatio, 2.0)));
				double diagonalFOV = Math.sqrt(
				(Math.pow(mFOVX, 2.0) +
				Math.pow(mFOVY, 2.0)));
				double focalLengthPx = diagonalPx /
				(2.0 * Math.tan(0.5 * diagonalFOV * Math.PI / 180f));
				
				mProjectionCV.put(0,0,focalLengthPx);
				mProjectionCV.put(0,1,0.0);
				mProjectionCV.put(0,2,0.5 * mWidthPx);
				mProjectionCV.put(1,0,0.0);
				mProjectionCV.put(1,1,focalLengthPx);
				mProjectionCV.put(1,2,0.5 * mHeightPx);
				mProjectionCV.put(2,0,0.0);
				mProjectionCV.put(2,1,0.0);
				mProjectionCV.put(2,2,1.0);
				
				}
				return mProjectionCV;
		}
				
}

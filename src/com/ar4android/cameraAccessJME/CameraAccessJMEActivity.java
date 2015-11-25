/* CameraAccessJMEActivity - CameraAccessJME Example
 * 
 * Example Chapter 2
 * accompanying the book
 * "Augmented Reality for Android Application Development", Packt Publishing, 2013.
 * 
 * Copyright � 2013 Jens Grubert, Raphael Grasset / Packt Publishing.
 * 
 */
package com.ar4android.cameraAccessJME;

import com.ar4android.cameraAccessJME.filter.CameraProjectionAdapter;
import com.ar4android.cameraAccessJME.filter.ImageDetectionFilter;
import com.jme3.app.AndroidHarness;
import android.content.pm.ActivityInfo;
import android.hardware.Camera;
import android.hardware.Camera.Size;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

import org.apache.http.entity.InputStreamEntity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.Log;
import android.view.ViewGroup;

import com.jme3.system.android.AndroidConfigChooser.ConfigType;
import com.jme3.texture.Image;

public class CameraAccessJMEActivity extends AndroidHarness {

	private static final String TAG = "CameraAccessJMEActivity";
	private Camera mCamera;
	private CameraPreview mPreview;
	private int mDesiredCameraPreviewWidth = 640;
	Size sizex;

	private byte[] mPreviewBufferRGB565 = null;

	java.nio.ByteBuffer mPreviewByteBufferRGB565;
	// the actual size of the preview images
	int mPreviewWidth;
	int mPreviewHeight;
	// If we have to convert the camera preview image into RGB565 or can use it
	// directly
	private boolean pixelFormatConversionNeeded = true;

	private boolean stopPreview = false;
	Image cameraJMEImageRGB565;

	// (OpenCV) ImageDetectionFilter.
	private ImageDetectionFilter[] mImageDetectionFilters;

	// (OpenCV) The indices of the active filters.
	private int mImageDetectionFilterIndex = 0;

	// (OpenCV) FlagDraw
	private boolean isTargetFound;

	// An adapter between the video camera and projection matrix.
	private CameraProjectionAdapter mCameraProjectionAdapter = new CameraProjectionAdapter();

	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS:
				Log.d(TAG, "OpenCV loaded successfully");

				final ImageDetectionFilter Marker_fireworks;
				try {
					// Define The fireworks image to be 1.0 units tall.
					Marker_fireworks = new ImageDetectionFilter(CameraAccessJMEActivity.this, R.drawable.fireworks,
							mCameraProjectionAdapter, 1.0);
				} catch (IOException e) {
					Log.e(TAG, "Failed to load drawable: " + "Marker_fireworks");
					e.printStackTrace();
					break;
				}

				final ImageDetectionFilter Marker_pukeko;
				try {
					Marker_pukeko = new ImageDetectionFilter(CameraAccessJMEActivity.this, R.drawable.pukeko,
							mCameraProjectionAdapter, 1.0);
				} catch (IOException e) {
					Log.e(TAG, "Failed to load drawable: " + "Marker_pukeko");
					e.printStackTrace();
					break;
				}

				final ImageDetectionFilter Marker_mini;
				try {
					Marker_mini = new ImageDetectionFilter(CameraAccessJMEActivity.this, R.drawable.mini,
							mCameraProjectionAdapter, 1.0);
				} catch (IOException e) {
					Log.e(TAG, "Failed to load drawable: " + "Marker_mini");
					e.printStackTrace();
					break;
				}

				mImageDetectionFilters = new ImageDetectionFilter[] { Marker_mini, Marker_fireworks, Marker_pukeko };
				break;
			default:
				super.onManagerConnected(status);
				break;
			}
		};
	};

	// Implement the interface for getting copies of preview frames
	private final Camera.PreviewCallback mCameraCallback = new Camera.PreviewCallback() {
		public void onPreviewFrame(byte[] data, Camera c) {
			if (c != null && stopPreview == false) {
				// data format Yuv NV21
				mPreviewByteBufferRGB565.clear();

				processFrame(data);

				mPreviewBufferRGB565 = mImageDetectionFilters[mImageDetectionFilterIndex].getDstByteArray();

				mPreviewByteBufferRGB565.put(mPreviewBufferRGB565);

				cameraJMEImageRGB565.setData(mPreviewByteBufferRGB565);

				if ((com.ar4android.cameraAccessJME.CameraAccessJME) app != null) {
					((com.ar4android.cameraAccessJME.CameraAccessJME) app).setTexture(cameraJMEImageRGB565);
					((com.ar4android.cameraAccessJME.CameraAccessJME) app)
							.setARFilter(mImageDetectionFilters[mImageDetectionFilterIndex]);

				}
			}
		}
	};

	// Retrieve an instance of the Camera object.
	public static Camera getCameraInstance() {
		Camera c = null;
		try {
			// get a Camera instance
			c = Camera.open(0);
		} catch (Exception e) {
			// Camera is does not exist or is already in use
			Log.e(TAG, "Camera not available or in use.");

		}
		// return NULL if camera is unavailable, otherwise return the Camera
		// instance
		return c;
	}

	// configure camera parameters like preview size
	private void initializeCameraParameters() {
		Camera.Parameters parameters = mCamera.getParameters();
		// Get a list of supported preview sizes.
		List<Camera.Size> sizes = parameters.getSupportedPreviewSizes();
		int currentWidth = 0;
		int currentHeight = 0;
		boolean foundDesiredWidth = false;
		for (Camera.Size size : sizes) {
			if (size.width == mDesiredCameraPreviewWidth) {
				currentWidth = size.width;
				currentHeight = size.height;
				foundDesiredWidth = true;
				sizex = size;
				break;
			}
		}

		if (foundDesiredWidth) {

			parameters.setPreviewSize(currentWidth, currentHeight);
			mCameraProjectionAdapter.setCameraParameters(parameters, sizex);

		}
		mCamera.setParameters(parameters);
	}

	public CameraAccessJMEActivity() {
		// Set the application class to run
		appClass = "com.ar4android.cameraAccessJME.CameraAccessJME";
		// Try ConfigType.FASTEST; or ConfigType.LEGACY if you have problems
		eglConfigType = ConfigType.BEST;
		// Exit Dialog title & message
		exitDialogTitle = "Exit?";
		exitDialogMessage = "Press Yes";
		// Enable verbose logging
		eglConfigVerboseLogging = false;
		// Choose screen orientation
		screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
		// Invert the MouseEvents X (default = true)
		mouseEventsInvertX = true;
		// Invert the MouseEvents Y (default = true)
		mouseEventsInvertY = true;
	}

	// We override AndroidHarness.onCreate() to be able to add the SurfaceView
	// needed for camera preview
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

	}

	@Override
	public void onResume() {
		super.onResume();

		// (OpenCV)
		if (!OpenCVLoader.initDebug()) {
			Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
			OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mOpenCVCallBack);
		} else {
			Log.d(TAG, "OpenCV library found inside package. Using it!");
			mOpenCVCallBack.onManagerConnected(LoaderCallbackInterface.SUCCESS);
		}

		stopPreview = false;
		// Create an instance of Camera
		mCamera = getCameraInstance();
		// initialize camera parameters
		initializeCameraParameters();
		// register our callback function to get access to the camera preview
		// frames
		preparePreviewCallbackBuffer();

		if (mCamera == null) {
			Log.e(TAG, "Camera not available");
		} else {
			// Create our Preview view and set it as the content of our
			// activity.
			mPreview = new CameraPreview(this, mCamera, mCameraCallback);
			// We do not want to display the Camera Preview view at startup - so
			// we resize it to 1x1 pixel.
			ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1, 1);
			addContentView(mPreview, lp);
		}
	}

	@Override
	protected void onPause() {
		stopPreview = true;
		super.onPause();
		// Make sure to release the camera immediately on pause.
		releaseCamera();
		// remove the SurfaceView
		ViewGroup parent = (ViewGroup) mPreview.getParent();
		parent.removeView(mPreview);
	}

	private void releaseCamera() {
		if (mCamera != null) {
			mCamera.setPreviewCallback(null);
			mCamera.stopPreview();
			// Release the camera.
			mCamera.release();
			mCamera = null;
		}
	}

	// prepares the Camera preview callback buffers.
	public void preparePreviewCallbackBuffer() {
		int pformat;
		pformat = mCamera.getParameters().getPreviewFormat();
		Log.e(TAG, "PREVIEW format: " + pformat);
		// Get pixel format information to compute buffer size.
		PixelFormat info = new PixelFormat();
		PixelFormat.getPixelFormatInfo(pformat, info);
		// The actual preview width and height.
		// They can differ from the requested width mDesiredCameraPreviewWidth
		mPreviewWidth = mCamera.getParameters().getPreviewSize().width;
		mPreviewHeight = mCamera.getParameters().getPreviewSize().height;
		int bufferSizeRGB565 = mPreviewWidth * mPreviewHeight * 2 + 4096;
		// Delete buffer before creating a new one.
		mPreviewBufferRGB565 = null;
		mPreviewBufferRGB565 = new byte[bufferSizeRGB565];

		mPreviewByteBufferRGB565 = ByteBuffer.allocateDirect(mPreviewBufferRGB565.length);
		cameraJMEImageRGB565 = new Image(Image.Format.RGB565, mPreviewWidth, mPreviewHeight, mPreviewByteBufferRGB565);
	}

	private void processFrame(byte[] data) {

		Mat mData = new Mat(mPreviewHeight + mPreviewHeight / 2, mPreviewWidth, CvType.CV_8UC1);// Yuv
																								// NV21
		mData.put(0, 0, data);

		Mat mRgba = new Mat(mPreviewHeight + mPreviewHeight / 2, mPreviewWidth, CvType.CV_8UC4);
		Imgproc.cvtColor(mData, mRgba, Imgproc.COLOR_YUV2BGRA_NV21, 4);

		mImageDetectionFilters[0].apply(mRgba, mRgba);
		isTargetFound = mImageDetectionFilters[0].getFlagTargetFound();
		Log.d(TAG, "isTargetFound : " + isTargetFound);
	}

}

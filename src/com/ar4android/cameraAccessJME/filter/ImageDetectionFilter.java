package com.ar4android.cameraAccessJME.filter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.Utils;
import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.DMatch;
import org.opencv.core.KeyPoint;
import org.opencv.core.Mat;
import org.opencv.core.MatOfDMatch;
import org.opencv.core.MatOfDouble;
import org.opencv.core.MatOfKeyPoint;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.MatOfPoint3f;
import org.opencv.core.Point;
import org.opencv.core.Point3;
import org.opencv.core.Scalar;
import org.opencv.features2d.DescriptorExtractor;
import org.opencv.features2d.DescriptorMatcher;
import org.opencv.features2d.FeatureDetector;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;


import android.content.Context;
import android.content.Intent;
import android.nfc.Tag;
import android.util.Log;

public final class ImageDetectionFilter{
	
	// Flag for debug.
	private String Tag = "ImageDetectionFilter";

	// The reference image (this detector's target).
	private final Mat mReferenceImage;
	// Features of the reference image.
	private final MatOfKeyPoint mReferenceKeypoints = new MatOfKeyPoint();
	// Descriptors of the reference image's features.
	private final Mat mReferenceDescriptors = new Mat();
	// The corner coordinates of the reference image, in pixels.
	// CvType defines the color depth, number of channels, and
	// channel layout in the image. Here, each point is represented
	// by two 32-bit floats.
	private final Mat mReferenceCorners = new Mat(4, 1, CvType.CV_32FC2);

	// The reference image's corner coordinates, in 3D, in real units.
		private final MatOfPoint3f mReferenceCorners3D = new MatOfPoint3f();
	
	// Features of the scene (the current frame).
	private final MatOfKeyPoint mSceneKeypoints = new MatOfKeyPoint();
	// Descriptors of the scene's features.
	private final Mat mSceneDescriptors = new Mat();
	// Tentative corner coordinates detected in the scene, in
	// pixels.
	private final Mat mCandidateSceneCorners = new Mat(4, 1, CvType.CV_32FC2);
	// Good corner coordinates detected in the scene, in pixels.
	private final MatOfPoint2f mSceneCorners2D = new MatOfPoint2f();
	// The good detected corner coordinates, in pixels, as integers.
	private final MatOfPoint mIntSceneCorners = new MatOfPoint();

	// A grayscale version of the scene.
	private Mat mGraySrc = new Mat();
	// Tentative matches of scene features and reference features.
	private final MatOfDMatch mMatches = new MatOfDMatch();

	// A feature detector, which finds features in images.
	private final FeatureDetector mFeatureDetector = FeatureDetector.create(FeatureDetector.ORB);
	// A descriptor extractor, which creates descriptors of
	// features.
	private final DescriptorExtractor mDescriptorExtractor = DescriptorExtractor.create(DescriptorExtractor.ORB);
	// A descriptor matcher, which matches features based on their
	// descriptors.
	private final DescriptorMatcher mDescriptorMatcher = DescriptorMatcher
			.create(DescriptorMatcher.BRUTEFORCE_HAMMINGLUT);

	private final Scalar mLineColor = new Scalar(0, 255, 0);

	byte[] dstByteArray = null;

	// Distortion coefficients of the camera's lens.
    // Assume no distortion. 假設沒有變形
    private final MatOfDouble mDistCoeffs = new MatOfDouble(
            0.0, 0.0, 0.0, 0.0);
	
 // An adaptor that provides the camera's projection matrix.
    private final CameraProjectionAdapter mCameraProjectionAdapter;
    
 // The Euler angles of the detected target.
    private final MatOfDouble mRVec = new MatOfDouble();
    // The XYZ coordinates of the detected target.
    private final MatOfDouble mTVec = new MatOfDouble();
    // The rotation matrix of the detected target.
    private final MatOfDouble mRotation =
    new MatOfDouble();
    // The OpenGL pose matrix of the detected target.
 //   private final float[] mGLPose = new float[12];
    private final float[] mGLPose = new float[12];
    
    // Whether the target is currently detected.
    private boolean mTargetFound = false;
	
   
    
	public ImageDetectionFilter(final Context context, 
			final int referenceImageResourceID,
			final CameraProjectionAdapter cameraProjectionAdapter,
			final double realSize) throws IOException {

		// Load the reference image from the app's resources.
		// It is loaded in BGR (blue, green, red) format.
		mReferenceImage = Utils.loadResource(context, referenceImageResourceID, Imgcodecs.CV_LOAD_IMAGE_COLOR);
		Log.d(Tag, "mReferenceImage");

		// Create grayscale and RGBA versions of the reference image.
		final Mat referenceImageGray = new Mat();
		Imgproc.cvtColor(mReferenceImage, referenceImageGray, Imgproc.COLOR_BGR2GRAY);

		Imgproc.cvtColor(mReferenceImage, mReferenceImage, Imgproc.COLOR_BGR2RGBA);
		Log.d(Tag, "referenceImageGray");

		// Store the reference image's corner coordinates, in pixels.
		mReferenceCorners.put(0, 0, new double[] { 0.0, 0.0 });
		mReferenceCorners.put(1, 0, new double[] { referenceImageGray.cols(), 0.0 });
		mReferenceCorners.put(2, 0, new double[] { referenceImageGray.cols(), referenceImageGray.rows() });
		mReferenceCorners.put(3, 0, new double[] { 0.0, referenceImageGray.rows() });

		 // Compute the image's width and height in real units, based
        // on the specified real size of the image's smaller
        // dimension.
		
		final double aspectRatio =
                (double)referenceImageGray.cols() /
                (double)referenceImageGray.rows();
        final double halfRealWidth;
        final double halfRealHeight;
        if (referenceImageGray.cols() > referenceImageGray.rows()) {
            halfRealHeight = 0.5f * realSize;
            halfRealWidth = halfRealHeight * aspectRatio;
        } else {
            halfRealWidth = 0.5f * realSize;
            halfRealHeight = halfRealWidth / aspectRatio;
        }
		
     // Define the real corner coordinates of the printed image
        // so that it normally lies in the xy plane (like a painting
        // or poster on a wall).
        // That is, +z normally points out of the page toward the
        // viewer.
        
        mReferenceCorners3D.fromArray(
                new Point3(-halfRealWidth, -halfRealHeight, 0.0),
                new Point3( halfRealWidth, -halfRealHeight, 0.0),
                new Point3( halfRealWidth,  halfRealHeight, 0.0),
                new Point3(-halfRealWidth,  halfRealHeight, 0.0));
        
		// Detect the reference features and compute their
		// descriptors.
		mFeatureDetector.detect(referenceImageGray, mReferenceKeypoints);
		Log.d(Tag, "mReferenceKeypoints");

		mDescriptorExtractor.compute(referenceImageGray, mReferenceKeypoints, mReferenceDescriptors);
		Log.d(Tag, "mReferenceDescriptors");
		
		 mCameraProjectionAdapter = cameraProjectionAdapter;

	}

	public void apply(Mat src, Mat dst) {

		// Convert the scene to grayscale.
		Imgproc.cvtColor(src, mGraySrc, Imgproc.COLOR_RGBA2GRAY);

		//mGraySrc = src.clone();
		// Detect the same features, compute their descriptors,
		// and match the scene descriptors to reference descriptors.
		mFeatureDetector.detect(mGraySrc, mSceneKeypoints);

		mDescriptorExtractor.compute(mGraySrc, mSceneKeypoints, mSceneDescriptors);

		mDescriptorMatcher.match(mSceneDescriptors, mReferenceDescriptors, mMatches);

		findPose();

		draw(src, dst);

		processRGBA2RGB565(dst);

	}

	private void findPose() {
		

		final List<DMatch> matchesList = mMatches.toList();

		if (matchesList.size() < 4) {
			// Log.d(Tag, "matchesList : "+matchesList.size());
			// Log.d(Tag, "matchesList < 4 Return.");
			// There are too few matches to find the homography.
			return;
		} else {
			// Log.d(Tag, "matchesList : "+matchesList.size());
		}

		final List<KeyPoint> referenceKeypointsList = mReferenceKeypoints.toList();
		final List<KeyPoint> sceneKeypointsList = mSceneKeypoints.toList();

		// Calculate the max and min distances between keypoints.
		double maxDist = 0.0;
		double minDist = Double.MAX_VALUE;

		for (final DMatch match : matchesList) {
			final double dist = match.distance;
			if (dist < minDist) {
				minDist = dist;

			}
			if (dist > maxDist) {
				maxDist = dist;
			}
		}

		// The thresholds for minDist are chosen subjectively
		// based on testing. The unit is not related to pixel
		// distances; it is related to the number of failed tests
		// for similarity between the matched descriptors.
		if (minDist > 50.0) {
			// The target is completely lost.
			// Discard any previously found corners.
			//mSceneCorners.create(0, 0, mSceneCorners.type());
			mTargetFound = false;
			return;
		} else if (minDist > 25.0) {
			// The target is lost but maybe it is still close.
			// Keep any previously found corners.
			return;
		}

		// Identify "good" keypoints and on match distance.
		final ArrayList<Point> goodReferencePointsList = new ArrayList<Point>();
		final ArrayList<Point> goodScenePointsList = new ArrayList<Point>();
		final double maxGoodMatchDist = 1.75 * minDist;
		for (final DMatch match : matchesList) {
			if (match.distance < maxGoodMatchDist) {
				goodReferencePointsList.add(referenceKeypointsList.get(match.trainIdx).pt);
				goodScenePointsList.add(sceneKeypointsList.get(match.queryIdx).pt);
			}
		}
		if (goodReferencePointsList.size() < 4 || goodScenePointsList.size() < 4) {
			// There are too few good points to find the homography.
			return;
		}
		// There are enough good points to find the homography.
		// (Otherwise, the method would have already returned.)

		// Convert the matched points to MatOfPoint2f format, as
		// required by the Calib3d.findHomography function.
		final MatOfPoint2f goodReferencePoints = new MatOfPoint2f();
		goodReferencePoints.fromList(goodReferencePointsList);

		final MatOfPoint2f goodScenePoints = new MatOfPoint2f();
		goodScenePoints.fromList(goodScenePointsList);

		// Find the homography.
		final Mat homography = Calib3d.findHomography(goodReferencePoints, goodScenePoints);

		// Use the homography to project the reference corner
		// coordinates into scene coordinates.
		Core.perspectiveTransform(mReferenceCorners, mCandidateSceneCorners, homography);

		// Convert the scene corners to integer format, as required
		// by the Imgproc.isContourConvex function.
		mCandidateSceneCorners.convertTo(mIntSceneCorners, CvType.CV_32S);

		// Check whether the corners form a convex polygon. If not,
		// (that is, if the corners form a concave polygon), the
		// detection result is invalid because no real perspective can
		// make the corners of a rectangular image look like a concave
		// polygon!
		if (!Imgproc.isContourConvex(mIntSceneCorners)) {
			// The corners form a convex polygon, so record them as
			// valid scene corners.
			return;
		}
		
		final double[] sceneCorner0 =
				mCandidateSceneCorners.get(0, 0);
		final	double[] sceneCorner1 =
				mCandidateSceneCorners.get(1, 0);
		final	double[] sceneCorner2 =
				mCandidateSceneCorners.get(2, 0);
		final	double[] sceneCorner3 =
				mCandidateSceneCorners.get(3, 0);
		
		// Get the value of mCandidateSceneCorners to form a point and put into MatOfPoint2f.
		mSceneCorners2D.fromArray(
				new Point(sceneCorner0[0], sceneCorner0[1]),
				new Point(sceneCorner1[0], sceneCorner1[1]),
				new Point(sceneCorner2[0], sceneCorner2[1]),
				new Point(sceneCorner3[0], sceneCorner3[1]));

		MatOfDouble projection =
				mCameraProjectionAdapter.getProjectionCV();
		
		// Find the target's Euler angles and XYZ coordinates.
        Calib3d.solvePnP(mReferenceCorners3D, mSceneCorners2D,
                projection, mDistCoeffs, mRVec, mTVec);
         
        // mRVec 旋轉矩陣
        final double[] rVecArray = mRVec.toArray(); 
  
        rVecArray[0] *= -1.0;
        
        mRVec.fromArray(rVecArray);
        
        // Convert the Euler angles to a 3x3 rotation matrix.
        Calib3d.Rodrigues(mRVec, mRotation);
        
        
         // mTVec 平移矩陣
        final double[] tVecArray = mTVec.toArray();
        
        
        // 透過 旋轉、平移矩陣 描述 辨識物姿態
//        mGLPose[0] = (float)mRotation.get(0, 0)[0];
//        mGLPose[1] = (float)mRotation.get(0, 1)[0]; //  z軸
//        mGLPose[2] = (float)mRotation.get(0, 2)[0]; // y軸
//        mGLPose[3] = (float)mRotation.get(1, 0)[0]; // z軸
//        mGLPose[4] = (float)mRotation.get(1, 1)[0];
//        mGLPose[5] = (float)mRotation.get(1, 2)[0];
//        mGLPose[6] = (float)mRotation.get(2, 0)[0]; // y軸 順時針
//        mGLPose[7] = (float)mRotation.get(2, 1)[0];
//        mGLPose[8] = (float)mRotation.get(2, 2)[0];
//        mGLPose[9] =   (float)tVecArray[0];
//        mGLPose[10] = -(float)tVecArray[1]; // negate y position
//        mGLPose[11] = -(float)tVecArray[2]; // negate z position
    
  // 換成 int 後 模型的位置、旋轉 比較穩定。      
        mGLPose[0] = (int)mRotation.get(0, 0)[0];
        mGLPose[1] = (int)mRotation.get(0, 1)[0]; //  z軸
        mGLPose[2] = (int)mRotation.get(0, 2)[0]; // y軸
        mGLPose[3] = (int)mRotation.get(1, 0)[0]; // z軸
        mGLPose[4] = (int)mRotation.get(1, 1)[0];
        mGLPose[5] = (int)mRotation.get(1, 2)[0];
        mGLPose[6] = (int)mRotation.get(2, 0)[0]; // y軸 順時針
        mGLPose[7] = (int)mRotation.get(2, 1)[0];
        mGLPose[8] = (int)mRotation.get(2, 2)[0];
        mGLPose[9] =   (int)tVecArray[0];
        mGLPose[10] = -(int)tVecArray[1]; // negate y position
        mGLPose[11] = -(int)tVecArray[2]; // negate z position
        
        mTargetFound = true;

	}

	protected void draw(final Mat src, final Mat dst) {

		if (dst != src) {
			src.copyTo(dst);
		}

		if(mTargetFound){
		// Outline the found target in green.
		Imgproc.line(dst, new Point(mSceneCorners2D.get(0, 0)), new Point(mSceneCorners2D.get(1, 0)), mLineColor, 4);
		Imgproc.line(dst, new Point(mSceneCorners2D.get(1, 0)), new Point(mSceneCorners2D.get(2, 0)), mLineColor, 4);
		Imgproc.line(dst, new Point(mSceneCorners2D.get(2, 0)), new Point(mSceneCorners2D.get(3, 0)), mLineColor, 4);
		Imgproc.line(dst, new Point(mSceneCorners2D.get(3, 0)), new Point(mSceneCorners2D.get(0, 0)), mLineColor, 4);
		}
	}


	private void processRGBA2RGB565(Mat dst) {
		// Change format from RGBA to RGB565. 
		Imgproc.cvtColor(dst, dst, Imgproc.COLOR_BGRA2BGR565);
		dstByteArray = new byte[(int) (dst.total() * dst.channels())];

		dst.get(0, 0, dstByteArray);
	}
	
	
	public byte[] getDstByteArray() {
		// Mat dst 轉換 RGB565， RGB565 轉換 byte[]
		return dstByteArray;
	}
	
	

	
	
	public float[] getGLPose() {
		
		return mGLPose;

	}
	
	public boolean getFlagTargetFound() {

		return mTargetFound;
	}
}

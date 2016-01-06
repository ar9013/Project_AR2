/* CameraAccessJME - CameraAccessJME Example
 * 
 * Example Chapter 2
 * accompanying the book
 * "Augmented Reality for Android Application Development", Packt Publishing, 2013.
 * 
 * Copyright � 2013 Jens Grubert, Raphael Grasset / Packt Publishing.
 * 
 */
package com.ar4android.cameraAccessJME;



import android.renderscript.Matrix3f;
import android.transition.Scene;
import android.util.Log;

import org.opencv.core.MatOfDouble;

import com.ar4android.cameraAccessJME.filter.ImageDetectionFilter;
import com.jme3.animation.AnimChannel;
import com.jme3.animation.AnimControl;
import com.jme3.animation.AnimEventListener;
import com.jme3.animation.LoopMode;
import com.jme3.app.SimpleApplication;
import com.jme3.light.DirectionalLight;
import com.jme3.material.Material;
import com.jme3.math.ColorRGBA;
import com.jme3.math.FastMath;
import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.jme3.renderer.Camera;
import com.jme3.renderer.ViewPort;
import com.jme3.scene.Geometry;
import com.jme3.scene.Node;
import com.jme3.scene.Spatial;
import com.jme3.scene.Spatial.CullHint;
import com.jme3.scene.shape.Quad;
import com.jme3.texture.Image;
import com.jme3.texture.Texture2D;



public class CameraAccessJME extends SimpleApplication implements AnimEventListener{

	public ImageDetectionFilter filter;
	float[] pose = new float[12];;
	boolean targetFound ;
	 Node vault = new Node("vault"); 
	
	private static final String TAG = "CameraAccessJME";
	// The geometry which will represent the video background
	private Geometry mVideoBGGeom;
	// The material which will be applied to the video background geometry.
	private Material mvideoBGMat;
	// The texture displaying the Android camera preview frames.
	private Texture2D mCameraTexture;
	// the JME image which serves as intermediate storage place for the Android camera frame before the pixels get uploaded into the texture.
	private Image mCameraImage;
	// A flag indicating if the scene has been already initialized.
	private boolean mSceneInitialized = false;
	// A flag indicating if a new Android camera image is available.
	boolean	mNewCameraFrameAvailable = false;

	private float fgCamFOVY = 50; // for a Samsung Galaxy SII
	float fgCamFOVX = 60f; // equivalent in 35mm photography: 28mm lens
	
	int mHeightPx = 480;
	int mWidthPx = 640;
	float fgCamNear = 10f;
	float fgCamFar = 1000f;

	Spatial ninja;
	Node nodeNinja = new Node();
	
	// for animation	
		// The controller allows access to the animation sequences of the model
		private AnimControl mAniControl;
		// the channel is used to run one animation sequence at a time
		private AnimChannel mAniChannel;
	
	public static void main(String[] args) {
		CameraAccessJME app = new CameraAccessJME();
		app.start();
	}

	// The default method used to initialize your JME application.
	@Override
	public void simpleInitApp() {
		
		// Do not display statistics or frames per second
		setDisplayStatView(false);
		setDisplayFps(false);
		// We use custom viewports - so the main viewport does not need to contain the rootNode
		viewPort.detachScene(rootNode);
		
		initVideoBackground(settings.getWidth(), settings.getHeight());
		initBackgroundCamera();
		initForegroundScene();			
		initForegroundCamera(fgCamFOVY);
		
	}

	// This function creates the geometry, the viewport and the virtual camera
	// needed for rendering the incoming Android camera frames in the scene
	// graph
	public void initVideoBackground(int screenWidth, int screenHeight) {
		// Create a Quad shape. 
		Quad videoBGQuad = new Quad(1, 1, true);
		// Create a Geometry with the Quad shape
		mVideoBGGeom = new Geometry("quad", videoBGQuad);		 
		float newWidth = 1.f * screenWidth / screenHeight;		
		// Center the Geometry in the middle of the screen.
		mVideoBGGeom.setLocalTranslation(-0.5f * newWidth, -0.5f, 0.f);//
		// Scale (stretch) the width of the Geometry to cover the whole screen width.
		mVideoBGGeom.setLocalScale(1.f * newWidth, 1.f, 1);
		// Apply a unshaded material which we will use for texturing.
		mvideoBGMat = new Material(assetManager, "Common/MatDefs/Misc/Unshaded.j3md");
		mVideoBGGeom.setMaterial(mvideoBGMat);
		// Create a new texture which will hold the Android camera preview frame pixels.
		mCameraTexture = new Texture2D();
		
		// Create a custom virtual camera with orthographic projection
		Camera videoBGCam = cam.clone();
		videoBGCam.setParallelProjection(true);
		// Also create a custom viewport. 
		ViewPort videoBGVP = renderManager.createMainView("VideoBGView",
				videoBGCam);
		// Attach the geometry representing the video background to the viewport.
		videoBGVP.attachScene(mVideoBGGeom);
		mSceneInitialized = true;		
	}

	public void initBackgroundCamera() {
		// Create a custom virtual camera with orthographic projection
		Camera videoBGCam = cam.clone();		
		videoBGCam.setParallelProjection(true);
		// Also create a custom viewport.
		ViewPort videoBGVP = renderManager.createMainView("VideoBGView",
				videoBGCam);
		// Attach the geometry representing the video background to the
		// viewport.
		videoBGVP.attachScene(mVideoBGGeom);
	}
	
	public void initForegroundScene() {
		// Load a model from test_data (OgreXML + material + texture)
		ninja  = assetManager.loadModel("Models/Ninja/Ninja.mesh.xml");
		
    	DirectionalLight sun = new DirectionalLight();

            ninja.scale(0.025f, 0.025f, 0.025f);
            ninja.rotate(0.0f, -3.0f, 0.0f);
            ninja.setLocalTranslation(0.0f, -2.5f, 0.0f);
            
            // You must add a light to make the model visible
            sun.setDirection(new Vector3f(-0.1f, -0.7f, -1.0f));
            mAniControl = ninja.getControl(AnimControl.class);
            mAniControl.addListener(this);
            mAniChannel = mAniControl.createChannel();
            // show animation from beginning
            mAniChannel.setAnim("Walk");
            mAniChannel.setLoopMode(LoopMode.Loop);
            mAniChannel.setSpeed(1f);
            
           
            nodeNinja.attachChild(ninja);
            nodeNinja.addLight(sun);
            
            
            
        	rootNode.attachChild(nodeNinja);
        	
        
	}
	
	public void initForegroundCamera(float fovY) {
		
		Camera fgCam = new Camera(settings.getWidth(), settings.getHeight());
		fgCam.setLocation(new Vector3f(0f, 0f, 50f));
		fgCam.setAxes(new Vector3f(-1f,0f,0f), new Vector3f(0f,1f,0f), new Vector3f(0f,0f,-1f));
		
		float phiDeg = 10;
		float phi = phiDeg / 180 * FastMath.PI; 
		Quaternion mRotQ = new Quaternion();
		
		final float right =
				(float)Math.tan(0.5f * fgCamFOVY * Math.PI / 180f) * fgCamNear;
		
		final float top = right / getAspectRatio();
		
		// set Frustum
		fgCam.setFrustum(fgCamNear, fgCamFar, -right, right, top, -top); 
	
		//fgCam.setFrustumPerspective(fovY,  getAspectRatio() , fgCamNear, fgCamFar);
		
		
		ViewPort fgVP = renderManager.createMainView("ForegroundView", fgCam);
		fgVP.attachScene(rootNode);
		fgVP.setClearFlags(false, true, false);
		fgVP.setBackgroundColor(ColorRGBA.Blue);
	
	}
	
	// This method retrieves the preview images from the Android world and puts them into a JME image.
	public void setTexture(final Image image) {
		if (!mSceneInitialized) {
			return;
		}
		mCameraImage = image;
		mNewCameraFrameAvailable = true;
	}

	// This method is called before every render pass.
	// Here we will update the JME texture if a new Android camera frame is available 
	@Override
	public void simpleUpdate(float tpf) {
		if(mNewCameraFrameAvailable) {
			mCameraTexture.setImage(mCameraImage);
			mvideoBGMat.setTexture("ColorMap", mCameraTexture);
			mNewCameraFrameAvailable = false;
		}
		
	
		if(targetFound){
			// 角色更新的參數有問題。
			pose = filter.getGLPose(); 
			ninja.setLocalTranslation(pose[9], pose[10], pose[11]);
			com.jme3.math.Matrix3f rotation = new com.jme3.math.Matrix3f(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5], pose[6], pose[7], pose[8]);
			ninja.setLocalRotation(rotation);
			
			ninja.setCullHint(CullHint.Never);

		}else{
			
			ninja.setCullHint(CullHint.Always);
		}
		
		// we have to update the video background node before the root node gets updated by the super class
		mVideoBGGeom.updateLogicalState(tpf);
		mVideoBGGeom.updateGeometricState();
	}

	@Override
	public void onAnimChange(AnimControl arg0, AnimChannel arg1, String arg2) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onAnimCycleDone(AnimControl arg0, AnimChannel arg1, String arg2) {
		// TODO Auto-generated method stub
		
	}

	public float getAspectRatio() {
		return (float)mWidthPx / (float)mHeightPx;
		}


	public void setARFilter(ImageDetectionFilter arFilter) {
		filter = arFilter;
		targetFound =filter.getFlagTargetFound();
		
//		if(targetFound){
//		pose = filter.getGLPose();
//		com.jme3.math.Matrix3f rotation = new com.jme3.math.Matrix3f(pose[0], pose[1], pose[2], pose[3], pose[4], pose[5], pose[6], pose[7], pose[8]);
//		ninja.setLocalRotation(rotation);
//        ninja.setLocalTranslation(pose[9], pose[10], pose[11]);
//		}
	}
}

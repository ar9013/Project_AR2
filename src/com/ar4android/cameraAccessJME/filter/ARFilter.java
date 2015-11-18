package com.ar4android.cameraAccessJME.filter;


public interface ARFilter extends Filter{
	public float[] getGLPose();
	public boolean getFlagTargetFound();
	public byte[] getDstByteArray();
}



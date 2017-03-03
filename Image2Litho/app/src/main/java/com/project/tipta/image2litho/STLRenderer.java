package com.project.tipta.image2litho;

import android.opengl.GLSurfaceView.Renderer;
import android.opengl.GLU;

import com.project.tipta.image2litho.stlbean.STLObject;
import com.project.tipta.image2litho.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

// Renderer
public class STLRenderer implements Renderer {
	public static final int FRAME_BUFFER_COUNT = 5;
	public float angleX;
	public float angleY;
	public float positionX = 0f;
	public float positionY = 0f;

	public float scale = 1.0f;

	private float scale_rember=1.0f;

	private float scale_now=1.0f;

	public float translation_z;
	
	public static float red;
	public static float green;
	public static float blue;
	public static float alpha;
	public static boolean displayAxes = false;
	public static boolean displayGrids = false;
	private static int bufferCounter = FRAME_BUFFER_COUNT;

	private STLObject stlObject;
	
	public STLRenderer(STLObject stlObject) {
		this.stlObject = stlObject;
		setTransLation_Z();
	}
	//rotation update
	public void requestRedraw() {
		bufferCounter = FRAME_BUFFER_COUNT;
	}
	
	//change stl object
	public void requestRedraw(STLObject stlObject) {
		this.stlObject = stlObject;
		setTransLation_Z();
		bufferCounter = FRAME_BUFFER_COUNT;
	}

	@Override
	public void onDrawFrame(GL10 gl) {
		if (bufferCounter < 1) {
			return;
		}
		bufferCounter--;
		System.out.println("update frame");
		gl.glLoadIdentity();
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		gl.glTranslatef(positionX, -positionY, 0);

		// rotation and apply Z-axis
		gl.glTranslatef(0, 0, translation_z);
		gl.glRotatef(angleX, 0, 1, 0);
		gl.glRotatef(angleY, 1, 0, 0);
		scale_rember=scale_now*scale;
		gl.glScalef(scale_rember, scale_rember, scale_rember);
		gl.glEnableClientState(GL10.GL_VERTEX_ARRAY);
		
		gl.glMatrixMode(GL10.GL_MODELVIEW);

		// draw object
		if (stlObject != null) {
			gl.glMaterialfv(GL10.GL_FRONT, GL10.GL_AMBIENT, new float[] { 0.57f,0.90f,0.93f,1.0f }, 0);
			gl.glMaterialfv(GL10.GL_FRONT, GL10.GL_DIFFUSE, new float[] { 0.57f,0.90f,0.93f,1.0f }, 0);
			
			gl.glEnable(GL10.GL_COLOR_MATERIAL);
			gl.glPushMatrix();
			gl.glColor4f(red,green,blue, 1.0f);
			stlObject.draw(gl);
			gl.glPopMatrix();
			gl.glDisable(GL10.GL_COLOR_MATERIAL);
		}
	}
	
	private FloatBuffer getFloatBufferFromArray(float[] vertexArray) {
		ByteBuffer vbb = ByteBuffer.allocateDirect(vertexArray.length * 4);
		vbb.order(ByteOrder.nativeOrder());
		FloatBuffer triangleBuffer = vbb.asFloatBuffer();
		triangleBuffer.put(vertexArray);
		triangleBuffer.position(0);
		return triangleBuffer;
	}

	private FloatBuffer getFloatBufferFromList(List<Float> vertexList) {
		ByteBuffer vbb = ByteBuffer.allocateDirect(vertexList.size() * 4);
		vbb.order(ByteOrder.nativeOrder());
		FloatBuffer triangleBuffer = vbb.asFloatBuffer();
		float[] array = new float[vertexList.size()];
		for (int i = 0; i < vertexList.size(); i++) {
			array[i] = vertexList.get(i);
		}
		triangleBuffer.put(array);
		triangleBuffer.position(0);
		return triangleBuffer;
	}

	@Override
	public void onSurfaceChanged(GL10 gl, int width, int height) {
		float aspectRatio = (float) width / height;

		gl.glViewport(0, 0, width, height);
		
		gl.glLoadIdentity();
		gl.glClear(GL10.GL_COLOR_BUFFER_BIT | GL10.GL_DEPTH_BUFFER_BIT);

		if (stlObject != null) {
			Log.i("maxX:" + stlObject.maxX);
			Log.i("minX:" + stlObject.minX);
			Log.i("maxY:" + stlObject.maxY);
			Log.i("minY:" + stlObject.minY);
			Log.i("maxZ:" + stlObject.maxZ);
			Log.i("minZ:" + stlObject.minZ);
		}

		GLU.gluPerspective(gl, 45f, aspectRatio, 1f, 5000f);// (stlObject.maxZ - stlObject.minZ) * 10f + 100f);

		gl.glMatrixMode(GL10.GL_MODELVIEW);
		GLU.gluLookAt(gl, 0, 0, 1000f, 0, 0, 0, 0, 1f, 0);
	}
	
	@Override
	public void onSurfaceCreated(GL10 gl, EGLConfig config) {
		gl.glEnable(GL10.GL_BLEND);
		gl.glClearDepthf(1.0f);
		gl.glEnable(GL10.GL_DEPTH_TEST);
		gl.glDepthFunc(GL10.GL_LEQUAL);
		gl.glHint(3152, 4354);
		gl.glEnable(GL10.GL_NORMALIZE);
		gl.glShadeModel(GL10.GL_SMOOTH);

		gl.glMatrixMode(GL10.GL_PROJECTION);

		// Lighting
		gl.glEnable(GL10.GL_LIGHTING);
		// Global light
		gl.glLightModelfv(GL10.GL_LIGHT_MODEL_TWO_SIDE, getFloatBufferFromArray(new  float[]{1.0f,1.0f,1.0f,1.0f}));
		gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_LIGHT_MODEL_TWO_SIDE, new float[]{0.75f, 0.75f, 0.75f, 1.0f}, 0);
		gl.glLightfv(GL10.GL_LIGHT0, GL10.GL_POSITION, new float[] { 1000f, 1000f, 1000f, 1.0f }, 0);
		gl.glEnable(GL10.GL_LIGHT0);

	}
	
	// adjust z axis
	private void setTransLation_Z (){
		if (stlObject != null) {
			Log.i("stl maxX:" + stlObject.maxX);
			Log.i("stl minX:" + stlObject.minX);
			Log.i("stl maxY:" + stlObject.maxY);
			Log.i("stl minY:" + stlObject.minY);
			Log.i("stl maxZ:" + stlObject.maxZ);
			Log.i("stl minZ:" + stlObject.minZ);
		}
		//compute distance
		float distance_x = stlObject.maxX - stlObject.minX;
		float distance_y = stlObject.maxY - stlObject.minY;
		float distance_z = stlObject.maxZ - stlObject.minZ;
		translation_z = distance_x;
		if (translation_z < distance_y) {
			translation_z = distance_y;
		}
		if (translation_z < distance_z) {
			translation_z = distance_z;
		}
		translation_z *= -2;
	}
	public void delete(){
		stlObject.delete();
		stlObject=null;
	}
	//scale rate
	public void setsclae(){
		scale_now=scale_rember;
		scale_rember=1.0f;
		scale=1.0f;
	}
}

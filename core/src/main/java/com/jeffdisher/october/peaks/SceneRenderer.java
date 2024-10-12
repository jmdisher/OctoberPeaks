package com.jeffdisher.october.peaks;

import java.io.IOException;

import com.badlogic.gdx.graphics.GL20;


/**
 * Responsible for rendering the OpenGL scene of the world (does not include UI elements, windows, etc).
 */
public class SceneRenderer
{
	private final GL20 _gl;
	private final int _program;
	private final int _uModelMatrix;
	private final int _uViewMatrix;
	private final int _uProjectionMatrix;
	private final int _uWorldLightLocation;
	private final int _image;
	private final int _quad;

	private float _locationX;
	private float _locationY;
	private float _locationZ;
	private float _rotateX;
	private float _rotateY;
	private Matrix _modelMatrix;
	private Matrix _viewMatrix;
	private Matrix _projectionMatrix;

	public SceneRenderer(GL20 gl) throws IOException
	{
		_gl = gl;
		
		// Create the shader program.
		_program = GraphicsHelpers.fullyLinkedProgram(_gl
				, "#version 100\n"
						+ "uniform mat4 uModelMatrix;\n"
						+ "uniform mat4 uViewMatrix;\n"
						+ "uniform mat4 uProjectionMatrix;\n"
						+ "uniform vec3 uWorldLightLocation;\n"
						+ "attribute vec3 aPosition;\n"
						+ "attribute vec3 aNormal;\n"
						+ "attribute vec2 aTexture0;\n"
						+ "varying float vDiffuseStrength;\n"
						+ "varying vec2 vTexture0;\n"
						+ "void main()\n"
						+ "{\n"
						+ "	vec3 worldSpaceVertex = vec3(uModelMatrix * vec4(aPosition, 1.0));\n"
						+ "	vec3 worldSpaceNormal = vec3(uModelMatrix * vec4(aNormal, 0.0));\n"
						+ "	float distanceToLight = length(uWorldLightLocation - worldSpaceVertex);\n"
						+ "	vec3 vectorToLight = normalize(uWorldLightLocation - worldSpaceVertex);\n"
						+ "	vDiffuseStrength = max(dot(worldSpaceNormal, vectorToLight), 0.5);\n"
						+ "	vTexture0 = aTexture0;\n"
						+ "	gl_Position = uProjectionMatrix * uViewMatrix * uModelMatrix * vec4(aPosition, 1.0);\n"
						+ "}\n"
				, "#version 100\n"
						+ "precision mediump float;\n"
						+ "uniform sampler2D uTexture0;\n"
						+ "varying float vDiffuseStrength;\n"
						+ "varying vec2 vTexture0;\n"
						+ "void main()\n"
						+ "{\n"
						+ "	gl_FragColor = vDiffuseStrength * texture2D(uTexture0, vTexture0);//vec4(vDiffuseStrength * vec3(1.0, 1.0, 1.0), 1.0);\n"
						+ "}\n"
				, new String[] {
						"aPosition",
						"aNormal",
						"aTexture0",
				}
		);
		_uModelMatrix = _gl.glGetUniformLocation(_program, "uModelMatrix");
		_uViewMatrix = _gl.glGetUniformLocation(_program, "uViewMatrix");
		_uProjectionMatrix = _gl.glGetUniformLocation(_program, "uProjectionMatrix");
		_uWorldLightLocation = _gl.glGetUniformLocation(_program, "uWorldLightLocation");
		_image = GraphicsHelpers.loadInternalRGBA(_gl, "op_logo.png");
		_quad = GraphicsHelpers.buildTestingScene(_gl);
		
		_locationX = -1.0f;
		_locationY = -3.0f;
		_locationZ =  0.0f;
		_rotateX = 0.0f;
		_rotateY = 0.0f;
		
		_modelMatrix = Matrix.translate(-0.5f, -0.5f, 0.0f);
		_updateViewMatrix();
		_projectionMatrix = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
	}

	public void render()
	{
		_gl.glUseProgram(_program);
		
		// Make sure that the texture is active.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _image);
		_gl.glUniform1i(_image, 0);
		
		// Set the matrices.
		_modelMatrix.uploadAsUniform(_gl, _uModelMatrix);
		_viewMatrix.uploadAsUniform(_gl, _uViewMatrix);
		_projectionMatrix.uploadAsUniform(_gl, _uProjectionMatrix);
		_gl.glUniform3f(_uWorldLightLocation, _locationX, _locationY, _locationZ);
		
		_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _quad);
		int cubeCount = 4;
		int quadsPerCube = 6;
		int verticesPerQuad = 6;
		_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, cubeCount * quadsPerCube * verticesPerQuad);
	}

	public void translate(float deltaX, float deltaY, float deltaZ)
	{
		_locationX += deltaX;
		_locationY += deltaY;
		_locationZ += deltaZ;
		_updateViewMatrix();
	}

	// X positive is "right"
	// Y positive is "down"
	public void rotate(int deltaX, int deltaY)
	{
		float xRadians = ((float)deltaX) / 300.0f;
		float yRadians = ((float)deltaY) / 300.0f;
		// Y is backward since we rotate left and consider positive sin of Y.
		_rotateX += xRadians;
		_rotateY -= yRadians;
		float pi = (float)Math.PI;
		float pi2 = 2.0f * pi;
		float piHalf = pi / 2.0f;
		if (_rotateX > pi2)
		{
			_rotateX -= pi2;
		}
		else if (_rotateX < -pi2)
		{
			_rotateX += pi2;
		}
		if (_rotateY > piHalf)
		{
			_rotateY = piHalf;
		}
		else if (_rotateY < -piHalf)
		{
			_rotateY = -piHalf;
		}
		_updateViewMatrix();
	}


	private void _updateViewMatrix()
	{
		// We will assume that we are looking at (0, 1, 0) when at 0 rotation.
		float lookX = - (float)Math.sin(_rotateX);
		float lookY = (float)Math.cos(_rotateX);
		float lookZ = (float)Math.sin(_rotateY);
		Vector looking = new Vector(lookX, lookY, lookZ).normalize();
		_viewMatrix = Matrix.lookAt(new Vector(_locationX, _locationY, _locationZ), new Vector(_locationX + looking.x(), _locationY + looking.y(), _locationZ + looking.z()), new Vector(0.0f, 0.0f, 1.0f));
	}
}

package com.jeffdisher.october.peaks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.badlogic.gdx.Gdx;
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

	private Matrix _modelMatrix;
	private Matrix _viewMatrix;
	private final Matrix _projectionMatrix;
	private Vector _eye;

	public SceneRenderer(GL20 gl) throws IOException
	{
		_gl = gl;
		
		// Create the shader program.
		_program = GraphicsHelpers.fullyLinkedProgram(_gl
				, _readUtf8Asset("scene.vert")
				, _readUtf8Asset("scene.frag")
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
		_image = GraphicsHelpers.loadInternalRGBA(_gl, "missing_texture.png");
		_quad = GraphicsHelpers.buildTestingScene(_gl);
		
		_modelMatrix = Matrix.translate(-0.5f, -0.5f, 0.0f);
		_viewMatrix = Matrix.identity();
		_projectionMatrix = Matrix.perspective(90.0f, 1.0f, 0.1f, 100.0f);
		_eye = new Vector(0.0f, 0.0f, 0.0f);
	}

	public void updatePosition(Vector eye, Vector target)
	{
		_eye = eye;
		_viewMatrix = Matrix.lookAt(eye, target, new Vector(0.0f, 0.0f, 1.0f));
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
		_gl.glUniform3f(_uWorldLightLocation, _eye.x(), _eye.y(), _eye.z());
		
		_gl.glBindBuffer(GL20.GL_ARRAY_BUFFER, _quad);
		int cubeCount = 4;
		int quadsPerCube = 6;
		int verticesPerQuad = 6;
		_gl.glDrawArrays(GL20.GL_TRIANGLES, 0, cubeCount * quadsPerCube * verticesPerQuad);
	}


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}
}

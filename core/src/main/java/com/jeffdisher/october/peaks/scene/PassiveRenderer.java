package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.ItemSlot;
import com.jeffdisher.october.types.PartialPassive;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the passive entities within the OpenGL scene.
 */
public class PassiveRenderer
{
	public static final int BUFFER_SIZE = 1024;
	public static class Resources
	{
		private final ItemTextureAtlas _itemAtlas;
		private final Program _program;
		private final int _uModelMatrix;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uBrightness;
		private final int _uUvBase;
		// TODO:  We will need to generalize this for other passive types.
		private final VertexArray _itemSlotVertices;
		
		public Resources(Environment environment, GL20 gl, ItemTextureAtlas itemAtlas) throws IOException
		{
			_itemAtlas = itemAtlas;
			
			// Create the shader program.
			// Note that ItemSlot instances are typically what passives are used for, and they have a per-instance
			// texture so we will need to pass in the base texture as a uniform and adjust the per-vertex coordinates as
			// relative.
			// TODO:  We will likely need to adapt how we are using this shader once other passive types are added.
			_program = Program.fullyLinkedProgram(gl
				, _readUtf8Asset("passive_item.vert")
				, _readUtf8Asset("passive_item.frag")
				, new String[] {
					"aPosition",
					"aNormal",
					"aTexture0",
					"aTexture1_ignored",
					"aBlockLightMultiplier_ignored",
					"aSkyLightMultiplier_ignored",
				}
			);
			_uModelMatrix = _program.getUniformLocation("uModelMatrix");
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uBrightness = _program.getUniformLocation("uBrightness");
			_uUvBase = _program.getUniformLocation("uUvBase");
			
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = direct.asFloatBuffer();
			
			// TODO:  We will need to generalize this for other passive types.
			float itemEdge = PassiveType.ITEM_SLOT.volume().width();
			BufferBuilder builder = new BufferBuilder(meshBuffer, _program.attributes);
			float textureSize = itemAtlas.coordinateSize;
			SceneMeshHelpers.drawPassiveStandingSquare(builder
				, itemEdge
				, textureSize
			);
			_itemSlotVertices = builder.finishOne().flush(gl);
		}
		
		public void shutdown(GL20 gl)
		{
			_itemSlotVertices.delete(gl);
			_program.delete();
		}
	}


	private final GL20 _gl;
	private final Binding<Float> _screenBrightness;
	private final Resources _resources;
	private final Map<Integer, PartialPassive> _itemSlotPassives;

	public PassiveRenderer(GL20 gl, Binding<Float> screenBrightness, LoadedResources resources)
	{
		_gl = gl;
		_screenBrightness = screenBrightness;
		_resources = resources.passiveResources();
		
		_itemSlotPassives = new HashMap<>();
	}

	public void renderEntities(Matrix viewMatrix, Matrix projectionMatrix, Vector eye)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_resources._program.useProgram();
		_gl.glUniform3f(_resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We just use the texture for the entity.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._itemAtlas.texture);
		
		// Render the passives.
		// TODO:  In the future, we should put all of these into a mutable VertexArray, or something, since this is very chatty and probably slow.
		for (PartialPassive itemSlotPassive : _itemSlotPassives.values())
		{
			EntityLocation location = itemSlotPassive.location();
			Item type = ((ItemSlot)itemSlotPassive.extendedData()).getType();
			
			// We only translate for model matrix (currently).
			Matrix model = Matrix.translate(location.x(), location.y(), location.z());
			model.uploadAsUniform(_gl, _resources._uModelMatrix);
			
			// We need to pass in the base texture coordinates of this type.
			float[] uvBase = _resources._itemAtlas.baseOfTexture(type.number());
			_gl.glUniform2f(_resources._uUvBase, uvBase[0], uvBase[1]);
			
			// Just draw the square.
			_resources._itemSlotVertices.drawAllTriangles(_gl);
		}
	}

	public void passiveEntityDidLoad(PartialPassive entity)
	{
		if (PassiveType.ITEM_SLOT == entity.type())
		{
			Object old = _itemSlotPassives.put(entity.id(), entity);
			Assert.assertTrue(null == old);
		}
	}

	public void passiveEntityDidChange(PartialPassive entity)
	{
		if (PassiveType.ITEM_SLOT == entity.type())
		{
			Object old = _itemSlotPassives.put(entity.id(), entity);
			Assert.assertTrue(null != old);
		}
	}

	public void passiveEntityDidUnload(int id)
	{
		_itemSlotPassives.remove(id);
	}


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}
}

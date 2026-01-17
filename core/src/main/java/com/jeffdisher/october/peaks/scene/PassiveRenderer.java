package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collection;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.peaks.GhostManager;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.peaks.utils.WorldCache;
import com.jeffdisher.october.peaks.wavefront.WavefrontReader;
import com.jeffdisher.october.types.Block;
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
	public static final int BUFFER_SIZE = 16 * 1024;
	public static final float TWO_PI_RADIANS = (float)(2.0 * Math.PI);

	public static class Resources
	{
		private final ItemTextureAtlas _itemAtlas;
		private final ItemSlotResources _itemSlotResources;
		private final FallingBlockResources _fallingBlockResources;
		private final ArrowResources _arrowResources;
		
		public Resources(Environment environment, GL20 gl, ItemTextureAtlas itemAtlas) throws IOException
		{
			_itemAtlas = itemAtlas;
			float textureSize = itemAtlas.coordinateSize;
			_itemSlotResources = new ItemSlotResources(environment, gl, textureSize);
			_fallingBlockResources = new FallingBlockResources(environment, gl, textureSize);
			_arrowResources = new ArrowResources(environment, gl);
		}
		
		public void shutdown(GL20 gl)
		{
			_itemSlotResources.shutdown(gl);
			_fallingBlockResources.shutdown(gl);
		}
	}

	public static class ItemSlotResources
	{
		private final Program _program;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uBrightness;
		private final int _uUvBase;
		private final int _uAnimation;
		private final int _uCentre;
		// TODO:  We will need to generalize this for other passive types.
		private final VertexArray _itemSlotVertices;
		
		public ItemSlotResources(Environment environment, GL20 gl, float textureSize) throws IOException
		{
			// Create the shader program.
			// Note that ItemSlot instances are typically what passives are used for, and they have a per-instance
			// texture so we will need to pass in the base texture as a uniform and adjust the per-vertex coordinates as
			// relative.
			_program = Program.fullyLinkedProgram(gl
				, MiscPeaksHelpers.readUtf8Asset("passive_item.vert")
				, MiscPeaksHelpers.readUtf8Asset("passive_item.frag")
				, new String[] {
					"aPosition",
					"aNormal",
					"aTexture0",
				}
			);
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uBrightness = _program.getUniformLocation("uBrightness");
			_uUvBase = _program.getUniformLocation("uUvBase");
			_uAnimation = _program.getUniformLocation("uAnimation");
			_uCentre = _program.getUniformLocation("uCentre");
			
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = direct.asFloatBuffer();
			
			float itemEdge = PassiveType.ITEM_SLOT.volume().width();
			BufferBuilder builder = new BufferBuilder(meshBuffer, _program.attributes);
			boolean[] attributesToUse = MeshHelperBufferBuilder.useActiveAttributes(_program.attributes);
			MeshHelperBufferBuilder builderWrapper = new MeshHelperBufferBuilder(builder, attributesToUse);
			SceneMeshHelpers.drawPassiveStandingSquare(builderWrapper
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

	public static class FallingBlockResources
	{
		private final Program _program;
		private final int _uModelMatrix;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uBrightness;
		private final int _uUvBase;
		// TODO:  We will need to generalize this for other passive types.
		private final VertexArray _fallingBlockVertices;
		
		public FallingBlockResources(Environment environment, GL20 gl, float textureSize) throws IOException
		{
			// Create the shader program.
			// Note that ItemSlot instances are typically what passives are used for, and they have a per-instance
			// texture so we will need to pass in the base texture as a uniform and adjust the per-vertex coordinates as
			// relative.
			_program = Program.fullyLinkedProgram(gl
				, MiscPeaksHelpers.readUtf8Asset("passive_block.vert")
				, MiscPeaksHelpers.readUtf8Asset("passive_block.frag")
				, new String[] {
					"aPosition",
					"aNormal",
					"aTexture0",
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
			
			BufferBuilder builder = new BufferBuilder(meshBuffer, _program.attributes);
			boolean[] attributesToUse = MeshHelperBufferBuilder.useActiveAttributes(_program.attributes);
			MeshHelperBufferBuilder builderWrapper = new MeshHelperBufferBuilder(builder, attributesToUse);
			SceneMeshHelpers.drawPassiveCube(builderWrapper, textureSize);
			_fallingBlockVertices = builder.finishOne().flush(gl);
		}
		
		public void shutdown(GL20 gl)
		{
			_fallingBlockVertices.delete(gl);
			_program.delete();
		}
	}

	public static class ArrowResources
	{
		private final Program _program;
		private final int _uModelMatrix;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uBrightness;
		// TODO:  We will need to generalize this for other passive types.
		private final VertexArray _arrowVertices;
		private final int _arrowTexture;
		
		public ArrowResources(Environment environment, GL20 gl) throws IOException
		{
			// Note that this shader program is a slightly modified version of the entity.* program.
			_program = Program.fullyLinkedProgram(gl
				, MiscPeaksHelpers.readUtf8Asset("passive_arrow.vert")
				, MiscPeaksHelpers.readUtf8Asset("passive_arrow.frag")
				, new String[] {
					"aPosition",
					"aNormal",
					"aTexture0",
				}
			);
			_uModelMatrix = _program.getUniformLocation("uModelMatrix");
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uBrightness = _program.getUniformLocation("uBrightness");
			
			// The arrow is just a model, much like the entities, so we use similar loading logic.
			String name = "passive_arrow";
			FileHandle meshFile = Gdx.files.internal(name + ".obj");
			FileHandle textureFile = Gdx.files.internal(name + ".png");
			
			// We will require that the entity has a mesh definition and a texture.
			Assert.assertTrue(meshFile.exists());
			Assert.assertTrue(textureFile.exists());
			
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = direct.asFloatBuffer();
			
			String rawMesh = meshFile.readString();
			BufferBuilder builder = new BufferBuilder(meshBuffer, _program.attributes);
			WavefrontReader.readFile((float[] position, float[] texture, float[] normal) -> {
				builder.appendVertex(position
					, normal
					, texture
				);
			}, rawMesh);
			_arrowVertices = builder.finishOne().flush(gl);
			_arrowTexture = TextureHelpers.loadHandleRGBA(gl, textureFile);
		}
		
		public void shutdown(GL20 gl)
		{
			_arrowVertices.delete(gl);
			gl.glDeleteTexture(_arrowTexture);
			_program.delete();
		}
	}


	private final GL20 _gl;
	private final Binding<Float> _screenBrightness;
	private final Resources _resources;
	private final WorldCache _worldCache;
	private final GhostManager _ghostManager;
	private final float _halfWidth;
	private final float _halfHeight;

	public PassiveRenderer(GL20 gl
		, Binding<Float> screenBrightness
		, LoadedResources resources
		, WorldCache worldCache
		, GhostManager ghostManager
	)
	{
		_gl = gl;
		_screenBrightness = screenBrightness;
		_resources = resources.passiveResources();
		_worldCache = worldCache;
		_ghostManager = ghostManager;
		
		_halfWidth = PassiveType.ITEM_SLOT.volume().width() / 2.0f;
		_halfHeight = PassiveType.ITEM_SLOT.volume().height() / 2.0f;
	}

	public void renderEntities(Matrix viewMatrix, Matrix projectionMatrix, Vector eye)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		
		long currentMillis = System.currentTimeMillis();
		Collection<PartialPassive> itemSlotPassives = _worldCache.getItemSlotPassives();
		Collection<PartialPassive> itemSlotGhosts = _ghostManager.pruneAndSnapshotItemSlotPassives(currentMillis);
		if (!itemSlotPassives.isEmpty() || !itemSlotGhosts.isEmpty())
		{
			_renderItemSlots(_resources._itemSlotResources, itemSlotPassives, itemSlotGhosts, viewMatrix, projectionMatrix, eye);
		}
		
		Collection<PartialPassive> fallingBlockPassives = _worldCache.getFallingBlockPassives();
		if (fallingBlockPassives.size() > 0)
		{
			_renderFallingBlocks(_resources._fallingBlockResources, fallingBlockPassives, viewMatrix, projectionMatrix, eye);
		}
		
		Collection<PartialPassive> arrowPassives = _worldCache.getArrowPassives();
		if (arrowPassives.size() > 0)
		{
			_renderArrows(_resources._arrowResources, arrowPassives, viewMatrix, projectionMatrix, eye);
		}
	}


	private void _renderItemSlots(ItemSlotResources resources, Collection<PartialPassive> itemSlotPassives, Collection<PartialPassive> itemSlotGhosts, Matrix viewMatrix, Matrix projectionMatrix, Vector eye)
	{
		resources._program.useProgram();
		_gl.glUniform3f(resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, resources._uProjectionMatrix);
		_gl.glUniform1f(resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We just use the texture for the entity.
		_gl.glUniform1i(resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._itemAtlas.texture);
		
		// We want to set the animation frame from 0.0f - 1.0f based on the time in a second.
		float animationTime = (float)(System.currentTimeMillis() % 1024L) / 1024.0f;
		_gl.glUniform1f(resources._uAnimation, animationTime * TWO_PI_RADIANS);
		
		// Render the passives.
		// TODO:  In the future, we should put all of these into a mutable VertexArray, or something, since this is very chatty and probably slow.
		for (PartialPassive itemSlotPassive : itemSlotPassives)
		{
			_renderItemSlotPassive(resources, itemSlotPassive);
		}
		
		// Walk any ghosts.
		for (PartialPassive itemSlotPassive : itemSlotGhosts)
		{
			_renderItemSlotPassive(resources, itemSlotPassive);
		}
	}

	private void _renderItemSlotPassive(ItemSlotResources resources, PartialPassive itemSlotPassive)
	{
		EntityLocation location = itemSlotPassive.location();
		Item type = ((ItemSlot)itemSlotPassive.extendedData()).getType();
		
		// Determine the centre of this for rotation.
		float centreX = location.x() + _halfWidth;
		float centreY = location.y() + _halfWidth;
		float centreZ = location.z() + _halfHeight;
		_gl.glUniform3f(resources._uCentre, centreX, centreY, centreZ);
		
		// We need to pass in the base texture coordinates of this type.
		float[] uvBase = _resources._itemAtlas.baseOfTexture(type.number());
		_gl.glUniform2f(resources._uUvBase, uvBase[0], uvBase[1]);
		
		// Just draw the square.
		resources._itemSlotVertices.drawAllTriangles(_gl);
	}

	private void _renderFallingBlocks(FallingBlockResources resources, Collection<PartialPassive> fallingBlockPassives, Matrix viewMatrix, Matrix projectionMatrix, Vector eye)
	{
		resources._program.useProgram();
		_gl.glUniform3f(resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, resources._uProjectionMatrix);
		_gl.glUniform1f(resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We will just use the item texture for all 6 faces of the cube.
		_gl.glUniform1i(resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._itemAtlas.texture);
		
		// Render the passives.
		// TODO:  In the future, we should put all of these into a mutable VertexArray, or something, since this is very chatty and probably slow.
		for (PartialPassive fallingBlockPassive : fallingBlockPassives)
		{
			// Create a model matrix just based on this translation.
			EntityLocation location = fallingBlockPassive.location();
			Matrix translation = Matrix.translate(location.x(), location.y(), location.z());
			translation.uploadAsUniform(_gl, resources._uModelMatrix);
			
			// We need to pass in the base texture coordinates of this type.
			Block block = (Block)fallingBlockPassive.extendedData();
			float[] uvBase = _resources._itemAtlas.baseOfTexture(block.item().number());
			_gl.glUniform2f(resources._uUvBase, uvBase[0], uvBase[1]);
			
			// Draw the cube.
			resources._fallingBlockVertices.drawAllTriangles(_gl);
		}
	}

	private void _renderArrows(ArrowResources resources, Collection<PartialPassive> arrowPassives, Matrix viewMatrix, Matrix projectionMatrix, Vector eye)
	{
		resources._program.useProgram();
		_gl.glUniform3f(resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, resources._uProjectionMatrix);
		_gl.glUniform1f(resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// We just use the texture for the entity.
		_gl.glUniform1i(resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, resources._arrowTexture);
		
		// Render the passives.
		// TODO:  In the future, we should put all of these into a mutable VertexArray, or something, since this is very chatty and probably slow.
		for (PartialPassive arrowPassive : arrowPassives)
		{
			EntityLocation location = arrowPassive.location();
			EntityLocation velocity = arrowPassive.velocity();
			Matrix translation = _generateArrowModelMatrix(location, velocity);
			translation.uploadAsUniform(_gl, resources._uModelMatrix);
			
			resources._arrowVertices.drawAllTriangles(_gl);
		}
	}

	private static Matrix _generateArrowModelMatrix(EntityLocation location, EntityLocation velocity)
	{
		// Note that the arrow model is already the expected size, centred at (0,0,0), and facing North.
		Matrix translate = Matrix.translate(location.x(), location.y(), location.z());
		Matrix rotate = Matrix.rotateToFace(Vector.fromEntityLocation(velocity));
		Matrix model = Matrix.multiply(translate, rotate);
		return model;
	}
}

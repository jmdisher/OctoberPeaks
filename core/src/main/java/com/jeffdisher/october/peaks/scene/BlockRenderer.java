package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.logic.SparseByteCube;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.Attribute;
import com.jeffdisher.october.peaks.graphics.BufferBuilder;
import com.jeffdisher.october.peaks.graphics.BufferBuilder.Buffer;
import com.jeffdisher.october.peaks.textures.AuxilliaryTextureAtlas;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.peaks.ui.Binding;
import com.jeffdisher.october.peaks.utils.MiscPeaksHelpers;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.EntityLocation;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.types.PassiveType;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the world within the OpenGL scene - this is things like blocks, items, and liquids, but
 * not entities.
 */
public class BlockRenderer
{
	public static final int BUFFER_SIZE = 1 * 1024 * 1024;
	public static final float TWO_PI_RADIANS = (float)(2.0 * Math.PI);

	public static class Resources
	{
		private final ItemTextureAtlas _itemAtlas;
		private final BlockModelsAndAtlas _blockModels;
		private final BasicBlockAtlas _blockTextures;
		private final AuxilliaryTextureAtlas _auxBlockTextures;
		private final Program _program;
		private final int _uModelMatrix;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uTexture1;
		private final int _uSkyLight;
		private final int _uBrightness;
		private final Map<Block, Prism> _blockModelBounds;
		private final int _highlightTexture;
		private final VertexArray _defaultHighlightCube;
		private final Map<Block, VertexArray> _blockModelHighlightCubes;
		
		public Resources(Environment environment, GL20 gl, ItemTextureAtlas itemAtlas) throws IOException
		{
			_itemAtlas = itemAtlas;
			
			// Some blocks have models and some are just blocks (potentially with sides) so we will load the models first and extract those from the list before defaulting to basic blocks.
			Block[] blocks = Arrays.stream(environment.items.ITEMS_BY_TYPE)
					.map((Item item) -> environment.blocks.fromItem(item))
					.filter((Block block) -> null != block)
					.toArray((int size) -> new Block[size])
			;
			_blockModels = BlockModelsAndAtlas.loadForItems(gl, blocks);
			Set<Block> models = _blockModels.getBlockSet();
			blocks = Arrays.stream(blocks)
					.filter((Block block) -> !models.contains(block))
					.toArray((int size) -> new Block[size])
			;
			_blockTextures = TextureHelpers.loadAtlasForBlocks(gl
					, blocks
					, "missing_texture.png"
			);
			
			// Load the secondary atlas for secondary textures.
			_auxBlockTextures = TextureHelpers.loadAuxTextureAtlas(gl
					, "aux_"
					, "missing_texture.png"
			);
			
			// Create the shader program.
			_program = Program.fullyLinkedProgram(gl
					, MiscPeaksHelpers.readUtf8Asset("scene.vert")
					, MiscPeaksHelpers.readUtf8Asset("scene.frag")
					, MeshHelperBufferBuilder.ATTRIBUTE_NAME_SUPERSET
			);
			_uModelMatrix = _program.getUniformLocation("uModelMatrix");
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uTexture1 = _program.getUniformLocation("uTexture1");
			_uSkyLight = _program.getUniformLocation("uSkyLight");
			_uBrightness = _program.getUniformLocation("uBrightness");
			
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = direct.asFloatBuffer();
			_blockModelBounds = _blockModels.buildModelBoundingBoxes();
			_highlightTexture = TextureHelpers.loadSinglePixelImageRGBA(gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
			_defaultHighlightCube = _createOutlinePrism(gl, _program.attributes, meshBuffer, Prism.getBoundsAtOrigin(1.0f, 1.0f, 1.0f), _auxBlockTextures);
			Map<Block, VertexArray> blockModelHighlightCubes = new HashMap<>();
			for (Map.Entry<Block, Prism> elt : _blockModelBounds.entrySet())
			{
				Block key = elt.getKey();
				Prism value = elt.getValue();
				VertexArray specialCube = _createOutlinePrism(gl, _program.attributes, meshBuffer, value, _auxBlockTextures);
				blockModelHighlightCubes.put(key, specialCube);
			}
			_blockModelHighlightCubes = Collections.unmodifiableMap(blockModelHighlightCubes);
		}
		
		public void shutdown(GL20 gl)
		{
			// We don't own _itemAtlas.
			_blockTextures.shutdown(gl);
			_auxBlockTextures.shutdown(gl);
			_program.delete();
			gl.glDeleteTexture(_highlightTexture);
			_defaultHighlightCube.delete(gl);
			for (VertexArray special : _blockModelHighlightCubes.values())
			{
				special.delete(gl);
			}
		}
	}

	/**
	 * Just the scene_itemSlot program and associated uniforms.
	 */
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
		private final VertexArray _itemSlotVertices;
		
		public ItemSlotResources(GL20 gl, ItemTextureAtlas itemAtlas) throws IOException
		{
			// Create the shader program.
			_program = Program.fullyLinkedProgram(gl
					, MiscPeaksHelpers.readUtf8Asset("scene_itemSlot.vert")
					, MiscPeaksHelpers.readUtf8Asset("scene_itemSlot.frag")
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
			float textureSize = itemAtlas.coordinateSize;
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


	private final Environment _environment;
	private final GL20 _gl;
	private final Binding<Float> _screenBrightness;
	private final Resources _resources;
	private final ItemSlotResources _itemSlotResources;
	private final CuboidMeshManager _cuboidMeshes;

	public BlockRenderer(Environment environment, GL20 gl, Binding<Float> screenBrightness, LoadedResources resources)
	{
		_environment = environment;
		_gl = gl;
		_screenBrightness = screenBrightness;
		_resources = resources.blockRenderer();
		_itemSlotResources = resources.blockItemSlotRenderer();
		
		_cuboidMeshes = new CuboidMeshManager(_environment, new CuboidMeshManager.IGpu() {
			@Override
			public VertexArray uploadBuffer(Buffer buffer)
			{
				return buffer.flush(_gl);
			}
			@Override
			public void deleteBuffer(VertexArray array)
			{
				array.delete(_gl);
			}
		}, _resources._program.attributes, _resources._blockModels, _resources._blockTextures, _resources._auxBlockTextures);
	}

	public Map<Block, Prism> getModelBoundingBoxes()
	{
		return _resources._blockModelBounds;
	}

	public void renderOpaqueBlocks(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_resources._program.useProgram();
		_gl.glUniform3f(_resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uSkyLight, skyLightMultiplier);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 2 textures.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glUniform1i(_resources._uTexture1, 1);
		_gl.glActiveTexture(GL20.GL_TEXTURE1);
		
		// We will bind the AUX texture atlas for texture unit 1 in all invocations, but we usually just reference "NONE" where not applicable.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._auxBlockTextures.texture);
		
		// We use the same cuboid set for all of these passes.
		Collection<CuboidMeshManager.CuboidMeshes> cuboids = _cuboidMeshes.viewCuboids();
		
		// Render the opaque cuboid vertices.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._blockTextures.getAtlasTexture());
		for (CuboidMeshManager.CuboidMeshes value : cuboids)
		{
			CuboidAddress key = value.address();
			if (null != value.opaqueArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _resources._uModelMatrix);
				value.opaqueArray().drawAllTriangles(_gl);
			}
		}
		
		// Render the complex models
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._blockModels.getModelAtlasTexture());
		for (CuboidMeshManager.CuboidMeshes value : cuboids)
		{
			CuboidAddress key = value.address();
			if (null != value.modelArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _resources._uModelMatrix);
				value.modelArray().drawAllTriangles(_gl);
			}
		}
	}

	public void renderTransparentBlocks(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_resources._program.useProgram();
		_gl.glUniform3f(_resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uSkyLight, skyLightMultiplier);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 2 textures.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glUniform1i(_resources._uTexture1, 1);
		_gl.glActiveTexture(GL20.GL_TEXTURE1);
		
		// We will bind the AUX texture atlas for texture unit 1 in all invocations, but we usually just reference "NONE" where not applicable.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._auxBlockTextures.texture);
		
		// We use the same cuboid set for all of these passes.
		Collection<CuboidMeshManager.CuboidMeshes> cuboids = _cuboidMeshes.viewCuboids();
		
		// Render the transparent cuboid vertices.
		// Note that we may want to consider rendering this with _gl.glDepthMask(false) but there doesn't seem to be an
		// ideal blending function to make this look right.  Leaving it read-write seems to produce better results, for
		// now.  In the future, more of the non-opaque blocks will be replaced by complex models.
		// Most likely, we will need to slice every cuboid by which of the 6 faces they include, and sort that way, but
		// this may not work for complex models.
		
		// We want to render the water before other transparent blocks since we don't want to see through the water if looking at leaves, for example.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._blockTextures.getAtlasTexture());
		// We will render the water first, since we are usually looking down at it.
		for (CuboidMeshManager.CuboidMeshes value : cuboids)
		{
			CuboidAddress key = value.address();
			if (null != value.waterArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _resources._uModelMatrix);
				value.waterArray().drawAllTriangles(_gl);
			}
		}
		
		// Finally, we can render the normal transparent blocks (although this does means some transparent blocks won't render through water).
		for (CuboidMeshManager.CuboidMeshes value : cuboids)
		{
			CuboidAddress key = value.address();
			if (null != value.transparentArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _resources._uModelMatrix);
				value.transparentArray().drawAllTriangles(_gl);
			}
		}
	}

	public void renderItemSlots(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier)
	{
		_itemSlotResources._program.useProgram();
		_gl.glUniform3f(_itemSlotResources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _itemSlotResources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _itemSlotResources._uProjectionMatrix);
		_gl.glUniform1f(_itemSlotResources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// The texture is just the item texture so we reach into that common atlas.
		_gl.glUniform1i(_itemSlotResources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._itemAtlas.texture);
		
		// We want to set the animation frame from 0.0f - 1.0f based on the time in a second.
		float animationTime = (float)(System.currentTimeMillis() % 2048L) / 2048.0f;
		_gl.glUniform1f(_itemSlotResources._uAnimation, animationTime * TWO_PI_RADIANS);
		
		// Render any item slot locations in this cuboid.
		Collection<CuboidMeshManager.CuboidMeshes> cuboids = _cuboidMeshes.viewCuboids();
		for (CuboidMeshManager.CuboidMeshes value : cuboids)
		{
			if (null != value.itemSlotArray())
			{
				CuboidAddress key = value.address();
				EntityLocation base = key.getBase().toEntityLocation();
				for (CuboidMeshManager.VisibleItemSlot slot : value.itemSlotArray())
				{
					// We precomputed the centre of this when creating it, since it is based on what block it is part of.
					_gl.glUniform3f(_itemSlotResources._uCentre, base.x() + slot.centreX(), base.y() + slot.centreY(), base.z() + slot.baseZ());
					
					// We need to pass in the base texture coordinates of this type.
					float[] uvBase = _resources._itemAtlas.baseOfTexture(slot.item().number());
					_gl.glUniform2f(_itemSlotResources._uUvBase, uvBase[0], uvBase[1]);
					
					// Just draw the square.
					_itemSlotResources._itemSlotVertices.drawAllTriangles(_gl);
				}
			}
		}
	}

	public void renderSelectedBlock(Matrix viewMatrix, Matrix projectionMatrix, Vector eye, float skyLightMultiplier, AbsoluteLocation selectedBlock, Block selectedType)
	{
		// We want to use the perspective projection and depth buffer for the main scene.
		_gl.glEnable(GL20.GL_DEPTH_TEST);
		_gl.glDepthFunc(GL20.GL_LESS);
		_resources._program.useProgram();
		_gl.glUniform3f(_resources._uWorldLightLocation, eye.x(), eye.y(), eye.z());
		viewMatrix.uploadAsUniform(_gl, _resources._uViewMatrix);
		projectionMatrix.uploadAsUniform(_gl, _resources._uProjectionMatrix);
		_gl.glUniform1f(_resources._uSkyLight, skyLightMultiplier);
		_gl.glUniform1f(_resources._uBrightness, _screenBrightness.get());
		Assert.assertTrue(GL20.GL_NO_ERROR == _gl.glGetError());
		
		// This shader uses 2 textures.
		_gl.glUniform1i(_resources._uTexture0, 0);
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glUniform1i(_resources._uTexture1, 1);
		_gl.glActiveTexture(GL20.GL_TEXTURE1);
		
		// We will bind the AUX texture atlas for texture unit 1 in all invocations, but we usually just reference "NONE" where not applicable.
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._auxBlockTextures.texture);
		
		// Highlight the selected entity or block - prioritize the block since the entity will restrict the block check distance.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._highlightTexture);
		_gl.glDepthFunc(GL20.GL_LEQUAL);
		Matrix model = Matrix.translate(selectedBlock.x(), selectedBlock.y(), selectedBlock.z());
		model.uploadAsUniform(_gl, _resources._uModelMatrix);
		VertexArray highlighter = _resources._blockModelHighlightCubes.getOrDefault(selectedType, _resources._defaultHighlightCube);
		highlighter.drawAllTriangles(_gl);
	}

	public Map<CuboidAddress, SparseByteCube> renderFireBlocksAndReturnValidFaces()
	{
		Collection<CuboidMeshManager.CuboidMeshes> cuboids = _cuboidMeshes.viewCuboids();
		
		// TODO:  Track and render the faces of burning blocks.
		
		// We now return the valid fire faces.
		return cuboids.stream()
			.filter((CuboidMeshManager.CuboidMeshes mesh) -> (null != mesh.fireFaces()))
			.collect(Collectors.toMap((CuboidMeshManager.CuboidMeshes mesh) -> mesh.address(), (CuboidMeshManager.CuboidMeshes mesh) -> mesh.fireFaces()))
		;
	}

	public void handleEndOfFrame()
	{
		// Handle any background baking.
		_cuboidMeshes.processBackground();
	}

	public void setCuboid(IReadOnlyCuboidData cuboid, ColumnHeightMap heightMap, Set<BlockAddress> changedBlocks)
	{
		_cuboidMeshes.setCuboid(cuboid, heightMap, changedBlocks);
	}

	public void removeCuboid(CuboidAddress address)
	{
		_cuboidMeshes.removeCuboid(address);
	}

	public void shutdown()
	{
		// Resources are shut down on their own lifecycle.
		_cuboidMeshes.shutdown();
	}


	private static VertexArray _createOutlinePrism(GL20 gl
		, Attribute[] attributes
		, FloatBuffer meshBuffer
		, Prism prism
		, AuxilliaryTextureAtlas auxAtlas
	)
	{
		BufferBuilder builder = new BufferBuilder(meshBuffer, attributes);
		MeshHelperBufferBuilder meshBuilder = new MeshHelperBufferBuilder(builder, MeshHelperBufferBuilder.USE_ALL_ATTRIBUTES);
		SceneMeshHelpers.populateOutlinePrism(gl, meshBuilder, prism, auxAtlas);
		return builder.finishOne().flush(gl);
	}
}

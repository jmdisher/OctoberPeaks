package com.jeffdisher.october.peaks.scene;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.aspects.Environment;
import com.jeffdisher.october.data.ColumnHeightMap;
import com.jeffdisher.october.data.IReadOnlyCuboidData;
import com.jeffdisher.october.peaks.graphics.Matrix;
import com.jeffdisher.october.peaks.graphics.Program;
import com.jeffdisher.october.peaks.graphics.VertexArray;
import com.jeffdisher.october.peaks.LoadedResources;
import com.jeffdisher.october.peaks.graphics.BufferBuilder.Buffer;
import com.jeffdisher.october.peaks.textures.BasicBlockAtlas;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.textures.TextureHelpers;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.types.Prism;
import com.jeffdisher.october.peaks.types.Vector;
import com.jeffdisher.october.types.AbsoluteLocation;
import com.jeffdisher.october.types.Block;
import com.jeffdisher.october.types.BlockAddress;
import com.jeffdisher.october.types.CuboidAddress;
import com.jeffdisher.october.types.Item;
import com.jeffdisher.october.utils.Assert;


/**
 * Responsible for rendering the world within the OpenGL scene - this is things like blocks, items, and liquids, but
 * not entities.
 */
public class BlockRenderer
{
	public static final int BUFFER_SIZE = 1 * 1024 * 1024;

	public static class Resources
	{
		private final TextureAtlas<ItemVariant> _itemAtlas;
		private final BlockModelsAndAtlas _blockModels;
		private final BasicBlockAtlas _blockTextures;
		private final TextureAtlas<SceneMeshHelpers.AuxVariant> _auxBlockTextures;
		private final Program _program;
		private final int _uModelMatrix;
		private final int _uViewMatrix;
		private final int _uProjectionMatrix;
		private final int _uWorldLightLocation;
		private final int _uTexture0;
		private final int _uTexture1;
		private final int _uSkyLight;
		private final Map<Block, Prism> _blockModelBounds;
		private final int _highlightTexture;
		private final VertexArray _defaultHighlightCube;
		private final Map<Block, VertexArray> _blockModelHighlightCubes;
		
		public Resources(Environment environment, GL20 gl, TextureAtlas<ItemVariant> itemAtlas) throws IOException
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
			_auxBlockTextures = TextureHelpers.loadAtlasForVariants(gl
					, "aux_"
					, SceneMeshHelpers.AuxVariant.class
					, "missing_texture.png"
			);
			
			// Create the shader program.
			_program = Program.fullyLinkedProgram(gl
					, _readUtf8Asset("scene.vert")
					, _readUtf8Asset("scene.frag")
					, new String[] {
							"aPosition",
							"aNormal",
							"aTexture0",
							"aTexture1",
							"aBlockLightMultiplier",
							"aSkyLightMultiplier",
					}
			);
			_uModelMatrix = _program.getUniformLocation("uModelMatrix");
			_uViewMatrix = _program.getUniformLocation("uViewMatrix");
			_uProjectionMatrix = _program.getUniformLocation("uProjectionMatrix");
			_uWorldLightLocation = _program.getUniformLocation("uWorldLightLocation");
			_uTexture0 = _program.getUniformLocation("uTexture0");
			_uTexture1 = _program.getUniformLocation("uTexture1");
			_uSkyLight = _program.getUniformLocation("uSkyLight");
			
			ByteBuffer direct = ByteBuffer.allocateDirect(BUFFER_SIZE);
			direct.order(ByteOrder.nativeOrder());
			FloatBuffer meshBuffer = direct.asFloatBuffer();
			_blockModelBounds = _blockModels.buildModelBoundingBoxes();
			_highlightTexture = TextureHelpers.loadSinglePixelImageRGBA(gl, new byte[] {(byte)0xff, (byte)0xff, (byte)0xff, 0x7f});
			_defaultHighlightCube = SceneMeshHelpers.createOutlinePrism(gl, _program.attributes, meshBuffer, Prism.getBoundsAtOrigin(1.0f, 1.0f, 1.0f), _auxBlockTextures);
			Map<Block, VertexArray> blockModelHighlightCubes = new HashMap<>();
			for (Map.Entry<Block, Prism> elt : _blockModelBounds.entrySet())
			{
				Block key = elt.getKey();
				Prism value = elt.getValue();
				VertexArray specialCube = SceneMeshHelpers.createOutlinePrism(gl, _program.attributes, meshBuffer, value, _auxBlockTextures);
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

	private final Environment _environment;
	private final GL20 _gl;
	private final Resources _resources;
	private final CuboidMeshManager _cuboidMeshes;

	public BlockRenderer(Environment environment, GL20 gl, LoadedResources resources)
	{
		_environment = environment;
		_gl = gl;
		_resources = resources.blockRenderer();
		
		
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
		}, _resources._program.attributes, _resources._itemAtlas, _resources._blockModels, _resources._blockTextures, _resources._auxBlockTextures);
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
		
		// Render any dropped items.
		_gl.glActiveTexture(GL20.GL_TEXTURE0);
		_gl.glBindTexture(GL20.GL_TEXTURE_2D, _resources._itemAtlas.texture);
		for (CuboidMeshManager.CuboidMeshes value : cuboids)
		{
			CuboidAddress key = value.address();
			if (null != value.itemsOnGroundArray())
			{
				Matrix model = Matrix.translate(32.0f * key.x(), 32.0f * key.y(), 32.0f * key.z());
				model.uploadAsUniform(_gl, _resources._uModelMatrix);
				value.itemsOnGroundArray().drawAllTriangles(_gl);
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


	private static String _readUtf8Asset(String name)
	{
		return new String(Gdx.files.internal(name).readBytes(), StandardCharsets.UTF_8);
	}
}

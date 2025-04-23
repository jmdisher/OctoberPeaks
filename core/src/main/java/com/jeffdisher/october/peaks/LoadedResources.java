package com.jeffdisher.october.peaks;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.scene.BlockRenderer;
import com.jeffdisher.october.peaks.scene.EntityRenderer;
import com.jeffdisher.october.peaks.textures.TextureAtlas;
import com.jeffdisher.october.peaks.types.ItemVariant;
import com.jeffdisher.october.peaks.ui.GlUi;


/**
 * Just a container for data resources which should only be loaded once and then passed around so that component
 * restarts don't have to load them again, exposing waste and failure states that late into a run.
 */
public record LoadedResources(TextureAtlas<ItemVariant> itemAtlas
		, BlockRenderer.Resources blockRenderer
		, EntityRenderer.Resources entityRenderer
		, SkyBox.Resources skyBox
		, EyeEffect.Resources eyeEffect
		, GlUi.Resources glui
		, AudioManager.Resources audioManager
)
{
	public void shutdown(GL20 gl)
	{
		itemAtlas.shutdown(gl);
		blockRenderer.shutdown(gl);
		entityRenderer.shutdown(gl);
		skyBox.shutdown(gl);
		eyeEffect.shutdown(gl);
		glui.shutdown(gl);
		audioManager.shutdown();
	}
}

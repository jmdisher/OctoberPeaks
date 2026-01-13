package com.jeffdisher.october.peaks;

import com.badlogic.gdx.graphics.GL20;
import com.jeffdisher.october.peaks.scene.BlockRenderer;
import com.jeffdisher.october.peaks.scene.EntityRenderer;
import com.jeffdisher.october.peaks.scene.PassiveRenderer;
import com.jeffdisher.october.peaks.textures.ItemTextureAtlas;
import com.jeffdisher.october.peaks.ui.GlUi;


/**
 * Just a container for data resources which should only be loaded once and then passed around so that component
 * restarts don't have to load them again, exposing waste and failure states that late into a run.
 */
public record LoadedResources(ItemTextureAtlas itemAtlas
		, BlockRenderer.Resources blockRenderer
		, BlockRenderer.ItemSlotResources blockItemSlotRenderer
		, EntityRenderer.Resources entityRenderer
		, SkyBox.Resources skyBox
		, EyeEffect.Resources eyeEffect
		, GlUi.Resources glui
		, AudioManager.Resources audioManager
		, PassiveRenderer.Resources passiveResources
)
{
	public void shutdown(GL20 gl)
	{
		itemAtlas.shutdown(gl);
		blockRenderer.shutdown(gl);
		blockItemSlotRenderer.shutdown(gl);
		entityRenderer.shutdown(gl);
		skyBox.shutdown(gl);
		eyeEffect.shutdown(gl);
		glui.shutdown(gl);
		audioManager.shutdown();
		passiveResources.shutdown(gl);
	}
}

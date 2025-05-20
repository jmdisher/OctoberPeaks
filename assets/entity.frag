#version 100
precision mediump float;

uniform sampler2D uTexture0;
uniform float uDamage;
uniform float uBrightness;

varying float vDiffuseStrength;
varying vec2 vTexture0;


void main()
{
	vec4 texture = texture2D(uTexture0, vTexture0);
	// We might change this into a uniform, in the future.
	float lightingMultiplier = 1.0;
	gl_FragColor = vec4(uBrightness * lightingMultiplier * vDiffuseStrength * texture.rgb, texture.a);
	// We just add the base colour in since it will clamp on overflow.
	gl_FragColor.r += uDamage;
}

#version 100
precision mediump float;

uniform sampler2D uTexture0;
uniform sampler2D uTexture1;
uniform float uBrightness;

varying float vDiffuseStrength;
varying vec2 vTexture0;
varying vec2 vTexture1;
varying float vLightMultiplier;


void main()
{
	vec4 texture0 = texture2D(uTexture0, vTexture0);
	vec4 texture1 = texture2D(uTexture1, vTexture1);
	vec4 texture = mix(texture0, texture1, texture1.a);
	gl_FragColor = vec4(uBrightness * vLightMultiplier * vDiffuseStrength * texture.rgb, texture.a);
}

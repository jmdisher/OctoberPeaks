#version 100
precision mediump float;

uniform sampler2D uTexture0;

varying float vDiffuseStrength;
varying vec2 vTexture0;

void main()
{
	vec4 texture = texture2D(uTexture0, vTexture0);
	gl_FragColor = vec4(vDiffuseStrength * texture.rgb, texture.a);
}

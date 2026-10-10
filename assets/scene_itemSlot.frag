#version 100
precision mediump float;

uniform sampler2D uTexture0;
uniform float uBrightness;

varying vec2 vTexture0;


void main()
{
	vec4 texture0 = texture2D(uTexture0, vTexture0);
	gl_FragColor = vec4(uBrightness * texture0.rgb, texture0.a);
}

#version 100
precision mediump float;

uniform sampler2D uTexture;
uniform vec4 uColour;

varying vec2 vTexture;


void main()
{
	// We treat this texture as monochromatic, just as a multiplier on the colour uniform.
	vec4 texture = texture2D(uTexture, vTexture);
	gl_FragColor = texture.r * uColour;
}

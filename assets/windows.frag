#version 100
precision mediump float;

uniform sampler2D uTexture;

varying vec2 vTexture;


void main()
{
	gl_FragColor = texture2D(uTexture, vTexture);
}

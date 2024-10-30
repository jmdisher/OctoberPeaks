#version 100
precision mediump float;

uniform sampler2D uTexture;
uniform vec2 uTextureBaseOffset;

varying vec2 vTexture;


void main()
{
	gl_FragColor = texture2D(uTexture, vTexture + uTextureBaseOffset);
}

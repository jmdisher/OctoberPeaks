#version 100
precision mediump float;

uniform samplerCube uTexture0;

varying vec3 vTexture0;

void main()
{
	gl_FragColor = textureCube(uTexture0, vTexture0);
}

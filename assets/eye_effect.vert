#version 100

attribute vec2 aPosition;
attribute vec2 aTexture;

varying vec2 vTexture;


void main()
{
	vTexture = aTexture;
	gl_Position = vec4(aPosition.x, aPosition.y, 0.0, 1.0);
}

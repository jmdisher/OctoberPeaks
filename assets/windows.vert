#version 100

uniform vec2 uOffset;
uniform vec2 uScale;

attribute vec2 aPosition;
attribute vec2 aTexture;

varying vec2 vTexture;


void main()
{
	vTexture = aTexture;
	gl_Position = vec4((uScale.x * aPosition.x) + uOffset.x, (uScale.y * aPosition.y) + uOffset.y, 0.0, 1.0);
}

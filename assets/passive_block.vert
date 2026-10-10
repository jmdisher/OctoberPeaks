#version 100

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform vec2 uUvBase;

attribute vec3 aPosition;
attribute vec2 aTexture0;

varying vec2 vTexture0;

void main()
{
	vec3 worldSpaceVertex = vec3(uModelMatrix * vec4(aPosition, 1.0));
	vTexture0 = aTexture0 + uUvBase;
	gl_Position = uProjectionMatrix * uViewMatrix * vec4(worldSpaceVertex, 1.0);
}

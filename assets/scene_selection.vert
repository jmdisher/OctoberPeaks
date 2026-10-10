#version 100

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform float uSkyLight;

attribute vec3 aPosition;
attribute vec2 aTexture0;
attribute vec2 aTexture1;
attribute float aBlockLightMultiplier;
attribute float aSkyLightMultiplier;

varying vec2 vTexture0;
varying vec2 vTexture1;
varying float vLightMultiplier;


void main()
{
	vTexture0 = aTexture0;
	vTexture1 = aTexture1;
	vLightMultiplier = clamp(aBlockLightMultiplier + (aSkyLightMultiplier * uSkyLight), 0.0, 1.0);
	gl_Position = uProjectionMatrix * uViewMatrix * uModelMatrix * vec4(aPosition, 1.0);
}

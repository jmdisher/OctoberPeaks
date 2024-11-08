#version 100

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform vec3 uWorldLightLocation;
uniform float uSkyLight;

attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aTexture0;
attribute vec2 aTexture1;
attribute float aBlockLightMultiplier;
attribute float aSkyLightMultiplier;

varying float vDiffuseStrength;
varying vec2 vTexture0;
varying vec2 vTexture1;
varying float vLightMultiplier;

void main()
{
	vec3 worldSpaceVertex = vec3(uModelMatrix * vec4(aPosition, 1.0));
	vec3 worldSpaceNormal = vec3(uModelMatrix * vec4(aNormal, 0.0));
	float distanceToLight = length(uWorldLightLocation - worldSpaceVertex);
	vec3 vectorToLight = normalize(uWorldLightLocation - worldSpaceVertex);
	vDiffuseStrength = max(dot(worldSpaceNormal, vectorToLight), 0.5);
	vTexture0 = aTexture0;
	vTexture1 = aTexture1;
	vLightMultiplier = clamp(aBlockLightMultiplier + (aSkyLightMultiplier * uSkyLight), 0.0, 1.0);
	gl_Position = uProjectionMatrix * uViewMatrix * uModelMatrix * vec4(aPosition, 1.0);
}

#version 100

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform vec3 uWorldLightLocation;
uniform vec2 uUvBase;

attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aTexture0;
// NOTE:  In order to avoid these "ignored" cases being optimized out, we will reference them in code, in meaningless ways.
attribute vec2 aTexture1_ignored;
attribute float aBlockLightMultiplier_ignored;
attribute float aSkyLightMultiplier_ignored;

varying float vDiffuseStrength;
varying vec2 vTexture0;

void main()
{
	vec3 worldSpaceVertex = vec3(uModelMatrix * vec4(aPosition, 1.0));
	vec3 worldSpaceNormal = vec3(uModelMatrix * vec4(aNormal, 0.0));
	vec3 vectorToLight = normalize(uWorldLightLocation - worldSpaceVertex);
	vDiffuseStrength = max(dot(worldSpaceNormal, vectorToLight), 0.5) + aBlockLightMultiplier_ignored + aSkyLightMultiplier_ignored;
	vTexture0 = aTexture0 + uUvBase + aTexture1_ignored;
	gl_Position = uProjectionMatrix * uViewMatrix * vec4(worldSpaceVertex, 1.0);
}

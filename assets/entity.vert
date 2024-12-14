#version 100

uniform mat4 uModelMatrix;
uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform vec3 uWorldLightLocation;

attribute vec3 aPosition;
attribute vec3 aNormal;
attribute vec2 aTexture0;

varying float vDiffuseStrength;
varying vec2 vTexture0;


void main()
{
	vec3 worldSpaceVertex = vec3(uModelMatrix * vec4(aPosition, 1.0));
	vec3 worldSpaceNormal = vec3(uModelMatrix * vec4(aNormal, 0.0));
	float distanceToLight = length(uWorldLightLocation - worldSpaceVertex);
	vec3 vectorToLight = normalize(uWorldLightLocation - worldSpaceVertex);
	vDiffuseStrength = max(dot(worldSpaceNormal, vectorToLight), 0.5);
	vTexture0 = aTexture0;
	gl_Position = uProjectionMatrix * uViewMatrix * uModelMatrix * vec4(aPosition, 1.0);
}

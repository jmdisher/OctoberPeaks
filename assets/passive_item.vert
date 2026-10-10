#version 100

uniform mat4 uViewMatrix;
uniform mat4 uProjectionMatrix;
uniform vec2 uUvBase;
uniform float uAnimation;
uniform vec3 uCentre;

attribute vec3 aPosition;
attribute vec2 aTexture0;

varying vec2 vTexture0;

void main()
{
	float sinAnimation = sin(uAnimation);
	float cosAnimation = cos(uAnimation);
	float zBounce = 0.2 * sinAnimation + 0.2;
	
	// Note that mat2 is column-major.
	mat2 rotation = mat2(cosAnimation, sinAnimation, -sinAnimation, cosAnimation);
	
	// We interpret the position vertex as relative to the uCentre uniform so we can rotate, directly.
	vec3 updatedPosition = vec3(rotation * vec2(aPosition.x, aPosition.y), aPosition.z + zBounce);
	
	vec3 worldSpaceVertex = updatedPosition + uCentre;
	vTexture0 = aTexture0 + uUvBase;
	gl_Position = uProjectionMatrix * uViewMatrix * vec4(worldSpaceVertex, 1.0);
}
